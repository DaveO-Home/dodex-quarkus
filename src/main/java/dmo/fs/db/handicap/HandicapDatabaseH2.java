package dmo.fs.db.handicap;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.DbConfiguration;
import dmo.fs.db.handicap.utils.DodexUtil;
import io.quarkus.runtime.configuration.ProfileManager;
import io.smallrye.mutiny.Uni;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.jdbcclient.JDBCPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowIterator;
import io.vertx.rxjava3.core.Vertx;
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

import static io.vertx.mutiny.jdbcclient.JDBCPool.pool;

public class HandicapDatabaseH2 extends DbH2 {
  private final static Logger logger =
      LoggerFactory.getLogger(HandicapDatabaseH2.class.getName());
  protected Properties dbProperties;
  protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
  protected Map<String, String> dbMap;
  protected JsonNode defaultNode;
  protected String webEnv = !ProfileManager.getLaunchMode().isDevOrTest() ? "prod" : "dev";
  protected DodexUtil dodexUtil = new DodexUtil();
  protected JDBCPool pool4;
  protected Boolean isCreateTables = false;
  protected Promise<String> returnPromise = Promise.promise();
  protected Promise<String> finalPromise = Promise.promise();
  protected JDBCConnectOptions connectOptions;
  protected PoolOptions poolOptions;

  public HandicapDatabaseH2(Map<String, String> dbOverrideMap, Properties dbOverrideProps)
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

  public HandicapDatabaseH2() throws IOException {
    super();
    defaultNode = dodexUtil.getDefaultNode();
    dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
    dbProperties = dodexUtil.mapToProperties(dbMap);

    dbProperties.setProperty("foreign_keys", "true");
    databaseSetup();
  }

  public HandicapDatabaseH2(Boolean isCreateTables)
      throws IOException {
    super();
    defaultNode = dodexUtil.getDefaultNode();

    dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
    dbProperties = dodexUtil.mapToProperties(dbMap);

    dbProperties.setProperty("foreign_keys", "true");
    this.isCreateTables = isCreateTables;
  }

  public Uni<String> checkOnTables() {
    if (isCreateTables) {
      databaseSetup();
    }
    return returnPromise.future();
  }

  private void databaseSetup() {
    if ("dev".equals(webEnv)) {
      DbConfiguration.configureTestDefaults(dbMap, dbProperties);
    } else {
      DbConfiguration.configureDefaults(dbMap, dbProperties);
    }

    poolOptions =
        new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

    connectOptions = new JDBCConnectOptions()
        .setJdbcUrl(dbMap.get("url") + dbMap.get("filename"))
        .setUser(dbProperties.getProperty("user"))
        .setPassword(dbProperties.getProperty("password"))
        .setIdleTimeout(1)
    // .setCachePreparedStatements(true)
    ;

    setJDBCConnectOptions(connectOptions);
    setPoolOptions(poolOptions);

    pool4 = pool(DodexUtil.getVertx(), connectOptions, poolOptions);

    pool4.getConnection().onItem().invoke(conn -> {
      conn.query(CHECKUSERSQL).execute().onItem().invoke(row -> {
            RowIterator<Row> ri = row.iterator();
            String val = null;
            while (ri.hasNext()) {
              val = ri.next().getString(0);
            }

            if (val == null) {
              final String usersSql = getCreateTable("USERS");

              conn.query(usersSql).execute().onFailure().invoke(err ->
                      logger.info(String.format("Users Table Error: %s", err.getMessage())))
                  .onItem().invoke(result -> logger.info("Users Table Added."))
                  .subscribeAsCompletionStage().isDone();
            }
          }).onFailure().invoke(err ->
              logger.info(String.format("Connection Users Table Error: %s", err.getMessage())))
          .flatMap(result -> conn.query(CHECKMESSAGESSQL)
              .execute().onItem().invoke(row -> {
                RowIterator<Row> ri = row.iterator();
                String val = null;
                while (ri.hasNext()) {
                  val = ri.next().getString(0);
                }

                if (val == null) {
                  final String sql = getCreateTable("MESSAGES");

                  conn.query(sql).execute().onFailure().invoke(err ->
                          logger.info(String.format("Messages Table Error: %s", err.getMessage())))
                      .onItem().invoke(row2 -> logger.info("Messages Table Added."))
                      .subscribeAsCompletionStage().isDone();
                }
              }).onFailure().invoke(err ->
                  logger.info(String.format("Connection Messages Table Error: %s", err.getMessage()))))
          .flatMap(result -> conn.query(CHECKUNDELIVEREDSQL).execute().onItem().invoke(row -> {
            RowIterator<Row> ri = row.iterator();
            String val = null;
            while (ri.hasNext()) {
              val = ri.next().getString(0);
            }

            if (val == null) {
              final String sql = getCreateTable("UNDELIVERED");

              conn.query(sql).execute().onFailure().invoke(err ->
                      logger.info(String.format("Undelivered Table Error: %s", err.getMessage())))
                  .onItem().invoke(row2 -> logger.info("Undelivered Table Added."))
                  .subscribeAsCompletionStage().isDone();
            }
          }).onFailure().invoke(err ->
              logger.info(String.format("Undelivered Table Error: %s", err.getMessage()))))
          .flatMap(result -> conn.query(CHECKGROUPSSQL).execute().onItem().invoke(row -> {
            RowIterator<Row> ri = row.iterator();
            String val = null;
            while (ri.hasNext()) {
              val = ri.next().getString(0);
            }
            if (val == null) {
              final String sql = getCreateTable("GROUPS");

              conn.query(sql).execute().onFailure().invoke(err ->
                      logger.info(String.format("Groups Table Error: %s", err.getMessage())))
                  .onItem().invoke(row2 -> logger.info("Groups Table Added."))
                  .subscribeAsCompletionStage().isDone();
            }
          }).onFailure().invoke(err ->
              logger.info(String.format("Groups Table Error: %s", err.getMessage()))))
          .flatMap(result -> conn.query(CHECKMEMBERSQL).execute().onItem().invoke(row -> {
            RowIterator<Row> ri = row.iterator();
            String val = null;
            while (ri.hasNext()) {
              val = ri.next().getString(0);
            }
            if (val == null) {
              final String sql = getCreateTable("MEMBER");

              conn.query(sql).execute().onFailure().invoke(err ->
                      logger.info(String.format("Member Table Error: %s", err.getMessage())))
                  .onItem().invoke(row2 -> logger.info("Member Table Added."))
                  .subscribeAsCompletionStage().isDone();
            }
          }).onFailure().invoke(err ->
              logger.info(String.format("Member Table Error: %s", err.getMessage()))))
          .flatMap(result -> conn.query(CHECKHANDICAPSQL).execute().onFailure().invoke(err ->
                  logger.error(String.format("Golfer Table Error: %s", err.getMessage())))
              .onItem().invoke(rows -> {
                Set<String> names = new HashSet<>();

                for (Row row : rows) {
                  names.add(row.getString(0));
                }

                String sql = getCreateTable("GOLFER");
                if ((names.contains("GOLFER"))) {
                  sql = SELECTONE;
                }

                conn.query(sql).execute().onFailure().invoke(err ->
                        logger.error(String.format("Golfer Table Error: %s", err.getMessage())))
                    .onItem().invoke(row1 -> {
                      if (!names.contains("GOLFER")) {
                        logger.warn("Golfer Table Added.");
                      }
                      String sql2 = getCreateTable("COURSE");

                      if ((names.contains("COURSE"))) {
                        sql2 = SELECTONE;
                      }
                      conn.query(sql2).execute().onFailure().invoke(err ->
                              logger.warn(String.format("Course Table Error: %s", err.getMessage())))
                          .onItem().invoke(row2 -> {
                            if (!names.contains("COURSE")) {
                              logger.warn("Course Table Added.");
                            }
                            String sql3 = getCreateTable("RATINGS");
                            if ((names.contains("RATINGS"))) {
                              sql3 = SELECTONE;
                            }
                            conn.query(sql3).execute().onFailure().invoke(err ->
                                    logger.warn(String.format("Ratings Table Error: %s", err.getMessage())))
                                .onItem().invoke(row3 -> {
                                  if (!names.contains("RATINGS")) {
                                    logger.warn("Ratings Table Added.");
                                  }
                                  String sql4 = getCreateTable("SCORES");
                                  if ((names.contains("SCORES"))) {
                                    sql4 = SELECTONE;
                                  }
                                  conn.query(sql4).execute().onFailure().invoke(err ->
                                          logger.error(String.format("Scores Table Error: %s", err.getMessage())))
                                      .onItem().invoke(row4 -> {
                                        if (!names.contains("SCORES")) {
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
        if (!isCreateTables) {
          try {
            setupSql(getJavaPool());
          } catch (IOException | SQLException e) {
            throw new RuntimeException(e);
          }
        }
      }).subscribeAsCompletionStage().isDone();

    }).onFailure().invoke(err -> {
      logger.info(String.format("Starting Create Tables Error: %s", err.getMessage()));
      err.printStackTrace();
    }).subscribeAsCompletionStage().isDone();
  }

  private JDBCPool getJavaPool() {
    return pool(DodexUtil.getVertx(), connectOptions, poolOptions);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getPool4() {
    return (T) pool4;
  }

  @Override
  public Vertx getVertxR() {
    return null;
  }

  @Override
  public void setVertxR(Vertx vertx) {

  }

  public io.vertx.mutiny.core.Vertx getVertx() {
    return null;
  }

  @Override
  public void setVertx(io.vertx.mutiny.core.Vertx vertx) {

  }
}
