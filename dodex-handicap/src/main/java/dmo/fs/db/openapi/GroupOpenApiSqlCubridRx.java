package dmo.fs.db.openapi;

import dmo.fs.db.MessageUser;
import dmo.fs.db.reactive.DbReactiveSqlBase;
import dmo.fs.db.reactive.DodexReactiveDatabase;
import dmo.fs.db.wsnext.DbConfiguration;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.SingleHelper;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.sqlclient.*;
import jakarta.enterprise.context.Dependent;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Dependent
public class GroupOpenApiSqlCubridRx extends GroupOpenApiSqlRx {
    protected final static Logger logger = LoggerFactory.getLogger(GroupOpenApiSqlCubridRx.class.getName());

    @Override
    public Future<JsonObject> addGroupAndMembers(JsonObject addGroupJson)
      throws InterruptedException, SQLException, IOException {
        final Promise<JsonObject> promise = Promise.promise();

        DodexReactiveDatabase dodexDatabase = DbConfiguration.getDefaultDb();
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
            if (groupJson.getInteger("status") == 0) {
                try {
                    addMembers(selectedUsers, groupJson).onSuccess(promise::complete).onFailure(err -> {
                        logger.error("Add group/member err1: {}", err.getMessage());
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
            logger.error("Add group/member err2: {}", err.getMessage());
            promise.complete(addGroupJson);
        });

        return promise.future();
    }

    @Override
    protected Future<JsonObject> addGroup(JsonObject addGroupJson)
      throws InterruptedException, SQLException, IOException {
        Promise<JsonObject> promise = Promise.promise();
        Timestamp currentDate = new Timestamp(new Date().getTime());

        DodexReactiveDatabase dodexDatabase = DbConfiguration.getDefaultDb();
        MessageUser messageUser = dodexDatabase.createMessageUser();
        messageUser.setName(addGroupJson.getString("groupOwner"));
        messageUser.setPassword(addGroupJson.getString("ownerId"));

        dodexDatabase.selectUser(messageUser, null).onSuccess(userData -> {
            addGroupJson.put("ownerKey", userData.getId());
            String sql = create.query(getGroupByName(), addGroupJson.getString("groupName")).toString();
            pool.rxGetConnection().doOnSuccess(conn -> conn.query(sql)
              .rxExecute().doOnSuccess(rows -> {
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
                      String addGroupSql = create.query(getAddGroup(), addGroupJson.getString("groupName"), addGroupJson.getInteger("ownerKey"), currentDate, currentDate).toString();

                      conn.query(addGroupSql).rxExecute().doOnSuccess(rows -> {
                          // Getting generated key
                          Query<RowSet<Row>> query = conn.query(create.query(getGroupByName(), addGroupJson.getString("groupName")).toString());
                          query.rxExecute().subscribe(rowSet -> {
                              for (Row row : rowSet) {
                                  addGroupJson.put("id", row.getLong(0));
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
                          });
                      }).subscribe(rows -> {
                      }, err -> {
                          logger.error("{}Error Adding group: {}{}", ColorUtilConstants.RED,
                            err, ColorUtilConstants.RESET);
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
                  logger.error("{}Error Adding group2: {}{}", ColorUtilConstants.RED,
                    err, ColorUtilConstants.RESET);
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

            String sql = create.query(getMembersByGroup(), getGroupJson.getString("groupName"), getGroupJson.getString("groupOwner")).toString();

            pool.rxGetConnection().doOnSuccess(conn -> conn.query(sql)
              .rxExecute().doOnSuccess(rows -> {
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
        String sql = create.query(getGroupByName(), groupJson.getString("groupName")).toString();
        pool.rxGetConnection().doOnSuccess(conn -> conn.query(sql)
            .rxExecute()
            .doOnSuccess(rows -> {
                if (rows.size() == 1) {
                    Row row = rows.iterator().next();
                    groupJson.put("id", row.getInteger(0));
                    groupJson.put("groupOwnerId", row.getInteger(2));
                }

                String byUserName = create.query(DbReactiveSqlBase.getUserByName(), groupJson.getString("groupOwner")).toString();
                conn.query(byUserName)
                  .rxExecute()
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

    @Override
    protected Future<List<String>> checkOnMembers(List<String> selectedList, JsonObject addGroupJson) {
        Promise<List<String>> waitFor = Promise.promise();
        List<String> newSelected = new ArrayList<>();
        Single<SqlConnection> connResult = pool.rxGetConnection();

        for (String user : selectedList) {
            Single.just(user).subscribe(SingleHelper.toObserver(userName -> {
                String sql = create.query(getMembersByGroup(), addGroupJson.getString("groupName"), userName.result()).toString();
                connResult.flatMap(conn -> {
                      conn.query(sql).rxExecute()
                        .doOnSuccess(rows -> {
                            if (rows.size() == 0) {
                                newSelected.add(userName.result());
                            }
                            if (userName.result().equals(selectedList.get(selectedList.size() - 1))) {
                                waitFor.complete(newSelected);
                                conn.close();
                            }
                        }).doOnError(Throwable::printStackTrace)
                        .subscribe();
                      return Single.just(conn);
                  }).doOnError(Throwable::printStackTrace)
                  .subscribe();
            }));
        }

        return waitFor.future();
    }

    @Override
    public Future<JsonObject> deleteGroup(JsonObject deleteGroupJson)
      throws InterruptedException, SQLException, IOException {
        Promise<JsonObject> promise = Promise.promise();
        checkOnGroupOwner(deleteGroupJson).onSuccess(checkedJson -> {
            if (checkedJson.getBoolean("isValidForOperation")) {
                String sql = create.query(getGroupByName(), deleteGroupJson.getString("groupName")).toString();

                pool.rxGetConnection().doOnSuccess(connection -> connection.query(sql)
                  .rxExecute()
                  .doOnSuccess(rows -> {
                      Integer id = 0;
                      for (Row row : rows) {
                          id = row.getInteger(0);
                      }

                      deleteGroupJson.put("id", id);
                      Tuple parameters = Tuple.of(id);
                      String deleteMembersSql = create.query(getDeleteMembers(), id).toString();

                      connection.rxBegin()
                        .doOnSuccess(tx -> connection
                          .query(deleteMembersSql)
                          .rxExecute()
                          .doOnSuccess(r -> {
                              int deletedMembers = 0;
                              for (RowSet<Row> s = r; s != null; s = s.next()) {
                                  if (s.rowCount() != 0) {
                                      deletedMembers += s.rowCount();
                                  }
                              }
                              if (deletedMembers > 0) {
                                  deleteGroupJson.put("errorMessage", deletedMembers + " members with ");
                              } else {
                                  parameters.clear();
                                  parameters.addInteger(0);
                              }
                              String deleteGroupByIdSql = create.query(getDeleteGroupById(), deleteGroupJson.getInteger("id")).toString();
                              connection
                                .query(deleteGroupByIdSql)
                                .rxExecute()
                                .doFinally(() -> {
                                    deleteGroupJson.put("status", 0);
                                    if (deleteGroupJson.getInteger("id") == 0) {
                                        deleteGroupJson.put("errorMessage", "No ");
                                    } else if (deleteGroupJson.getString("errorMessage") == null) {
                                        deleteGroupJson.put("errorMessage", "");
                                    }
                                    deleteGroupJson.put("errorMessage", deleteGroupJson.getString("errorMessage") + "group deleted");
                                    tx.rxCommit().doFinally(() ->
                                      connection.rxClose().doFinally(() -> promise.complete(deleteGroupJson)).subscribe()
                                    ).subscribe();
                                }).subscribe(v -> {
                                }, err -> {
                                    errData(err, promise, deleteGroupJson);
                                    if (err != null && err.getMessage() != null) {
                                        connection.close();
                                    }
                                });
                          }).doOnError(err -> {
                              errData(err, promise, deleteGroupJson);
                          }).subscribe())
                        .subscribe(v -> {
                        }, err -> {
                            errData(err, promise, deleteGroupJson);
                            if (err != null && err.getMessage() != null) {
                                connection.close();
                            }
                        });
                  }).subscribe(v -> {
                  }, err -> {
                      errData(err, promise, deleteGroupJson);
                  })
                ).subscribe(v -> {
                }, err -> {
                    errData(err, promise, deleteGroupJson);
                });
            } else {
                deleteGroupJson.put("errorMessage", checkedJson.getString("errorMessage"));
                promise.complete(deleteGroupJson);
            }
        });
        return promise.future();
    }

    @Override
    protected Future<JsonObject> deleteMembers(List<String> selectedUsers, JsonObject deleteGroupJson) {
        Promise<JsonObject> promise = Promise.promise();

        checkOnGroupOwner(deleteGroupJson).onSuccess(checkedJson -> {
            if (checkedJson.getBoolean("isValidForOperation")) {
                String groupByNameSql = create.query(getGroupByName(), deleteGroupJson.getString("groupName")).toString();

                pool.rxGetConnection()
                  .doOnSuccess(connection -> connection.query(groupByNameSql)
                    .rxExecute()
                    .doOnSuccess(rows -> {
                        Integer id = 0;
                        for (Row row : rows) {
                            id = row.getInteger(0);
                        }
                        deleteGroupJson.put("id", id);
                        connection.rxBegin().doOnSuccess(tx -> { //connection
                            StringBuilder sql = new StringBuilder();
                            StringBuilder stringBuilder = new StringBuilder();

                            List<Tuple> userList = new ArrayList<>();
                            for (String name : selectedUsers) {
                                if (DbConfiguration.isUsingSqlite3() || DbConfiguration.isUsingH2()
                                  || DbConfiguration.isUsingCubrid()) {
                                    stringBuilder.append('\'').append(name).append("',");
                                } else {
                                    userList.add(Tuple.of(name));
                                }
                            }

                            Single<RowSet<Row>> query;

                            stringBuilder.append("''"); //.append(deleteGroupJson.getString("groupOwner")).append("'");
                            sql.append(getupUsersByNameSqlite().replace("cast(", "").replace(" as varchar)", ""));
                            query = connection.query(sql.toString().replace("?", stringBuilder.toString())).execute();

                            query.doOnSuccess(result -> {
                                // Sqlite3(jdbc client) 'select' does not work well with executeBatch
                                List<Tuple> list = new ArrayList<>();
                                for (RowSet<Row> rows2 = result; rows2 != null; rows2 = rows2.next()) {
                                    if (DbConfiguration.isUsingSqlite3() || DbConfiguration.isUsingH2()
                                      || DbConfiguration.isUsingCubrid()) {
                                        for (Row row : rows2) {
                                            list.add(Tuple.of(deleteGroupJson.getInteger("id"), row.getInteger(0)));
                                        }
                                    } else {
                                        if (rows2.iterator().hasNext()) {
                                            Row row = rows2.iterator().next();
                                            list.add(Tuple.of(deleteGroupJson.getInteger("id"), row.getInteger(0)));
                                        }
                                    }
                                }
                                connection.preparedQuery(getDeleteMember()).executeBatch(list)
                                  .doOnSuccess(res -> {
                                      int rows3 = 0;
                                      for (RowSet<Row> s = res; s != null; s = s.next()) {
                                          if (s.rowCount() != 0) {
                                              rows3 += s.rowCount();
                                          }
                                      }
                                      deleteGroupJson.put("errorMessage", "Members Deleted: " + rows3);
                                      tx.rxCommit().doFinally(() ->
                                        connection.rxClose().doFinally(() -> promise.complete(deleteGroupJson)).subscribe()
                                      ).subscribe();

                                  }).subscribe(v -> {
                                  }, err -> {
                                      errData(err, promise, deleteGroupJson);
                                      if (err != null && err.getMessage() != null) {
                                          // committing because some of the batch deletes may have succeeded
                                          tx.rxCommit().doFinally(() ->
                                            connection.rxClose().subscribe()
                                          ).subscribe();
                                      }
                                  });

                            }).subscribe(v -> {
                            }, Throwable::printStackTrace);
                        }).subscribe(v -> {
                        }, Throwable::printStackTrace); // .subscribe(v -> {}, Throwable::printStackTrace));
                    }).subscribe(v -> {
                    }, Throwable::printStackTrace))
                  .subscribe(v -> {
                  }, Throwable::printStackTrace);
            } else {
                deleteGroupJson.put("errorMessage", checkedJson.getString("errorMessage"));
                promise.complete(deleteGroupJson);
            }
        });

        return promise.future();
    }

    public static void setCreate(DSLContext create) {
        GroupOpenApiSqlCubridRx.create = create;
    }

    public static void setPool(Pool pool) {
        GroupOpenApiSqlCubridRx.pool = pool;
    }

    public static void setQmark(boolean qmark) {
        GroupOpenApiSqlCubridRx.qmark = qmark;
    }

}
