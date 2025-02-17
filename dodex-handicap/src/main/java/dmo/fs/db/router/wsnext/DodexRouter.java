package dmo.fs.db.router.wsnext;

import dmo.fs.db.dodex.CreateDatabase;
import dmo.fs.db.dodex.CreateDatabaseImpl;
import dmo.fs.db.wsnext.DbConfiguration;
import dmo.fs.kafka.KafkaEmitterDodex;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import dmo.fs.utils.ParseQueryUtilHelper;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.arc.properties.UnlessBuildProperty;
import io.quarkus.websockets.next.*;

import io.vertx.core.buffer.Buffer;
import io.vertx.reactivex.jdbcclient.JDBCPool;
import io.vertx.sqlclient.PoolOptions;
import jakarta.enterprise.inject.spi.CDI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Locale;

/*
    To run without setting - DEFAULT_DB=h2 or postgres or mariadb
    1. Set "dodex.default.db" entry in .../src/main/resources/application.properties file
    2. change "dodex.default.db" below to proper value
    3. The default is now "h2". You can still set DEFAULT_DB to supported db
 */
@IfBuildProperty(name = "dodex.default.db", stringValue = "h2")
@UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "sqlite3", enableIfMissing = true)
@UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "firebase", enableIfMissing = true)
@UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "neo4j", enableIfMissing = true)
@UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "cassandra", enableIfMissing = true)
@UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "cubrid", enableIfMissing = true)
@WebSocket(path = "/dodex")
public class DodexRouter extends DodexRouterBase {
    protected static final Logger logger = LoggerFactory.getLogger(DodexRouter.class.getSimpleName());

    protected static final KafkaEmitterDodex ke = CDI.current().select(KafkaEmitterDodex.class).isUnsatisfied() ? null :
      CDI.current().select(KafkaEmitterDodex.class).get();

    private final CreateDatabase createDatabase = CreateDatabaseImpl.getDefaultDb();

    public DodexRouter() throws SQLException, IOException, InterruptedException {
        System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tF %1$tT] [%4$s] %5$s %3$s %n");
        String value = isProduction ? "prod" : "dev";

        Locale.setDefault(Locale.US);
        if (isProduction) {
            DodexUtil.setEnv("prod");
        } else {
            DodexUtil.setEnv(value);
        }
        /*
            All databases using mutiny
         */
        pool = createDatabase.getPool4();

        createDatabase.getPoolOptions();
        createDatabase.getConnectOptions();
        /*
            Setting up application sql(jooq) and processing logic
         */
        dodexDatabase = DbConfiguration.getDefaultDb();
        dodexDatabase.setConnectOptions(createDatabase.getConnectOptions());
        dodexDatabase.setPoolOptions(createDatabase.getPoolOptions());
        dbPromise.complete(pool);
    }

    @OnOpen()
    public String onOpen() throws SQLException, IOException, InterruptedException {
        queryParams = connection.handshakeRequest().query().transform(q -> {
            String queryString = URLDecoder.decode(q, StandardCharsets.UTF_8);
            return ParseQueryUtilHelper.getQueryMap(queryString);
        });
        queryParams.put("remoteAddress", remoteAddress);

        sessionsNext.put(connection.id(), queryParams);
        logger.info(String.join("", ColorUtilConstants.BLUE_BOLD_BRIGHT,
          queryParams.get("handle"), ColorUtilConstants.RESET));
        broadcast(connection, "User " + queryParams.get("handle") + " joined", queryParams);

        if (ke != null) {
            ke.setValue("sessions", connection.getOpenConnections().size());
        }
        setup();
        doConnection(connection);

        return null;
    }

    @OnClose
    public void onClose() {
        String handle = sessionsNext.get(connection.id()).get("handle");
        if (logger.isInfoEnabled()) {
            logger.info("{}Closing ws-connection to client: {}{}", ColorUtilConstants.BLUE_BOLD_BRIGHT,
              handle, ColorUtilConstants.RESET);
        }

        sessionsNext.remove(connection.id());
        connection.broadcast().sendText("User " + handle + " left").subscribe().asCompletionStage();
        if (ke != null) {
            ke.setValue("sessions", connection.getOpenConnections().size());
        }
    }

    @OnTextMessage()
    public String onMessage(String message) {
        doMessage(connection, message);
        return null;
    }

    @OnPongMessage
    void pong(Buffer data) {
        logger.debug("Pong received: {}", data);
    }

    public static KafkaEmitterDodex getKafkaEmitterDodex() {
        return ke;
    }

    public <T> T  getConnectionOptions() {
        return createDatabase.getConnectOptions();
    }

    public PoolOptions getPoolOptions() {
        return createDatabase.getPoolOptions();
    }
/*
//  This causes warning messages when using gRPC
    @RouteFilter(500)
    void getRemoteAddress(RoutingContext rc) {
        if (rc != null) {
            if (rc.request() != null && rc.request().remoteAddress() != null) {
                remoteAddress = rc.request().remoteAddress().toString();
            }
            rc.next();
        }
    }
 */
}
