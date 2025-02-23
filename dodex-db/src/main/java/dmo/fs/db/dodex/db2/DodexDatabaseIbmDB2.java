package dmo.fs.db.dodex.db2;

import com.fasterxml.jackson.databind.JsonNode;

import dmo.fs.db.dodex.CreateDatabaseImpl;
import dmo.fs.db.dodex.utils.ColorUtilConstants;
import dmo.fs.db.dodex.utils.DodexUtil;
import io.smallrye.mutiny.Uni;
import io.vertx.db2client.DB2ConnectOptions;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.db2client.DB2Builder;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowIterator;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class DodexDatabaseIbmDB2 extends DbIbmDB2 {
    protected static final Logger logger = LoggerFactory.getLogger(DodexDatabaseIbmDB2.class.getName());
    protected Properties dbProperties;
    protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
    protected Map<String, String> dbMap;
    protected JsonNode defaultNode;
    protected String webEnv = DodexUtil.getEnv();
    protected DodexUtil dodexUtil = new DodexUtil();
    protected Pool pool;
    protected DB2ConnectOptions connectOptions;
    protected PoolOptions poolOptions;

    public DodexDatabaseIbmDB2(Map<String, String> dbOverrideMap, Properties dbOverrideProps) throws IOException {
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
        CreateDatabaseImpl.mapMerge(dbMap, dbOverrideMap);
        databaseSetup().futureAndForget();
    }

    public DodexDatabaseIbmDB2() throws InterruptedException, IOException, SQLException {
        super();
        defaultNode = dodexUtil.getDefaultNode();

        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
        dbProperties = dodexUtil.mapToProperties(dbMap);
        databaseSetup().futureAndForget();
    }

    @Override
    public Uni<String> checkOnTables() throws InterruptedException, SQLException {
        return Uni.createFrom().item("true");
    }

    @Override
    public Promise<Pool> databaseSetup() {
        if ("dev".equals(webEnv)) {
            CreateDatabaseImpl.configureTestDefaults(dbMap, dbProperties);
        } else {
            CreateDatabaseImpl.configureDefaults(dbMap, dbProperties); // Prod
        }

        Promise<Pool> promise = Promise.promise();
        pool = getPool(dbMap, dbProperties);

        pool.getConnection().flatMap(conn -> {
            conn.query(CHECKUSERSQL).execute().flatMap(rows -> {
                RowIterator<Row> ri = rows.iterator();
                String val = null;
                while (ri.hasNext()) {
                    val = ri.next().getString(0);
                }

                if (val == null) {
                    final String usersSql = getCreateTable("USERS");
                    conn.query(usersSql).execute().onFailure().invoke(error -> {
                        logger.error("{}Users Table Error0: {}{}", ColorUtilConstants.RED, error,
                          ColorUtilConstants.RESET);
                    }).invoke(c -> {
                        logger.info("{}Users Table Added.{}", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                          ColorUtilConstants.RESET);
                    }).subscribeAsCompletionStage().isDone();
                }
                return Uni.createFrom().item(conn);
            }).onFailure().invoke(error -> {
                logger.error("{}Users Table Error1: {}{}", ColorUtilConstants.RED, error, ColorUtilConstants.RESET);
            }).subscribeAsCompletionStage().isDone();
            return Uni.createFrom().item(conn);
        }).flatMap(conn -> {
            conn.query(CHECKMESSAGESQL).execute().flatMap(rows -> {
                RowIterator<Row> ri = rows.iterator();
                String val = null;
                while (ri.hasNext()) {
                    val = ri.next().getString(0);
                }
                if (val == null) {
                    final String sql = getCreateTable("MESSAGES");
                    conn.query(sql).execute().onFailure().invoke(error -> {
                        logger.error("{}Messages Table Error: {}{}", ColorUtilConstants.RED, error,
                          ColorUtilConstants.RESET);
                    }).invoke(c -> {
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
                    final String sql = getCreateTable("UNDELIVERED");

                    conn.query(sql).execute().onFailure().invoke(error -> {
                        logger.error("{}Undelivered Table Error: {}{}", ColorUtilConstants.RED, error,
                          ColorUtilConstants.RESET);
                    }).invoke(c -> {
                        logger.info("{}Undelivered Table Added.{}", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                          ColorUtilConstants.RESET);
                    }).subscribeAsCompletionStage().isDone();
                }
                return Uni.createFrom().item(conn);
            }).onFailure().invoke(error -> {
                logger.error("{}Undelivered Table Error1: {}{}", ColorUtilConstants.RED, error,
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

    @SuppressWarnings("unchecked")
    public <T> T getConnectOptions() {
        return (T) connectOptions;
    }

    public PoolOptions getPoolOptions() {
        return poolOptions;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getPool4() {
        return (T) pool;
    }

    @Override
    public void setVertx(Vertx vertx) {
    }

    @Override
    public Vertx getVertx() {
        return DodexUtil.getVertx();
    }

    @Override
    public void setVertxR(io.vertx.reactivex.core.Vertx vertx) {
    }

    @Override
    public io.vertx.reactivex.core.Vertx getVertxR() {
        return DodexUtil.getVertxR();
    }

    protected Pool getPool(Map<String, String> dbMap, Properties dbProperties) {

        poolOptions = new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

        connectOptions = new DB2ConnectOptions()
          .setHost(dbMap.get("host"))
          .setPort(Integer.parseInt(dbMap.get("port")))
          .setUser(dbProperties.getProperty("user"))
          .setPassword(dbProperties.getProperty("password"))
          .setDatabase(dbMap.get("dbname"))
          .setSsl(Boolean.parseBoolean(dbProperties.getProperty("ssl")));

        return DB2Builder
          .pool()
          .with(poolOptions)
          .connectingTo(connectOptions)
          .using(DodexUtil.getVertx())
          .build();
    }
}
