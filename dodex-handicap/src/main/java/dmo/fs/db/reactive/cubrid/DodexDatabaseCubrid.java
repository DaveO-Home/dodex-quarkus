package dmo.fs.db.reactive.cubrid;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.MessageUser;
import dmo.fs.db.MessageUserImpl;
import dmo.fs.db.reactive.DbConfiguration;
import dmo.fs.db.reactive.DbCubridSqlBase;
import dmo.fs.db.reactive.DodexReactiveDatabase;
import dmo.fs.quarkus.Server;
import dmo.fs.utils.DodexUtil;
import io.vertx.core.Promise;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.jdbcclient.JDBCPool;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class DodexDatabaseCubrid extends DbCubridSqlBase implements DodexReactiveDatabase {
    protected static final Logger logger = LoggerFactory.getLogger(DodexDatabaseCubrid.class.getName());
    protected Properties dbProperties;
    protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
    protected Map<String, String> dbMap;
    protected JsonNode defaultNode;
    protected String webEnv = Server.isProduction() ? "prod" : "dev";
    protected DodexUtil dodexUtil = new DodexUtil();
    protected JDBCPool pool;

    public DodexDatabaseCubrid(Map<String, String> dbOverrideMap, Properties dbOverrideProps) throws IOException {
        super();

        defaultNode = dodexUtil.getDefaultNode();

        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
        dbProperties = dodexUtil.mapToProperties(dbMap);

        if (dbOverrideProps != null) {
            this.dbProperties = dbOverrideProps;
        }
//        if (dbOverrideMap != null) {
        this.dbOverrideMap = dbOverrideMap;
//        }

        DbConfiguration.mapMerge(dbMap, dbOverrideMap);
    }

    public DodexDatabaseCubrid() throws IOException {
        super();
        defaultNode = dodexUtil.getDefaultNode();

        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
        dbProperties = dodexUtil.mapToProperties(dbMap);
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


        try {
            setupSql(pool);
            promise.complete(pool);
        } catch (Exception e) {
            e.printStackTrace();
        }

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

    @SuppressWarnings("unchecked")
    protected static <T> T getPool(Map<String, String> dbMap, Properties dbProperties) {

        PoolOptions poolOptions = new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);
        JDBCConnectOptions connectOptions;
        connectOptions = new JDBCConnectOptions()
          .setJdbcUrl(dbMap.get("url") + dbMap.get("host") + dbMap.get("dbname") + "?charSet=UTF-8")
          .setUser(dbProperties.getProperty("user"))
          .setPassword(dbProperties.getProperty("password"))
//           .setDatabase(dbMap.get("dbname")+"?charSet=utf8")
          // .setSsl(Boolean.valueOf(dbProperties.getProperty("ssl")))
          .setIdleTimeout(1)
        // .setCachePreparedStatements(true)
        ;

        Vertx vertx = Server.vertx;

        setJDBCConnectOptions(connectOptions);
        setPoolOptions(poolOptions);

        return (T) JDBCPool.pool(vertx, connectOptions, poolOptions);
    }

}
