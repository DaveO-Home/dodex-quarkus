package dmo.fs.spa.router;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.sql.SQLException;
import java.util.Optional;

import org.davidmoten.rx.jdbc.Database;

import dmo.fs.spa.SpaApplication;
import dmo.fs.spa.db.SpaDatabase;
import dmo.fs.spa.db.SpaDbConfiguration;
import dmo.fs.spa.utils.SpaLogin;
import dmo.fs.spa.utils.SpaUtil;
import dmo.fs.utils.ColorUtilConstants;
import io.quarkus.runtime.configuration.ProfileManager;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.sstore.SessionStore;

public class SpaRoutes {
    protected Vertx vertx;
    private final static Logger logger = LoggerFactory.getLogger(SpaRoutes.class.getName());
    private final static String FAILURE = "{\"status\":\"-99\"}";
    private final boolean isProduction = !ProfileManager.getLaunchMode().isDevOrTest();
    protected Router router;
    protected SessionStore sessionStore;
    protected SpaDatabase spaDatabase;
    protected Database db;

    public SpaRoutes(Router router, Vertx vertx) throws InterruptedException, IOException, SQLException {
        this.vertx = vertx;
        this.router = router;
        sessionStore = LocalSessionStore.create(vertx);
        spaDatabase = SpaDbConfiguration.getSpaDb();
        db = spaDatabase.getDatabase();

        setGetLoginRoute();
        setPutLoginRoute();
        setLogoutRoute();
        setUnregisterLoginRoute();
    }

    public void setGetLoginRoute() {
        SessionHandler sessionHandler = SessionHandler.create(sessionStore);
        Route route = router.route(HttpMethod.GET, "/userlogin").handler(sessionHandler);
        route.method(HttpMethod.POST);
        route.consumes("application/json");

        if (!isProduction) {
            route.handler(CorsHandler.create("*").allowedMethod(HttpMethod.GET).allowedMethod(HttpMethod.POST));
        }

        route.handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            response.putHeader("content-type", "application/json");

            routingContext.request().bodyHandler(bodyHandler -> {
                Session session = routingContext.session();

                routingContext.put("name", "getlogin");
                if (session.get("login") != null) {
                    session.remove("login");
                }

                String body = bodyHandler.toString("UTF-8");

                try {
                    SpaApplication spaApplication = new SpaApplication();
                    Future<SpaLogin> future = spaApplication.getLogin(URLDecoder.decode(body, "UTF-8"));

                    future.onSuccess(result -> {
                        if (result.getId() == null) {
                            result.setId(0l);
                        }
                        session.put("login", new JsonObject(result.getMap()));
                        response.end(new JsonObject(result.getMap()).encode());
                    });

                    future.onFailure(failed -> {
                        logger.error(String.join("", ColorUtilConstants.RED_BOLD_BRIGHT, "Add Login Failed: ",
                                failed.getMessage(), ColorUtilConstants.RESET));
                        response.end(FAILURE);
                    });

                } catch (UnsupportedEncodingException | InterruptedException | SQLException e) {
                    logger.error(String.join("", ColorUtilConstants.RED_BOLD_BRIGHT,
                            "Context Configuration failed...: ", e.getMessage(), ColorUtilConstants.RESET));

                } catch (Exception exception) {
                    exception.printStackTrace();
                }

            });

            final Optional<String> queryData = Optional.ofNullable(routingContext.request().query());
            if (queryData.isPresent()) {
                SpaApplication spaApplication = null;
                try {

                    spaApplication = new SpaApplication();

                } catch (InterruptedException | IOException | SQLException e1) {
                    e1.printStackTrace();
                }
                try {
                    Future<SpaLogin> future = spaApplication.getLogin(URLDecoder.decode(queryData.get(), "UTF-8"));

                    future.onSuccess(result -> {
                        if (result.getId() == null) {
                            result.setId(0l);
                        }
                        // session.put("login", new JsonObject(result.getMap()));
                        response.end(new JsonObject(result.getMap()).encode());
                    });

                    future.onFailure(failed -> {
                        logger.error(String.join("", ColorUtilConstants.RED_BOLD_BRIGHT, "Add Login Failed: ",
                                failed.getMessage(), ColorUtilConstants.RESET));
                        response.end(FAILURE);
                    });

                } catch (UnsupportedEncodingException | InterruptedException | SQLException e) {
                    logger.error(String.join("", ColorUtilConstants.RED_BOLD_BRIGHT,
                            "Context Configuration failed...: ", e.getMessage(), ColorUtilConstants.RESET));

                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }
        });
    }

    public void setPutLoginRoute() {
        SessionHandler sessionHandler = SessionHandler.create(sessionStore);
        Route route = router.route(HttpMethod.PUT, "/userlogin").handler(sessionHandler);

        if (!isProduction) {
            route.handler(CorsHandler.create("*").allowedMethod(HttpMethod.PUT));
        }

        route.handler(BodyHandler.create()).handler(routingContext -> {
            SpaApplication spaApplication = null;

            try {

                spaApplication = new SpaApplication();

            } catch (InterruptedException | IOException | SQLException e1) {
                e1.printStackTrace();
            }
            Session session = routingContext.session();

            routingContext.put("name", "putlogin");
            if (session.get("login") != null) {
                session.remove("login");
            }

            HttpServerResponse response = routingContext.response();
            response.putHeader("content-type", "application/json");

            final Optional<String> bodyData = Optional.ofNullable(routingContext.getBodyAsString());

            if (bodyData.isPresent()) {
                try {
                    SpaLogin spaLogin = spaDatabase.createSpaLogin();
                    spaLogin = SpaUtil.parseBody(URLDecoder.decode(routingContext.getBodyAsString(), "UTF-8"),
                            spaLogin);

                    JsonObject jsonObject = new JsonObject(spaLogin.getMap());
                    Future<SpaLogin> futureLogin = spaApplication.getLogin(jsonObject.encode());

                    futureLogin.onSuccess(result -> {
                        if (result.getStatus().equals("0")) {
                            result.setStatus("-2");
                            response.end(new JsonObject(result.getMap()).encode());
                        } else {
                            Future<SpaLogin> future = null;
                            try {
                                future = new SpaApplication().addLogin(routingContext.getBodyAsString());
                            } catch (InterruptedException | SQLException | IOException e) {
                                e.printStackTrace();
                            }

                            future.onSuccess(result2 -> {
                                session.put("login", new JsonObject(result2.getMap()));
                                response.end(new JsonObject(result2.getMap()).encode());
                            });

                            future.onFailure(failed -> {
                                logger.error(String.join("", ColorUtilConstants.RED_BOLD_BRIGHT,
                                        "Add Login failed...: ", failed.getMessage(), ColorUtilConstants.RESET));
                                response.end(FAILURE);
                            });
                        }

                    });

                    futureLogin.onFailure(failed -> {
                        logger.error(String.join("", ColorUtilConstants.RED_BOLD_BRIGHT, "Add Login failed...: ",
                                failed.getMessage(), ColorUtilConstants.RESET));
                        response.end(FAILURE);
                    });

                } catch (InterruptedException | SQLException e) {
                    logger.error(String.join("", ColorUtilConstants.RED_BOLD_BRIGHT,
                            "Context Configuration failed...: ", e.getMessage(), ColorUtilConstants.RESET));

                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }
        });
    }

    public void setLogoutRoute() {
        SessionHandler sessionHandler = SessionHandler.create(sessionStore);
        Route route = router.route(HttpMethod.DELETE, "/userlogin").handler(sessionHandler);
        if (!isProduction) {
            route.handler(CorsHandler.create("*").allowedMethod(HttpMethod.DELETE));
        }

        route.handler(routingContext -> {
            Session session = routingContext.session();
            String data = null;

            routingContext.put("name", "getlogin");
            String status = "0";
            if (!session.isEmpty()) {
                session.destroy();
            } else {
                status = "-3";
            }

            HttpServerResponse response = routingContext.response();
            response.putHeader("content-type", "application/json");

            final Optional<String> queryData = Optional.ofNullable(routingContext.request().query());
            if (queryData.isPresent()) {
                try {
                    data = String.join("", "{\"status\":\"", status, "\"}");
                } catch (Exception e) {
                    logger.error(String.join("", ColorUtilConstants.RED_BOLD_BRIGHT,
                            "Context Configuration failed...: ", e.getMessage(), ColorUtilConstants.RESET));
                }
            }

            if (data == null) {
                data = FAILURE;
            }
            response.end(data);
        });
    }

    public void setUnregisterLoginRoute() {
        SessionHandler sessionHandler = SessionHandler.create(sessionStore);
        Route route = router.route(HttpMethod.DELETE, "/userlogin/unregister").handler(sessionHandler);

        if (!isProduction) {
            route.handler(CorsHandler.create("*").allowedMethod(HttpMethod.DELETE));
        }

        route.handler(routingContext -> {
            SpaApplication spaApplication = null;
            try {

                spaApplication = new SpaApplication();

            } catch (InterruptedException | IOException | SQLException e1) {
                e1.printStackTrace();
            }
            Session session = routingContext.session();

            routingContext.put("name", "unregisterlogin");

            if (!session.isEmpty()) {
                session.destroy();
            }

            HttpServerResponse response = routingContext.response();
            response.putHeader("content-type", "application/json");

            final Optional<String> queryData = Optional.ofNullable(routingContext.request().query());
            if (queryData.isPresent()) {
                try {

                    Future<SpaLogin> future = spaApplication
                            .unregisterLogin(URLDecoder.decode(queryData.get(), "UTF-8"));

                    future.onSuccess(result -> {
                        session.destroy();
                        response.end(new JsonObject(result.getMap()).encode());
                    });

                    future.onFailure(failed -> {
                        logger.error(String.join("", ColorUtilConstants.RED_BOLD_BRIGHT, "Unregister Login failed...: ",
                                failed.getMessage(), ColorUtilConstants.RESET));
                        response.end(FAILURE);
                    });

                } catch (Exception e) {
                    logger.error(String.join("", ColorUtilConstants.RED_BOLD_BRIGHT,
                            "Context Configuration failed...: ", e.getMessage(), ColorUtilConstants.RESET));
                    e.printStackTrace();
                }
            }
        });
    }

    public Router getRouter() throws InterruptedException {
        return router;
    }
}
