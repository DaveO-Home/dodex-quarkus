package dmo.fs.spa.db.reactive;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;

import dmo.fs.quarkus.Server;
import io.quarkus.runtime.configuration.ProfileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.db.DbConfiguration;
import dmo.fs.spa.utils.SpaLogin;
import dmo.fs.spa.utils.SpaLoginImpl;
import dmo.fs.utils.DodexUtil;
import io.reactivex.Completable;
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

public class SpaDatabaseH2 extends DbH2 {
	private static final Logger logger = LoggerFactory.getLogger(SpaDatabaseH2.class.getName());
	protected Properties dbProperties = new Properties();
	protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
	protected Map<String, String> dbMap = new ConcurrentHashMap<>();
	protected JsonNode defaultNode;
	protected String webEnv = !ProfileManager.getLaunchMode().isDevOrTest() ? "prod" : "dev";
	protected DodexUtil dodexUtil = new DodexUtil();
	protected JDBCPool pool;

	public SpaDatabaseH2(Map<String, String> dbOverrideMap, Properties dbOverrideProps) throws IOException {
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

	public SpaDatabaseH2() throws InterruptedException, IOException, SQLException {
		super();
		defaultNode = dodexUtil.getDefaultNode();
		dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
		dbProperties = dodexUtil.mapToProperties(dbMap);
	}

	@Override
	public Future<Void> databaseSetup() {
		if ("dev".equals(webEnv)) {
			DbConfiguration.configureTestDefaults(dbMap, dbProperties);
		} else {
			DbConfiguration.configureDefaults(dbMap, dbProperties); // Prod
		}

		Promise<Void> setupPromise = Promise.promise();
		pool = getPool(dbMap, dbProperties);

		Completable completable = pool.rxGetConnection().flatMapCompletable(conn -> conn.rxBegin()
				.flatMapCompletable(tx -> conn.query(CHECKLOGINSQL).rxExecute().doOnSuccess(row -> {
					RowIterator<Row> ri = row.iterator();
					String val = null;
					while (ri.hasNext()) {
						val = ri.next().getString(0);
					}
					if (val == null) {
						final String usersSql = getCreateTable("LOGIN");

						Single<RowSet<Row>> crow = conn.query(usersSql).rxExecute().doOnError(err -> {
							logger.info(String.format("Login Table Create Error: %s", err.getMessage()));
						}).doOnSuccess(result -> logger.warn("Login Table Added."));

						crow.subscribe(result -> {
							conn.rxClose().doOnSubscribe(res -> tx.rxCommit().subscribe()).subscribe();
						}, err -> {
							logger.error(String.format("Login Table Result Error: %s", err.getMessage()));
							err.printStackTrace();
						});
					} else {
						conn.rxClose().doOnSubscribe(res -> tx.rxCommit().subscribe()).subscribe();
					}
				}).doOnError(err -> {
					logger.error(String.format("Login Table Query Error: %s", err.getMessage()));

				}).doOnError(Throwable::printStackTrace).flatMapCompletable(res -> Completable.complete())));

		completable.subscribe(() -> {
			try {
				setupSql(pool);
				setupPromise.complete();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}, err -> {
			logger.error(String.format("Tables Create Error: %s", err.getMessage()));
			err.printStackTrace();
		});

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
	private static <T> T getPool(Map<String, String> dbMap, Properties dbProperties) {

		PoolOptions poolOptions = new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

		JDBCConnectOptions connectOptions;
		connectOptions = new JDBCConnectOptions().setJdbcUrl(dbMap.get("url") + dbMap.get("filename"))
				.setUser(dbProperties.getProperty("user")).setPassword(dbProperties.getProperty("password"))
				.setIdleTimeout(1);

		Vertx vertx = Server.vertx;

		return (T) JDBCPool.pool(vertx, connectOptions, poolOptions);
	}

}
