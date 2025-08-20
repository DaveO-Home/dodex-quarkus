/*
    This does not work in Quarkus
 */
package dmo.fs.db.handicap;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.handicap.utils.DodexUtil;
import dmo.fs.db.router.wsnext.DodexRouter;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Promise;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.mutiny.jdbcclient.JDBCPool;
import jakarta.enterprise.inject.spi.CDI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class DodexDatabaseSqlite3  extends DbDefinitionBase implements HandicapDatabase {
    protected final static Logger logger =
      LoggerFactory.getLogger(DodexDatabaseSqlite3.class.getName());
    protected Properties dbProperties;
    protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
    protected Map<String, String> dbMap;
    protected JsonNode defaultNode;
    protected String webEnv = DodexUtil.getEnv();
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
//        defaultNode = dodexUtil.getDefaultNode();
//        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
//        dbProperties = dodexUtil.mapToProperties(dbMap);
//
//        dbProperties.setProperty("foreign_keys", "true");
//        databaseSetup();
    }

//    public DodexDatabaseSqlite3(Boolean isCreateTables)
//      throws IOException {
//        super();
////        defaultNode = dodexUtil.getDefaultNode();
////
////        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
////        dbProperties = dodexUtil.mapToProperties(dbMap);
////
////        dbProperties.setProperty("foreign_keys", "true");
////        this.isCreateTables = isCreateTables;
////        logger.info("In (true) sqlite: " + defaultNode);
//    }
    @Override
    public Uni<String> checkOnTables() {
        if (isCreateTables) {
//            databaseSetup();
            returnPromise.complete("");
        }
        return returnPromise.future();
    }

    protected void databaseSetup() {
        DodexRouter dodexRouter = CDI.current().select(DodexRouter.class).isUnsatisfied() ? null :
          CDI.current().select(DodexRouter.class).get();
        if(dodexRouter == null) {
            throw new NullPointerException("DodexRouter from CDI");
        }
        pool4 = (JDBCPool) dodexRouter.getPool();

        setConnectOptions(dodexRouter.getConnectionOptions());
        setPoolOptions(dodexRouter.getPoolOptions());

        try {
            setupSql(pool4);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getPool4() {
        return (T) pool4;
    }

    @Override
    public io.vertx.mutiny.core.Vertx getVertx() {
        return null;
    }

    @Override
    public void setVertxR(Vertx vertx) {
        //
    }

    @Override
    public Vertx getVertxR() {
        return null;
    }
}
