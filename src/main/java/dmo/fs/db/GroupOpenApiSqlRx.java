package dmo.fs.db;

import dmo.fs.db.reactive.DodexReactiveDatabase;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.jdbcclient.JDBCPool;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.sqlclient.Pool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.SessionScoped;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@SessionScoped
@Dependent
public class GroupOpenApiSqlRx extends GroupOpenApiSql {
  protected final static Logger logger = LoggerFactory.getLogger(GroupOpenApiSqlRx.class.getName());

  @Override
  public Future<JsonObject> addGroupAndMembers(JsonObject addGroupJson)
      throws InterruptedException, SQLException, IOException {
    final Promise<JsonObject> promise = Promise.promise();

    DodexReactiveDatabase dodexDatabase = dmo.fs.db.DbConfiguration.getDefaultDb();
    final MessageUser messageUser = dodexDatabase.createMessageUser();
    final Map<String, String> selected = DodexUtil.commandMessage(addGroupJson.getString("groupMessage"));
    final List<String> selectedUsers = Arrays.asList(selected.get("selectedUsers").split(","));

    messageUser.setName(addGroupJson.getString("groupOwner"));
    messageUser.setPassword(addGroupJson.getString("ownerId"));
    String ownerKey = addGroupJson.getString("ownerKey");

    if (ownerKey != null) {
      messageUser.setId(Long.valueOf(ownerKey));
    }

    addGroup(addGroupJson).onSuccess(groupJson -> {
      String entry0 = selectedUsers.get(0);

      if (groupJson.getInteger("status") == 0 &&
          entry0 != null && !"".equals(entry0)) {
        try {
          addMembers(selectedUsers, groupJson).onSuccess(promise::complete).onFailure(err -> {
            logger.error("Add group/member err: " + err.getMessage());
            addGroupJson.put("status", -1);
            addGroupJson.put("errorMessage", err.getMessage());
            promise.complete(addGroupJson);
          });
        } catch (InterruptedException | SQLException | IOException err) {
          err.printStackTrace();
          addGroupJson.put("status", -1);
          addGroupJson.put("errorMessage", err.getMessage());
          promise.tryComplete(addGroupJson);
        }
      } else {
        promise.complete(addGroupJson);
      }
    }).onFailure(err -> {
      logger.error("Add group/member err: " + err.getMessage());
      promise.complete(addGroupJson);
    });

    return promise.future();
  }

  @Override
  protected Future<JsonObject> addGroup(JsonObject addGroupJson)
      throws InterruptedException, SQLException, IOException {
    Promise<JsonObject> promise = Promise.promise();
    Timestamp current = new Timestamp(new Date().getTime());
    OffsetDateTime time = OffsetDateTime.now();
    Object currentDate = dmo.fs.db.DbConfiguration.isUsingPostgres() ? time : current;

    DodexReactiveDatabase dodexDatabase = dmo.fs.db.DbConfiguration.getDefaultDb();
    MessageUser messageUser = dodexDatabase.createMessageUser();
    messageUser.setName(addGroupJson.getString("groupOwner"));
    messageUser.setPassword(addGroupJson.getString("ownerId"));

    dodexDatabase.selectUser(messageUser, null).onSuccess(userData -> {
      addGroupJson.put("ownerKey", userData.getId());
      pool.rxGetConnection().doOnSuccess(conn -> conn.preparedQuery(getGroupByName())
          .rxExecute(Tuple.of(addGroupJson.getString("groupName"))).doOnSuccess(rows -> {
            if (rows.size() == 1) {
              Row row = rows.iterator().next();
              addGroupJson.put("id", row.getInteger(0));
            }
          })
          .doOnError(err -> {
            err.printStackTrace();
          })
          .doAfterSuccess(result -> {
            if (addGroupJson.getInteger("id") == null) {
              Tuple parameters = Tuple.of(addGroupJson.getString("groupName"),
                  addGroupJson.getInteger("ownerKey"), currentDate, currentDate);

              String sql = getAddGroup();
              if (dmo.fs.db.DbConfiguration.isUsingMariadb()) {
                sql = getMariaAddGroup();
              }

              conn.preparedQuery(sql).rxExecute(parameters).doOnSuccess(rows -> {
                for (Row row : rows) {
                  addGroupJson.put("id", row.getLong(0));
                }
                if (dmo.fs.db.DbConfiguration.isUsingMariadb()) {
                  addGroupJson.put("id", rows.property(MySQLClient.LAST_INSERTED_ID));
                } else if (dmo.fs.db.DbConfiguration.isUsingSqlite3() || dmo.fs.db.DbConfiguration.isUsingCubrid()) {
                  addGroupJson.put("id", rows.property(JDBCPool.GENERATED_KEYS).getLong(0));
                }

                LocalDate localDate = LocalDate.now();
                LocalTime localTime = LocalTime.of(LocalTime.now().getHour(),
                    LocalTime.now().getMinute(), LocalTime.now().getSecond());
                ZonedDateTime zonedDateTime =
                    ZonedDateTime.of(localDate, localTime, ZoneId.systemDefault());
                String openApiDate = zonedDateTime.format(formatter);

                addGroupJson.put("created", openApiDate);
                addGroupJson.put("status", 0);
                conn.close();
                promise.complete(addGroupJson);
              }).subscribe(rows -> {
              }, err -> {
                logger.error(String.format("%sError Adding group: %s%s", ColorUtilConstants.RED,
                    err, ColorUtilConstants.RESET));
                errData(err, promise, addGroupJson);
                if (err != null && err.getMessage() != null) {
                  conn.close();
                }
                if (addGroupJson.getInteger("id") == null) {
                  addGroupJson.put("id", -1);
                }
              });
            } else {
              promise.complete(addGroupJson);
            }
          }).doOnError(Throwable::printStackTrace)
          .subscribe(rows -> {
          }, err -> {
            logger.error(String.format("%sError Adding group: %s%s", ColorUtilConstants.RED,
                err, ColorUtilConstants.RESET));
            errData(err, promise, addGroupJson);
            if (err != null && err.getMessage() != null) {
              conn.close();
            }
            if (addGroupJson.getInteger("id") == null) {
              addGroupJson.put("id", -1);
            }
          })).doOnError(Throwable::printStackTrace).subscribe();
    });
    return promise.future();
  }

  @Override
  public Future<JsonObject> deleteGroupOrMembers(JsonObject deleteGroupJson)
      throws InterruptedException, SQLException, IOException {
    Promise<JsonObject> promise = Promise.promise();
    DodexReactiveDatabase dodexDatabase = dmo.fs.db.DbConfiguration.getDefaultDb();
    MessageUser messageUser = dodexDatabase.createMessageUser();
    Map<String, String> selected = DodexUtil.commandMessage(deleteGroupJson.getString("groupMessage"));
    final List<String> selectedUsers = Arrays.asList(selected.get("selectedUsers").split(","));

    messageUser.setName(deleteGroupJson.getString("groupOwner"));
    messageUser.setPassword(deleteGroupJson.getString("ownerId"));
    String ownerKey = deleteGroupJson.getString("ownerKey");

    if (ownerKey != null) {
      messageUser.setId(Long.valueOf(ownerKey));
    }

    String entry0 = selectedUsers.get(0);

    if (deleteGroupJson.getInteger("status") == 0 &&
        "".equals(entry0)) {
      try {
        deleteGroup(deleteGroupJson)
            .onSuccess(deleteGroupObject -> promise.complete(deleteGroupJson))
            .onFailure(err -> errData(err, promise, deleteGroupJson));
      } catch (InterruptedException | SQLException | IOException err) {
        errData(err, promise, deleteGroupJson);
      }
    } else if (deleteGroupJson.getInteger("status") == 0) {
      try {
        deleteMembers(selectedUsers, deleteGroupJson)
            .onSuccess(promise::complete)
            .onFailure(err -> {
              errData(err, promise, deleteGroupJson);
            });
      } catch (InterruptedException | SQLException | IOException err) {
        errData(err, promise, deleteGroupJson);
      }
    } else {
      promise.complete(deleteGroupJson);
    }

    return promise.future();
  }

  @Override
  public Future<JsonObject> getMembersList(JsonObject getGroupJson)
      throws InterruptedException, SQLException, IOException {
    Promise<JsonObject> promise = Promise.promise();

    DodexReactiveDatabase dodexDatabase = DbConfiguration.getDefaultDb();
    MessageUser messageUser = dodexDatabase.createMessageUser();

    messageUser.setName(getGroupJson.getString("groupOwner"));
    messageUser.setPassword(getGroupJson.getString("ownerId"));

    dodexDatabase.selectUser(messageUser, null).onSuccess(userData -> {
      JsonArray members = new JsonArray();
      getGroupJson.put("ownerKey", userData.getId());
      Tuple parameters = Tuple.of(getGroupJson.getString("groupName"),
          getGroupJson.getString("groupOwner"));

      pool.rxGetConnection().doOnSuccess(conn -> conn.preparedQuery(getMembersByGroup())
          .rxExecute(parameters).doOnSuccess(rows -> {
            if (rows.size() > 0) {
              for (Row row : rows) {
                if (!row.getString(1).equals(getGroupJson.getString("groupOwner"))) {
                  members.add(new JsonObject().put("name", row.getString(1)));
                } else {
                  getGroupJson.put("id", row.getInteger(2));
                }
              }
              getGroupJson.put("members", members.encode());
              promise.complete(getGroupJson);
            } else {
              getGroupJson.put("errorMessage", "Group not found: " + getGroupJson.getString("groupName"));
              getGroupJson.put("id", 0);
              promise.complete(getGroupJson);
            }
          })
          .doOnError(Throwable::printStackTrace)
          .subscribe(o -> {
          }, Throwable::printStackTrace)).subscribe(o -> {
      }, Throwable::printStackTrace);
    });

    return promise.future();
  }

  @Override
  protected Future<JsonObject> checkOnGroupOwner(JsonObject groupJson) {
    Promise<JsonObject> waitFor = Promise.promise();

    pool.rxGetConnection().doOnSuccess(conn -> conn.preparedQuery(getGroupByName())
            .rxExecute(Tuple.of(groupJson.getString("groupName")))
            .doOnSuccess(rows -> {
              if (rows.size() == 1) {
                Row row = rows.iterator().next();
                groupJson.put("id", row.getInteger(0));
                groupJson.put("groupOwnerId", row.getInteger(2));
              }

              conn.preparedQuery(dmo.fs.db.reactive.DbReactiveSqlBase.getUserByName())
                  .rxExecute(Tuple.of(groupJson.getString("groupOwner")))
                  .doOnSuccess(rows2 -> {
                    if (rows2.size() == 1) {
                      Row row = rows2.iterator().next();
                      groupJson.put("checkGroupOwnerId", row.getInteger(0));
                      groupJson.put("checkGroupOwner", row.getString(1));
                    }
                    conn.close();
                  })
                  .doOnError(err -> {
                    errData(err, waitFor, groupJson);
                    conn.close();
                  })
                  .doFinally(() -> {
                    JsonObject config = Vertx.currentContext().config();
                    boolean isCheckForOwner =
                        config.getBoolean("dodex.groups.checkForOwner") != null &&
                            config.getBoolean("dodex.groups.checkForOwner");
                    groupJson.put("checkForOwner", isCheckForOwner);
                    groupJson.put("isValidForOperation", groupJson.getInteger("status") != -1 &&
                        !isCheckForOwner || Objects.equals(groupJson.getInteger("checkGroupOwnerId"), groupJson.getInteger("groupOwnerId")));
                    if (!groupJson.getBoolean("isValidForOperation")) {
                      groupJson.put("errorMessage", "Contact owner for group administration");
                    }
                    waitFor.complete(groupJson);
                  }).subscribe();
            })
            .doOnError(err -> {
              errData(err, waitFor, groupJson);
              conn.close();
            }).subscribe())
        .doOnError(err -> {
          errData(err, waitFor, groupJson);
        }).subscribe();

    return waitFor.future();
  }

  public static void setCreate(DSLContext create) {
    GroupOpenApiSqlRx.create = create;
  }

  public static void setPool(Pool pool) {
    GroupOpenApiSqlRx.pool = pool;
  }

  public static void setQmark(boolean qmark) {
    GroupOpenApiSqlRx.qmark = qmark;
  }

}
