package dmo.fs.db;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.quarkus.runtime.configuration.ProfileManager;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowIterator;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class DodexDatabasePostgres extends DbPostgres {
	private static final Logger logger = LoggerFactory.getLogger(DodexDatabasePostgres.class.getName());
	protected Properties dbProperties;
	protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
	protected Map<String, String> dbMap;
	protected JsonNode defaultNode;
	protected String webEnv = !ProfileManager.getLaunchMode().isDevOrTest() ? "prod" : "dev";
	protected DodexUtil dodexUtil = new DodexUtil();

	public DodexDatabasePostgres(Map<String, String> dbOverrideMap, Properties dbOverrideProps) throws IOException {
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

		assert dbOverrideMap != null;
		DbConfiguration.mapMerge(dbMap, dbOverrideMap);
	}

	public DodexDatabasePostgres() throws IOException {
		super();
		defaultNode = dodexUtil.getDefaultNode();

		dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
		dbProperties = dodexUtil.mapToProperties(dbMap);
	}

	@Override
	public Promise<Pool> databaseSetup() {
		if ("dev".equals(webEnv)) {
			// dbMap.put("dbname", "myDbname"); // this will be merged into the default map
			DbConfiguration.configureTestDefaults(dbMap, dbProperties);
		} else {
			DbConfiguration.configureDefaults(dbMap, dbProperties); // Prod
		}

		Promise<Pool> promise = Promise.promise();
		PgPool pool = getPool(dbMap, dbProperties);

		pool.getConnection().flatMap(conn -> {
			conn.query(CHECKUSERSQL).execute().flatMap(rows -> {
				RowIterator<Row> ri = rows.iterator();
				String val = null;
				while (ri.hasNext()) {
					val = ri.next().getString(0);
				}
				if (val == null) {
					final String usersSql = getCreateTable("USERS").replace("dummy",
							dbProperties.get("user").toString());
					conn.query(usersSql).execute().onFailure().invoke(error -> {
						logger.error("{}Users Table Error: {}{}", ColorUtilConstants.RED, error,
								ColorUtilConstants.RESET);
					}).onItem().invoke(c -> {
						logger.info("{}Users Table Added.{}", ColorUtilConstants.BLUE_BOLD_BRIGHT,
								ColorUtilConstants.RESET);
					}).subscribeAsCompletionStage().isDone();
				}
				return Uni.createFrom().item(conn);
			}).onFailure().invoke(error -> {
				logger.error("{}Users Table Error: {}{}", ColorUtilConstants.RED, error, ColorUtilConstants.RESET);
			}).subscribeAsCompletionStage().isDone();
			return Uni.createFrom().item(conn);
		}).flatMap(conn -> {
			conn.query(CHECKMESSAGESSQL).execute().flatMap(rows -> {
				RowIterator<Row> ri = rows.iterator();
				String val = null;
				while (ri.hasNext()) {
					val = ri.next().getString(0);
				}
				if (val == null) {
					final String sql = getCreateTable("MESSAGES").replace("dummy", dbProperties.get("user").toString());
					conn.query(sql).execute().onFailure().invoke(error -> {
						logger.error("{}Messages Table Error: {}{}", ColorUtilConstants.RED, error,
								ColorUtilConstants.RESET);
					}).onItem().invoke(c -> {
						logger.info("{}Messages Table Added.{}", ColorUtilConstants.BLUE_BOLD_BRIGHT,
								ColorUtilConstants.RESET);
					}).subscribeAsCompletionStage().isDone();
				}
				return Uni.createFrom().item(conn);
			}).subscribeAsCompletionStage().isDone();
			return Uni.createFrom().item(conn);
		}).flatMap(conn -> {
			conn.query(CHECKUNDELIVEREDSQL).execute().flatMap(rows -> {
				RowIterator<Row> ri = rows.iterator();
				String val = null;
				while (ri.hasNext()) {
					val = ri.next().getString(0);
				}
				if (val == null) {
					final String sql = getCreateTable("UNDELIVERED").replace("dummy",
							dbProperties.get("user").toString());

					conn.query(sql).execute().onFailure().invoke(error -> {
						logger.error("{}Undelivered Table Error: {}{}", ColorUtilConstants.RED, error,
								ColorUtilConstants.RESET);
					}).onItem().invoke(c -> {
						logger.info("{}Undelivered Table Added.{}", ColorUtilConstants.BLUE_BOLD_BRIGHT,
								ColorUtilConstants.RESET);
					}).subscribeAsCompletionStage().isDone();
				}
				return Uni.createFrom().item(conn);
			}).onFailure().invoke(error -> {
				logger.error("{}Undelivered Table Error: {}{}", ColorUtilConstants.RED, error,
						ColorUtilConstants.RESET);
			}).subscribeAsCompletionStage().isDone();
			return Uni.createFrom().item(conn);
		}).flatMap(conn -> {
			promise.complete(pool);
			conn.close().onFailure().invoke(Throwable::printStackTrace).subscribeAsCompletionStage().isDone();
			return Uni.createFrom().item(pool);
		}).subscribeAsCompletionStage().isDone();

		return promise;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getPool() {
		return (T) pool;
	}

	@Override
	public MessageUser createMessageUser() {
		return new MessageUserImpl();
	}

	private static PgPool getPool(Map<String, String> dbMap, Properties dbProperties) {

		PoolOptions poolOptions = new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

		PgConnectOptions connectOptions;

		connectOptions = new PgConnectOptions()
			.setHost(dbMap.get("host"))
			.setPort(Integer.parseInt(dbMap.get("port")))
			.setUser(dbProperties.getProperty("user"))
			.setPassword(dbProperties.getProperty("password"))
			.setDatabase(dbMap.get("dbname"))
			.setSsl(Boolean.parseBoolean(dbProperties.getProperty("ssl")))
			.setIdleTimeout(1);

		return PgPool.pool(DodexUtil.getVertx(), connectOptions, poolOptions);
	}
}
