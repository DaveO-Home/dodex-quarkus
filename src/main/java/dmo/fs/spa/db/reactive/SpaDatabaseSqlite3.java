package dmo.fs.spa.db.reactive;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.DbConfiguration;
import dmo.fs.quarkus.Server;
import dmo.fs.spa.utils.SpaLogin;
import dmo.fs.spa.utils.SpaLoginImpl;
import dmo.fs.utils.DodexUtil;
import io.quarkus.runtime.configuration.ProfileManager;
import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.jdbcclient.JDBCPool;
import io.vertx.reactivex.sqlclient.Row;
import io.vertx.reactivex.sqlclient.RowIterator;
import io.vertx.reactivex.sqlclient.RowSet;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class SpaDatabaseSqlite3 extends DbSqlite3 {
	protected static final Logger logger = LoggerFactory.getLogger(SpaDatabaseSqlite3.class.getName());
	protected Properties dbProperties;
	protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
	protected Map<String, String> dbMap;
	protected JsonNode defaultNode;
	protected String webEnv = !ProfileManager.getLaunchMode().isDevOrTest() ? "prod" : "dev";
	protected DodexUtil dodexUtil = new DodexUtil();
	protected JDBCPool pool;

	public SpaDatabaseSqlite3(Map<String, String> dbOverrideMap, Properties dbOverrideProps) throws IOException {
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

	public SpaDatabaseSqlite3() throws InterruptedException, IOException, SQLException {
		super();
		defaultNode = dodexUtil.getDefaultNode();
		dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
		dbProperties = dodexUtil.mapToProperties(dbMap);
	}

	@Override
	public Future<Void> databaseSetup() {
		if ("dev".equals(webEnv)) {
			// dbMap.put("dbname", "myDbname"); // this wiil be merged into the default map
			DbConfiguration.configureTestDefaults(dbMap, dbProperties);
		} else {
			DbConfiguration.configureDefaults(dbMap, dbProperties); // Prod
		}

		Promise<Void> setupPromise = Promise.promise();
		pool = getPool(dbMap, dbProperties);
		pool.rxGetConnection().doOnSuccess(conn -> conn.rxBegin()
			.doOnSuccess(tx -> conn.query(CHECKLOGINSQL).rxExecute().doOnSuccess(row -> {
				RowIterator<Row> ri = row.iterator();
				String val = null;
				while (ri.hasNext()) {
					val = ri.next().getString(0);
				}
				if (val == null) {
					final String loginSql = getCreateTable("LOGIN");

					Single<RowSet<Row>> crow = conn.query(loginSql).rxExecute()
						.doOnError(err -> {
							logger.info(String.format("Login Table Error: %s", err.getCause().getMessage()));
						}).doOnSuccess(result -> {
							logger.info("Login Table Added.");
						});

					crow.subscribe(result -> {
						conn.rxClose().doOnSubscribe(data -> tx.rxCommit().subscribe()).subscribe();

						try {
							setupSql(pool);
							setupPromise.complete();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}, err -> {
						logger.info(String.format("Set Pool Error: %s", err.getMessage()));
						err.printStackTrace();
					});
				} else {
					conn.rxClose().subscribe();
					try {
						setupSql(pool);
						setupPromise.complete();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}).subscribe()).subscribe()).subscribe();

		return setupPromise.future();
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getPool() {
		return (T) pool;
	}

	@Override
	public SpaLogin createSpaLogin() {
		return new SpaLoginImpl();
	}

	@SuppressWarnings("unchecked")
	protected static <T> T getPool(Map<String, String> dbMap, Properties dbProperties) {

		PoolOptions poolOptions = new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

		JDBCConnectOptions connectOptions;
		connectOptions = new JDBCConnectOptions()
				.setJdbcUrl(dbMap.get("url") + dbMap.get("filename") + "?foreign_keys=on;").setIdleTimeout(1);

		Vertx vertx = Server.vertx;

		return (T) JDBCPool.pool(vertx, connectOptions, poolOptions);
	}

}
