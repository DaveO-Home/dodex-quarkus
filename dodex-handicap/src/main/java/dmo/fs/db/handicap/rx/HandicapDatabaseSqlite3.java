/*
    This does not work in Quarkus
 */
package dmo.fs.db.handicap.rx;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.handicap.HandicapDatabase;
import dmo.fs.db.handicap.utils.DodexUtil;
import dmo.fs.db.reactive.DbConfiguration;
import dmo.fs.quarkus.Server;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Promise;
import io.vertx.rxjava3.jdbcclient.JDBCPool;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class HandicapDatabaseSqlite3 extends DbDefinitionBase implements HandicapDatabase {
    protected final static Logger logger =
      LoggerFactory.getLogger(HandicapDatabaseSqlite3.class.getName());
    protected Properties dbProperties;
    protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
    protected Map<String, String> dbMap;
    protected JsonNode defaultNode;
    protected String webEnv = Server.isProduction() ? "prod" : "dev";
    protected DodexUtil dodexUtil = new DodexUtil();
    protected JDBCPool pool4;
    protected Boolean isCreateTables = false;
    protected Promise<String> returnPromise = Promise.promise();

    public HandicapDatabaseSqlite3(Map<String, String> dbOverrideMap, Properties dbOverrideProps)
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

    public HandicapDatabaseSqlite3() throws IOException {
        super();
        defaultNode = dodexUtil.getDefaultNode();
        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
        dbProperties = dodexUtil.mapToProperties(dbMap);

        dbProperties.setProperty("foreign_keys", "true");
        databaseSetup();
    }

    public HandicapDatabaseSqlite3(Boolean isCreateTables)
      throws IOException {
        super();
        defaultNode = dodexUtil.getDefaultNode();

        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
        dbProperties = dodexUtil.mapToProperties(dbMap);

        dbProperties.setProperty("foreign_keys", "true");
        this.isCreateTables = isCreateTables;
        logger.info("In (true) sqlite: {}", defaultNode);
    }

    public Uni<String> checkOnTables() {
        if (isCreateTables) {
            databaseSetup();
        }
        return returnPromise.future();
    }

    protected void databaseSetup() {
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getPool4() {
        return (T) pool4;
    }

    @Override
    public void setVertx(io.vertx.mutiny.core.Vertx vertx) {

    }

    @Override
    public io.vertx.rxjava3.core.Vertx getVertxR() {
        return null;
    }

    @Override
    public <T> void setConnectOptions(T connectOptions) {

    }

    @Override
    public void setPoolOptions(PoolOptions poolOptions) {

    }

    public io.vertx.mutiny.core.Vertx getVertx() {
        return null;
    }
}
