package dmo.fs.quarkus;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.utils.ColorUtilConstants;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.quarkus.runtime.configuration.ProfileManager;
import io.vertx.core.Promise;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;

@QuarkusMain
public class Server implements QuarkusApplication {
    private static final Logger logger = LoggerFactory.getLogger(Server.class.getName());
    private static Promise<HttpServer> serverPromise = Promise.promise();
    private static Promise<Router> routesPromise = Promise.promise();

    public static void main(String... args) {
        Quarkus.run(Server.class, args);
        Locale.setDefault(new Locale("US"));
    }

    @Override
    public int run(String... args) throws InterruptedException, ExecutionException, IOException {
        Vertx vertx = Vertx.vertx();
        HttpServer server = vertx.createHttpServer();
        Config config = ConfigProvider.getConfig();
        final boolean isProduction = !ProfileManager.getLaunchMode().isDevOrTest();

        serverPromise.complete(server); // passing HttpServer instance to "DodexRoutes" for reactivex setup
        String host = isProduction ? config.getValue("quarkus.http.host", String.class)
                : config.getValue("%dev.quarkus.http.host", String.class); // see application.properties
        Integer port = isProduction ? config.getValue("quarkus.http.port", Integer.class)
                : config.getValue("%dev.quarkus.http.port", Integer.class);
        boolean color = isProduction ? config.getValue("quarkus.log.console.color", Boolean.class)
                : config.getValue("%dev.quarkus.log.console.color", Boolean.class);

        if (!color) {
            ColorUtilConstants.colorOff();
        }

        routesPromise.future().onSuccess(router -> {
            if (!isProduction) {
                router.get("/bye").handler(rc -> {
                    rc.response().end("bye");
                    Quarkus.asyncExit();
                });
                List<Route> routesList = router.getRoutes();

                for (Route r : routesList) {
                    String path = parsePath(r);
                    if (path != null && !"/".equals(path)) {
                        String methods = path + (r.methods() == null ? "" : r.methods());
                        logger.info("{}{}{}", ColorUtilConstants.CYAN_BOLD_BRIGHT, methods, ColorUtilConstants.RESET);
                    }
                }
                logger.info("{}{}{}{}{}{}", ColorUtilConstants.GREEN_BOLD_BRIGHT, "HTTP Started on : http://", host,
                        ":", port, ColorUtilConstants.RESET);
                // });
            } else {
                logger.info("{}{}{}{}{}{}", ColorUtilConstants.GREEN_BOLD_BRIGHT, "HTTP Started on http://", host, ":",
                        port, ColorUtilConstants.RESET);
            }
        });
        Quarkus.waitForExit();

        server.close();

        return 0;
    }

    private String parsePath(Route route) {
        if (!route.isRegexPath()) {
            return route.getPath();
        }

        String info = route.toString();

        return info.substring(info.indexOf("pattern=") + 8, info.indexOf(',', info.indexOf("pattern=")));
    }

    public static Promise<HttpServer> getServerPromise() {
        return serverPromise;
    }

    public static void setRoutesPromise(Promise<Router> promise) {
        routesPromise = promise;
    }
}
