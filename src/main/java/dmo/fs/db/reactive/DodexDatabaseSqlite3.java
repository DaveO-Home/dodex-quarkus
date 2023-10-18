package dmo.fs.db.reactive;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.DbConfiguration;
import dmo.fs.db.MessageUser;
import dmo.fs.db.MessageUserImpl;
import dmo.fs.quarkus.Server;
import dmo.fs.utils.DodexUtil;
import io.quarkus.runtime.configuration.ProfileManager;
import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.core.Promise;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.jdbcclient.JDBCPool;
import io.vertx.reactivex.sqlclient.Row;
import io.vertx.reactivex.sqlclient.RowIterator;
import io.vertx.reactivex.sqlclient.RowSet;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class DodexDatabaseSqlite3 extends DbSqlite3 {
  protected static final Logger logger = LoggerFactory.getLogger(DodexDatabaseSqlite3.class.getName());
  protected Properties dbProperties = new Properties();
  protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
  protected Map<String, String> dbMap = new ConcurrentHashMap<>();
  protected JsonNode defaultNode;
  protected String webEnv = !ProfileManager.getLaunchMode().isDevOrTest() ? "prod" : "dev";
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

  public DodexDatabaseSqlite3() throws InterruptedException, IOException, SQLException {
    super();
    defaultNode = dodexUtil.getDefaultNode();

    dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
    dbProperties = dodexUtil.mapToProperties(dbMap);
  }

  @SuppressWarnings("unchecked")
  protected static <T> T getPool(Map<String, String> dbMap, Properties dbProperties) {

    PoolOptions poolOptions = new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

    JDBCConnectOptions connectOptions;
    connectOptions = new JDBCConnectOptions()
        .setJdbcUrl(dbMap.get("url") + dbMap.get("filename") + "?foreign_keys=on;")
        .setIdleTimeout(1)
    // .setCachePreparedStatements(true)
    ;

    Vertx vertx = Server.vertx;

    setJDBCConnectOptions(connectOptions);
    setPoolOptions(poolOptions);

    return (T) JDBCPool.pool(vertx, connectOptions, poolOptions);
  }

  @Override
  public Promise<JDBCPool> databaseSetup() {
    if ("dev".equals(webEnv)) {
      DbConfiguration.configureTestDefaults(dbMap, dbProperties);
    } else {
      DbConfiguration.configureDefaults(dbMap, dbProperties); // Prod
    }

    Promise<JDBCPool> promise = Promise.promise();

    pool = getPool(dbMap, dbProperties);

    Completable completable = pool.rxGetConnection().flatMapCompletable(conn -> conn.rxBegin()
        .flatMapCompletable(tx -> conn.query(CHECKUSERSQL).rxExecute().doOnSuccess(row -> {
              RowIterator<Row> ri = row.iterator();
              String val = null;
              while (ri.hasNext()) {
                val = ri.next().getString(0);
              }
              if (val == null) {
                final String usersSql = getCreateTable("USERS");

                Single<RowSet<Row>> crow = conn.query(usersSql).rxExecute()
                    .doOnError(err -> {
                      logger.info(String.format("Users Table Error: %s", err.getCause().getMessage()));
                    }).doOnSuccess(result -> {
                      logger.info("Users Table Added.");
                    });

                crow.subscribe(result -> {
                  //
                }, err -> {
                  logger.info(String.format("Users Table Error: %s", err.getMessage()));
                });
              }
            }).doOnError(err -> {
              logger.info(String.format("Users Table Error: %s", err.getMessage()));

            }).flatMap(
                result -> conn.query(CHECKMESSAGESSQL).rxExecute().doOnSuccess(row -> {
                  RowIterator<Row> ri = row.iterator();
                  String val = null;
                  while (ri.hasNext()) {
                    val = ri.next().getString(0);
                  }

                  if (val == null) {
                    final String sql = getCreateTable("MESSAGES");

                    Single<RowSet<Row>> crow = conn.query(sql).rxExecute()
                        .doOnError(err -> {
                          logger.info(String.format("Messages Table Error: %s", err.getMessage()));
                        }).doOnSuccess(row2 -> {
                          logger.info("Messages Table Added.");
                        });

                    crow.subscribe(res -> {
                      //
                    }, err -> {
                      logger.info(String.format("Messages Table Error: %s", err.getMessage()));
                    });
                  }
                }).doOnError(err -> {
                  logger.info(String.format("Messages Table Error: %s", err.getMessage()));

                })).flatMap(result -> conn.query(CHECKUNDELIVEREDSQL).rxExecute()
                .doOnSuccess(row -> {
                  RowIterator<Row> ri = row.iterator();
                  String val = null;
                  while (ri.hasNext()) {
                    val = ri.next().getString(0);
                  }

                  if (val == null) {
                    final String sql = getCreateTable("UNDELIVERED");

                    Single<RowSet<Row>> crow = conn.query(sql).rxExecute()
                        .doOnError(err -> {
                          logger.info(String.format("Undelivered Create Table Error: %s", err.getMessage()));
                        }).doOnSuccess(row2 -> {
                          logger.info("Undelivered Table Added.");
                        });

                    crow.subscribe(res -> {
                      //
                    }, err -> {
                      logger.info(String.format("Undelivered Table Error: %s", err.getMessage()));
                    });
                }
                }))
            .flatMap(
                result -> conn.query(CHECKGROUPSSQL).rxExecute().doOnSuccess(row -> {
                  RowIterator<Row> ri = row.iterator();
                  String val = null;
                  while (ri.hasNext()) {
                    val = ri.next().getString(0);
                  }

                  if (val == null) {
                    final String sql = getCreateTable("GROUPS");

                    Single<RowSet<Row>> crow = conn.query(sql).rxExecute()
                        .doOnError(err -> {
                          logger.info(String.format("Groups Table Error: %s", err.getMessage()));
                        }).doOnSuccess(row2 -> {
                          logger.info("Groups Table Added.");
                        });

                    crow.subscribe(res -> {
                      //
                    }, err -> {
                      logger.info(String.format("Groups Table Error: %s", err.getMessage()));
                    });
                  }
                }).doOnError(err -> {
                  logger.info(String.format("Groups Table Error: %s", err.getMessage()));

                })).flatMap(result -> conn.query(CHECKMEMBERSQL).rxExecute()
                .doOnSuccess(row -> {
                  RowIterator<Row> ri = row.iterator();
                  String val = null;
                  while (ri.hasNext()) {
                    val = ri.next().getString(0);
                  }

                  if (val == null) {
                    final String sql = getCreateTable("MEMBER");

                    Single<RowSet<Row>> crow = conn.query(sql).rxExecute()
                        .doOnError(err -> {
                          logger.info(String.format("Member Create Table Error: %s", err.getMessage()));
                        }).doOnSuccess(row2 -> {
                          logger.info("Member Table Added.");
                        });

                    crow.subscribe(result2 -> conn.rxClose().doOnSubscribe(c ->
                        tx.rxCommit().subscribe()).subscribe(), err ->
                        logger.info(String.format("Member Table Error: %s", err.getMessage())));
                  } else {
                    conn.rxClose().doOnSubscribe(c -> tx.rxCommit().subscribe()).subscribe();
                  }
                }).doOnError(err -> {
                  logger.info(String.format("Member Check Table Error: %s", err.getMessage()));
                }))
            .flatMapCompletable(res -> Completable.complete())
        ));

    completable.doOnComplete(() -> {
      try {
        setupSql(pool);
        promise.complete(pool);
      } catch (Exception e) {
        e.printStackTrace();
      }
    })
    .subscribe(() -> {
    }, err -> {
      logger.info(String.format("Tables Create Error: %s", err.getMessage()));
      err.printStackTrace();
    });

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

}
