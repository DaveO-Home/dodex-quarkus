package dmo.fs.db.wsnext.postgres;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.MessageUser;
import dmo.fs.db.MessageUserImpl;
import dmo.fs.db.wsnext.DbConfiguration;
import dmo.fs.db.wsnext.DbDefinitionBase;
import dmo.fs.db.wsnext.DodexDatabase;
import dmo.fs.utils.DodexUtil;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class DodexDatabasePostgres extends DbDefinitionBase implements DodexDatabase {
    protected static final Logger logger = LoggerFactory.getLogger(DodexDatabasePostgres.class.getName());
    protected Properties dbProperties;
    protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
    protected Map<String, String> dbMap;
    protected JsonNode defaultNode;
    protected String webEnv = DodexUtil.getEnv();
    protected DodexUtil dodexUtil = new DodexUtil();

    public DodexDatabasePostgres(Map<String, String> dbOverrideMap, Properties dbOverrideProps) throws IOException {
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

        assert dbOverrideMap != null;
        DbConfiguration.mapMerge(dbMap, dbOverrideMap);
    }

    public DodexDatabasePostgres() throws IOException {
        super();
        defaultNode = dodexUtil.getDefaultNode();

        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
        dbProperties = dodexUtil.mapToProperties(dbMap);
    }

    @Override
    public Promise<Pool> databaseSetup() {
        if ("dev".equals(webEnv)) {
            // dbMap.put("dbname", "myDbname"); // this will be merged into the default map
            DbConfiguration.configureTestDefaults(dbMap, dbProperties);
        } else {
            DbConfiguration.configureDefaults(dbMap, dbProperties); // Prod
        }

        Promise<Pool> promise = Promise.promise();
        PgPool pool = getPool(dbMap, dbProperties);

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

    protected PgPool getPool(Map<String, String> dbMap, Properties dbProperties) {

        PoolOptions poolOptions = new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

        PgConnectOptions connectOptions;

        connectOptions = new PgConnectOptions()
          .setHost(dbMap.get("host"))
          .setPort(Integer.parseInt(dbMap.get("port")))
          .setUser(dbProperties.getProperty("user"))
          .setPassword(dbProperties.getProperty("password"))
          .setDatabase(dbMap.get("dbname"))
          .setSsl(Boolean.parseBoolean(dbProperties.getProperty("ssl")))
          .setIdleTimeout(1);

        /* For OpenApi if not using Handicap */
        setPgConnectOptions(connectOptions);
        setPoolOptions(poolOptions);

        return PgPool.pool(DodexUtil.getVertx(), connectOptions, poolOptions);
    }
}
