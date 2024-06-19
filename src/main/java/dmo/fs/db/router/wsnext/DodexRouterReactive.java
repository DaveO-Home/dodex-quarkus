package dmo.fs.db.router.wsnext;

import dmo.fs.db.reactive.DbConfiguration;
import dmo.fs.db.reactive.DodexReactiveBase;
import dmo.fs.db.reactive.DodexReactiveDatabase;
import dmo.fs.kafka.KafkaEmitterDodex;
import dmo.fs.quarkus.Server;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import dmo.fs.utils.ParseQueryUtilHelper;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.arc.properties.UnlessBuildProperty;
import io.quarkus.websockets.next.*;
import io.reactivex.rxjava3.core.Observable;
import io.vertx.core.Promise;
import io.vertx.mutiny.core.Vertx;
import io.vertx.reactivex.jdbcclient.JDBCPool;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/*
    Using mutiny the sqlite3 database gets locked on Quarkus shutdown, so using reactive.
    "dodex.default.db" can be "sqlite3" or "cubrid"
 */
@IfBuildProperty(name = "dodex.default.db", stringValue = "sqlite3", enableIfMissing = true)
@UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "h2", enableIfMissing = true)
@UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "firebase", enableIfMissing = true)
@UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "neo4j", enableIfMissing = true)
@UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "cassandra", enableIfMissing = true)
@UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "mariadb", enableIfMissing = true)
@UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "postgres", enableIfMissing = true)
@UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "ibmdb2", enableIfMissing = true)
@WebSocket(path = "/dodex")
public class DodexRouterReactive extends DodexReactiveBase {
    protected static final Logger logger = LoggerFactory.getLogger(DodexRouterReactive.class.getSimpleName());

    protected static final KafkaEmitterDodex ke = CDI.current().select(KafkaEmitterDodex.class).isUnsatisfied() ? null :
      CDI.current().select(KafkaEmitterDodex.class).get();
    protected boolean isUsingCubrid;
    protected DodexReactiveDatabase dodexReactiveDatabase;
    @Inject
    Vertx vertx;
    @Inject
    WebSocketConnection connection;

    public DodexRouterReactive() throws SQLException, IOException, InterruptedException {
        super();
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

        dodexReactiveDatabase = DbConfiguration.getDefaultDb();
        DodexReactiveBase.setDodexDatabase(dodexReactiveDatabase);
        DodexReactiveDatabase.setVertx(Server.vertx);
        Promise<JDBCPool> dbPromiseReactive = dodexReactiveDatabase.databaseSetup();
    /*
      Give new tables time to create.
     */
        long ob = Observable.timer(250, TimeUnit.MILLISECONDS).blockingFirst();
    }

    @OnOpen
    public void onOpen() throws InterruptedException, IOException, SQLException {
        queryParams = connection.handshakeRequest().query().transform(q -> {
            String queryString = URLDecoder.decode(q, StandardCharsets.UTF_8);
            return ParseQueryUtilHelper.getQueryMap(queryString);
        });

        sessions.put(connection.id(), connection);
        sessionsNext.put(connection.id(), queryParams);
        logger.info(String.join("", ColorUtilConstants.BLUE_BOLD_BRIGHT,
          queryParams.get("handle"), ColorUtilConstants.RESET));
        broadcast(connection, "User " + queryParams.get("handle") + " joined", queryParams);

        if (ke != null) {
            ke.setValue("sessions", sessionsNext.size());
        }

        String currentRemoteAddress = remoteAddress;

        if (isUsingCubrid()) {
            CubridRouterReactive cubridRouterReactive = new CubridRouterReactive();
            cubridRouterReactive.setup();
            try {
                cubridRouterReactive.doConnection(getThisWebSocket(connection), currentRemoteAddress, sessionsNext);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        } else {
            DodexReactiveBase dodexReactiveBase = new DodexReactiveBase();
            dodexReactiveBase.setup();
            try {
                dodexReactiveBase.doConnection(sessions.get(connection.id()), currentRemoteAddress, sessionsNext);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @OnTextMessage()
    public String onMessage(String message) {
        sessions = connection.getOpenConnections().stream().collect(Collectors.toConcurrentMap(WebSocketConnection::id, v -> v));

        if (isUsingCubrid()) {
            CubridRouterReactive cubridRouterReactive = new CubridRouterReactive();
            cubridRouterReactive.doMessage(connection, sessions, message, sessionsNext);
        } else {
            doMessage(connection, sessions, message);
        }

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

    public boolean isUsingCubrid() {
        return isUsingCubrid;
    }

    public void setUsingCubrid(boolean isUsingCubrid) {
        this.isUsingCubrid = isUsingCubrid;
    }

    public static KafkaEmitterDodex getKafkaEmitterDodex() {
        return ke;
    }


//  @RouteFilter(500)
//  void getRemoteAddress(RoutingContext rc) {
//    if (rc != null) {
//      if (rc.request() != null && rc.request().remoteAddress() != null) {
//        remoteAddress = rc.request().remoteAddress().toString();
//      }
//      rc.next();
//    }
//  }
}