package dmo.fs.db.handicap;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.handicap.utils.DodexUtil;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class HandicapDatabaseMariadb extends dmo.fs.db.handicap.DbDefinitionBase implements HandicapDatabase {
    protected final static Logger logger =
      LoggerFactory.getLogger(HandicapDatabaseMariadb.class.getName());
    protected MySQLPool pool4;
    protected Properties dbProperties;
    protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
    protected Map<String, String> dbMap;
    protected JsonNode defaultNode;
    protected String webEnv = DodexUtil.getEnv();
    protected DodexUtil dodexUtil = new DodexUtil();
    protected Boolean isCreateTables = false;
    protected Promise<String> returnPromise = Promise.promise();

    /*
        Sets up DSL for jooq and handicap sql
    */
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
        DbConfiguration.mapMerge(dbMap, dbOverrideMap);
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

    protected void databaseSetup() throws InterruptedException, SQLException {
        Promise<String> finalPromise = Promise.promise();
        if ("dev".equals(webEnv)) {
            DbConfiguration.configureTestDefaults(dbMap, dbProperties);
        } else {
            DbConfiguration.configureDefaults(dbMap, dbProperties);
        }

        MySQLConnectOptions connectOptions = new MySQLConnectOptions()
          .setPort(Integer.parseInt(dbMap.get("port"))).setHost(dbMap.get("host"))
          .setDatabase(dbMap.get("dbname")).setUser(dbProperties.getProperty("user"))
          .setPassword(dbProperties.getProperty("password"))
          .setSsl(Boolean.parseBoolean(dbProperties.getProperty("ssl"))).setIdleTimeout(1)
          .setCharset("utf8mb4");

        // Pool options
        PoolOptions poolOptions =
          new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

        setPoolOptions(poolOptions);

        // Create the client pool
        pool4 = MySQLPool.pool(DodexUtil.getVertx(), connectOptions, poolOptions);
        /*
            No need to set up DSL if only generating jooq records(generate project)
         */
        if (!isCreateTables) {
            try {
                setupSql(pool4);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
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
