package dmo.fs.quarkus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dmo.fs.utils.ColorUtilConstants;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.quarkus.runtime.configuration.ProfileManager;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.ext.web.Route;
import io.vertx.mutiny.ext.web.Router;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServer;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ExecutionException;

@QuarkusMain
public class Server implements QuarkusApplication {
    private static final Logger logger = LoggerFactory.getLogger(Server.class.getName());
    private static final Promise<HttpServer> serverPromise = Promise.promise();
    private static Promise<Router> routesPromise = Promise.promise();
    private static Integer port;
    private static Boolean isUsingHandicap = false;
    private static String defaultDbName = "";
    private static boolean color = true;
    public static boolean isProduction;
    public static final Vertx vertx = Vertx.vertx();

    public static void main(String... args) {
        Quarkus.run(Server.class, args);
        Locale.setDefault(new Locale("US"));
    }

    @Override
    public int run(String... args) throws InterruptedException, ExecutionException, IOException {
        ObjectMapper jsonMapper = new ObjectMapper();
        JsonNode node;

        HttpServer server = vertx.createHttpServer();
        Config config = ConfigProvider.getConfig();
        isProduction = !ProfileManager.getLaunchMode().isDevOrTest();

        serverPromise.complete(server); // passing HttpServer instance to "DodexRoutes" for reactivex setup
        String host = isProduction ? config.getValue("quarkus.http.host", String.class)
                : config.getValue("%dev.quarkus.http.host", String.class); // see application.properties
        port = isProduction ? config.getValue("quarkus.http.port", Integer.class)
                : config.getValue("%dev.quarkus.http.port", Integer.class);
//        boolean color = isProduction ? config.getValue("quarkus.log.console.color", Boolean.class)
//                : config.getValue("%dev.quarkus.log.console.color", Boolean.class);

        try (InputStream in = getClass().getResourceAsStream("/application-conf.json")) {
            node = jsonMapper.readTree(in);
            JsonObject jsonObject = JsonObject.mapFrom(node);
            final Optional<Boolean> optionalColor = Optional.ofNullable(jsonObject.getBoolean("log.console.color"));
            optionalColor.ifPresent(aBoolean -> color = aBoolean);
        }

        if (!color) {
            ColorUtilConstants.colorOff();
        }

        routesPromise.future().onItem().invoke(router -> {
            Set<String> supportedDBs = new HashSet<>();
            supportedDBs.add("h2");
            supportedDBs.add("postgres");
            supportedDBs.add("mariadb");
            boolean isUseHandicapSet = "true".equals(System.getenv().get("USE_HANDICAP"));
            if (isUsingHandicap && supportedDBs.contains(defaultDbName) && isUseHandicapSet) {
                logger.warn(
                        String.format(
                                "%sHandicap Started on port: %s%s",
                                ColorUtilConstants.YELLOW,
                                Server.getPort(), ColorUtilConstants.RESET
                        )
                );
            }
            if (!isProduction) {
                router.get("/bye").handler(rc -> {
                    rc.response().end("bye").subscribeAsCompletionStage().isDone();
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
            } else {
                logger.info("{}{}{}{}{}{}", ColorUtilConstants.GREEN_BOLD_BRIGHT, "HTTP Started on http://", host, ":",
                        port, ColorUtilConstants.RESET);
            }
        }).onFailure().invoke(Throwable::printStackTrace).subscribeAsCompletionStage().isDone();

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

    public static Integer getPort() {
        return port;
    }

    public static void setIsUsingHandicap(Boolean isUsingHandicap) {
        Server.isUsingHandicap = isUsingHandicap;
    }

    public static void setDefaultDbName(String defaultDbName) {
        Server.defaultDbName = defaultDbName;
    }
}
