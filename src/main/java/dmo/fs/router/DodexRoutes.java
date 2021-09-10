package dmo.fs.router;

import java.io.IOException;
import java.sql.SQLException;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.quarkus.Server;
import dmo.fs.spa.db.reactive.SpaRoutes;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.annotations.CommandLineArguments;
import io.quarkus.runtime.configuration.ProfileManager;
import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.Route.HttpMethod;
import io.quarkus.vertx.web.RoutingExchange;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.FaviconHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.TimeoutHandler;
import io.vertx.mutiny.core.Vertx;

@ApplicationScoped
public class DodexRoutes {
    private static final Logger logger = LoggerFactory.getLogger(DodexRoutes.class.getName());
    private final StaticHandler staticHandler = StaticHandler.create();
    private final boolean isProduction = !ProfileManager.getLaunchMode().isDevOrTest();
    private final io.vertx.core.Vertx coreVertx = io.vertx.core.Vertx.vertx();
    @Inject
    @CommandLineArguments
    String[] args;

    @Inject
    Vertx vertx;

    void onStart(@Observes StartupEvent event) {
        System.setProperty("org.jooq.no-logo", "true");
        System.setProperty("org.jooq.no-tips", "true");
        logger.info(String.format("%sDodex Server on Quarkus started%s", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                ColorUtilConstants.RESET));
    }
    void onStop(@Observes ShutdownEvent event) {
        if (logger.isInfoEnabled()) {
            logger.info(String.format("%sStopping Quarkus%s", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                    ColorUtilConstants.RESET));
        }
    }

    // /* Just a way to gracefully shutdown the dev server */
    @Route(regex = "/dev[/]?|/dev/.*\\.html", methods = HttpMethod.GET)
    void dev(RoutingExchange ex) {
        HttpServerResponse response = ex.response();
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
        HttpServerResponse response = ex.response();
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
        HttpServerResponse response = ex.response(); // routingContext.response();
        response.putHeader("content-type", "text/html");

        if (isProduction) {
            int length = ex.context().request().path().length();
            String path = ex.context().request().path();
            String file = length < 7 ? "dodex/index.html" : path.substring(1).replace("ddex", "dodex");

            response.sendFile(file);
        } else {
            response.setStatusCode(404).end("not found");
        }
    }

    // static content and Spa Routes
    public void init(@Observes Router router) {
        String value = System.getenv("VERTXWEB_ENVIRONMENT");
        FaviconHandler faviconHandler = FaviconHandler.create(coreVertx);
        
        if (isProduction) {
            DodexUtil.setEnv("prod");
            staticHandler.setCachingEnabled(true);
        } else {
            DodexUtil.setEnv(value == null ? "dev" : value);
            staticHandler.setCachingEnabled(false);
        }
        router.route().failureHandler(ctx -> { 
            ctx.next();
            if (logger.isInfoEnabled()) {
                logger.error(String.format("%sFAILURE in static route: %d%s", ColorUtilConstants.RED_BOLD_BRIGHT,
                        ctx.statusCode(), ColorUtilConstants.RESET));
            }
        });

        String readme = "/dist_test/react-fusebox";
        if (isProduction) {
            readme = "/dist/react-fusebox";
        }
        router.route(readme + "/README.md").produces("text/markdown").handler(ctx -> {
            HttpServerResponse response = ctx.response();
            String acceptableContentType = ctx.getAcceptableContentType();
            response.putHeader("content-type", acceptableContentType);
            response.sendFile("static/dist_test/README.md");
        });

        staticHandler.setWebRoot("static");

        router.route("/*").produces("text/plain").produces("text/html").produces("text/markdown").produces("image/*")
                .handler(staticHandler).handler(TimeoutHandler.create(2000));

        if ("dev".equals(DodexUtil.getEnv())) {
            router.route().handler(CorsHandler.create("*"/* Need ports 8089 & 9876 */)
                    .allowedMethod(io.vertx.core.http.HttpMethod.GET));
        }

        Server.getServerPromise().future().onSuccess(httpServer -> {
            try {
                setDodexRoute(httpServer, router);
            } catch (InterruptedException | IOException | SQLException e) {
                e.printStackTrace();
            }
        });
        
        router.route().handler(faviconHandler);
    }

    public void setDodexRoute(HttpServer server, Router router) throws InterruptedException, IOException, SQLException {
		DodexUtil du = new DodexUtil();
		String defaultDbName = du.getDefaultDb();
        Promise<Void> routesPromise = Promise.promise();

        logger.info("{}{}{}{}{}",ColorUtilConstants.PURPLE_BOLD_BRIGHT, "Using ", defaultDbName, " database", ColorUtilConstants.RESET);

        switch(defaultDbName) {
            case "sqlite3": // non mutiny supported db's - uses Vertx reactivex instead
            case "h2": 
            case "cubrid": 
                DodexRouter dodexRouter = CDI.current().select(DodexRouter.class).get();
                dodexRouter.setReactive(true);
                new SpaRoutes(coreVertx, router, routesPromise);
                break;
            default:
                new dmo.fs.spa.router.SpaRoutes(router, routesPromise); // Supported SqlClients for async db's - mutiny
            break;
        }
       
        Server.setRoutesPromise(routesPromise);
    }
}