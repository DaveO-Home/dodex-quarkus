package dmo.fs.db.dodex.mariadb;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.dodex.CreateDatabaseImpl;
import dmo.fs.db.dodex.utils.DodexUtil;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowIterator;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class HandicapDatabaseMariadb extends DbMariadb {
    protected final static Logger logger =
      LoggerFactory.getLogger(HandicapDatabaseMariadb.class.getName());
    private MySQLConnectOptions connectOptions;
    private PoolOptions poolOptions;
    protected MySQLPool pool4;
    protected Properties dbProperties;
    protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
    protected Map<String, String> dbMap;
    protected JsonNode defaultNode;
    protected String webEnv = DodexUtil.getEnv();
    protected DodexUtil dodexUtil = new DodexUtil();
    protected Boolean isCreateTables = false;
    protected Promise<String> returnPromise = Promise.promise();

    public HandicapDatabaseMariadb(Map<String, String> dbOverrideMap, Properties dbOverrideProps)
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

        assert dbOverrideMap != null;
        CreateDatabaseImpl.mapMerge(dbMap, dbOverrideMap);
        databaseSetup();
    }

    public HandicapDatabaseMariadb() throws InterruptedException, IOException, SQLException {
        super();

        defaultNode = dodexUtil.getDefaultNode();

        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
        dbProperties = dodexUtil.mapToProperties(dbMap);

        dbProperties.setProperty("foreign_keys", "true");

        databaseSetup();
    }

    public HandicapDatabaseMariadb(Boolean isCreateTables)
      throws IOException {
        super();
        defaultNode = dodexUtil.getDefaultNode();
        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
        dbProperties = dodexUtil.mapToProperties(dbMap);

        dbProperties.setProperty("foreign_keys", "true");
        this.isCreateTables = isCreateTables;
    }

    public Uni<String> checkOnTables() throws InterruptedException, SQLException {
        if (isCreateTables) {
            databaseSetup();
        }
        return returnPromise.future();
    }

    public Promise<Pool> databaseSetup() {
        Promise<Pool> poolPromise = Promise.promise();
        Promise<String> finalPromise = Promise.promise();
        if ("dev".equals(webEnv)) {
            CreateDatabaseImpl.configureTestDefaults(dbMap, dbProperties);
        } else {
            CreateDatabaseImpl.configureDefaults(dbMap, dbProperties);
        }

        connectOptions = new MySQLConnectOptions()
          .setPort(Integer.parseInt(dbMap.get("port"))).setHost(dbMap.get("host"))
          .setDatabase(dbMap.get("dbname")).setUser(dbProperties.getProperty("user"))
          .setPassword(dbProperties.getProperty("password"))
          .setSsl(Boolean.parseBoolean(dbProperties.getProperty("ssl"))).setIdleTimeout(1)
          .setCharset("utf8mb4");

        // Pool options
        poolOptions =
          new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

        // Create the client pool
        pool4 = MySQLPool.pool(DodexUtil.getVertx(), connectOptions, poolOptions);
        String dbName = " and table_schema = '" + dbMap.get("dbname") + "';";

        pool4.withTransaction(conn -> {
            conn.query(CHECKUSERSQL+dbName).execute().onItem().invoke(row -> {
                  RowIterator<Row> ri = row.iterator();
                  Integer val = null;
                  while (ri.hasNext()) {
                      val = ri.next().getInteger(0);
                  }

                  if (val == null) {
                      final String usersSql =
                        getCreateTable("USERS");

                      Uni<RowSet<Row>> crow = conn.query(usersSql).execute().onFailure().invoke(err -> {
                          logger.info(String.format("Users Table Error: %s", err.getMessage()));
                      }).onItem().invoke(result -> {
                          logger.info("Users Table Added.");
                      });

                      crow.subscribeAsCompletionStage().isDone();
                  }
              }).onFailure().invoke(err -> {
                  logger.info(String.format("Users Table Error: %s", err.getMessage()));
              }).flatMap(result -> conn.query(CHECKMESSAGESSQL+dbName).execute().onItem().invoke(row -> {
                  RowIterator<Row> ri = row.iterator();
                  Integer val = null;
                  while (ri.hasNext()) {
                      val = ri.next().getInteger(0);
                  }

                  if (val == null) {
                      final String sql =
                        getCreateTable("MESSAGES");

                      Uni<RowSet<Row>> crow = conn.query(sql).execute().onFailure().invoke(err -> {
                          logger.info(String.format("Messages Table Error: %s", err.getMessage()));
                      }).onItem().invoke(row2 -> {
                          logger.info("Messages Table Added.");
                      });

                      crow.subscribeAsCompletionStage().isDone();
                  }
              }).onFailure().invoke(err -> logger.info(String.format("Messages Table Error: %s", err.getMessage()))))
              .flatMap(result -> conn.query(CHECKUNDELIVEREDSQL+dbName).execute().onItem().invoke(row -> {
                  RowIterator<Row> ri = row.iterator();
                  Integer val = null;
                  while (ri.hasNext()) {
                      val = ri.next().getInteger(0);
                  }

                  if (val == null) {
                      final String sql = getCreateTable("UNDELIVERED");

                      Uni<RowSet<Row>> crow = conn.query(sql).execute().onFailure().invoke(err -> {
                          logger.info(String.format("Undelivered Table Error: %s", err.getMessage()));
                      }).onItem().invoke(row2 -> logger.info("Undelivered Table Added."));

                      crow.subscribeAsCompletionStage().isDone();
                  }
              }).onFailure().invoke(err -> {
                  logger.info(String.format("Messages Table Error: %s", err.getMessage()));
              }))
              .flatMap(result -> conn.query(CHECKGROUPSSQL+dbName).execute().onItem().invoke(row -> {
                  RowIterator<Row> ri = row.iterator();
                  Integer val = null;
                  while (ri.hasNext()) {
                      val = ri.next().getInteger(0);
                  }

                  if (val == null) {
                      final String sql = getCreateTable("GROUPS");

                      Uni<RowSet<Row>> crow = conn.query(sql).execute().onFailure().invoke(err -> {
                          logger.info(String.format("Groups Table Error: %s", err.getMessage()));
                      }).onItem().invoke(row2 -> logger.info("Groups Table Added."));

                      crow.subscribeAsCompletionStage().isDone();
                  }
              }).onFailure().invoke(err -> {
                  logger.info(String.format("Groups Table Error: %s", err.getMessage()));
              }))
              .flatMap(result -> conn.query(CHECKMEMBERSQL+dbName).execute().onItem().invoke(row -> {
                  RowIterator<Row> ri = row.iterator();
                  Integer val = null;
                  while (ri.hasNext()) {
                      val = ri.next().getInteger(0);
                  }

                  if (val == null) {
                      final String sql = getCreateTable("MEMBER");

                      Uni<RowSet<Row>> crow = conn.query(sql).execute().onFailure().invoke(err -> {
                          logger.info(String.format("Member Table Error: %s", err.getMessage()));
                      }).onItem().invoke(row2 -> logger.info("Member Table Added."));

                      crow.subscribeAsCompletionStage().isDone();
                  }
              }).onFailure().invoke(err -> {
                  logger.info(String.format("Undelivered Table Error: %s", err.getMessage()));
              }))
              .flatMap(result -> conn.query(CHECKHANDICAPSQL+dbName).execute().onFailure().invoke(err ->
                  logger.error(String.format("Golfer Table Error: %s", err.getMessage())))
                .onItem().invoke(rows -> {
                    Set<String> names = new HashSet<>();

                    for (Row row : rows) {
                        names.add(row.getString(0));
                    }

                    String sql = getCreateTable("GOLFER");
                    if ((names.contains("golfer"))) {
                        sql = SELECTONE;
                    }

                    conn.query(sql).execute().onFailure().invoke(err -> {
                        logger.error(String.format("Golfer Table Error: %s", err.getMessage()));
                    }).onItem().invoke(row1 -> {
                        if (!names.contains("golfer")) {
                            logger.warn("Golfer Table Added.");
                        }
                        String sql2 = getCreateTable("COURSE");

                        if ((names.contains("COURSE"))) {
                            sql2 = SELECTONE;
                        }
                        conn.query(sql2).execute().onFailure().invoke(err -> {
                            logger.warn(String.format("Course Table Error: %s", err.getMessage()));
                        }).onItem().invoke(row2 -> {
                            if (!names.contains("course")) {
                                logger.warn("Course Table Added.");
                            }
                            String sql3 = getCreateTable("RATINGS");
                            if ((names.contains("ratings"))) {
                                sql3 = SELECTONE;
                            }
                            conn.query(sql3).execute().onFailure().invoke(err -> {
                                logger.warn(String.format("Ratings Table Error: %s", err.getMessage()));
                            }).onItem().invoke(row3 -> {
                                if (!names.contains("ratings")) {
                                    logger.warn("Ratings Table Added.");
                                }
                                String sql4 = getCreateTable("SCORES");
                                if ((names.contains("scores"))) {
                                    sql4 = SELECTONE;
                                }
                                conn.query(sql4).execute().onFailure().invoke(err -> {
                                    logger.error(String.format("Scores Table Error: %s", err.getMessage()));
                                }).onItem().invoke(row4 -> {
                                    if (!names.contains("scores")) {
                                        logger.warn("Scores Table Added.");
                                    }
                                }).onTermination().invoke(() -> {
                                    finalPromise.complete(isCreateTables.toString());
                                    returnPromise.complete(isCreateTables.toString());
                                    conn.close().subscribeAsCompletionStage().isDone();
                                }).subscribeAsCompletionStage().isDone();
                            }).subscribeAsCompletionStage().isDone();
                        }).subscribeAsCompletionStage().isDone();
                    }).subscribeAsCompletionStage().isDone();
                })).subscribeAsCompletionStage().isDone();

            finalPromise.future().onItem().invoke(isTablesCreated -> {
                poolPromise.complete(pool4);
            }).subscribeAsCompletionStage().isDone();

            return null;
        }).subscribeAsCompletionStage().isDone();
        return poolPromise;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getPool4() {
        return (T) pool4;
    }

    @Override
    public void setVertx(io.vertx.mutiny.core.Vertx vertx) {

    }

    @Override
    public io.vertx.mutiny.core.Vertx getVertx() {
        return null;
    }

    @Override
    public void setVertxR(Vertx vertx) {

    }

    @Override
    public Vertx getVertxR() {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConnectOptions() {
        return (T) connectOptions;
    }

    public PoolOptions getPoolOptions() {
        return poolOptions;
    }
}
