package dmo.fs.db.reactive.sqlite3;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.MessageUser;
import dmo.fs.db.MessageUserImpl;
import dmo.fs.db.reactive.DbConfiguration;
import dmo.fs.db.reactive.DbReactiveSqlBase;
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
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class DodexDatabaseSqlite3 extends DbReactiveSqlBase implements DodexReactiveDatabase {
    protected static final Logger logger = LoggerFactory.getLogger(DodexDatabaseSqlite3.class.getName());
    protected Properties dbProperties = new Properties();
    protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
    protected Map<String, String> dbMap = new ConcurrentHashMap<>();
    protected JsonNode defaultNode;
    protected String webEnv = Server.isProduction() ? "prod" : "dev";
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

}
