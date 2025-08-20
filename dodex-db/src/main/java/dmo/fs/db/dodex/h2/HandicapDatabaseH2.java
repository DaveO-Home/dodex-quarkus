package dmo.fs.db.dodex.h2;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.dodex.CreateDatabaseImpl;
import dmo.fs.db.dodex.utils.DodexUtil;
import io.smallrye.mutiny.Uni;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.jdbcclient.JDBCPool;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowIterator;
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

import static io.vertx.mutiny.jdbcclient.JDBCPool.pool;

public class HandicapDatabaseH2 extends DbH2 {
    protected final static Logger logger =
      LoggerFactory.getLogger(HandicapDatabaseH2.class.getName());
    protected Properties dbProperties;
    protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
    protected Map<String, String> dbMap;
    protected JsonNode defaultNode;
    protected String webEnv = DodexUtil.getEnv();
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

    @Override
    public Uni<String> checkOnTables() {
        return returnPromise.future();
    }

    @Override
    public Promise<Pool> databaseSetup() {
        Promise<Pool> poolPromise = Promise.promise();
        if ("dev".equals(webEnv)) {
            CreateDatabaseImpl.configureTestDefaults(dbMap, dbProperties);
        } else {
            CreateDatabaseImpl.configureDefaults(dbMap, dbProperties);
        }

        poolOptions =
          new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);
        String fileName = dbMap.get("filename");
        connectOptions = new JDBCConnectOptions()
          .setJdbcUrl(dbMap.get("url") + dbMap.get("filename"))
          .setUser(dbProperties.getProperty("user"))
          .setPassword(dbProperties.getProperty("password"))
          .setIdleTimeout(1)
        // .setCachePreparedStatements(true)
        ;

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
                          logger.info("Users Table Error: {}", err.getMessage()))
                        .onItem().invoke(result -> logger.info("{} -- Users Table Added.", fileName))
                        .subscribeAsCompletionStage().isDone();
                  }
              }).onFailure().invoke(err ->
                logger.info("Connection Users Table Error: {}", err.getMessage()))
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
                            logger.info("Messages Table Error: {}", err.getMessage()))
                          .onItem().invoke(row2 -> logger.info("{} -- Messages Table Added.", fileName))
                          .subscribeAsCompletionStage().isDone();
                    }
                }).onFailure().invoke(err ->
                  logger.info("Connection Messages Table Error: {}", err.getMessage())))
              .flatMap(result -> conn.query(CHECKUNDELIVEREDSQL).execute().onItem().invoke(row -> {
                  RowIterator<Row> ri = row.iterator();
                  String val = null;
                  while (ri.hasNext()) {
                      val = ri.next().getString(0);
                  }

                  if (val == null) {
                      final String sql = getCreateTable("UNDELIVERED");

                      conn.query(sql).execute().onFailure().invoke(err ->
                          logger.info("Undelivered Table Error: {}", err.getMessage()))
                        .onItem().invoke(row2 -> logger.info("{} -- Undelivered Table Added.", fileName))
                        .subscribeAsCompletionStage().isDone();
                  }
              }).onFailure().invoke(err ->
                logger.info("Undelivered Table Error2: {}", err.getMessage())))
              .flatMap(result -> conn.query(CHECKGROUPSSQL).execute().onItem().invoke(row -> {
                  RowIterator<Row> ri = row.iterator();
                  String val = null;
                  while (ri.hasNext()) {
                      val = ri.next().getString(0);
                  }
                  if (val == null) {
                      final String sql = getCreateTable("GROUPS");

                      conn.query(sql).execute().onFailure().invoke(err ->
                          logger.info("Groups Table Error: {}", err.getMessage()))
                        .onItem().invoke(row2 -> logger.info("{} -- Groups Table Added.", fileName))
                        .subscribeAsCompletionStage().isDone();
                  }
              }).onFailure().invoke(err ->
                logger.info("Groups Table Error2: {}", err.getMessage())))
              .flatMap(result -> conn.query(CHECKMEMBERSQL).execute().onItem().invoke(row -> {
                  RowIterator<Row> ri = row.iterator();
                  String val = null;
                  while (ri.hasNext()) {
                      val = ri.next().getString(0);
                  }
                  if (val == null) {
                      final String sql = getCreateTable("MEMBER");

                      conn.query(sql).execute().onFailure().invoke(err ->
                          logger.info("Member Table Error: {}", err.getMessage()))
                        .onItem().invoke(row2 -> logger.info("{} -- Member Table Added.", fileName))
                        .subscribeAsCompletionStage().isDone();
                  }
              }).onFailure().invoke(err ->
                logger.info("Member Table Error2: {}", err.getMessage())))
              .flatMap(result -> conn.query(CHECKHANDICAPSQL).execute().onFailure().invoke(err ->
                  logger.error(String.format("Golfer Table Error: %s", err.getMessage())))
                .onItem().invoke(rows -> {
                    Set<String> names = new HashSet<>();

                    for (Row row : rows) {
                        names.add(row.getString(0));
                    }

                    String sql = getCreateTable("GOLFER");
                    if (names.contains("GOLFER")) {
                        sql = SELECTONE;
                    }

                    conn.query(sql).execute().onFailure().invoke(err ->
                        logger.error("Golfer Table Error: {}", err.getMessage()))
                      .onItem().invoke(row1 -> {
                          if (!names.contains("GOLFER")) {
                              logger.warn("{} -- Golfer Table Added.", fileName);
                          }
                          String sql2 = getCreateTable("COURSE");

                          if (names.contains("COURSE")) {
                              sql2 = SELECTONE;
                          }
                          conn.query(sql2).execute().onFailure().invoke(err ->
                              logger.warn("Course Table Error: {}", err.getMessage()))
                            .onItem().invoke(row2 -> {
                                if (!names.contains("COURSE")) {
                                    logger.warn("{} -- Course Table Added.", fileName);
                                }
                                String sql3 = getCreateTable("RATINGS");
                                if (names.contains("RATINGS")) {
                                    sql3 = SELECTONE;
                                }
                                conn.query(sql3).execute().onFailure().invoke(err ->
                                    logger.warn("Ratings Table Error: {}", err.getMessage()))
                                  .onItem().invoke(row3 -> {
                                      if (!names.contains("RATINGS")) {
                                          logger.warn("{} -- Ratings Table Added.", fileName);
                                      }
                                      String sql4 = getCreateTable("SCORES");
                                      if (names.contains("SCORES")) {
                                          sql4 = SELECTONE;
                                      }
                                      conn.query(sql4).execute().onFailure().invoke(err ->
                                          logger.error("Scores Table Error: {}", err.getMessage()))
                                        .onItem().invoke(row4 -> {
                                            if (!names.contains("SCORES")) {
                                                logger.warn("{} -- Scores Table Added.", fileName);
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

        }).onFailure().invoke(err -> {
            logger.info("Starting Create Tables Error: {}", err.getMessage());
            err.printStackTrace();
        }).subscribeAsCompletionStage().isDone();
        return poolPromise;
    }

    protected JDBCPool getJavaPool() {
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
        //
    }

    @Override
    public io.vertx.mutiny.core.Vertx getVertx() {
        return null;
    }

    @Override
    public void setVertx(io.vertx.mutiny.core.Vertx vertx) {
        //
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
