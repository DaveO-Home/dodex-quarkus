package dmo.fs.db.wsnext.h2;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.MessageUser;
import dmo.fs.db.MessageUserImpl;
import dmo.fs.db.wsnext.DbConfiguration;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.quarkus.runtime.configuration.ProfileManager;
import io.smallrye.mutiny.Uni;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.jdbcclient.JDBCPool;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowIterator;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class DodexDatabaseH2 extends DbH2Next {
    protected static final Logger logger = LoggerFactory.getLogger(DodexDatabaseH2.class.getSimpleName());
    protected Properties dbProperties;
    protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
    protected Map<String, String> dbMap;
    protected JsonNode defaultNode;
    protected String webEnv = !ProfileManager.getLaunchMode().isDevOrTest() ? "prod" : "dev";
    protected DodexUtil dodexUtil = new DodexUtil();

    public DodexDatabaseH2(Map<String, String> dbOverrideMap, Properties dbOverrideProps) throws IOException {
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

    public DodexDatabaseH2() throws IOException {
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
        JDBCPool pool = getPool(dbMap, dbProperties);

        pool.getConnection().flatMap(conn -> {
            conn.begin().onItem().invoke(trans -> {
                conn.query(CHECKUSERSQL).execute().flatMap(rows -> {
                    RowIterator<Row> ri = rows.iterator();
                    String val = null;
                    while (ri.hasNext()) {
                        val = ri.next().getString(0);
                    }
                    if (val == null) {
                        final String usersSql = getCreateTable("USERS");
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
            }).flatMap(trans -> {
                conn.query(CHECKMESSAGESSQL).execute().flatMap(rows -> {
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
                        }).onItem().invoke(c -> {
                            logger.info("{}Messages Table Added.{}", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                                ColorUtilConstants.RESET);
                        }).subscribeAsCompletionStage().isDone();
                    }
                    return Uni.createFrom().item(conn);
                }).subscribeAsCompletionStage().isDone();
                return Uni.createFrom().item(conn);
            }).flatMap(trans -> {
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
            }).flatMap(trans -> {
                conn.query(CHECKGROUPSSQL).execute().flatMap(rows -> {
                    RowIterator<Row> ri = rows.iterator();
                    String val = null;
                    while (ri.hasNext()) {
                        val = ri.next().getString(0);
                    }
                    if (val == null) {
                        final String sql = getCreateTable("GROUPS");

                        conn.query(sql).execute().onFailure().invoke(error -> {
                            logger.error("{}Groups Table Error: {}{}", ColorUtilConstants.RED, error,
                                ColorUtilConstants.RESET);
                        }).onItem().invoke(c -> {
                            logger.info("{}Groups Table Added.{}", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                                ColorUtilConstants.RESET);
                        }).subscribeAsCompletionStage().isDone();
                    }
                    return Uni.createFrom().item(conn);
                }).onFailure().invoke(error -> {
                    logger.error("{}Groups Table Error: {}{}", ColorUtilConstants.RED, error,
                        ColorUtilConstants.RESET);
                }).subscribeAsCompletionStage().isDone();
                return Uni.createFrom().item(conn);
            }).flatMap(trans -> {
                conn.query(CHECKMEMBERSQL).execute().flatMap(rows -> {
                    RowIterator<Row> ri = rows.iterator();
                    String val = null;
                    while (ri.hasNext()) {
                        val = ri.next().getString(0);
                    }
                    if (val == null) {
                        final String sql = getCreateTable("MEMBER");

                        conn.query(sql).execute().onFailure().invoke(error -> {
                            logger.error("{}Member Table Error: {}{}", ColorUtilConstants.RED, error,
                                ColorUtilConstants.RESET);
                        }).onItem().invoke(c -> {
                            logger.info("{}Member Table Added.{}", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                                ColorUtilConstants.RESET);
                        }).subscribeAsCompletionStage().isDone();
                    }
                    return Uni.createFrom().item(conn);
                }).onFailure().invoke(error -> {
                    logger.error("{}Member Table Error: {}{}", ColorUtilConstants.RED, error,
                        ColorUtilConstants.RESET);
                }).subscribeAsCompletionStage().isDone();
                return Uni.createFrom().item(conn);
            }).flatMap(trans -> {
                promise.complete(pool);
                conn.close().onFailure().invoke(Throwable::printStackTrace).subscribeAsCompletionStage().isDone();
                return Uni.createFrom().item(pool);
            }).subscribe().asCompletionStage();
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

    protected static JDBCPool getPool(Map<String, String> dbMap, Properties dbProperties) {

        PoolOptions poolOptions = new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

        JDBCConnectOptions connectOptions;

        connectOptions = new JDBCConnectOptions()
            .setJdbcUrl(dbMap.get("url") + dbMap.get("filename"))
            .setUser(dbProperties.getProperty("user"))
            .setPassword(dbProperties.getProperty("password"))
            .setAutoGeneratedKeys(true)
            .setIdleTimeout(1);

        return JDBCPool.pool(DodexUtil.getVertx(), connectOptions, poolOptions);
    }
}
