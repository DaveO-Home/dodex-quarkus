/*
    This does not work in Quarkus
 */
package dmo.fs.db.handicap;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.reactive.DbConfiguration;
import dmo.fs.db.handicap.utils.DodexUtil;
import dmo.fs.quarkus.Server;
import io.smallrye.mutiny.Uni;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.mutiny.core.Promise;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.mutiny.jdbcclient.JDBCPool;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class DodexDatabaseSqlite3 extends DbSqlite3 {
    protected final static Logger logger =
      LoggerFactory.getLogger(DodexDatabaseSqlite3.class.getName());
    protected Properties dbProperties;
    protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
    protected Map<String, String> dbMap;
    protected JsonNode defaultNode;
    protected String webEnv = Server.isProduction() ? "prod" : "dev";
    protected DodexUtil dodexUtil = new DodexUtil();
    protected JDBCPool pool4;
    protected Boolean isCreateTables = false;
    protected Promise<String> returnPromise = Promise.promise();

    public DodexDatabaseSqlite3(Map<String, String> dbOverrideMap, Properties dbOverrideProps)
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
        DbConfiguration.mapMerge(dbMap, dbOverrideMap);
        databaseSetup();
    }

    public DodexDatabaseSqlite3() throws IOException {
        super();
        defaultNode = dodexUtil.getDefaultNode();
        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
        dbProperties = dodexUtil.mapToProperties(dbMap);

        dbProperties.setProperty("foreign_keys", "true");
        databaseSetup();
    }

    public DodexDatabaseSqlite3(Boolean isCreateTables)
      throws IOException {
        super();
        defaultNode = dodexUtil.getDefaultNode();

        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
        dbProperties = dodexUtil.mapToProperties(dbMap);

        dbProperties.setProperty("foreign_keys", "true");
        this.isCreateTables = isCreateTables;
        logger.info("In (true) sqlite: " + defaultNode);
    }

    public Uni<String> checkOnTables() {
        if (isCreateTables) {
            databaseSetup();
        }
        return returnPromise.future();
    }

    protected void databaseSetup() {
        if ("dev".equals(webEnv)) {
            DbConfiguration.configureTestDefaults(dbMap, dbProperties);
        } else {
            DbConfiguration.configureDefaults(dbMap, dbProperties); // Using prod (./dodex.db)
        }

        PoolOptions poolOptions =
          new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

        JDBCConnectOptions connectOptions;

        connectOptions = new JDBCConnectOptions()
          .setJdbcUrl(dbMap.get("url") + dbMap.get("filename") + "?foreign_keys=on;")
          .setIdleTimeout(1)
        // .setCachePreparedStatements(true)
        ;
        if (DodexUtil.getVertxR() == null) {
            DodexUtil.setVertxR(Vertx.vertx());
        }
        setJDBCConnectOptions(connectOptions);
        setPoolOptions(poolOptions);
        pool4 = JDBCPool.pool(DodexUtil.getVertx(), connectOptions, poolOptions);

        pool4.getDelegate().getConnection().flatMap(conn -> conn.begin().flatMap(tx ->
          conn.query(CHECKUSERSQL).execute().onSuccess(row -> {
              io.vertx.sqlclient.RowIterator<io.vertx.sqlclient.Row> ri = row.iterator();
              String val = null;
              while (ri.hasNext()) {
                  val = ri.next().getString(0);
              }
              if (val == null) {
                  final String usersSql = getCreateTable("USERS");

                  conn.query(usersSql).execute()
                    .onFailure(err -> {
                        conn.close();
                        logger.info(String.format("Users Table Error: %s", err.getMessage()));
                    }).onSuccess(result -> logger.info("Users Table Added."));
              }
          }).flatMap(m ->
            conn.query(CHECKMESSAGESSQL).execute().onSuccess(row -> {
                io.vertx.sqlclient.RowIterator<io.vertx.sqlclient.Row> ri = row.iterator();
                String val = null;
                while (ri.hasNext()) {
                    val = ri.next().getString(0);
                }
                if (val == null) {
                    final String messagesSql = getCreateTable("MESSAGES");

                    conn.query(messagesSql).execute()
                      .onFailure(err -> {
                          conn.close();
                          logger.info(String.format("Message Table Error: %s", err.getMessage()));
                      }).onSuccess(result -> logger.info("Message Table Added."));

                }
            }).flatMap(u ->
              conn.query(CHECKUNDELIVEREDSQL).execute().onSuccess(row -> {
                    io.vertx.sqlclient.RowIterator<io.vertx.sqlclient.Row> ri = row.iterator();
                    String val = null;
                    while (ri.hasNext()) {
                        val = ri.next().getString(0);
                    }

                    if (val == null) {
                        final String undeliveredSql = getCreateTable("UNDELIVERED");

                        conn.query(undeliveredSql).execute()
                          .onFailure(err -> {
                              conn.close();
                              logger.info(String.format("Undelivered Table Error: %s", err.getMessage()));
                          }).onSuccess(result -> logger.info("Undelivered Table Added."));
                    }
                })
                .flatMap(g ->
                  conn.query(CHECKGOLFERSQL).execute().onSuccess(row -> {
                        io.vertx.sqlclient.RowIterator<io.vertx.sqlclient.Row> ri = row.iterator();
                        String val = null;
                        while (ri.hasNext()) {
                            val = ri.next().getString(0);
                        }
                        if (val == null) {
                            final String golferSql = getCreateTable("GOLFER");

                            conn.query(golferSql).execute()
                              .onFailure(err -> {
                                  conn.close();
                                  logger.info(String.format("Golfer Table Error: %s", err.getMessage()));
                              }).onSuccess(result -> logger.info("Golfer Table Added."));
                        }
                    }).onFailure(Throwable::printStackTrace)
                    .flatMap(c ->
                      conn.query(CHECKCOURSESQL).execute().onSuccess(row -> {
                            io.vertx.sqlclient.RowIterator<io.vertx.sqlclient.Row> ri = row.iterator();
                            String val = null;
                            while (ri.hasNext()) {
                                val = ri.next().getString(0);
                            }
                            if (val == null) {
                                final String courseSql = getCreateTable("COURSE");

                                conn.query(courseSql).execute()
                                  .onFailure(err -> {
                                      conn.close();
                                      logger.info(String.format("Course Table Error: %s", err.getMessage()));
                                  }).onSuccess(result -> logger.info("Course Table Added."));
                            }
                        }).onFailure(Throwable::printStackTrace)
                        .flatMap(r ->
                          conn.query(CHECKRATINGSSQL).execute().onSuccess(row -> {
                                io.vertx.sqlclient.RowIterator<io.vertx.sqlclient.Row> ri = row.iterator();
                                String val = null;
                                while (ri.hasNext()) {
                                    val = ri.next().getString(0);
                                }
                                if (val == null) {
                                    final String ratingsSql = getCreateTable("RATINGS");

                                    conn.query(ratingsSql).execute()
                                      .onFailure(err -> {
                                          conn.close();
                                          logger.info(String.format("Ratings Table Error: %s", err.getMessage()));
                                      }).onSuccess(result -> logger.info("Ratings Table Added."));
                                }
                            }).onFailure(Throwable::printStackTrace)
                            .flatMap(s ->
                              conn.query(CHECKSCORESSQL).execute().onSuccess(row -> {
                                    io.vertx.sqlclient.RowIterator<io.vertx.sqlclient.Row> ri = row.iterator();
                                    String val = null;
                                    while (ri.hasNext()) {
                                        val = ri.next().getString(0);
                                    }
                                    if (val == null) {
                                        final String scoresSql = getCreateTable("SCORES");

                                        conn.query(scoresSql).execute()
                                          .onFailure(err -> {
                                              conn.close();
                                              logger.info(String.format("Scores Table Error: %s", err.getMessage()));
                                          }).onSuccess(result -> {
                                              logger.info("Scores Table Added.");
                                              tx.commit();
                                              conn.close();
                                              returnPromise.complete("");
                                          });
                                    } else {
                                        returnPromise.complete("");
                                    }
                                }).onFailure(Throwable::printStackTrace)
                                .onComplete(rows -> {
                                    if (!isCreateTables) {
                                        try {
                                            setupSql(pool4);
                                        } catch (SQLException | IOException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                })
                            ))))))));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getPool4() {
        return (T) pool4;
    }

    public io.vertx.mutiny.core.Vertx getVertx() {
        return null;
    }
}
