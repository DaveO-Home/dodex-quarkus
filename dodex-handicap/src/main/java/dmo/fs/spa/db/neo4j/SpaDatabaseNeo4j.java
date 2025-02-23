package dmo.fs.spa.db.neo4j;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.quarkus.Server;
import dmo.fs.spa.db.SpaDbConfiguration;
import dmo.fs.spa.utils.SpaLogin;
import dmo.fs.spa.utils.SpaLoginImpl;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Promise;
import jakarta.ws.rs.core.Response.Status;
import org.neo4j.driver.*;
import org.neo4j.driver.exceptions.NoSuchRecordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

public class SpaDatabaseNeo4j extends DbNeo4j {
    protected final static Logger logger = LoggerFactory.getLogger(SpaDatabaseNeo4j.class.getName());
    protected Properties dbProperties = new Properties();
    protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
    protected Map<String, String> dbMap = new ConcurrentHashMap<>();
    protected JsonNode defaultNode;
    protected String webEnv = Server.isProduction() ? "prod" : "dev";
    protected DodexUtil dodexUtil = new DodexUtil();

    public SpaDatabaseNeo4j(Map<String, String> dbOverrideMap, Properties dbOverrideProps)
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

        SpaDbConfiguration.mapMerge(dbMap, dbOverrideMap);
    }

    public SpaDatabaseNeo4j() throws InterruptedException, IOException, SQLException {
        super();

        defaultNode = dodexUtil.getDefaultNode();
        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
        dbProperties = dodexUtil.mapToProperties(dbMap);
    }

    @Override
    public SpaLogin createSpaLogin() {
        return new SpaLoginImpl();
    }

    @Override
    public Promise<Void> databaseSetup() {
        if ("dev".equals(webEnv)) {
            // dbMap.put("dbname", "myDbname"); // this wiil be merged into the default map
            SpaDbConfiguration.configureTestDefaults(dbMap, dbProperties);
        } else {
            SpaDbConfiguration.configureDefaults(dbMap, dbProperties); // Prod
        }

        Promise<Void> promise = Promise.promise();
        Driver driver = getDriver(dbMap, dbProperties);

        Session session = driver.session(); //.asyncSession();
        int value = -1;
        try {
            org.neo4j.driver.Record record = session.run(getCheckConstraints()).single();
            value = record.get(0).asInt();
            value = session.run(getCheckApoc()).single().get(0).asInt();
            if (value == 1) {
                apocConstraints(driver, promise);
            } else {
                createConstraints(driver, promise);
            }
        } catch (Exception exception) {
            Throwable source = exception;
            if (exception instanceof CompletionException) {
                source = exception.getCause();
            }
            Status status = Status.INTERNAL_SERVER_ERROR;
            if (source instanceof NoSuchRecordException) {
                status = Status.NOT_FOUND;
            }
            logger.error("Error Checking Constraints: {}{}{}", ColorUtilConstants.RED,
              status.getReasonPhrase(), ColorUtilConstants.RESET);
            source.printStackTrace();
            promise.complete();
        }

        return promise;
    }

    // If apoc plugin installed
    protected void apocConstraints(Driver driver, Promise<Void> promise) {
        Multi.createFrom().resource(driver::session,
            session -> session.executeWrite(tx -> {
                Result resultConstraints = tx.run(getCreateConstraints());
                return Multi.createFrom().item(resultConstraints)
                  .map(record -> "constraints");
            }))
          .withFinalizer(session -> {
              promise.complete();
              session.close();
              return Uni.createFrom().nothing();
          })
          .onFailure().invoke(Throwable::printStackTrace)
          .subscribe().asStream();
    }

    // If minimum install
    protected void createConstraints(Driver driver, Promise<Void> promise) {
        Session session = driver.session();
        if (session != null) {
            try (Transaction tx = session.beginTransaction()) {
                tx.run(getLoginNameConstraint());
                tx.commit();
                promise.complete();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            session.close();
        }
    }

    protected Driver getDriver(Map<String, String> dbMap, Properties dbProperties) {
        String uri = String.join(":", dbMap.get("protocol"),
          String.format("//%s", dbMap.get("host")),
          dbMap.get("port"));

        return GraphDatabase.driver(uri,
          AuthTokens.basic(dbProperties.getProperty("user"), dbProperties.getProperty("password")));
    }
}
