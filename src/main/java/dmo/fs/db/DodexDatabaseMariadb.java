package dmo.fs.db;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;

import org.davidmoten.rx.jdbc.ConnectionProvider;
import org.davidmoten.rx.jdbc.Database;
import org.davidmoten.rx.jdbc.pool.NonBlockingConnectionPool;
import org.davidmoten.rx.jdbc.pool.Pools;

import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.reactivex.disposables.Disposable;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class DodexDatabaseMariadb extends DbMariadb {
	private final static Logger logger = LoggerFactory.getLogger(DodexDatabaseMariadb.class.getName());
	protected Disposable disposable;
	protected ConnectionProvider cp;
	protected NonBlockingConnectionPool pool;
	protected Database db;
	protected Properties dbProperties = new Properties();
	protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
	protected Map<String, String> dbMap = new ConcurrentHashMap<>();
	protected JsonNode defaultNode;
	protected String webEnv = DbConfiguration.isProduction() ? "prod": "dev";
	protected DodexUtil dodexUtil = new DodexUtil();

	public DodexDatabaseMariadb(Map<String, String> dbOverrideMap, Properties dbOverrideProps)
			throws InterruptedException, IOException, SQLException {
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
		databaseSetup();
	}

	public DodexDatabaseMariadb() throws InterruptedException, IOException, SQLException {
		super();

		defaultNode = dodexUtil.getDefaultNode();

		dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
		dbProperties = dodexUtil.mapToProperties(dbMap);
		
		dbProperties.setProperty("foreign_keys", "true");

		databaseSetup();
	}

	private void databaseSetup() throws InterruptedException, SQLException {
		if ("dev".equals(webEnv)) {
			DbConfiguration.configureTestDefaults(dbMap, dbProperties);
		} else {
			DbConfiguration.configureDefaults(dbMap, dbProperties); // Using prod (./dodex.db)
		}
		cp = DbConfiguration.getMariadbConnectionProvider();

		pool = Pools.nonBlocking().maxPoolSize(Runtime.getRuntime().availableProcessors() * 5).connectionProvider(cp)
				.build();
		
		db = Database.from(pool);

		Future.future(prom -> {
			db.member().doOnSuccess(c -> {
				Statement stat = c.value().createStatement();

				// stat.executeUpdate("drop table UNDELIVERED");
				// stat.executeUpdate("drop table USERS");
				// stat.executeUpdate("drop table MESSAGES");
				

				String sql = getCreateTable("USERS");
				if (!tableExist(c.value(), "USERS")) {
					stat.executeUpdate(sql);
				}
				sql = getCreateTable("MESSAGES");
				if (!tableExist(c.value(), "MESSAGES")) {
					stat.executeUpdate(sql);
				}
				sql = getCreateTable("UNDELIVERED");
				if (!tableExist(c.value(), "UNDELIVERED")) {
					stat.executeUpdate(sql);
				}
				stat.close();
				c.value().close();
			}).subscribe(result -> {
				prom.complete();
			}, throwable -> {
				logger.error(String.join(ColorUtilConstants.RED, "Error creating database tables: ", throwable.getMessage(), ColorUtilConstants.RESET));
				throwable.printStackTrace();
			});
		});
	}
	
	@Override
	public Database getDatabase() {
		return Database.from(pool);
	}

    @Override
	public String getDbName() throws IOException {
		return dodexUtil.getDefaultDb();
	}

	@Override
	public NonBlockingConnectionPool getPool() {
		return pool;
	}

	@Override
	public MessageUser createMessageUser() {
		return new MessageUserImpl();
	}

	@Override
	public void callSetupSql() throws SQLException {
		setupSql(db);
	}

	// per stack overflow
	private static boolean tableExist(Connection conn, String tableName) throws SQLException {
		boolean exists = false;
		try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null)) {
			while (rs.next()) {
				String name = rs.getString("TABLE_NAME");
				if (name != null && name.equalsIgnoreCase(tableName)) {
					exists = true;
					break;
				}
			}
		}
		return exists;
	}
}