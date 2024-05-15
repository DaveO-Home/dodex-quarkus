package dmo.fs.db.router;

import dmo.fs.db.openapi.GroupOpenApi;
import dmo.fs.db.openapi.GroupOpenApiSql;
import dmo.fs.db.openapi.GroupOpenApiSqlRx;
import dmo.fs.db.wsnext.DbConfiguration;
import dmo.fs.utils.Group;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;

/*
    Groups will work with "sqlite3" without setting USE_HANDICAP=true. For 'h2', 'mariadb' and 'postgres',
    USE_HANDICAP=true is required.
 */
@Path("/groups")
public class OpenApiRouter {
  protected static final Logger logger = LoggerFactory.getLogger(OpenApiRouter.class.getName());
  protected static boolean isDebug = System.getenv("DEBUG") != null || System.getProperty("DEBUG") != null;

  private GroupOpenApi groupOpenApi;

  @PUT
  @Path("addGroup")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Group openApiAddGroup(Group requestGroup) throws SQLException, IOException, InterruptedException, ExecutionException {
    JsonObject addGroupJson = new JsonObject(requestGroup.getMap());
    isDebug = false;
    if (DbConfiguration.isUsingSqlite3()) {
      groupOpenApi = new GroupOpenApiSqlRx();
      return groupOpenApi.addGroupAndMembers(addGroupJson)
        .onFailure(Throwable::printStackTrace)
        .onSuccess(addGroupObject -> {
          if (isDebug) {
            logger.info("OpenApi AddGroup1: {}", addGroupObject.getMap());
          }
        }).toCompletionStage().toCompletableFuture().get().mapTo(Group.class);
    } else {
        groupOpenApi = new GroupOpenApiSql();

        return groupOpenApi.addGroupAndMembers(addGroupJson).onSuccess(addGroupObject -> {
        if (isDebug) {
          logger.info("OpenApi AddGroup2: {}", addGroupObject.getMap());
        }
      }).toCompletionStage().toCompletableFuture().get().mapTo(Group.class);
    }
  }

  @DELETE
  @Path("removeGroup")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Group openApiDeleteGroup(Group requestGroup) throws SQLException, IOException, InterruptedException, ExecutionException {
    JsonObject deleteGroupJson = new JsonObject(requestGroup.getMap());
    isDebug = false;

    if (DbConfiguration.isUsingSqlite3()) {
      groupOpenApi = new GroupOpenApiSqlRx();
      return groupOpenApi.deleteGroupOrMembers(deleteGroupJson).onSuccess(deleteGroupObject -> {
        if (isDebug) {
          logger.info("OpenApi DeleteGroup1: {}", deleteGroupObject.getMap());
        }
      }).toCompletionStage().toCompletableFuture().get().mapTo(Group.class);
    } else {
      groupOpenApi = new GroupOpenApiSql();

      return groupOpenApi.deleteGroupOrMembers(deleteGroupJson).onSuccess(deleteGroupObject -> {
        if (isDebug) {
          logger.info("OpenApi DeleteGroup2: {}", deleteGroupObject.getMap());
        }
      }).toCompletionStage().toCompletableFuture().get().mapTo(Group.class);
    }
  }

  @POST
  @Path("getGroup/{groupId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Group openApiById(Group newGroup) throws SQLException, IOException, InterruptedException, ExecutionException {
    JsonObject getGroupJson = new JsonObject(newGroup.getMap());
    isDebug = false;
    if (DbConfiguration.isUsingSqlite3()) {
      groupOpenApi = new GroupOpenApiSqlRx();
      if (!"true".equalsIgnoreCase(System.getenv("USE_HANDICAP"))) {
        DbConfiguration.getDefaultDb(); // setup database for groups if not using handicap
      }
      return groupOpenApi.getMembersList(getGroupJson).onComplete(getGroupObject -> {
        if (isDebug) {
          logger.info("OpenApi By Group Id: {}", getGroupObject.result().getMap());
        }
      }).toCompletionStage().toCompletableFuture().get().mapTo(Group.class);
    } else {
      groupOpenApi = new GroupOpenApiSql();
      if (!"true".equalsIgnoreCase(System.getenv("USE_HANDICAP"))) {
        DbConfiguration.getDefaultDb(); // setup database for groups if not using handicap
      }
      return groupOpenApi.getMembersList(getGroupJson).onComplete(getGroupObject -> {
        if (isDebug) {
          logger.info("OpenApi By Group Id: {} -- {}", getGroupObject.result().getMap(), getGroupObject);
        }
      }).toCompletionStage().toCompletableFuture().get().mapTo(Group.class);
    }
  }
}
