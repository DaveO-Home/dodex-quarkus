package dmo.fs.db.router.wsnext;

import dmo.fs.db.wsnext.DbConfiguration;
import dmo.fs.kafka.KafkaEmitterDodex;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import dmo.fs.utils.ParseQueryUtilHelper;
import io.quarkus.arc.properties.UnlessBuildProperty;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
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
    2. Uncomment line below.
 */
//@IfBuildProperty(name = "dodex.default.db", stringValue = "h2")
@UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "sqlite3")
@UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "firebase")
@UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "neo4j")
@UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "cassandra")
@UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "cubrid")
@WebSocket(path = "/dodex")
public class DodexRouter extends DodexRouterBase {
    protected static final Logger logger = LoggerFactory.getLogger(DodexRouter.class.getSimpleName());

    protected static final KafkaEmitterDodex ke = CDI.current().select(KafkaEmitterDodex.class).isUnsatisfied() ? null :
      CDI.current().select(KafkaEmitterDodex.class).get();

    public DodexRouter() throws SQLException, IOException, InterruptedException {
        System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tF %1$tT] [%4$s] %5$s %3$s %n");
        System.setProperty("dmo.fs.level", "INFO");
        System.setProperty("org.jooq.no-logo", "true");
        String value = isProduction ? "prod" : "dev";

        Locale.setDefault(new Locale("US"));
        if (isProduction) {
            DodexUtil.setEnv("prod");
        } else {
            DodexUtil.setEnv(value);
        }
        dodexDatabase = DbConfiguration.getDefaultDb();
        dbPromise = dodexDatabase.databaseSetup();
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
            logger.info(String.format("%sClosing ws-connection to client: %s%s", ColorUtilConstants.BLUE_BOLD_BRIGHT,
              handle, ColorUtilConstants.RESET));
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

    public static KafkaEmitterDodex getKafkaEmitterDodex() {
        return ke;
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
