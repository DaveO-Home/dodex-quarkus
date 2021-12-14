package dmo.fs.router;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.Locale;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.CDI;
import javax.websocket.MessageHandler;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.db.DbConfiguration;
import dmo.fs.db.reactive.DodexReactiveDatabase;
import dmo.fs.db.reactive.DodexReactiveRouter;
import dmo.fs.db.reactive.CubridReactiveRouter;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.quarkus.vertx.web.RouteFilter;
import io.vertx.ext.web.RoutingContext;

/*
    Quarkus does not allow a conditional ServerEndpoint - the @OnOpen is used for
    Mutiny, Reactivex, Cassandra(Akka), Firebase etc.
*/
@ServerEndpoint("/dodex")
@ApplicationScoped
public class DodexRouter extends DodexRouterBase {
    private static final Logger logger = LoggerFactory.getLogger(DodexRouter.class.getName());
    private boolean isUsingCassandra = false;
    private boolean isUsingFirebase = false;
    private boolean isUsingCubrid = false;
    private static boolean isUsingNeo4j = false;

    public DodexRouter() throws InterruptedException, IOException, SQLException {
        System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tF %1$tT] [%4$s] %5$s %3$s %n");
        System.setProperty("dmo.fs.level", "INFO");
        System.setProperty("org.jooq.no-logo", "true");
        String value = System.getenv("VERTXWEB_ENVIRONMENT");

        Locale.setDefault(new Locale("US"));
        if (isProduction) {
            DodexUtil.setEnv("prod");
        } else {
            DodexUtil.setEnv(value == null ? "dev" : value);
        }
    }

    @Override
    @OnOpen
    public void onOpen(Session session) throws InterruptedException, IOException, SQLException {
        DodexReactiveDatabase dodexReactiveDatabase;
        DodexReactiveRouter[] dodexReactiveRouter = { null };
        CubridReactiveRouter[] cubridReactiveRouter = { null };
        String currentRemoteAddress = remoteAddress;

        if (isUsingCassandra()) {
            CassandraRouter cassandraRouter = CDI.current().select(CassandraRouter.class).get();
            cassandraRouter.setRemoteAddress(currentRemoteAddress);
            if (!isInitialized) {
                cassandraRouter.setSessions(sessions);
                broadcast(session, "User " + session.getRequestParameterMap().get("handle").get(0) + " joined");
            }
            cassandraRouter.setCassandraHandler(session);
            isInitialized = true;
            return;
        } else if (isUsingFirebase()) {
            FirebaseRouter firebaseRouter = CDI.current().select(FirebaseRouter.class).get();
            firebaseRouter.setRemoteAddress(currentRemoteAddress);
            firebaseRouter.setFirebaseHandler(session);
            if (!isInitialized) {
                broadcast(session, "User " + session.getRequestParameterMap().get("handle").get(0) + " joined");
            }
            isInitialized = true;
            return;
        }  else if (isUsingNeo4j()) {
            Neo4jRouter neo4jRouter = CDI.current().select(Neo4jRouter.class).get();
            neo4jRouter.setRemoteAddress(currentRemoteAddress);
            neo4jRouter.setNeo4jHandler(session);
            if (!isInitialized) {
                broadcast(session, "User " + session.getRequestParameterMap().get("handle").get(0) + " joined");
            }
            isInitialized = true;
            return;
        } else {
            if (isReactive) {
                if(isUsingCubrid()) {
                    cubridReactiveRouter[0] = new CubridReactiveRouter();
                } else {
                    dodexReactiveRouter[0] = new DodexReactiveRouter();
                }
            }

            if (isReactive && !isInitialized) {
                dodexReactiveDatabase = DbConfiguration.getDefaultDb();
                DodexReactiveRouter.setDodexDatabase(dodexReactiveDatabase);
                DodexReactiveDatabase.setVertx(io.vertx.reactivex.core.Vertx.vertx());
                dbPromiseReactive = dodexReactiveDatabase.databaseSetup();
                dbPromiseReactive.future().onComplete(jdbcPool -> {
                    if(isUsingCubrid()) {
                        cubridReactiveRouter[0].setup();
                    } else {
                        dodexReactiveRouter[0].setup();
                    }
                });
                isInitialized = true;
            } else if(isUsingNeo4j()) {
                // 
            } else if (!isSetupDone && !isReactive) {
                setup();
            }
        }
        sessions.put(session.getId(), session);

        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
                if (isReactive) {
                    if(isUsingCubrid()) {
                        cubridReactiveRouter[0].doMessage(session, sessions, message);
                    } else {
                        dodexReactiveRouter[0].doMessage(session, sessions, message);
                    }
                } else if(isUsingNeo4j()) {
                    // 
                } else {
                    doMessage(session, message);
                }
            }
        });

        logger.info(String.join("", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                session.getRequestParameterMap().get("handle").get(0), ColorUtilConstants.RESET));

        broadcast(session, "User " + session.getRequestParameterMap().get("handle").get(0) + " joined");
        if (isReactive) {
            if (!dbPromiseReactive.future().isComplete()) {
                dbPromiseReactive.future().onSuccess(pool -> {
                    try {
                        if(isUsingCubrid()) {
                            cubridReactiveRouter[0].doConnection(session, currentRemoteAddress);
                        } else {
                            dodexReactiveRouter[0].doConnection(session, currentRemoteAddress);
                        }
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                });
            } else {
                if(isUsingCubrid()) {
                    cubridReactiveRouter[0].doConnection(session, currentRemoteAddress);
                } else {
                    dodexReactiveRouter[0].doConnection(session, currentRemoteAddress);
                }
            }
        } else if(isUsingNeo4j()) {
            // 
        } else {
            doConnection(session);
        }
    }

    @Override
    @OnClose
    public void onClose(Session session) {
        sessions.remove(session.getId());
        if (logger.isInfoEnabled()) {
            logger.info(String.format("%sClosing ws-connection to client: %s%s", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                    session.getRequestParameterMap().get("handle").get(0), ColorUtilConstants.RESET));
        }
        broadcast(session, "User " + session.getRequestParameterMap().get("handle").get(0) + " left");
    }

    @Override
    @OnError
    public void onError(Session session, Throwable throwable) {
        sessions.remove(session.getId());
        throwable.printStackTrace();
        if (logger.isInfoEnabled()) {
            logger.info(String.format("%sWebsocket-failure...User %s%s%s%s", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                    session.getRequestParameterMap().get("handle").get(0), " left on error: ", throwable.getMessage(),
                    ColorUtilConstants.RESET));
        }
    }

    public boolean isUsingCassandra() {
        return isUsingCassandra;
    }

    public void setUsingCassandra(boolean isUsingCassandra) {
        this.isUsingCassandra = isUsingCassandra;
    }

    public boolean isUsingFirebase() {
        return isUsingFirebase;
    }

    public void setUsingFirebase(boolean isUsingFirebase) {
        this.isUsingFirebase = isUsingFirebase;
    }

    public boolean isUsingCubrid() {
        return isUsingCubrid;
    }

    public void setUsingCubrid(boolean isUsingCubrid) {
        this.isUsingCubrid = isUsingCubrid;
    }

    public void setUsingNeo4j(boolean isUsingNeo4j) {
        DodexRouter.isUsingNeo4j = isUsingNeo4j;
    }

    public static boolean isUsingNeo4j() {
        return isUsingNeo4j;
    }

    @RouteFilter(500)
    void getRemoteAddress(RoutingContext rc) {
        if (rc != null) {
            if (rc.request() != null && rc.request().remoteAddress() != null) {
                remoteAddress = rc.request().remoteAddress().toString();
            }
            rc.next();
        }
    }
}
