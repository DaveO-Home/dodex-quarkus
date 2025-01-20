package dmo.fs.db.wsnext.mariadb;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.MessageUser;
import dmo.fs.db.MessageUserImpl;
import dmo.fs.db.wsnext.DbConfiguration;
import dmo.fs.db.wsnext.DbDefinitionBase;
import dmo.fs.db.wsnext.DodexDatabase;
import dmo.fs.utils.DodexUtil;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.sqlclient.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class DodexDatabaseMariadb extends DbDefinitionBase implements DodexDatabase {
    protected static final Logger logger = LoggerFactory.getLogger(DodexDatabaseMariadb.class.getName());
    protected Properties dbProperties = new Properties();
    protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
    protected Map<String, String> dbMap = new ConcurrentHashMap<>();
    protected JsonNode defaultNode;
    protected String webEnv = DodexUtil.getEnv();
    protected DodexUtil dodexUtil = new DodexUtil();

    public DodexDatabaseMariadb(Map<String, String> dbOverrideMap, Properties dbOverrideProps) throws IOException {
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

        DbConfiguration.mapMerge(dbMap, dbOverrideMap);
    }

    public DodexDatabaseMariadb() throws InterruptedException, IOException, SQLException {
        super();

        defaultNode = dodexUtil.getDefaultNode();

        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
        dbProperties = dodexUtil.mapToProperties(dbMap);

        dbProperties.setProperty("foreign_keys", "true");
    }

    @Override
    public Promise<Pool> databaseSetup() {
        if ("dev".equals(webEnv)) {
            DbConfiguration.configureTestDefaults(dbMap, dbProperties);
        } else {
            DbConfiguration.configureDefaults(dbMap, dbProperties); // Prod
        }

        Promise<Pool> promise = Promise.promise();
//        MySQLPool pool = getPool(dbMap, dbProperties);

//        try {
//            pool.getConnection().onItem().delayIt().by(Duration.ofMillis(750))
//              .subscribe().asCompletionStage().get().closeAndAwait();
//        } catch (Exception e) {
//            logger.error(e.getMessage());
//        }

        promise.complete(pool);
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

//    protected MySQLPool getPool(Map<String, String> dbMap, Properties dbProperties) {
//
//        PoolOptions poolOptions = new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);
//
//        MySQLConnectOptions connectOptions;
//        connectOptions = new MySQLConnectOptions()
//          .setHost(dbMap.get("host"))
//          .setPort(Integer.parseInt(dbMap.get("port")))
//          .setUser(dbProperties.getProperty("user"))
//          .setPassword(dbProperties.getProperty("password"))
//          .setDatabase(dbMap.get("dbname"))
//          .setSsl(Boolean.valueOf(dbProperties.getProperty("ssl")))
//          .setCharset("utf8mb4").setIdleTimeout(1);
//
//        /* For OpenApi if not using Handicap */
//        setMySQLConnectOptions(connectOptions);
//        setPoolOptions(poolOptions);
//
//        return MySQLPool.pool(DodexUtil.getVertx(), connectOptions, poolOptions);
//    }
}