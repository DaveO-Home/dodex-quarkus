package dmo.fs.spa.router;

import com.google.cloud.firestore.Firestore;
import dmo.fs.db.wsnext.DbConfiguration;
import dmo.fs.spa.SpaApplication;
import dmo.fs.spa.db.SpaDatabase;
import dmo.fs.spa.db.SpaDbConfiguration;
import dmo.fs.spa.db.SpaNeo4j;
import dmo.fs.spa.db.reactive.SpaDatabaseReactive;
import dmo.fs.spa.utils.SpaLogin;
import dmo.fs.spa.utils.SpaUtil;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.http.HttpServerRequest;
import io.vertx.mutiny.core.http.HttpServerResponse;
import io.vertx.mutiny.ext.web.Route;
import io.vertx.mutiny.ext.web.Router;
import io.vertx.mutiny.ext.web.Session;
import io.vertx.mutiny.ext.web.handler.CorsHandler;
import io.vertx.mutiny.ext.web.handler.SessionHandler;
import io.vertx.mutiny.ext.web.sstore.LocalSessionStore;
import io.vertx.mutiny.ext.web.sstore.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class SpaRoutes {
  protected static final Logger logger = LoggerFactory.getLogger(SpaRoutes.class.getName());
  protected static final String FAILURE = "{\"status\":\"-99\"}";
  protected SpaDatabase spaDatabase;
  protected SpaDatabaseReactive spaDatabaseReactive;
  protected SpaNeo4j spaNeo4j;

  protected Vertx vertx = DodexUtil.getVertx();
  protected Router router;
  protected SessionStore sessionStore;
  protected Firestore firestore;

  public SpaRoutes(Router router, Promise<Router> routesPromise) {
    setSpaRoutes(router, routesPromise);
  }

  public SpaRoutes(Router router, Promise<Router> routesPromise, Firestore firestore) {
    this.firestore = firestore;
    setSpaRoutes(router, routesPromise);
  }

  protected void setSpaRoutes(Router router, Promise<Router> routesPromise) {
    this.router = router;
    sessionStore = LocalSessionStore.create(vertx);

    if (SpaDbConfiguration.isUsingCassandra() || DbConfiguration.isUsingFirebase()
        || SpaDbConfiguration.isUsingNeo4j()) {
      if (SpaDbConfiguration.isUsingNeo4j()) {
        spaNeo4j = SpaDbConfiguration.getSpaDb();
        spaNeo4j.databaseSetup();
      }
      setGetLoginRoute();
      setPutLoginRoute();
      setLogoutRoute();
      setUnregisterLoginRoute();
      routesPromise.complete(router);
    } else {
      String defaultDb;
      try {
        defaultDb = new DodexUtil().getDefaultDb();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      if ("h2".equals(defaultDb) || "sqlite3".equals(defaultDb)) {
        try {
          spaDatabaseReactive = SpaDbConfiguration.getSpaDb();
          if (spaDatabaseReactive != null) {
            spaDatabaseReactive.databaseSetup().onSuccess(none -> {
              setGetLoginRoute();
              setPutLoginRoute();
              setLogoutRoute();
              setUnregisterLoginRoute();
              routesPromise.complete(router);
            });
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      } else {
        spaDatabase = SpaDbConfiguration.getSpaDb();
        if (spaDatabase != null) {
          spaDatabase.databaseSetup().onSuccess(none -> {
            setGetLoginRoute();
            setPutLoginRoute();
            setLogoutRoute();
            setUnregisterLoginRoute();
            routesPromise.complete(router);
          }).onFailure(err -> {
            logger.info("Database setup Failed: {}", err.getMessage());
          });
        }
      }
    }
  }

  public void setGetLoginRoute() {
    SessionHandler sessionHandler = SessionHandler.create(sessionStore);

    Route route = router.route(HttpMethod.POST, "/userlogin"); // .handler(sessionHandler);
    if ("dev".equals(DodexUtil.getEnv())) {
      route.handler(CorsHandler.create().allowedMethod(HttpMethod.POST));
    }

    route.failureHandler(routingContext -> {
      HttpServerResponse response = routingContext.response();
      if(response.getStatusCode() != 200) {
        logger.info("Login Post Error: {} -- {} -- {}", response.headers(), response.getStatusCode(), response.getStatusMessage());
      }
    });

    route.handler(routingContext -> routingContext.request().bodyHandler(bodyHandler -> {
      SpaApplication spaApplication = null;

      try {
        spaApplication = new SpaApplication();
        if (spaDatabase != null) {
          spaApplication.setDatabase(spaDatabase);
        }
      } catch (InterruptedException | IOException | SQLException e1) {
        e1.printStackTrace();
      }
      String body = bodyHandler.toString("UTF-8");
      String bodyDecoded;

      bodyDecoded = URLDecoder.decode(body, StandardCharsets.UTF_8);

//            Session session = routingContext.session();
      routingContext.get("getlogin");
      routingContext.put("name", "getlogin");
//            if (session.get("login") != null) {
//                session.remove("login");
//            }

      HttpServerResponse response = routingContext.response();
      HttpServerRequest request = routingContext.request();
      final Optional<String> queryData = Optional.ofNullable(bodyDecoded);

      if (queryData.isPresent()) {
        response.putHeader("content-type", "application/json");

        Promise<SpaLogin> promise;
        try {
          assert spaApplication != null;
          promise = spaApplication.getLogin(bodyDecoded);
          promise.future().onItem().call(result -> {
            if (result.getId() == null) {
              result.setId(0L);
            }
//                        session.put("login", new JsonObject(result.getMap()));
            response.send(new JsonObject(result.getMap()).encode()).subscribeAsCompletionStage().isDone();
            routingContext.request().resume();
            return Uni.createFrom().item(result);
          }).onFailure().invoke(failed -> {
            failed.printStackTrace();
            logger.error(String.format("%s%s%s%s", ColorUtilConstants.RED_BOLD_BRIGHT,
                "Get Login Failed: ", failed, ColorUtilConstants.RESET));
            response.end(FAILURE).subscribeAsCompletionStage().isDone();
          }).subscribeAsCompletionStage().isDone();
        } catch (InterruptedException | ExecutionException | SQLException e) {
          e.printStackTrace();
        }
      } else {
        routingContext.response().setStatusCode(500).end("Somethings wrong - Post")
            .subscribeAsCompletionStage().isDone();
      }
    }));

    route.handler(sessionHandler);
  }

  public void setPutLoginRoute() {
//        SessionHandler sessionHandler = SessionHandler.create(sessionStore);
    Route route = router.route(HttpMethod.PUT, "/userlogin"); // .handler(sessionHandler);

    if ("dev".equals(DodexUtil.getEnv())) {
      route.handler(CorsHandler.create().allowedMethod(HttpMethod.PUT));
    }

    route.failureHandler(routingContext -> {
      HttpServerResponse response = routingContext.response();
      if(response.getStatusCode() != 200) {
        logger.info("Login PUT Error: {} -- {} -- {}", response.headers(), response.getStatusCode(), response.getStatusMessage());
      }
    });

    route.handler(routingContext -> routingContext.request().bodyHandler(bodyHandler -> {
      SpaApplication spaApplication = null;
      try {
        spaApplication = new SpaApplication();
        if (spaDatabase != null) {
          spaApplication.setDatabase(spaDatabase);
        }
      } catch (InterruptedException | IOException | SQLException e1) {
        e1.printStackTrace();
      }
      final String body = bodyHandler.toString("UTF-8");
      try {
        final String bodyDecoded = URLDecoder.decode(body, StandardCharsets.UTF_8);
//                Session session = routingContext.session();

        routingContext.put("name", "putlogin");
//                if (session.get("login") != null) {
//                    session.remove("login");
//                }

        HttpServerResponse response = routingContext.response();

        final Optional<String> queryData = Optional.ofNullable(bodyDecoded);
        if (queryData.isPresent()) {
          if(!response.headWritten()) {
            response.putHeader("content-type", "application/json");
          }
          SpaLogin spaLogin = SpaUtil.createSpaLogin();

          SpaUtil.parseBody(bodyDecoded, spaLogin);
          JsonObject jsonObject = new JsonObject(spaLogin.getMap());
          assert spaApplication != null;
          Promise<SpaLogin> promiseLogin = spaApplication.getLogin(jsonObject.encode());

          promiseLogin.future().onItem().call(result -> {
            if ("0".equals(result.getStatus())) {
              result.setStatus("-2");
              response.end(new JsonObject(result.getMap()).encode())
                  .subscribeAsCompletionStage().isDone();
            } else {
              SpaApplication spaApplicationAdd = null;
              try {
                spaApplicationAdd = new SpaApplication();
                if (spaDatabase != null) {
                  spaApplicationAdd.setDatabase(spaDatabase);
                }
              } catch (InterruptedException | IOException | SQLException e) {
                e.printStackTrace();
              }
              Promise<SpaLogin> promise;
              try {
                assert spaApplicationAdd != null;
                promise = spaApplicationAdd.addLogin(bodyDecoded);

                promise.future().onItem().call(result2 -> {
//                                    session.put("login", new JsonObject(result2.getMap()));
                  response.end(new JsonObject(result2.getMap()).encode())
                      .subscribeAsCompletionStage().isDone();
                  return Uni.createFrom().item(result2);
                }).onFailure().invoke(failed -> {
                  logger.error(String.format("%s%s%s%s", ColorUtilConstants.RED_BOLD_BRIGHT,
                      "Add Login failed...: ", failed.getMessage(),
                      ColorUtilConstants.RESET));
                  response.end(FAILURE).subscribeAsCompletionStage().isDone();
                }).subscribeAsCompletionStage().isDone();
              } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
              }
            }
            return Uni.createFrom().item(result);
          }).onFailure().invoke(failed -> {
            failed.printStackTrace();
            logger.error(String.format("%s%s%s%s", ColorUtilConstants.RED_BOLD_BRIGHT,
                "Check Login failed...: ", failed, ColorUtilConstants.RESET));
            response.end(FAILURE).subscribeAsCompletionStage().isDone();
          }).subscribeAsCompletionStage().isDone();
        } else {
          routingContext.response().setStatusCode(500).end("Somethings wrong - Put")
              .subscribeAsCompletionStage().isDone();
        }
      } catch (InterruptedException | ExecutionException | SQLException e) {
        e.printStackTrace();
      }
    }));
  }

  public void setLogoutRoute() {
    SessionHandler sessionHandler = SessionHandler.create(sessionStore);
    Route route = router.route(HttpMethod.DELETE, "/userlogin"); // .handler(sessionHandler);
    if ("dev".equals(DodexUtil.getEnv())) {
      route.handler(CorsHandler.create().allowedMethod(HttpMethod.DELETE));
    }

    route.failureHandler(routingContext -> {
      HttpServerResponse response = routingContext.response();
      if(response.getStatusCode() != 200) {
        logger.info("Login DELETE Error: {} -- {} -- {}", response.headers(), response.getStatusCode(), response.getStatusMessage());
      }
    });

    route.handler(routingContext -> {
      // Session is now always null - Security?
//            Session session = routingContext.session();
      String data = null;

      routingContext.put("name", "getlogin");
      String status = "0";
//            if (!session.isEmpty()) {
//                session.destroy();
//            } else {
//                status = "-3";
//            }

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
      response.end(data).subscribeAsCompletionStage().isDone();
    });
  }

  public void setUnregisterLoginRoute() {
    SessionHandler sessionHandler = SessionHandler.create(sessionStore);
    Route route = router.route(HttpMethod.DELETE, "/userlogin/unregister").handler(sessionHandler);

    if ("dev".equals(DodexUtil.getEnv())) {
      route.handler(CorsHandler.create().allowedMethod(HttpMethod.DELETE));
    }

    route.failureHandler(routingContext -> {
      HttpServerResponse response = routingContext.response();
      if(response.getStatusCode() != 200) {
        logger.info("Login Unregister Error: {} -- {} -- {}", response.headers(), response.getStatusCode(), response.getStatusMessage());
      }
    });

    route.handler(routingContext -> {
      SpaApplication spaApplication = null;
      try {
        spaApplication = new SpaApplication();
        if (spaDatabase != null) {
          spaApplication.setDatabase(spaDatabase);
        }
      } catch (InterruptedException | IOException | SQLException e1) {
        e1.printStackTrace();
      }
      Session session = routingContext.session();

      routingContext.put("name", "unregisterlogin");

//            if (!session.isEmpty()) {
//                session.destroy();
//            }

      HttpServerResponse response = routingContext.response();

      final Optional<String> queryData = Optional.ofNullable(routingContext.request().query());
      if (queryData.isPresent()) {
        Promise<SpaLogin> promise;
        response.putHeader("content-type", "application/json");

        try {
          assert spaApplication != null;
          promise = spaApplication.unregisterLogin(URLDecoder.decode(queryData.get(), StandardCharsets.UTF_8));

          promise.future().onItem().call(result -> {
//                        session.destroy();
            response.end(new JsonObject(result.getMap()).encode()).subscribeAsCompletionStage().isDone();
            return Uni.createFrom().item(result);
          }).onFailure().invoke(failed -> {
            logger.error(String.format("%s%s%s%s", ColorUtilConstants.RED_BOLD_BRIGHT,
                "Unregister Login failed...: ", failed.getMessage(), ColorUtilConstants.RESET));
            response.end(FAILURE).subscribeAsCompletionStage().isDone();
          }).subscribeAsCompletionStage().isDone();
        } catch (InterruptedException | ExecutionException e) {
          e.printStackTrace();
        }
      } else {
        routingContext.response().setStatusCode(500).end("Somethings wrong - Delete")
            .subscribeAsCompletionStage().isDone();
      }
    });
  }

  public Router getRouter() {
    return router;
  }
}
