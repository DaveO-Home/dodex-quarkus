package dmo.fs.spa.router;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.sql.SQLException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.spa.SpaApplication;
import dmo.fs.spa.db.SpaDatabase;
import dmo.fs.spa.db.SpaDbConfiguration;
import dmo.fs.spa.utils.SpaLogin;
import dmo.fs.spa.utils.SpaUtil;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.smallrye.mutiny.Uni;
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
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.core.Vertx;

public class SpaRoutes {
    private static final Logger logger = LoggerFactory.getLogger(SpaRoutes.class.getName());
    private static final String FAILURE = "{\"status\":\"-99\"}";
    protected static final SpaDatabase spaDatabase = SpaDbConfiguration.getSpaDb();

    protected Vertx vertx = Vertx.vertx();
    protected Router router;
    protected SessionStore sessionStore;
    // protected SpaApplication spaApplication;

    public SpaRoutes(Router router, io.vertx.core.Promise<Void> routesPromise)
            throws InterruptedException, IOException, SQLException {
        // spaApplication = new SpaApplication();
        this.router = router;
        sessionStore = LocalSessionStore.create(io.vertx.core.Vertx.vertx());

        spaDatabase.databaseSetup().onSuccess(none -> {
            setGetLoginRoute();
            setPutLoginRoute();
            setLogoutRoute();
            setUnregisterLoginRoute();
            routesPromise.complete();
        });
    }

    public void setGetLoginRoute() {
        SessionHandler sessionHandler = SessionHandler.create(sessionStore);

        Route route = router.route(HttpMethod.POST, "/userlogin").handler(sessionHandler);
        if ("dev".equals(DodexUtil.getEnv())) {
            route.handler(CorsHandler.create("*").allowedMethod(HttpMethod.POST));
        }

        route.handler(routingContext -> {
            routingContext.request().bodyHandler(bodyHandler -> {
                SpaApplication spaApplication = null;
                try {
                    spaApplication = new SpaApplication();
                    spaApplication.setDatabase(spaDatabase);
                } catch (InterruptedException | IOException | SQLException e1) {
                    e1.printStackTrace();
                }
                String body = bodyHandler.toString("UTF-8");
                String bodyDecoded = null;

                try {
                    bodyDecoded = URLDecoder.decode(body, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

                Session session = routingContext.session();

                routingContext.put("name", "getlogin");
                if (session.get("login") != null) {
                    session.remove("login");
                }

                HttpServerResponse response = routingContext.response();

                final Optional<String> queryData = Optional.ofNullable(bodyDecoded);

                if (queryData.isPresent()) {
                    response.putHeader("content-type", "application/json");

                    Promise<SpaLogin> promise = spaApplication.getLogin(bodyDecoded);

                    promise.future().onItem().call(result -> {
                        if (result.getId() == null) {
                            result.setId(0l);
                        }

                        session.put("login", new JsonObject(result.getMap()));
                        response.end(new JsonObject(result.getMap()).encode());
                        return Uni.createFrom().item(result);
                    }).onFailure().invoke(failed -> {
                        logger.error(String.format("%s%s%s%s", ColorUtilConstants.RED_BOLD_BRIGHT, "Get Login Failed: ",
                                failed.getMessage(), ColorUtilConstants.RESET));
                        response.end(FAILURE);
                    }).subscribeAsCompletionStage();
                } else {
                    routingContext.response().setStatusCode(500).end("Somethings wrong - Post");
                }
            });
        });
    }

    public void setPutLoginRoute() {
        SessionHandler sessionHandler = SessionHandler.create(sessionStore);
        Route route = router.route(HttpMethod.PUT, "/userlogin").handler(sessionHandler);

        if ("dev".equals(DodexUtil.getEnv())) {
            route.handler(CorsHandler.create("*").allowedMethod(HttpMethod.PUT));
        }

        route.handler(routingContext -> {
            routingContext.request().bodyHandler(bodyHandler -> {
                SpaApplication spaApplication = null;
                try {
                    spaApplication = new SpaApplication();
                    spaApplication.setDatabase(spaDatabase);
                } catch (InterruptedException | IOException | SQLException e1) {
                    e1.printStackTrace();
                }
                final String body = bodyHandler.toString("UTF-8");
                try {
                    final String bodyDecoded = URLDecoder.decode(body, "UTF-8");
                    Session session = routingContext.session();

                    routingContext.put("name", "putlogin");
                    if (session.get("login") != null) {
                        session.remove("login");
                    }

                    HttpServerResponse response = routingContext.response();

                    final Optional<String> queryData = Optional.ofNullable(bodyDecoded);
                    if (queryData.isPresent()) {
                        response.putHeader("content-type", "application/json");
                        SpaLogin spaLogin = SpaUtil.createSpaLogin();

                        spaLogin = SpaUtil.parseBody(bodyDecoded, spaLogin);
                        JsonObject jsonObject = new JsonObject(spaLogin.getMap());
                        Promise<SpaLogin> promiseLogin = spaApplication.getLogin(jsonObject.encode());

                        promiseLogin.future().onItem().call(result -> {
                            if ("0".equals(result.getStatus())) {
                                result.setStatus("-2");
                                response.end(new JsonObject(result.getMap()).encode());
                            } else {
                                SpaApplication spaApplicationAdd = null;
                                try {
                                    spaApplicationAdd = new SpaApplication();
                                    spaApplicationAdd.setDatabase(spaDatabase);
                                } catch (InterruptedException | IOException | SQLException e) {
                                    e.printStackTrace();
                                }
                                Promise<SpaLogin> promise = null;
                                promise = spaApplicationAdd.addLogin(bodyDecoded);

                                promise.future().onItem().call(result2 -> {
                                    session.put("login", new JsonObject(result2.getMap()));
                                    response.end(new JsonObject(result2.getMap()).encode());
                                    return Uni.createFrom().item(result2);
                                }).onFailure().invoke(failed -> {
                                    logger.error(String.format("%s%s%s%s", ColorUtilConstants.RED_BOLD_BRIGHT,
                                            "Add Login failed...: ", failed.getMessage(), ColorUtilConstants.RESET));
                                    response.end(FAILURE);
                                }).subscribeAsCompletionStage();
                            }
                            return Uni.createFrom().item(result);
                        }).onFailure().invoke(failed -> {
                            logger.error(String.format("%s%s%s%s", ColorUtilConstants.RED_BOLD_BRIGHT,
                                    "Check Login failed...: ", failed.getMessage(), ColorUtilConstants.RESET));
                            response.end(FAILURE);
                        }).subscribeAsCompletionStage();
                    } else {
                        routingContext.response().setStatusCode(500).end("Somethings wrong - Put");
                    }
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            });
        });
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
                spaApplication.setDatabase(spaDatabase);
            } catch (InterruptedException | IOException | SQLException e1) {
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
                Promise<SpaLogin> promise;
                response.putHeader("content-type", "application/json");

                try {
                    promise = spaApplication.unregisterLogin(URLDecoder.decode(queryData.get(), "UTF-8"));

                    promise.future().onItem().call(result -> {
                        session.destroy();
                        response.end(new JsonObject(result.getMap()).encode());
                        return Uni.createFrom().item(result);
                    }).onFailure().invoke(failed -> {
                        logger.error(String.format("%s%s%s%s", ColorUtilConstants.RED_BOLD_BRIGHT,
                                "Unregister Login failed...: ", failed.getMessage(), ColorUtilConstants.RESET));
                        response.end(FAILURE);
                    }).subscribeAsCompletionStage();
                } catch (UnsupportedEncodingException e) {
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
