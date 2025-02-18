package dmo.fs.db.generate;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.generate.utils.DodexUtil;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Future;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.rxjava3.core.Promise;
import io.vertx.rxjava3.jdbcclient.JDBCPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
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

public class HandicapDatabaseH2 extends DbH2 {
  private final static Logger logger = LoggerFactory.getLogger(HandicapDatabaseH2.class.getName());
  protected JDBCPool pool4;
  protected Properties dbProperties;
  protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
  protected Map<String, String> dbMap;
  protected JsonNode defaultNode;
  protected String webEnv = "dev"; // System.getenv("VERTXWEB_ENVIRONMENT");
  protected DodexUtil dodexUtil = new DodexUtil();
  protected Boolean isCreateTables = false;
  protected Promise<String> returnPromise = Promise.promise();

  public HandicapDatabaseH2(Map<String, String> dbOverrideMap, Properties dbOverrideProps)
      throws IOException {
    super();

    defaultNode = dodexUtil.getDefaultNode();

    webEnv = webEnv == null || "prod".equals(webEnv) ? "prod" : "dev";

    dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
    dbProperties = dodexUtil.mapToProperties(dbMap);

    if (dbOverrideProps != null && !dbOverrideProps.isEmpty()) {
      this.dbProperties = dbOverrideProps;
    }
    if (dbOverrideMap != null) {
      this.dbOverrideMap = dbOverrideMap;
    }

    dbProperties.setProperty("foreign_keys", "true");

    DbConfiguration.mapMerge(dbMap, dbOverrideMap);
    databaseSetup();
  }

  public HandicapDatabaseH2() throws IOException {
    super();

    defaultNode = dodexUtil.getDefaultNode();
    webEnv = webEnv == null || "prod".equals(webEnv) ? "prod" : "dev";

    dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
    dbProperties = dodexUtil.mapToProperties(dbMap);

    dbProperties.setProperty("foreign_keys", "true");

    databaseSetup();
  }

  public HandicapDatabaseH2(Boolean isCreateTables) throws IOException {
    super();
    defaultNode = dodexUtil.getDefaultNode();
    webEnv = webEnv == null || "prod".equals(webEnv) ? "prod" : "dev";

    dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
    dbProperties = dodexUtil.mapToProperties(dbMap);

    dbProperties.setProperty("foreign_keys", "true");
    this.isCreateTables = isCreateTables;
  }

  public Future<String> checkOnTables() throws InterruptedException, SQLException {
//    databaseSetup();
      returnPromise.complete(isCreateTables.toString());
    return returnPromise.future();
  }

  private void databaseSetup() {
    Promise<String> finalPromise = Promise.promise();
logger.info("Doing Generate*****************: {}", this);
    if ("dev".equals(webEnv)) {
      DbConfiguration.configureTestDefaults(dbMap, dbProperties);
    } else {
      DbConfiguration.configureDefaults(dbMap, dbProperties);
    }

    JDBCConnectOptions connectOptions;

    connectOptions = new JDBCConnectOptions().setJdbcUrl(dbMap.get("url") + dbMap.get("filename"))
            .setUser(dbProperties.getProperty("user"))
            .setPassword(dbProperties.getProperty("password"))
            .setIdleTimeout(1)
    // .setCachePreparedStatements(true)
    ;

    // Pool options
    PoolOptions poolOptions = new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

    // Create the client pool
    pool4 = JDBCPool.pool(DodexUtil.getVertx(), connectOptions, poolOptions);

    Completable completable = pool4.rxGetConnection().flatMapCompletable(conn -> conn.rxBegin()
        .flatMapCompletable(tx -> conn.query(CHECKUSERSQL).rxExecute().doOnSuccess(row -> {
          RowIterator<Row> ri = row.iterator();
          String val = null;
          while (ri.hasNext()) {
            val = ri.next().getString(0);
          }

          if (val == null) {
            final String usersSql = getCreateTable("USERS");

            Single<RowSet<Row>> crow = conn.query(usersSql).rxExecute().doOnError(err ->
                    logger.info(String.format("Users Table Error: %s",
                err.getMessage()))).doOnSuccess(result -> logger.info("Users Table Added." + webEnv));

            crow.subscribe(result -> {
              //
            }, err -> logger.info(String.format("Users Table Error: %s", err.getMessage())));
          }
        }).doOnError(err -> logger.info(String.format("Users Table Error: %s", err.getMessage())))
        .flatMap(result -> conn.query(CHECKMESSAGESSQL).rxExecute().doOnSuccess(row -> {
          RowIterator<Row> ri = row.iterator();
          String val = null;
          while (ri.hasNext()) {
            val = ri.next().getString(0);
          }

          if (val == null) {
            final String sql = getCreateTable("MESSAGES");

            Single<RowSet<Row>> crow = conn.query(sql).rxExecute().doOnError(err -> logger.info(
                String.format("Messages Table Error: %s", err.getMessage()))).doOnSuccess(row2 ->
                    logger.info("Messages Table Added."));

            crow.subscribe(res -> {
              //
            }, err -> logger.info(
                String.format("Messages Table Error: %s", err.getMessage())));
          }
        }).doOnError(err -> logger.info(String.format("Messages Table Error: %s", err.getMessage()))))
          .flatMap(
            result -> conn.query(CHECKUNDELIVEREDSQL).rxExecute().doOnSuccess(row -> {
              RowIterator<Row> ri = row.iterator();
              String val = null;
              while (ri.hasNext()) {
                val = ri.next().getString(0);
              }

              if (val == null) {
                final String sql = getCreateTable("UNDELIVERED");

                Single<RowSet<Row>> crow = conn.query(sql).rxExecute().doOnError(err ->
                        logger.info(String.format("Undelivered Table Error: %s",
                    err.getMessage()))).doOnSuccess(row2 -> logger.info("Undelivered Table Added."));

                crow.subscribe(result2 -> {
                  //
                }, err -> logger.info(String.format("Messages Table Error: %s",
                    err.getMessage())));
              }
            }).doOnError(err -> logger.info(
                String.format("Messages Table Error: %s", err.getMessage()))))
            .flatMap(
                result -> conn.query(CHECKHANDICAPSQL).rxExecute()
                    .doOnError(err -> logger.error(String.format("Golfer Table Error: %s",
                        err.getMessage())))
                    .doOnSuccess(rows -> {
                      Set<String> names = new HashSet<>();

                      for (Row row : rows) {
                        names.add(row.getString(0));
                      }
                      String sql = getCreateTable("GOLFER");
                      if((names.contains("golfer"))) {
                        sql = SELECTONE;
                      }
                      conn.query(sql).rxExecute()
                          .doOnError(err -> logger.error(String.format("Golfer Table Error: %s",
                              err.getMessage())))
                          .doOnSuccess(row1 -> {
                            if (!names.contains("golfer")) {
                              logger.warn("Golfer Table Added.");
                            }
                            String sql2 = getCreateTable("COURSE");
                            if((names.contains("course"))) {
                              sql2 = SELECTONE;
                            }
                            conn.query(sql2).rxExecute()
                                .doOnError(err -> logger.warn(String.format("Course Table Error: %s",
                                    err.getMessage()))).doOnSuccess(row2 -> {
                                  if (!names.contains("course")) {
                                    logger.warn("Course Table Added.");
                                  }
                                  String sql3 = getCreateTable("RATINGS");
                                  if((names.contains("ratings"))) {
                                    sql3 = SELECTONE;
                                  }
                                  conn.query(sql3).rxExecute()
                                      .doOnError(err -> logger.warn(String.format("Ratings Table Error: %s",
                                          err.getMessage()))).doOnSuccess(row3 -> {
                                        if (!names.contains("ratings")) {
                                          logger.warn("Ratings Table Added.");
                                        }
                                        String sql4 = getCreateTable("SCORES");
                                        if((names.contains("scores"))) {
                                          sql4 = SELECTONE;
                                        }
                                        conn.query(sql4).rxExecute()
                                            .doOnError(err -> logger.error(String.format("Scores Table Error: %s",
                                                err.getMessage()))).doOnSuccess(row4 -> {
                                              if (!names.contains("scores")) {
                                                logger.warn("Scores Table Added.");
                                              }
                                              finalPromise.complete(isCreateTables.toString());
                                              if (isCreateTables) {
                                                returnPromise.complete(isCreateTables.toString());
                                              }
                                            }).subscribe();
                                      }).subscribe();
                                }).subscribe();
                          }).subscribe();
                    }))
                .flatMapCompletable(res -> tx.rxCommit().doOnSubscribe(sub -> Completable.complete().subscribe()))));

    completable.subscribe(() -> {
      finalPromise.future().onComplete(c -> {
        if (!isCreateTables) {
          try {
            setupSql(pool4);
          } catch (SQLException | IOException e) {
            e.printStackTrace();
          }
        }
      });
    }, err -> {
      logger.error(String.format("Tables Create Error: %s", err.getMessage()));
      err.printStackTrace();
    });
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> R getPool4() {
    return (R) pool4;
  }
}
