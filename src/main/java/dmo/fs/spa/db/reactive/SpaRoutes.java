package dmo.fs.spa.db.reactive;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.spa.db.SpaDbConfiguration;
import dmo.fs.spa.utils.SpaLogin;
import dmo.fs.spa.utils.SpaUtil;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.sstore.SessionStore;

public class SpaRoutes {
    private static final Logger logger = LoggerFactory.getLogger(SpaRoutes.class.getName());
    private static final String FAILURE = "{\"status\":\"-99\"}";
    protected Vertx vertx;
    protected Router router;
    protected SessionStore sessionStore;
    protected static final SpaDatabaseReactive spaDatabaseReactive = SpaDbConfiguration.getSpaDb();

    public SpaRoutes(Vertx vertx, io.vertx.mutiny.ext.web.Router router, io.vertx.mutiny.core.Promise<io.vertx.mutiny.ext.web.Router> routesPromise)
            throws InterruptedException, SQLException {
        this.vertx = vertx;
        this.router = router.getDelegate();
        sessionStore = LocalSessionStore.create(vertx);
        spaDatabaseReactive.databaseSetup().onSuccess(none -> {
            setGetLoginRoute();
            setPutLoginRoute();
            setLogoutRoute();
            setUnregisterLoginRoute();
            routesPromise.complete(router);
        });
    }

    public void setGetLoginRoute() {
        SessionHandler sessionHandler = SessionHandler.create(sessionStore);
        Route route = router.route(HttpMethod.POST, "/userlogin").handler(sessionHandler);

        if ("dev".equals(DodexUtil.getEnv())) {
            route.handler(CorsHandler.create("*").allowedMethod(HttpMethod.POST));
        }
        route.handler(routingContext -> routingContext.request().bodyHandler(bodyHandler -> {
            SpaApplication spaApplication = null;
            try {
                spaApplication = new SpaApplication();
                spaApplication.setDatabase(spaDatabaseReactive);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            String body = bodyHandler.toString("UTF-8");
            String bodyDecoded;

            bodyDecoded = URLDecoder.decode(body, StandardCharsets.UTF_8);

            Session session = routingContext.session();

            routingContext.put("name", "getlogin");
            if (session.get("login") != null) {
                session.remove("login");
            }

            HttpServerResponse response = routingContext.response();

            final Optional<String> queryData = Optional.ofNullable(bodyDecoded);

            if (queryData.isPresent()) {
                response.putHeader("content-type", "application/json");

                Future<SpaLogin> future = null;
                try {
                    future = spaApplication.getLogin(bodyDecoded);
                } catch (InterruptedException | SQLException e) {
                    e.printStackTrace();
                }

                assert future != null;
                future.onSuccess(result -> {
                    if (result.getId() == null) {
                        result.setId(0L);
                    }
                    session.put("login", new JsonObject(result.getMap()));
                    response.end(new JsonObject(result.getMap()).encode());
                });

                future.onFailure(failed -> {
                    logger.error(String.join("", ColorUtilConstants.RED_BOLD_BRIGHT, "Add Login Failed: ",
                            failed.getMessage(), ColorUtilConstants.RESET));
                    response.end(FAILURE);
                });
            } else {
                routingContext.response().setStatusCode(500).end("Somethings wrong - Post");
            }
        }));
    }

    public void setPutLoginRoute() {
        SessionHandler sessionHandler = SessionHandler.create(sessionStore);
        Route route = router.route(HttpMethod.PUT, "/userlogin").handler(sessionHandler);

        if ("dev".equals(DodexUtil.getEnv())) {
            route.handler(CorsHandler.create("*").allowedMethod(HttpMethod.PUT));
        }

        route.handler(routingContext -> routingContext.request().bodyHandler(bodyHandler -> {
            SpaApplication spaApplication = null;
            try {
                spaApplication = new SpaApplication();
                spaApplication.setDatabase(spaDatabaseReactive);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            String body = bodyHandler.toString("UTF-8");
            String bodyDecoded;

            bodyDecoded = URLDecoder.decode(body, StandardCharsets.UTF_8);

            Session session = routingContext.session();

            routingContext.put("name", "putlogin");
            if (session.get("login") != null) {
                session.remove("login");
            }

            HttpServerResponse response = routingContext.response();

            final Optional<String> queryData = Optional.ofNullable(bodyDecoded);

            if (queryData.isPresent()) {
                response.putHeader("content-type", "application/json");

                try {
                    SpaLogin defaultSpaLogin = SpaUtil.createSpaLogin();
                    SpaLogin spaLogin = SpaUtil.parseBody(queryData.get(), defaultSpaLogin);

                    JsonObject jsonObject = new JsonObject(spaLogin.getMap());
                    Future<SpaLogin> futureLogin = spaApplication.getLogin(jsonObject.encode());

                    futureLogin.onSuccess(result -> {

                        if ("0".equals(result.getStatus())) {
                            result.setStatus("-2");
                            response.end(new JsonObject(result.getMap()).encode());
                        } else {
                            Future<SpaLogin> future = null;
                            try {
                                SpaApplication spaApplicationAdd = new SpaApplication();
                                spaApplicationAdd.setDatabase(spaDatabaseReactive);
                                future = spaApplicationAdd.addLogin(queryData.get());
                            } catch (InterruptedException | SQLException e) {
                                e.printStackTrace();
                            }

                            assert future != null;
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
            } else {
                routingContext.response().setStatusCode(500).end("Somethings wrong - Put");
            }
        }));
    }

    public void setLogoutRoute() {
        SessionHandler sessionHandler = SessionHandler.create(sessionStore);
        Route route = router.route(HttpMethod.DELETE, "/userlogin").handler(sessionHandler);
        if ("dev".equals(DodexUtil.getEnv())) {
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

        if ("dev".equals(DodexUtil.getEnv())) {
            route.handler(CorsHandler.create("*").allowedMethod(HttpMethod.DELETE));
        }

        route.handler(routingContext -> {
            SpaApplication spaApplication = null;
            try {
                spaApplication = new SpaApplication();
                spaApplication.setDatabase(spaDatabaseReactive);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            Session session = routingContext.session();

            routingContext.put("name", "unregisterlogin");

            if (!session.isEmpty()) {
                session.destroy();
            }

            HttpServerResponse response = routingContext.response();

            final Optional<String> queryData = Optional.ofNullable(routingContext.request().query());
            if (queryData.isPresent()) {
                response.putHeader("content-type", "application/json");

                try {

                    Future<SpaLogin> future = spaApplication
                            .unregisterLogin(URLDecoder.decode(queryData.get(), StandardCharsets.UTF_8));

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
            } else {
                routingContext.response().setStatusCode(500).end("Somethings wrong - Delete");
            }
        });
    }

    public Router getRouter() {
        return router;
    }
}
