package dmo.fs.router;

import java.io.IOException;
import java.sql.SQLException;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

import dmo.fs.spa.router.SpaRoutes;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ProfileManager;
import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.RoutingExchange;
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

    void onStart(@Observes StartupEvent event) {
        String value = System.getenv("VERTXWEB_ENVIRONMENT");
        System.setProperty("org.jooq.no-logo", "true");

        if (isProduction) {
            DodexUtil.setEnv("prod");
            staticHandler.setCachingEnabled(true);
        } else {
            DodexUtil.setEnv(value == null ? "dev" : value);
            staticHandler.setCachingEnabled(false);
        }
        staticHandler.setWebRoot("static");

        logger.info(String.join("", ColorUtilConstants.BLUE_BOLD_BRIGHT, "Dodex Server on Quarkus started",
                ColorUtilConstants.RESET));
    }

    void onStop(@Observes ShutdownEvent event) {
        logger.info(String.join("", ColorUtilConstants.BLUE_BOLD_BRIGHT, "Stopping Quarkus", ColorUtilConstants.RESET));
    }

    @Route(regex = "/test[/]?|/test/.*\\.html", methods = HttpMethod.GET)
    void test(RoutingExchange ex) {
        HttpServerResponse response = ex.response(); // routingContext.response();
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
    public void init(@Observes Router router) {
        SpaRoutes spaRoutes;
        Router vertxRouter = router;
        io.vertx.ext.web.Route route = router.route("/*").handler(staticHandler).handler(TimeoutHandler.create(2000));
        FaviconHandler faviconHandler = FaviconHandler.create();

        if (DodexUtil.getEnv().equals("dev")) {
            route.handler(CorsHandler.create("*"/* Need ports 8089 & 9876 */).allowedMethod(HttpMethod.GET));
        }

        try {
            spaRoutes = new SpaRoutes(router);
            vertxRouter = spaRoutes.getRouter();
        } catch (InterruptedException | IOException | SQLException e) {
            e.printStackTrace();
        }

        vertxRouter.route().handler(faviconHandler);
    }
}