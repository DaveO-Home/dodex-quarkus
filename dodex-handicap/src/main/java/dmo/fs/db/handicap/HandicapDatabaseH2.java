package dmo.fs.db.handicap;

import dmo.fs.db.router.wsnext.DodexRouter;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.rxjava3.core.Vertx;
import jakarta.enterprise.inject.spi.CDI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/*
    Sets up DSL for jooq and handicap sql
 */
public class HandicapDatabaseH2 extends DbDefinitionBase implements HandicapDatabase {
    protected final static Logger logger =
      LoggerFactory.getLogger(HandicapDatabaseH2.class.getName());

    protected Pool pool4;
    protected Boolean isCreateTables = false;
    protected Promise<String> returnPromise = Promise.promise();

    public HandicapDatabaseH2(Map<String, String> dbOverrideMap, Properties dbOverrideProps)
      throws IOException {
        super();
        databaseSetup();
    }

    public HandicapDatabaseH2() throws IOException {
        super();
        databaseSetup();
    }

    public HandicapDatabaseH2(Boolean isCreateTables)
      throws IOException {
        super();
        this.isCreateTables = isCreateTables;
    }

    public Uni<String> checkOnTables() {
        if (isCreateTables) {
            databaseSetup();
        }
        return returnPromise.future();
    }

    protected void databaseSetup() {
        DodexRouter dodexRouter = CDI.current().select(DodexRouter.class).isUnsatisfied() ? null :
          CDI.current().select(DodexRouter.class).get();
        if(dodexRouter == null) {
            throw new NullPointerException("DodexRouter from CDI");
        }
        pool4 = dodexRouter.getPool();

        setConnectOptions(dodexRouter.getConnectionOptions());
        setPoolOptions(dodexRouter.getPoolOptions());
        /*
            No need to set up DSL if only generating jooq records(generate project)
         */
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
    public Vertx getVertxR() {
        return null;
    }

    @Override
    public void setVertxR(Vertx vertx) {
    }

    public io.vertx.mutiny.core.Vertx getVertx() {
        return null;
    }

    @Override
    public void setVertx(io.vertx.mutiny.core.Vertx vertx) {
    }
}
