package dmo.fs.spa.db;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;

import dmo.fs.quarkus.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.spa.utils.SpaLogin;
import dmo.fs.spa.utils.SpaLoginImpl;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowIterator;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;

public class SpaDatabasePostgres extends DbPostgres {
	protected static final Logger logger = LoggerFactory.getLogger(SpaDatabasePostgres.class.getName());
	protected Properties dbProperties = new Properties();
	protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
	protected Map<String, String> dbMap = new ConcurrentHashMap<>();
	protected JsonNode defaultNode;
	protected String webEnv = Server.isProduction() ? "prod" : "dev";
	protected DodexUtil dodexUtil = new DodexUtil();

	public SpaDatabasePostgres(Map<String, String> dbOverrideMap, Properties dbOverrideProps) throws IOException {
		super();

		defaultNode = dodexUtil.getDefaultNode();

		dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
		dbProperties = dodexUtil.mapToProperties(dbMap);

		if (dbOverrideProps != null) {
			this.dbProperties = dbOverrideProps;
		}
		if (dbOverrideMap != null) {
			this.dbOverrideMap = dbOverrideMap;
		}

		SpaDbConfiguration.mapMerge(dbMap, dbOverrideMap);
		// databaseSetup();
	}

	public SpaDatabasePostgres() throws InterruptedException, IOException, SQLException {
		super();

		defaultNode = dodexUtil.getDefaultNode();
		webEnv = webEnv == null || "prod".equals(webEnv) ? "prod" : "dev";

		dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
		dbProperties = dodexUtil.mapToProperties(dbMap);
		// databaseSetup();
	}

	@Override
	public Future<Void> databaseSetup() {
		if ("dev".equals(webEnv)) {
			// dbMap.put("dbname", "/myDbname"); // this wiil be merged into the default map
			SpaDbConfiguration.configureTestDefaults(dbMap, dbProperties);
		} else {
			SpaDbConfiguration.configureDefaults(dbMap, dbProperties); // Prod
		}

		Promise<Void> setupPromise = Promise.promise();
		PgPool pool = getPool(dbMap, dbProperties);

		pool.getConnection().flatMap(conn -> {
			conn.query(CHECKLOGIN).execute().flatMap(rows -> {
				RowIterator<Row> ri = rows.iterator();
				String val = null;
				while (ri.hasNext()) {
					val = ri.next().getString(0);
				}
				if (val == null) {
					final String usersSql = getCreateTable("LOGIN").replace("dummy",
							dbProperties.get("user").toString());
					conn.query(usersSql).execute().onFailure().invoke(error -> {
						logger.error("{}Login Table Error: {}{}", ColorUtilConstants.RED, error,
								ColorUtilConstants.RESET);
					}).onItem().invoke(c -> {
						logger.info("{}Login Table Added.{}", ColorUtilConstants.BLUE_BOLD_BRIGHT,
								ColorUtilConstants.RESET);
					}).subscribeAsCompletionStage().isDone();
				}
				return Uni.createFrom().item(conn);
			}).onFailure().invoke(error -> {
				logger.error("{}Users Table Error: {}{}", ColorUtilConstants.RED, error, ColorUtilConstants.RESET);
			}).subscribeAsCompletionStage().isDone();
			setupPromise.complete();
			return Uni.createFrom().item(conn);
		}).flatMap(conn -> {
			setupSql(pool);
			conn.close().onFailure().invoke(Throwable::printStackTrace).subscribeAsCompletionStage().isDone();
			return Uni.createFrom().item(pool);
		}).onFailure().invoke(Throwable::printStackTrace).subscribeAsCompletionStage().isDone();

		return setupPromise.future();
	}

	@Override
	public SpaLogin createSpaLogin() {
		return new SpaLoginImpl();
	}

	protected static PgPool getPool(Map<String, String> dbMap, Properties dbProperties) {

		PoolOptions poolOptions = new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

		PgConnectOptions connectOptions;
		connectOptions = new PgConnectOptions()
				.setHost(dbMap.get("host"))
				.setPort(Integer.parseInt(dbMap.get("port")))
				.setUser(dbProperties.getProperty("user")).setPassword(dbProperties.getProperty("password"))
				.setDatabase(dbMap.get("dbname")).setSsl(Boolean.parseBoolean(dbProperties.getProperty("ssl")))
				.setIdleTimeout(1);

		Vertx vertx = DodexUtil.getVertx();
		return PgPool.pool(vertx, connectOptions, poolOptions);
	}
}