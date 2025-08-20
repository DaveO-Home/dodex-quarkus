package dmo.fs.db.handicap;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.utils.Constants;
import dmo.fs.db.handicap.utils.DodexUtil;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.pgclient.PgBuilder;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
/*
    Sets up DSL for jooq and handicap sql
 */
public class HandicapDatabasePostgres extends DbDefinitionBase implements HandicapDatabase {
    protected final static Logger logger =
      LoggerFactory.getLogger(HandicapDatabasePostgres.class.getName());
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
        this.isCreateTables = isCreateTables;
    }

    @Override
    public Uni<String> checkOnTables() {
        if (isCreateTables) {
            databaseSetup();
        }
        return returnPromise.future();
    }

    protected void databaseSetup() {
        Promise<Boolean> finalPromise = Promise.promise();
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

        setPoolOptions(poolOptions);
        setConnectOptions(connectOptions);

        Pool pool = PgBuilder
          .pool()
          .with(poolOptions)
          .connectingTo(connectOptions)
          .using(DodexUtil.getVertx())
          .build();

        if(logger.isDebugEnabled()) {
            logger.debug("{}Pool Created: {} -- {}", Constants.dodexDebug, pool, isCreateTables);
        }

        /*
            No need to set up DSL if only generating jooq records(generate project)
         */
        finalPromise.complete(isCreateTables);
        finalPromise.future().onItem().invoke(isTablesCreated -> {
            if (!isCreateTables) {
                try {
                    setupSql(pool);
                    returnPromise.complete(isCreateTables.toString());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }).subscribeAsCompletionStage().isDone();

//            return null;
//        }).subscribeAsCompletionStage().isDone();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getPool4() {
        return (T) pool;
    }

    @Override
    public void setVertxR(io.vertx.rxjava3.core.Vertx vertx) {
        //
    }

    @Override
    public io.vertx.rxjava3.core.Vertx getVertxR() {
        return null;
    }
}
