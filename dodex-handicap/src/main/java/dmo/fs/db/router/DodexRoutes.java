package dmo.fs.db.router;

import com.google.cloud.firestore.Firestore;
import dmo.fs.db.router.wsnext.*;
import dmo.fs.db.wsnext.cassandra.DodexCassandra;
import dmo.fs.quarkus.Server;
import dmo.fs.spa.db.SpaDbConfiguration;
import dmo.fs.spa.db.reactive.SpaRoutes;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import golf.handicap.routes.GrpcRoutes;
import golf.handicap.routes.HandicapRoutes;
import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.annotations.CommandLineArguments;
import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.Route.HttpMethod;
import io.quarkus.vertx.web.RoutingExchange;
import io.vertx.core.net.NetServerOptions;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.eventbus.EventBus;
import io.vertx.mutiny.core.http.HttpServerResponse;
import io.vertx.mutiny.ext.web.Router;
import io.vertx.mutiny.ext.web.handler.CorsHandler;
import io.vertx.mutiny.ext.web.handler.FaviconHandler;
import io.vertx.mutiny.ext.web.handler.StaticHandler;
import io.vertx.mutiny.ext.web.handler.TimeoutHandler;
import io.vertx.reactivex.ext.eventbus.bridge.tcp.TcpEventBusBridge;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@Unremovable
@ApplicationScoped
public class DodexRoutes {
    @Inject
    Vertx vertx;
    @Inject
    EventBus eb;
    protected static final Logger logger = LoggerFactory.getLogger(DodexRoutes.class.getName());
    protected static HandicapRoutes routesHandicap;
    protected final StaticHandler staticHandler = StaticHandler.create();
    protected boolean isProduction = Server.isProduction();
    protected io.vertx.reactivex.core.Vertx reactiveVertx = null;
    protected TcpEventBusBridge bridge;
    Firestore firestore;
    protected final io.vertx.core.Promise<Void> handicapPromise = io.vertx.core.Promise.promise();
    @Inject
    @CommandLineArguments
    String[] args;

    void onStart(@Observes StartupEvent event) {
        String startupMessage = "In Production";

        List<String> profiles = Server.getProfiles();
        for (String p : profiles) {
            if ("dev".equals(p) || "test".equals(p)) {
                isProduction = false;
                DodexUtil.setEnv("dev");
            } else {
                DodexUtil.setEnv(p);
            }
        }

        startupMessage = "dev".equals(DodexUtil.getEnv()) ? "In Development" : startupMessage;

        logger.info("{}{}{}", ColorUtilConstants.BLUE_BOLD_BRIGHT, startupMessage, ColorUtilConstants.RESET);
        logger.info("{}Dodex Server on Quarkus started{}", ColorUtilConstants.BLUE_BOLD_BRIGHT, ColorUtilConstants.RESET);
    }

    void onStop(@Observes ShutdownEvent event) {
        if (logger.isInfoEnabled()) {
            logger.info("{}Stopping Quarkus{}", ColorUtilConstants.BLUE_BOLD_BRIGHT, ColorUtilConstants.RESET);
        }
        if (bridge != null) {
            bridge.close();
        }
    }

    // /* Just a way to gracefully shutdown the dev server */
    @Route(regex = "/dev[/]?|/dev/.*\\.html", methods = HttpMethod.GET)
    void dev(RoutingExchange ex) {
        io.vertx.core.http.HttpServerResponse response = ex.response();
        response.putHeader("content-type", "text/html");

        if (isProduction) {
            response.setStatusCode(404).end("not found");
        } else {
            Quarkus.asyncExit();
            response.end("<div><strong>Exited</strong></dev>");
        }
    }

    @Route(regex = "/test[/]?|/test/.*\\.html", methods = HttpMethod.GET)
    void test(RoutingExchange ex) {
        io.vertx.core.http.HttpServerResponse response = ex.response();
        response.putHeader("content-type", "text/html");

        if (isProduction) {
            response.setStatusCode(404).end("not found");
        } else {
            int length = ex.context().request().path().length();
            String path = ex.context().request().path();
            String file = length < 7 ? "test/index.html" : path.substring(1);

            response.sendFile(file);
        }
    }

    // dodex conflicts with websocket endpoint "/dodex" so using ddex
    @Route(regex = "/ddex[/]?|/ddex/.*\\.html", methods = HttpMethod.GET)
    public void prod(RoutingExchange ex) {
        io.vertx.core.http.HttpServerResponse response = ex.response(); // routingContext.response();
        response.putHeader("content-type", "text/html");

        if (isProduction) {
            int length = ex.context().request().path().length();
            String path = ex.context().request().path();
            String file = length < 7 ? "dodex/index.html" : path.substring(1).replace("ddex", "dodex");

            response.sendFile(file);
        } else {
            response.setStatusCode(404).end("<h3>Not found-try production mode</h3>");
        }
    }

    @Route(regex = "/monitor[/]?|/monitor/.*\\.html", methods = HttpMethod.GET)
    void monitor(RoutingExchange ex) {
        io.vertx.core.http.HttpServerResponse response = ex.response();
        response.putHeader("content-type", "text/html");

        int length = ex.context().request().path().length();
        String path = ex.context().request().path();
        String file = length < 10 ? "monitor/index.html" : path.substring(1);

        response.sendFile(file);
    }

    // static content and Spa Routes
    public void init(@Observes Router router) {
        String value = Server.isProduction ? "prod" : "dev";
        FaviconHandler faviconHandler = FaviconHandler.create(vertx);

        if (isProduction) {
            DodexUtil.setEnv("prod");
            staticHandler.setCachingEnabled(true);
        } else {
            DodexUtil.setEnv(value);
            staticHandler.setCachingEnabled(false);
        }

        String readme = "/spa_test/react-fusebox";
        if (isProduction) {
            readme = "/dist/react-fusebox";
        }
        router.route(readme + "/README.md").produces("text/markdown").handler(ctx -> {
            HttpServerResponse response = ctx.response();
            String acceptableContentType = ctx.getAcceptableContentType();
            response.putHeader("content-type", acceptableContentType);
            response.sendFile("spa_test/README.md").subscribeAsCompletionStage().isDone();
        });

        router.route("/*").handler(StaticHandler.create())   //StaticHandler.create())
          .produces("text/plain")
          .produces("text/html")
          .produces("text/markdown")
          .produces("image/*")
          .handler(staticHandler)
        ;
        router.route().handler(TimeoutHandler.create(2000));

        if ("dev".equals(DodexUtil.getEnv())) {
            router.route().handler(CorsHandler.create(/* Need ports 8089 & 9876 */)
              .allowedMethod(io.vertx.core.http.HttpMethod.GET));
        }

        if (routesHandicap == null) {
            routesHandicap = new GrpcRoutes(Server.getVertxMutiny(), router);
            routesHandicap.getVertxRouter(handicapPromise);
        }

        Server.getServerPromise().future().onItem().invoke(isProduction -> {
            try {
                setDodexRoute(router);
            } catch (InterruptedException | IOException | SQLException e) {
                e.printStackTrace();
            }
        }).subscribeAsCompletionStage().isDone();

        router.route().handler(faviconHandler);
    }

    public void setDodexRoute(Router router) throws InterruptedException, IOException, SQLException {
        DodexUtil du = new DodexUtil();
        String defaultDbName = du.getDefaultDb();
        Promise<Router> routerPromise = Promise.promise();

        logger.info("{}{}{}{}{}", ColorUtilConstants.PURPLE_BOLD_BRIGHT, "Using ", defaultDbName, " database",
          ColorUtilConstants.RESET);
        Server.setDefaultDbName(defaultDbName);

//        DodexRouter dodexRouter = null;
//        DodexRouterReactive dodexRouterReactive = null;

        switch (defaultDbName) {
            case "cubrid":
                DodexRouterReactive dodexRouterReactiveCubrid = CDI.current().select(DodexRouterReactive.class).get();
                dodexRouterReactiveCubrid.setUsingCubrid(true);
            // fallthrough
            case "sqlite3": // non mutiny supported db's - uses Vertx reactivex instead
                DodexRouterReactive dodexRouterReactive = CDI.current().select(DodexRouterReactive.class).get();
                router.route().handler(routingContext -> {
                    dodexRouterReactive.setRemoteAddress(routingContext.request().remoteAddress().toString());
                    routingContext.next();
                });
//                    dodexRouterReactive.setReactive(true);
                try {
                    new SpaRoutes(vertx.getDelegate(), router, routerPromise);
                } catch (InterruptedException | SQLException e) {
                    throw new RuntimeException(e);
                }
                break;
            case "h2":
                DodexRouter dodexRouterH2 = CDI.current().select(DodexRouter.class).get();

                router.route().handler(routingContext -> {
                    dodexRouterH2.setRemoteAddress(routingContext.request().remoteAddress().toString());
                    routingContext.next();
                });

                handicapPromise.future().onSuccess(r -> {
                    try {
                        new SpaRoutes(vertx.getDelegate(), router, routerPromise);
                    } catch (InterruptedException | SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
                break;
            case "cassandra":
                try {
                    reactiveVertx = Server.vertx;
                    CassandraRouter cassandraRouter = CDI.current().select(CassandraRouter.class).get();
                    router.route().handler(routingContext -> {
                        cassandraRouter.setRemoteAddress(routingContext.request().remoteAddress().toString());
                        routingContext.next();
                    });
                    cassandraRouter.getDatabasePromise().future().onSuccess(none -> setupEventBridge(cassandraRouter));
                    cassandraRouter.setEb(reactiveVertx.eventBus());

                    SpaDbConfiguration.getSpaDb();
                    new dmo.fs.spa.router.SpaRoutes(router, routerPromise);
                } catch (Exception ex) {
                    throw ex;
                }
                break;
            case "firebase":
                try {
                    FirebaseRouter firebaseRouter = CDI.current().select(FirebaseRouter.class).get();
                    router.route().handler(routingContext -> {
                        firebaseRouter.setRemoteAddress(routingContext.request().remoteAddress().toString());
                        routingContext.next();
                    });
                    firestore = firebaseRouter.getDbf();
                    new dmo.fs.spa.router.SpaRoutes(router, routerPromise, firestore);
                } catch (Exception ex) {
                    throw ex;
                }
                break;
            case "neo4j":
                Neo4jRouter neo4jRouter = CDI.current().select(Neo4jRouter.class).get();
                router.route().handler(routingContext -> {
                    neo4jRouter.setRemoteAddress(routingContext.request().remoteAddress().toString());
                    routingContext.next();
                });
                SpaDbConfiguration.getSpaDb();
            // fallthrough
            default:
                if(!"neo4j".equals(defaultDbName)) {
                    DodexRouter dodexRouter = CDI.current().select(DodexRouter.class).get();
                    router.route().handler(routingContext -> {
                        dodexRouter.setRemoteAddress(routingContext.request().remoteAddress().toString());
                        routingContext.next();
                    });
                }
                new dmo.fs.spa.router.SpaRoutes(router, routerPromise); // Supported SqlClients for async db's - mutiny
                break;
        }

        Server.setRoutesPromise(routerPromise);
    }

    protected void setupEventBridge(CassandraRouter cassandraRouter) {
        DodexCassandra dodexCassandra = cassandraRouter.getDodexCassandra();
        Config config = ConfigProvider.getConfig();

        int eventBridgePort = isProduction ? Integer.parseInt(config.getConfigValue("prod.bridge.port").getValue())
          : Integer.parseInt(config.getConfigValue("dev.bridge.port").getValue());

        bridge = TcpEventBusBridge.create(reactiveVertx,
          new BridgeOptions().addInboundPermitted(new PermittedOptions().setAddress("vertx"))
            .addOutboundPermitted(new PermittedOptions().setAddress("akka"))
            .addInboundPermitted(new PermittedOptions().setAddress("akka"))
            .addOutboundPermitted(new PermittedOptions().setAddress("vertx")),
          new NetServerOptions(), event -> dodexCassandra.getEbConsumer().handle(event));

        bridge.listen(eventBridgePort, res -> {
            if (res.succeeded()) {
                logger.info("{}{}{}{}",
                  ColorUtilConstants.GREEN_BOLD_BRIGHT, "TCP Event Bus Bridge Started: ", eventBridgePort, ColorUtilConstants.RESET);
            } else {
                logger.error("{}{}{}",
                  ColorUtilConstants.RED_BOLD_BRIGHT, res.cause().getMessage(), ColorUtilConstants.RESET);
            }
        });
    }

    public TcpEventBusBridge getBridge() {
        return bridge;
    }

    public Firestore getFirestore() {
        return firestore;
    }

    public void setFirestore(Firestore firestore) {
        this.firestore = firestore;
    }
}
