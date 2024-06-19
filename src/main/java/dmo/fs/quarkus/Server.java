package dmo.fs.quarkus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dmo.fs.utils.ColorUtilConstants;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.smallrye.config.SmallRyeConfig;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.file.FileSystem;
//import io.vertx.mutiny.core.http.HttpServer;
import io.vertx.mutiny.ext.web.Route;
import io.vertx.mutiny.ext.web.Router;
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
    protected static final Logger logger = LoggerFactory.getLogger(Server.class.getName());
    protected static final Promise<Boolean> serverPromise = Promise.promise();
    protected static Promise<Router> routesPromise = Promise.promise();
    protected static Integer port;
    protected static Boolean isUsingHandicap = false;
    protected static String defaultDbName = "sqlite3";
    protected static boolean color = true;
    public static boolean isProduction = true;
    protected static final Vertx vertxMutiny = Vertx.vertx();
    public static final io.vertx.reactivex.core.Vertx vertx = io.vertx.reactivex.core.Vertx.vertx();
    FileSystem fs = vertxMutiny.fileSystem();

    public static void main(String... args) {
        Quarkus.run(Server.class, args);
        Locale.setDefault(new Locale("US"));
    }

    @Override
    public int run(String... args) throws InterruptedException, ExecutionException, IOException {
        ObjectMapper jsonMapper = new ObjectMapper();
        JsonNode node;

        List<String> profiles = getProfiles();
        for(String p : profiles) {
            if("dev".equals(p) || "test".equals(p)) {
                isProduction = false;
            }
        }

        Config config = ConfigProvider.getConfig();

        serverPromise.complete(isProduction); // passing ready to "DodexRoutes" for reactive setup
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
            if(!isUseHandicapSet) {
                isUseHandicapSet = "true".equals(System.getProperty("USE_HANDICAP"));
            }

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
        }).onFailure().invoke(Throwable::printStackTrace)
            .onItem().invoke(v -> checkInstallation(fs))
            .subscribeAsCompletionStage().toCompletableFuture().get();

        Quarkus.waitForExit();

        return 0;
    }

    public static List<String> getProfiles() {
        return ConfigProvider.getConfig().unwrap(SmallRyeConfig.class).getProfiles();
    }

    protected void checkInstallation (FileSystem fs) {
            String handicapBase = "../../../../";
            String quarkusBase = "../../";
        if (!isProduction) {
            String fileDir = "./src/spa-react/node_modules/";
            if (!(fs.existsBlocking(handicapBase + fileDir) || fs.existsBlocking(quarkusBase + fileDir))) {
                logger.info("{}{}{}", ColorUtilConstants.CYAN_BOLD_BRIGHT,
                    String.format("%s\n%s\n%s","To install the test spa application, execute 'npm install --legacy-peer-deps' in 'src/spa-react/'",
                    "then 'cd ./devl' and execute 'npx gulp prd'(this bypasses the tests); to view, use 'http://localhost:8089/spa/react-fusebox/appl/testapp.html'",
                    "or execute 'npx gulp rebuild'(dev build without tests); to view, use 'http://localhost:8089/spa_test/react-fusebox/appl/testapp_dev.html'")
                    , ColorUtilConstants.RESET);
            }
            fileDir = "./src/main/resources/META-INF/resources/group/";
            if (!(fs.existsBlocking(handicapBase + fileDir) || fs.existsBlocking(quarkusBase + fileDir))) {
                logger.info("{}{}{}", ColorUtilConstants.CYAN_BOLD_BRIGHT,
                    "To install the dodex group addon, execute 'npm run group:prod' in './src/grpc/client/'"
                    , ColorUtilConstants.RESET);
            }
            fileDir = "./src/main/resources/META-INF/resources/node_modules/";
            if (!(fs.existsBlocking(handicapBase + fileDir) || fs.existsBlocking(quarkusBase + fileDir))) {
                logger.info("{}{}{}", ColorUtilConstants.CYAN_BOLD_BRIGHT,
                    "To install dodex , execute 'npm install' in 'src/main/resources/META-INF/resources/'"
                    , ColorUtilConstants.RESET);
            }
            fileDir = "./src/firebase/node_modules/";
            if (!(fs.existsBlocking(handicapBase + fileDir) || fs.existsBlocking(quarkusBase + fileDir))) {
                logger.info("{}{}{}", ColorUtilConstants.CYAN_BOLD_BRIGHT,
                    "To install the firebase client , execute 'npm install' in './src/firebase/'"
                    , ColorUtilConstants.RESET);
            }
            fileDir = "./src/grpc/client/node_modules/";
            if (!(fs.existsBlocking(handicapBase + fileDir) || fs.existsBlocking(quarkusBase + fileDir))) {
                logger.info("{}{}{}", ColorUtilConstants.CYAN_BOLD_BRIGHT,
                    "To install the gRPC client , execute 'npm install' and 'npm run esbuild:build' in './src/grpc/client/'"
                    , ColorUtilConstants.RESET);
            }
        }
    }

    protected String parsePath(Route route) {
        if (!route.isRegexPath()) {
            return route.getPath();
        }

        String info = route.toString();

        return info.substring(info.indexOf("pattern=") + 8, info.indexOf(',', info.indexOf("pattern=")));
    }

    public static Promise<Boolean> getServerPromise() {
        return serverPromise;
    }

    public static void setRoutesPromise(Promise<Router> promise) {
        routesPromise = promise;
    }

    public static Integer getPort() {
        return port;
    }

    public static boolean isProduction() {
        return isProduction;
    }

    public static void setIsUsingHandicap(Boolean isUsingHandicap) {
        Server.isUsingHandicap = isUsingHandicap;
    }

    public static void setDefaultDbName(String defaultDbName) {
        Server.defaultDbName = defaultDbName;
    }
}
