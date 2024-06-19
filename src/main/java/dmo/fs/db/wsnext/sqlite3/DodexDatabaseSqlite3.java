package dmo.fs.db.wsnext.sqlite3;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.reactive.DbConfiguration;
import dmo.fs.db.MessageUser;
import dmo.fs.db.MessageUserImpl;
import dmo.fs.quarkus.Server;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.smallrye.mutiny.Uni;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.jdbcclient.JDBCPool;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowIterator;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
/*
    This is not used as the mutiny config locks the sqlite database on quarkus shutdown.(pool not closing properly?)
    See ...db.reactive for the reactive configs.
 */
public class DodexDatabaseSqlite3 extends DbSqlite3 {
    protected static final Logger logger = LoggerFactory.getLogger(DodexDatabaseSqlite3.class.getName());
    protected Properties dbProperties;
    protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
    protected Map<String, String> dbMap;
    protected JsonNode defaultNode;
    protected String webEnv = Server.isProduction() ? "prod" : "dev";
    protected DodexUtil dodexUtil = new DodexUtil();
    protected JDBCPool pool;

    public DodexDatabaseSqlite3(Map<String, String> dbOverrideMap, Properties dbOverrideProps) throws IOException {
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

        DbConfiguration.mapMerge(dbMap, dbOverrideMap);
    }

    public DodexDatabaseSqlite3() throws IOException {
        super();
        defaultNode = dodexUtil.getDefaultNode();

        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
        dbProperties = dodexUtil.mapToProperties(dbMap);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Promise<Pool> databaseSetup() {
        if ("dev".equals(webEnv)) {
            DbConfiguration.configureTestDefaults(dbMap, dbProperties);
        } else {
            DbConfiguration.configureDefaults(dbMap, dbProperties); // Prod
        }

        Promise<Pool> promise = Promise.promise();

        pool = getPool(dbMap, dbProperties);

        CompletableFuture<SqlConnection> completable = pool.getConnection().invoke(conn -> {
            conn.begin().onItem().invoke(trans -> {
                    conn.query(CHECKUSERSQL).execute().invoke(rows -> {
                        RowIterator<Row> ri = rows.iterator();
                        String val = null;
                        while (ri.hasNext()) {
                            val = ri.next().getString(0);
                        }
                        if (val == null) {
                            final String usersSql = getCreateTable("USERS");
                            conn.query(usersSql).execute()
                                .onFailure().invoke(error ->
                                    logger.error("{}Users Table Error1: {}{}", ColorUtilConstants.RED, error,
                                        ColorUtilConstants.RESET))
                                .subscribeAsCompletionStage()
                                .thenRun(() -> {
                                    logger.info("{}Users Table Added.{}", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                                        ColorUtilConstants.RESET);
                                    trans.commit().subscribeAsCompletionStage().isDone();
                                });
                        }
                    }).onFailure().invoke(error -> {
                        logger.error("{}Users Table Error2: {}{}", ColorUtilConstants.RED, error, ColorUtilConstants.RESET);
                    }).subscribeAsCompletionStage().isDone();
                })
                .invoke(trans -> {
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
                                trans.commit().subscribeAsCompletionStage().isDone();
                            }).subscribeAsCompletionStage().isDone();
                        }
                        return Uni.createFrom().item(conn);
                    }).subscribeAsCompletionStage().isDone();
                })
                .invoke(trans -> {
                    conn.query(CHECKUNDELIVEREDSQL).execute().invoke(rows -> {
                        RowIterator<Row> ri = rows.iterator();
                        String val = null;
                        while (ri.hasNext()) {
                            val = ri.next().getString(0);
                        }
                        if (val == null) {
                            final String sql = getCreateTable("UNDELIVERED");

                            conn.query(sql).execute().onFailure().invoke(error -> {
                                logger.error("{}Undelivered Table Error1: {}{}", ColorUtilConstants.RED, error,
                                    ColorUtilConstants.RESET);
                            }).onItem().invoke(c -> {
                                logger.info("{}Undelivered Table Added.{}", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                                    ColorUtilConstants.RESET);
                                trans.commit().subscribeAsCompletionStage().isDone();
                            }).subscribeAsCompletionStage().isDone();
                        }
                    }).onFailure().invoke(error -> {
                        logger.error("{}Undelivered Table Error2: {}{}", ColorUtilConstants.RED, error,
                            ColorUtilConstants.RESET);
                    }).subscribeAsCompletionStage().isDone();
                })
                .invoke(trans -> {
                    conn.query(CHECKGROUPSSQL).execute().invoke(rows -> {
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
                                trans.commit().subscribeAsCompletionStage().isDone();
                            }).subscribeAsCompletionStage().isDone();
                        }
                    }).onFailure().invoke(error -> {
                        logger.error("{}Groups Table Error: {}{}", ColorUtilConstants.RED, error,
                            ColorUtilConstants.RESET);
                    }).subscribeAsCompletionStage().isDone();
                })
                .invoke(trans -> {
                    conn.query(CHECKMEMBERSQL).execute().invoke(rows -> {
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
                            }).onItem().invoke(rs -> {
                                logger.info("{}Member Table Added.{}", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                                    ColorUtilConstants.RESET);
                                trans.commit().subscribeAsCompletionStage().isDone();
                            }).subscribeAsCompletionStage().isDone();
                        }
                    }).onFailure().invoke(error -> {
                        logger.error("{}Member Table Error: {}{}", ColorUtilConstants.RED, error,
                            ColorUtilConstants.RESET);
                    }).subscribeAsCompletionStage().isDone();
                })
                .invoke(trans -> {
                    trans.completion().onItem().invoke(() ->
                            conn.close().onFailure().invoke(Throwable::printStackTrace)
                                .subscribeAsCompletionStage().thenRun(() -> promise.complete(pool)))
                        .subscribeAsCompletionStage().isDone();

                }).subscribe().asCompletionStage();
        }).subscribeAsCompletionStage();

        completable.thenRun(() -> {
            try {
                setupSql(pool);
                promise.complete(pool);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return promise;
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

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getPool() {
        return (T) pool;
    }

    @Override
    public MessageUser createMessageUser() {
        return new MessageUserImpl();
    }

}
