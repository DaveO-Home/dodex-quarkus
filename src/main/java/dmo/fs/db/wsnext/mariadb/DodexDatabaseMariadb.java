package dmo.fs.db.wsnext.mariadb;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.reactive.DbConfiguration;
import dmo.fs.db.MessageUser;
import dmo.fs.db.MessageUserImpl;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.quarkus.runtime.configuration.ProfileManager;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowIterator;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class DodexDatabaseMariadb extends DbMariadb {
	protected static final Logger logger = LoggerFactory.getLogger(DodexDatabaseMariadb.class.getName());
	protected Properties dbProperties = new Properties();
	protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
	protected Map<String, String> dbMap = new ConcurrentHashMap<>();
	protected JsonNode defaultNode;
	protected String webEnv = !ProfileManager.getLaunchMode().isDevOrTest() ? "prod" : "dev";
	protected DodexUtil dodexUtil = new DodexUtil();

	public DodexDatabaseMariadb(Map<String, String> dbOverrideMap, Properties dbOverrideProps) throws IOException {
		super();

		defaultNode = dodexUtil.getDefaultNode();

		dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
		dbProperties = dodexUtil.mapToProperties(dbMap);

		if (dbOverrideProps != null && dbOverrideProps.size() > 0) {
			this.dbProperties = dbOverrideProps;
		}
		if (dbOverrideMap != null) {
			this.dbOverrideMap = dbOverrideMap;
		}

		dbProperties.setProperty("foreign_keys", "true");

		DbConfiguration.mapMerge(dbMap, dbOverrideMap);
	}

	@Override
	public <T> T getPool4() {
		return null;
	}

	public DodexDatabaseMariadb() throws InterruptedException, IOException, SQLException {
		super();

		defaultNode = dodexUtil.getDefaultNode();

		dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
		dbProperties = dodexUtil.mapToProperties(dbMap);

		dbProperties.setProperty("foreign_keys", "true");
	}

	@Override
	public Promise<Pool> databaseSetup() {
		if ("dev".equals(webEnv)) {
			DbConfiguration.configureTestDefaults(dbMap, dbProperties);
		} else {
			DbConfiguration.configureDefaults(dbMap, dbProperties); // Prod
		}

		Promise<Pool> promise = Promise.promise();
		MySQLPool pool = getPool(dbMap, dbProperties);

		pool.getConnection().flatMap(conn -> {
			conn.query(CHECKUSERSQL).execute().flatMap(rows -> {
				RowIterator<Row> ri = rows.iterator();
				Integer val = null;
				while (ri.hasNext()) {
					val = ri.next().getInteger(0);
				}
				if (val == null) {
					final String usersSql = getCreateTable("USERS");
					conn.query(usersSql).execute().onItem().invoke(r -> {
						logger.info("{}Users Table Added.{}", ColorUtilConstants.BLUE_BOLD_BRIGHT,
								ColorUtilConstants.RESET);
					}).onFailure().invoke(error -> {
						logger.error("{}Users Table Error: {}{}", ColorUtilConstants.RED, error,
								ColorUtilConstants.RESET);
					}).subscribeAsCompletionStage().isDone();
				}
				return Uni.createFrom().item(conn);
			}).onFailure().invoke(error -> {
				logger.error("{}Users Check Table Error: {}{}", ColorUtilConstants.RED, error,
						ColorUtilConstants.RESET);
			}).subscribeAsCompletionStage().isDone();
			return Uni.createFrom().item(conn);
		}).flatMap(conn -> {
			conn.query(CHECKMESSAGESSQL).execute().flatMap(rows -> {
				RowIterator<Row> ri = rows.iterator();
				Integer val = null;
				while (ri.hasNext()) {
					val = ri.next().getInteger(0);
				}
				if (val == null) {
					final String sql = getCreateTable("MESSAGES");
					conn.query(sql).execute().onItem().invoke(r -> {
						logger.info("{}Messages Table Added.{}", ColorUtilConstants.BLUE_BOLD_BRIGHT,
								ColorUtilConstants.RESET);
					}).onFailure().invoke(error -> {
						logger.error("{}Messages Create Table Error: {}{}", ColorUtilConstants.RED, error,
								ColorUtilConstants.RESET);
					}).subscribeAsCompletionStage().isDone();
				}
				return Uni.createFrom().item(conn);
			}).subscribeAsCompletionStage().isDone();
			return Uni.createFrom().item(conn);
		}).flatMap(conn -> {
			conn.query(CHECKUNDELIVEREDSQL).execute().flatMap(rows -> {
				RowIterator<Row> ri = rows.iterator();
				Integer val = null;
				while (ri.hasNext()) {
					val = ri.next().getInteger(0);
				}
				if (val == null) {
					final String sql = getCreateTable("UNDELIVERED");

					conn.query(sql).execute().onItem().invoke(r -> {
						logger.info("{}Undelivered Table Added.{}", ColorUtilConstants.BLUE_BOLD_BRIGHT,
								ColorUtilConstants.RESET);
					}).onFailure().invoke(error -> {
						logger.error("{}Creating Undelivered Table Error: {}{}", ColorUtilConstants.RED, error,
								ColorUtilConstants.RESET);
					}).subscribeAsCompletionStage().isDone();
				}
				return Uni.createFrom().item(conn);
			}).onFailure().invoke(error -> {
				logger.error("{}Check Undelivered Table Error: {}{}", ColorUtilConstants.RED, error,
						ColorUtilConstants.RESET);
			}).subscribeAsCompletionStage().isDone();
			return Uni.createFrom().item(conn);
		}).flatMap(conn -> {
			promise.complete(pool);
			conn.close().onFailure().invoke(Throwable::printStackTrace).subscribeAsCompletionStage().isDone();
			return null;
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

	protected static MySQLPool getPool(Map<String, String> dbMap, Properties dbProperties) {

		PoolOptions poolOptions = new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

		MySQLConnectOptions connectOptions;
		connectOptions = new MySQLConnectOptions()
			.setHost(dbMap.get("host"))
			.setPort(Integer.parseInt(dbMap.get("port")))
			.setUser(dbProperties.getProperty("user"))
			.setPassword(dbProperties.getProperty("password"))
			.setDatabase(dbMap.get("dbname"))
			.setSsl(Boolean.valueOf(dbProperties.getProperty("ssl")))
			.setCharset("utf8mb4").setIdleTimeout(1);

		return MySQLPool.pool(DodexUtil.getVertx(), connectOptions, poolOptions);
	}
}