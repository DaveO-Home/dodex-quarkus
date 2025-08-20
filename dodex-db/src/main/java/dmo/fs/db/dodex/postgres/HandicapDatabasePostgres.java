package dmo.fs.db.dodex.postgres;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.dodex.CreateDatabaseImpl;
import dmo.fs.db.dodex.utils.Constants;
import dmo.fs.db.dodex.utils.DodexUtil;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.pgclient.PgBuilder;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowIterator;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class HandicapDatabasePostgres extends DbPostgres {
    protected final static Logger logger =
      LoggerFactory.getLogger(HandicapDatabasePostgres.class.getName());
    private PgConnectOptions connectOptions;
    private PoolOptions poolOptions;
    protected Pool pool;
    protected Properties dbProperties;
    protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
    protected Map<String, String> dbMap;
    protected JsonNode defaultNode;
    protected String webEnv = DodexUtil.getEnv();
    protected DodexUtil dodexUtil = new DodexUtil();
    protected Boolean isCreateTables = false;
    protected Promise<String> returnPromise = Promise.promise();

    public HandicapDatabasePostgres(Map<String, String> dbOverrideMap, Properties dbOverrideProps)
      throws IOException {
        super();

        defaultNode = dodexUtil.getDefaultNode();
        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
        dbProperties = dodexUtil.mapToProperties(dbMap);

        if (dbOverrideProps != null && !dbOverrideProps.isEmpty()) {
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

    public HandicapDatabasePostgres() throws IOException {
        super();

        defaultNode = dodexUtil.getDefaultNode();
        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
        dbProperties = dodexUtil.mapToProperties(dbMap);

        dbProperties.setProperty("foreign_keys", "true");

        databaseSetup();
    }

    public HandicapDatabasePostgres(Boolean isCreateTables)
      throws IOException {
        super();
        defaultNode = dodexUtil.getDefaultNode();
        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
        dbProperties = dodexUtil.mapToProperties(dbMap);

        dbProperties.setProperty("foreign_keys", "true");
        this.isCreateTables = isCreateTables;
    }

    @Override
    public Uni<String> checkOnTables() {
        if (isCreateTables) {
            databaseSetup();
        }
        return returnPromise.future();
    }

    @Override
    public Promise<Pool> databaseSetup() {
        Promise<Pool> poolPromise = Promise.promise();
        Promise<String> finalPromise = Promise.promise();
        if ("dev".equals(webEnv)) {
            CreateDatabaseImpl.configureTestDefaults(dbMap, dbProperties);
        } else {
            CreateDatabaseImpl.configureDefaults(dbMap, dbProperties);
        }

        connectOptions = new PgConnectOptions().setHost(dbMap.get("host"))
          .setPort(Integer.parseInt(dbMap.get("port")))
          .setUser(dbProperties.getProperty("user"))
          .setPassword(dbProperties.getProperty("password"))
          .setDatabase(dbMap.get("dbname")).setSsl(Boolean.parseBoolean(dbProperties.getProperty("ssl")))
          .setIdleTimeout(1)
        // .setCachePreparedStatements(true)
        ;

        poolOptions =
          new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

//        pool4 = PgPool.pool(DodexUtil.getVertx(), connectOptions, poolOptions);
        pool = PgBuilder
          .pool()
          .with(poolOptions)
          .connectingTo(connectOptions)
          .using(DodexUtil.getVertx())
          .build();

        logger.debug("{}Pool Created: {}", Constants.dodexDebug, pool);

        pool.withTransaction(conn -> {
            conn.query(CHECKUSERSQL).execute().onItem().invoke(row -> {
                  RowIterator<Row> ri = row.iterator();
                  String val = null;
                  while (ri.hasNext()) {
                      val = ri.next().getString(0);
                  }

                  if (val == null) {
                      final String usersSql =
                        getCreateTable("USERS").replaceAll("dummy", dbProperties.get("user").toString());

                      Uni<RowSet<Row>> crow = conn.query(usersSql).execute().onFailure()
                        .invoke(err -> logger.info(String.format("Users Table Error: %s", err.getMessage())))
                        .onItem().invoke(result -> logger.info("Users Table Added."));

                      crow.subscribeAsCompletionStage().isDone();
                  }
              }).onFailure().invoke(err -> logger.info(String.format("Users Table Error: %s", err.getMessage())))
              .flatMap(result -> conn.query(CHECKMESSAGESSQL).execute().onItem().invoke(row -> {
                  RowIterator<Row> ri = row.iterator();
                  String val = null;
                  while (ri.hasNext()) {
                      val = ri.next().getString(0);
                  }

                  if (val == null) {
                      final String sql =
                        getCreateTable("MESSAGES").replaceAll("dummy", dbProperties.get("user").toString());

                      Uni<RowSet<Row>> crow = conn.query(sql).execute().onFailure()
                        .invoke(err -> logger.info(String.format("Messages Table Error: %s", err.getMessage())))
                        .onItem().invoke(row2 -> logger.info("Messages Table Added."));

                      crow.subscribeAsCompletionStage().isDone();
                  }
              }).onFailure().invoke(err -> logger.info(String.format("Messages Table Error: %s", err.getMessage()))))
              .flatMap(result -> conn.query(CHECKUNDELIVEREDSQL).execute().onItem().invoke(row -> {
                  RowIterator<Row> ri = row.iterator();
                  String val = null;
                  while (ri.hasNext()) {
                      val = ri.next().getString(0);
                  }

                  if (val == null) {
                      final String sql = getCreateTable("UNDELIVERED").replaceAll("dummy",
                        dbProperties.get("user").toString());

                      Uni<RowSet<Row>> crow = conn.query(sql).execute().onFailure()
                        .invoke(err -> logger.info(String.format("Undelivered Table Error: %s", err.getMessage())))
                        .onItem().invoke(row2 -> logger.info("Undelivered Table Added."));

                      crow.subscribeAsCompletionStage().isDone();
                  }
              }).onFailure().invoke(err -> logger.info(String.format("Messages Table Error: %s", err.getMessage()))))
              .flatMap(result -> conn.query(CHECKHANDICAPSQL).execute().onFailure().invoke(err ->
                  logger.error(String.format("Golfer Table Error: %s", err.getMessage())))
                .onItem().invoke(rows -> {
                    Set<String> names = new HashSet<>();

                    for (Row row : rows) {
                        names.add(row.getString(0));
                    }
                    String sql =
                      getCreateTable("GOLFER").replaceAll("dummy", dbProperties.get("user").toString());
                    if (names.contains("golfer")) {
                        sql = SELECTONE;
                    }

                    conn.query(sql).execute().onFailure()
                      .invoke(err -> logger.error(String.format("Golfer Table Error: %s", err.getMessage())))
                      .onItem().invoke(row1 -> {
                          if (!names.contains("golfer")) {
                              logger.warn("Golfer Table Added.");
                          }
                          String sql2 =
                            getCreateTable("COURSE").replaceAll("dummy", dbProperties.get("user").toString());
                          if (names.contains("course")) {
                              sql2 = SELECTONE;
                          }
                          conn.query(sql2).execute().onFailure()
                            .invoke(err -> logger.warn(String.format("Course Table Error: %s", err.getMessage())))
                            .onItem().invoke(row2 -> {
                                if (!names.contains("course")) {
                                    logger.warn("Course Table Added.");
                                }
                                String sql3 = getCreateTable("RATINGS").replaceAll("dummy",
                                  dbProperties.get("user").toString());
                                if (names.contains("ratings")) {
                                    sql3 = SELECTONE;
                                }
                                conn.query(sql3).execute().onFailure()
                                  .invoke(err -> logger.warn(String.format("Ratings Table Error: %s", err.getMessage())))
                                  .onItem().invoke(row3 -> {
                                      if (!names.contains("ratings")) {
                                          logger.warn("Ratings Table Added.");
                                      }
                                      String sql4 = getCreateTable("SCORES").replaceAll("dummy",
                                        dbProperties.get("user").toString());
                                      if (names.contains("scores")) {
                                          sql4 = SELECTONE;
                                      }
                                      conn.query(sql4).execute().onFailure()
                                        .invoke(err -> logger.error(String.format("Scores Table Error: %s", err.getMessage())))
                                        .onItem().invoke(row4 -> {
                                            if (!names.contains("scores")) {
                                                logger.warn("Scores Table Added.");
                                            }
                                            String sql5 = getCreateTable("GROUPS").replaceAll("dummy",
                                              dbProperties.get("user").toString());
                                            if (names.contains("groups")) {
                                                sql5 = SELECTONE;
                                            }
                                            conn.query(sql5).execute().onFailure()
                                              .invoke(err -> logger.error(String.format("Groups Table Error: %s", err.getMessage())))
                                              .onItem().invoke(row5 -> {
                                                  if (!names.contains("groups")) {
                                                      logger.warn("Groups Table Added.");
                                                  }
                                                  String sql6 = getCreateTable("MEMBER").replaceAll("dummy",
                                                    dbProperties.get("user").toString());
                                                  if (names.contains("member")) {
                                                      sql6 = SELECTONE;
                                                  }
                                                  conn.query(sql6).execute().onFailure()
                                                    .invoke(err -> logger.error(String.format("Member Table Error: %s", err.getMessage())))
                                                    .onItem().invoke(row6 -> {
                                                        if (!names.contains("member")) {
                                                            logger.warn("Member Table Added.");
                                                        }
                                                    })
                                                    .onTermination().invoke(() -> {
                                                        finalPromise.complete(isCreateTables.toString());
                                                        returnPromise.complete(isCreateTables.toString());
                                                        conn.close().subscribeAsCompletionStage().isDone();
                                                    }).subscribeAsCompletionStage().isDone();
                                              }).subscribeAsCompletionStage().isDone();
                                        }).subscribeAsCompletionStage().isDone();
                                  }).subscribeAsCompletionStage().isDone();
                            }).subscribeAsCompletionStage().isDone();
                      }).subscribeAsCompletionStage().isDone();
                })).subscribeAsCompletionStage().isDone();

            finalPromise.future().onItem().invoke(isTablesCreated -> {
                if (!isCreateTables) {
                    poolPromise.complete(pool);
                }
            }).subscribeAsCompletionStage().isDone();

            return null;
        }).subscribeAsCompletionStage().isDone();
        return poolPromise;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getPool4() {
        return (T) pool;
    }

    @Override
    public void setVertx(io.vertx.mutiny.core.Vertx vertx) {
        //
    }

    @Override
    public io.vertx.mutiny.core.Vertx getVertx() {
        return null;
    }

    @Override
    public void setVertxR(Vertx vertx) {
        //
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

    @Override
    public PoolOptions getPoolOptions() {
        return poolOptions;
    }
}
