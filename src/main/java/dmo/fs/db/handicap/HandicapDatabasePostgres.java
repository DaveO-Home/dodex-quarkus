package dmo.fs.db.handicap;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.handicap.utils.DodexUtil;
import io.quarkus.runtime.configuration.ProfileManager;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowIterator;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.pgclient.PgConnectOptions;
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

public class HandicapDatabasePostgres extends DbPostgres {
  protected final static Logger logger =
      LoggerFactory.getLogger(HandicapDatabasePostgres.class.getName());
  protected PgPool pool4;
  protected Properties dbProperties;
  protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
  protected Map<String, String> dbMap;
  protected JsonNode defaultNode;
  protected String webEnv = !ProfileManager.getLaunchMode().isDevOrTest() ? "prod" : "dev";
  protected DodexUtil dodexUtil = new DodexUtil();
  protected static Boolean isCreateTables = false;
  protected Promise<String> returnPromise = Promise.promise();

  public HandicapDatabasePostgres(Map<String, String> dbOverrideMap, Properties dbOverrideProps)
      throws IOException {
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
    DbConfiguration.mapMerge(dbMap, dbOverrideMap);
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
    HandicapDatabasePostgres.isCreateTables = isCreateTables;
  }

  public Uni<String> checkOnTables() {
    if (isCreateTables) {
      databaseSetup();
    }
    return returnPromise.future();
  }

  protected void databaseSetup() {
    Promise<String> finalPromise = Promise.promise();
    if ("dev".equals(webEnv)) {
      DbConfiguration.configureTestDefaults(dbMap, dbProperties);
    } else {
      DbConfiguration.configureDefaults(dbMap, dbProperties);
    }

    PgConnectOptions connectOptions;

    connectOptions = new PgConnectOptions().setHost(dbMap.get("host"))
        .setPort(Integer.parseInt(dbMap.get("port")))
        .setUser(dbProperties.getProperty("user"))
        .setPassword(dbProperties.getProperty("password"))
        .setDatabase(dbMap.get("dbname")).setSsl(Boolean.parseBoolean(dbProperties.getProperty("ssl")))
        .setIdleTimeout(1)
    // .setCachePreparedStatements(true)
    ;

    PoolOptions poolOptions =
        new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

    setPgConnectOptions(connectOptions);
    setPoolOptions(poolOptions);

    pool4 = PgPool.pool(DodexUtil.getVertx(), connectOptions, poolOptions);

    pool4.withTransaction(conn -> {
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
                if ((names.contains("golfer"))) {
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
                      if ((names.contains("course"))) {
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
                            if ((names.contains("ratings"))) {
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
                                  if ((names.contains("scores"))) {
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
                                        if ((names.contains("groups"))) {
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
                                              if ((names.contains("member"))) {
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
          try {
            setupSql(pool4);
          } catch (IOException | SQLException e) {
            throw new RuntimeException(e);
          }
        }
      }).subscribeAsCompletionStage().isDone();

      return null;
    }).subscribeAsCompletionStage().isDone();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getPool4() {
    return (T) pool4;
  }

  @Override
  public void setVertxR(io.vertx.rxjava3.core.Vertx vertx) {

  }

  @Override
  public io.vertx.rxjava3.core.Vertx getVertxR() {
    return null;
  }
}
