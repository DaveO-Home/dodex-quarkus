package dmo.fs.db.wsnext.h2;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.MessageUser;
import dmo.fs.db.MessageUserImpl;
import dmo.fs.db.wsnext.DbDefinitionBase;
import dmo.fs.db.wsnext.DodexDatabase;
import dmo.fs.utils.DodexUtil;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.sqlclient.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class DodexDatabaseH2 extends DbDefinitionBase implements DodexDatabase {
    protected static final Logger logger = LoggerFactory.getLogger(DodexDatabaseH2.class.getSimpleName());
    protected Properties dbProperties;
    protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
    protected Map<String, String> dbMap;
    protected JsonNode defaultNode;
    protected String webEnv = DodexUtil.getEnv();
    protected DodexUtil dodexUtil = new DodexUtil();

//    public DodexDatabaseH2(Map<String, String> dbOverrideMap, Properties dbOverrideProps) throws IOException {
//        super();
//    }

    public DodexDatabaseH2() throws IOException {
        super();
    }

    @Override
    public Promise<Pool> databaseSetup() {

        Promise<Pool> promise = Promise.promise();

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

}
