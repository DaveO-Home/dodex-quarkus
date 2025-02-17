package dmo.fs.db.wsnext.db2;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.MessageUser;
import dmo.fs.db.MessageUserImpl;
import dmo.fs.db.wsnext.DbConfiguration;
import dmo.fs.db.wsnext.DbDefinitionBase;
import dmo.fs.db.wsnext.DodexDatabase;
import dmo.fs.quarkus.Server;
import dmo.fs.utils.DodexUtil;
import io.vertx.db2client.DB2ConnectOptions;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.db2client.DB2Builder;
import io.vertx.mutiny.db2client.DB2Pool;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
/*
    DB2 only supports "dodex"
*/
public class DodexDatabaseIbmDB2 extends DbDefinitionBase implements DodexDatabase {
    protected static final Logger logger = LoggerFactory.getLogger(DodexDatabaseIbmDB2.class.getName());
    protected Properties dbProperties;
    protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
    protected Map<String, String> dbMap;
    protected JsonNode defaultNode;
    protected String webEnv = DodexUtil.getEnv();
    protected DodexUtil dodexUtil = new DodexUtil();
    protected DB2ConnectOptions connectOptions;

    public DodexDatabaseIbmDB2(Map<String, String> dbOverrideMap, Properties dbOverrideProps) throws IOException {
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
        databaseSetup().futureAndAwait();
    }

    public DodexDatabaseIbmDB2() throws InterruptedException, IOException, SQLException {
        super();
        defaultNode = dodexUtil.getDefaultNode();

        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
        dbProperties = dodexUtil.mapToProperties(dbMap);
        databaseSetup().future().subscribeAsCompletionStage().isDone();
    }

    @Override
    public Promise<Pool> databaseSetup() {
        if ("dev".equals(webEnv)) {
            DbConfiguration.configureTestDefaults(dbMap, dbProperties);
        } else {
            DbConfiguration.configureDefaults(dbMap, dbProperties); // Prod
        }

        Promise<Pool> promise = Promise.promise();
        pool = getPool(dbMap, dbProperties);
;
        setupSql(pool);

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

    protected Pool getPool(Map<String, String> dbMap, Properties dbProperties) {

        poolOptions = new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

        connectOptions = new DB2ConnectOptions()
          .setHost(dbMap.get("host"))
          .setPort(Integer.parseInt(dbMap.get("port")))
          .setUser(dbProperties.getProperty("user"))
          .setPassword(dbProperties.getProperty("password"))
          .setDatabase(dbMap.get("dbname"))
          .setSsl(Boolean.parseBoolean(dbProperties.getProperty("ssl")));
        setDB2ConnectOptions(connectOptions);
        setPoolOptions(poolOptions);
        return DB2Builder
          .pool()
          .with(poolOptions)
          .connectingTo(db2ConnectOptions)
          .using(Server.getVertxMutiny())
          .build();
    }
}
