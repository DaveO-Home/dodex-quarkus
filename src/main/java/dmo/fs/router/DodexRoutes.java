package dmo.fs.router;

import java.io.IOException;
import java.sql.SQLException;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import dmo.fs.spa.router.SpaRoutes;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.annotations.CommandLineArguments;
import io.quarkus.runtime.configuration.ProfileManager;
import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.RoutingExchange;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.FaviconHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.TimeoutHandler;

@ApplicationScoped
public class DodexRoutes {
    private final static Logger logger = LoggerFactory.getLogger(DodexRoutes.class.getName());
    private final StaticHandler staticHandler = StaticHandler.create();
    private final boolean isProduction = !ProfileManager.getLaunchMode().isDevOrTest();
    @Inject
    @CommandLineArguments
    String[] args;

    void onStart(@Observes StartupEvent event) {
        System.setProperty("org.jooq.no-logo", "true");

        logger.info(String.format("%sDodex Server on Quarkus started%s", ColorUtilConstants.BLUE_BOLD_BRIGHT, ColorUtilConstants.RESET));
    }

    void onStop(@Observes ShutdownEvent event) {
        logger.info(String.format("%sStopping Quarkus%s", ColorUtilConstants.BLUE_BOLD_BRIGHT, ColorUtilConstants.RESET));
    }

    @Inject Vertx vertx;
    /* Just a way to gracefully shutdown the dev server */
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

		if(isProduction) {
            int length = ex.context().request().path().length();
            String path = ex.context().request().path();
            String file = length < 7 ? "dodex/index.html" : path.substring(1).replace("ddex", "dodex");

            response.sendFile(file);
        } else {
            response.setStatusCode(404).end("not found");
        }
	}

    // static content and Spa Routes
    public void init(@Observes Router router) throws InterruptedException, IOException, SQLException {
        SpaRoutes spaRoutes;
        Router vertxRouter = router;
        String value = System.getenv("VERTXWEB_ENVIRONMENT");
        FaviconHandler faviconHandler = FaviconHandler.create();

        if (isProduction) {
            DodexUtil.setEnv("prod");
            staticHandler.setCachingEnabled(true);
        } else {
            DodexUtil.setEnv(value == null ? "dev" : value);
            staticHandler.setCachingEnabled(false);
        }

        staticHandler.setWebRoot("static");
        router.route("/*").handler(staticHandler).handler(TimeoutHandler.create(2000));

        if (DodexUtil.getEnv().equals("dev")) {
            router.route().handler(CorsHandler.create("*"/* Need ports 8089 & 9876 */).allowedMethod(HttpMethod.GET));
        }

        spaRoutes = new SpaRoutes(router, vertx);
        vertxRouter = spaRoutes.getRouter();

        vertxRouter.route().handler(faviconHandler);
    }
}