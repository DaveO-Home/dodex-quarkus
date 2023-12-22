package dmo.fs.router;

import dmo.fs.db.handicap.DbConfiguration;
import dmo.fs.db.handicap.HandicapDatabase;
import dmo.fs.db.GroupOpenApiSql;
import dmo.fs.db.GroupOpenApiSqlRx;
import dmo.fs.utils.DodexUtil;
import dmo.fs.utils.Group;

import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jooq.DSLContext;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
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
@SessionScoped
public class OpenApiRouter {
    protected static final Logger logger = LoggerFactory.getLogger(OpenApiRouter.class.getName());
    protected static boolean isDebug = System.getenv("DEBUG") != null || System.getProperty("DEBUG") != null;

    private GroupOpenApiSql groupOpenApiSql;
    private GroupOpenApiSqlRx groupOpenApiSqlRx;

    @PUT
    @Path("addGroup")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Group openApiAddGroup(Group requestGroup) throws SQLException, IOException, InterruptedException, ExecutionException {
        JsonObject addGroupJson = new JsonObject(requestGroup.getMap());
        isDebug = false;
        if(DbConfiguration.isUsingSqlite3()) {
            if(groupOpenApiSqlRx == null) {
                groupOpenApiSqlRx = new GroupOpenApiSqlRx();
            }
            return groupOpenApiSqlRx.addGroupAndMembers(addGroupJson)
                .onFailure(Throwable::printStackTrace)
                .onSuccess(addGroupObject -> {
                if (isDebug) {
                    logger.info("OpenApi AddGroup: {}", addGroupObject.getMap());
                }
            }).toCompletionStage().toCompletableFuture().get().mapTo(Group.class);
        } else {
            if(groupOpenApiSql == null) {
                groupOpenApiSql = new GroupOpenApiSql();
            }
            return groupOpenApiSql.addGroupAndMembers(addGroupJson).onSuccess(addGroupObject -> {
                if (isDebug) {
                    logger.info("OpenApi AddGroup: {}", addGroupObject.getMap());
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
        if(DbConfiguration.isUsingSqlite3()) {
            if(groupOpenApiSqlRx == null) {
                groupOpenApiSqlRx = new GroupOpenApiSqlRx();
            }
            return groupOpenApiSqlRx.deleteGroupOrMembers(deleteGroupJson).onSuccess(deleteGroupObject -> {
                if (isDebug) {
                    logger.info("OpenApi DeleteGroup: {}", deleteGroupObject.getMap());
                }
            }).toCompletionStage().toCompletableFuture().get().mapTo(Group.class);
        } else {
            if(groupOpenApiSql == null) {
                groupOpenApiSql = new GroupOpenApiSql();
            }
            return groupOpenApiSql.deleteGroupOrMembers(deleteGroupJson).onSuccess(deleteGroupObject -> {
                if (isDebug) {
                    logger.info("OpenApi DeleteGroup: {}", deleteGroupObject.getMap());
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
 
        if(DbConfiguration.isUsingSqlite3()) {
            if (groupOpenApiSqlRx == null) {
                groupOpenApiSqlRx = new GroupOpenApiSqlRx();
                if(!"true".equalsIgnoreCase(System.getenv("USE_HANDICAP"))) {
                    DbConfiguration.getDefaultDb(); // setup database for groups if not using handicap
                }
            }
            return groupOpenApiSqlRx.getMembersList(getGroupJson).onComplete(getGroupObject -> {
                if (isDebug) {
                    logger.info("OpenApi By Group Id: {}", getGroupObject.result().getMap());
                }
            }).toCompletionStage().toCompletableFuture().get().mapTo(Group.class);
        } else {
            if (groupOpenApiSql == null) {
                groupOpenApiSql = new GroupOpenApiSql();
                if(!"true".equalsIgnoreCase(System.getenv("USE_HANDICAP"))) {
                    DbConfiguration.getDefaultDb(); // setup database for groups if not using handicap
                }
            }
            return groupOpenApiSql.getMembersList(getGroupJson).onComplete(getGroupObject -> {
                if (isDebug) {
                    logger.info("OpenApi By Group Id: {} -- {}", getGroupObject.result().getMap(), getGroupObject);
                }
            }).toCompletionStage().toCompletableFuture().get().mapTo(Group.class);
        }
    }
}
