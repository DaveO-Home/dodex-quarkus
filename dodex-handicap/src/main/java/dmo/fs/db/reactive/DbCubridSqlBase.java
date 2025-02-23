package dmo.fs.db.reactive;

import dmo.fs.db.MessageUser;
import dmo.fs.utils.ColorUtilConstants;
import io.quarkus.websockets.next.WebSocketConnection;
import io.reactivex.Completable;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.sqlclient.Row;
import io.vertx.reactivex.sqlclient.RowSet;
import io.vertx.reactivex.sqlclient.SqlConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public abstract class DbCubridSqlBase extends DbReactiveSqlBase {
    protected static Logger logger = LoggerFactory.getLogger(DbCubridSqlBase.class.getName());

    @Override
    public Future<MessageUser> addUser(WebSocketConnection ws, MessageUser messageUser) {
        Promise<MessageUser> promise = Promise.promise();
        Timestamp current = new Timestamp(new Date().getTime());

        pool.getConnection(c -> {
            SqlConnection conn = c.result();
            String sql = create.query(getInsertUser(), messageUser.getName(), messageUser.getPassword(), messageUser.getIp(),
              current).toString();

            conn.rxBegin().doOnSuccess(tx -> {
                conn.query(sql).rxExecute().doOnSuccess(rows -> {
                    messageUser.setId(0L);
                    for (Row row : rows) {
                        messageUser.setId(row.getLong(0));
                    }

                    messageUser.setLastLogin(current);
                    tx.commit();
                    conn.close();
                    promise.tryComplete(messageUser);
                }).doOnError(err -> {
                    logger.error(String.format("%sError adding user: %s%s", ColorUtilConstants.RED, err,
                      ColorUtilConstants.RESET));
                }).subscribe(rows -> {
                    //
                }, err -> {
                    logger.error(String.format("%sError Adding user: %s%s", ColorUtilConstants.RED, err,
                      ColorUtilConstants.RESET));
                    err.printStackTrace();
                });
            }).doOnError(Throwable::printStackTrace).subscribe();
        });

        return promise.future();
    }

    @Override
    public Future<Integer> updateUser(WebSocketConnection ws, MessageUser messageUser) {
        Promise<Integer> promise = Promise.promise();
        Timestamp timeStamp = new Timestamp(new Date().getTime());

        pool.getConnection(c -> {
            SqlConnection conn = c.result();

            String sql = create.query(getUpdateUser(), messageUser.getId(), messageUser.getName(),
              messageUser.getPassword(), messageUser.getIp(), timeStamp, timeStamp).toString();

            conn.query(sql).rxExecute().doOnSuccess(rows -> {
                conn.close();
                promise.complete(rows.rowCount());
            }).doOnError(err -> {
                logger.error(String.format("%sError Updating user Reactive: %s%s", ColorUtilConstants.RED, err,
                  ColorUtilConstants.RESET));
                ws.sendText(err.toString()).subscribeAsCompletionStage().isDone();
                conn.close();
            }).subscribe(rows -> {
                //
            }, err -> {
                logger.error(String.format("%sError Updating user on subscribe: %s%s", ColorUtilConstants.RED, err,
                  ColorUtilConstants.RESET));
                err.printStackTrace();
            });
        });

        return promise.future();
    }

    @Override
    public Future<Long> deleteUser(WebSocketConnection ws, MessageUser messageUser) {
        Promise<Long> promise = Promise.promise();

        pool.getConnection(c -> {
            SqlConnection conn = c.result();
            String query = create.query(getDeleteUser(), messageUser.getName(), messageUser.getPassword()).toString();

            conn.query(query).rxExecute().doOnSuccess(rows -> {
                Long id = 0L;
                for (Row row : rows) {
                    id = row.getLong(0);
                }
                Long count = Long.valueOf(Integer.toString(rows.rowCount()));
                messageUser.setId(id > 0L ? id : count);
                conn.close();
                promise.complete(count);
            }).doOnError(err -> {
                logger.error(String.format("%sError deleting user: %s%s", ColorUtilConstants.RED, err,
                  ColorUtilConstants.RESET));
                ws.sendText(err.toString()).subscribeAsCompletionStage().isDone();
                conn.close();
            }).subscribe(rows -> {
                //
            }, Throwable::printStackTrace);
        });
        return promise.future();
    }

    @Override
    public Future<Long> getUserIdByName(String name) {
        Promise<Long> promise = Promise.promise();

        pool.getConnection(c -> {
            SqlConnection conn = c.result();
            String query = create.query(getUserByName(), name).toString();
            conn.query(query).execute(ar -> {
                if (ar.succeeded()) {
                    RowSet<Row> rows = ar.result();
                    Long id = 0L;
                    for (Row row : rows) {
                        id = row.getLong(0);
                    }
                    conn.close();
                    promise.complete(id);
                } else {
                    logger.error(String.format("%sError finding user by name: %s - %s%s", ColorUtilConstants.RED, name,
                      ar.cause().getMessage(), ColorUtilConstants.RESET));
                    conn.close();
                }
            });
        });
        return promise.future();
    }

    @Override
    public Future<MessageUser> selectUser(MessageUser messageUser, WebSocketConnection ws) {
        MessageUser resultUser = createMessageUser();
        Promise<MessageUser> promise = Promise.promise();

        pool.getConnection(c -> {
            SqlConnection conn = c.result();

            conn.query(create.query(getUserById(), messageUser.getName(), messageUser.getPassword()).toString())
              .execute(ar -> {
                  if (ar.succeeded()) {
                      Future<Integer> future1 = null;
                      RowSet<Row> rows = ar.result();

                      if (rows.size() == 0) {
                          Future<MessageUser> future2 = addUser(ws, messageUser);

                          future2.onComplete(handler -> {
                              MessageUser result = future2.result();
                              resultUser.setId(result.getId());
                              resultUser.setName(result.getName());
                              resultUser.setPassword(result.getPassword());
                              resultUser.setIp(result.getIp());
                              resultUser.setLastLogin(result.getLastLogin() == null ? new Date().getTime()
                                : result.getLastLogin());
                              promise.complete(resultUser);
                          });
                      } else {
                          for (Row row : rows) {
                              resultUser.setId(row.getLong(0));
                              resultUser.setName(row.getString(1));
                              resultUser.setPassword(row.getString(2));
                              resultUser.setIp(row.getString(3));
                              resultUser.setLastLogin(row.getOffsetTime("LAST_LOGIN"));
                          }
                      }

                      if (rows.size() > 0) {
                          try {
                              conn.close();
                              if (ws != null) {
                                  future1 = updateUser(ws, resultUser);
                              }
                              promise.complete(resultUser);
                          } catch (Exception e) {
                              e.printStackTrace();
                          }
                      }

                      if (rows.size() > 0 && future1 != null) {
                          future1.onComplete(v -> {
                              //
                          });
                      }
                  } else {
                      logger.error(String.format("%sError selecting user: %s%s", ColorUtilConstants.RED,
                        ar.cause().getMessage(), ColorUtilConstants.RESET));
                      conn.close();
                  }
              });
        });
        return promise.future();
    }

    @Override
    public Future<StringBuilder> buildUsersJson(MessageUser messageUser) {
        Promise<StringBuilder> promise = Promise.promise();

        pool.getConnection(c -> {
            SqlConnection conn = c.result();

            conn.query(create.query(getAllUsers(), messageUser.getName()).toString()).execute(ar -> {
                if (ar.succeeded()) {
                    RowSet<Row> rows = ar.result();
                    JsonArray ja = new JsonArray();

                    for (Row row : rows) {
                        ja.add(new JsonObject().put("name", row.getString(1)));
                    }
                    conn.close();
                    promise.complete(new StringBuilder(ja.toString()));
                } else {
                    logger.error(String.format("%sError build user json: %s%s", ColorUtilConstants.RED,
                      ar.cause().getMessage(), ColorUtilConstants.RESET));
                    conn.close();
                }
            });
        });
        return promise.future();
    }

    @Override
    public Future<Map<String, Integer>> processUserMessages(WebSocketConnection ws, MessageUser messageUser) {
        RemoveUndelivered removeUndelivered = new RemoveUndeliveredCubrid();
        RemoveMessage removeMessage = new RemoveMessageCubrid();
        CompletePromise completePromise = new CompletePromise();

        removeUndelivered.setUserId(messageUser.getId());

        /*
         * Get all undelivered messages for current user
         */
        Future<Void> future = Future.future(promise -> {
            completePromise.setPromise(promise);
            pool.rxGetConnection()
              .flatMapCompletable(
                conn -> conn.rxBegin()
                  .flatMapCompletable(tx -> conn
                    .query(create.query(getUserUndelivered(), messageUser.getId()).toString())
                    .rxExecute().doOnSuccess(rows -> {
                        for (Row row : rows) {
                            OffsetDateTime postDate = null;

                            postDate = row.getOffsetDateTime("POST_DATE");

                            long epochMilli = postDate.toInstant().toEpochMilli();
                            Date date = new Date(epochMilli);

                            DateFormat formatDate = DateFormat
                              .getDateInstance(DateFormat.DEFAULT, Locale.getDefault());

                            String handle = row.getString(4);
                            String message = row.getString(2);

                            // Send messages back to client
                            ws.sendText(
                                handle + formatDate.format(date) + " " + message)
                              .subscribeAsCompletionStage().isDone();
                            removeUndelivered.getMessageIds().add(row.getLong(1));
                            removeMessage.getMessageIds().add(row.getLong(1));
                        }
                    }).doOnError(err -> {
                        logger.info(String.format("%sRetriveing Messages Error: %s%s",
                          ColorUtilConstants.RED, err.getMessage(),
                          ColorUtilConstants.RESET));
                        err.printStackTrace();
                    }).flatMapCompletable(res -> tx.rxCommit().doFinally(completePromise)
                      .doOnSubscribe(onSubscribe -> {
                          tx.completion(x -> {
                              if (x.failed()) {
                                  tx.rollback();
                                  logger.error(String.format(
                                    "%sMessages Transaction Error: %s%s",
                                    ColorUtilConstants.RED, x.cause(),
                                    ColorUtilConstants.RESET));
                              }
                              conn.close();
                          });
                      })
                    )
                  ).doOnError(err -> {
                      logger.info(String.format("%sDatabase for Messages Error: %s%s",
                        ColorUtilConstants.RED, err.getMessage(), ColorUtilConstants.RESET));
                      err.printStackTrace();
                      conn.close();
                  })
              ).subscribe();
        });

        future.compose(v -> Future.<Void>future(promise -> {
            removeUndelivered.setPromise(promise);
            try {
                removeUndelivered.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (removeUndelivered.getCount() > 0) {
                logger.info(
                  String.join(ColorUtilConstants.BLUE_BOLD_BRIGHT, Integer.toString(removeUndelivered.getCount()),
                    " Messages Delivered", " to ", messageUser.getName(), ColorUtilConstants.RESET));
            }
        })).compose(v -> Future.<Void>future(promise -> {
            removeMessage.setPromise(promise);
            try {
                removeMessage.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

        return removeUndelivered.getPromise2().future();
    }

    @Override
    public Future<Void> addUndelivered(Long userId, Long messageId) {
        Promise<Void> promise = Promise.promise();

        String sql = create.query(getAddUndelivered(), userId, messageId).toString();
        pool.getConnection(c -> {
            SqlConnection conn = c.result();

            conn.query(sql).execute(ar -> {
                if (ar.succeeded()) {
                    conn.close();
                    promise.complete();
                } else {
                    logger.error(String.format("%sAdd Undelivered Error: %s%s", ColorUtilConstants.RED,
                      ar.cause().getMessage(), ColorUtilConstants.RESET));
                    conn.close();
                }
            });
        });

        return promise.future();
    }

    class RemoveUndeliveredCubrid extends RemoveUndelivered {
        @Override
        public void run() throws Exception {
            completePromise.setPromise(promise);

            for (Long messageId : messageIds) {
                pool.getConnection(c -> {
                    SqlConnection conn = c.result();
                    String query = create.query(getRemoveUndelivered(), userId, messageId).toString();

                    conn.query(query).execute(ar -> {
                        if (ar.succeeded()) {
                            RowSet<Row> rows = ar.result();
                            for (Row row : rows) {
                                logger.info(row.toJson().toString());
                            }
                            count += rows.rowCount() == 0 ? 1 : rows.rowCount();
                        } else {
                            logger.error(String.format("Deleting Undelivered: %s", ar.cause().getMessage()));
                        }
                        if (messageIds.size() == count) {
                            try {
                                conn.close();
                                completePromise.run();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            counts.put("messages", count);
                            promise2.complete(counts);
                        }
                    });
                });
            }
        }
    }

    class RemoveMessageCubrid extends RemoveMessage {
        @Override
        public void run() throws Exception {
            completePromise.setPromise(promise);

            for (Long messageId : messageIds) {
                pool.getConnection(c -> {
                    String sql = null;

                    SqlConnection conn = c.result();

                    sql = create.query(getRemoveMessage(), messageId, messageId).toString();

                    conn.query(sql).execute(ar -> {
                        if (ar.succeeded()) {
                            RowSet<Row> rows = ar.result();

                            count += rows.rowCount() == 0 ? 1 : rows.rowCount();
                            if (messageIds.size() == count) {
                                try {
                                    conn.close();
                                    completePromise.run();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        } else {
                            logger.error(String.format("%sDeleting Message: %s%s", ColorUtilConstants.RED,
                              ar.cause().getMessage(), ColorUtilConstants.RESET));
                        }
                    });
                });
            }
        }
    }

    @Override
    public Future<Long> addMessage(WebSocketConnection ws, MessageUser messageUser, String message) {
        Promise<Long> promise = Promise.promise();

        Timestamp current = new Timestamp(new Date().getTime());

        Object postDate = current;

        pool.getConnection(ar -> {
            if (ar.succeeded()) {
                SqlConnection conn = ar.result();

                String query = create.query(getAddMessage(), message, messageUser.getName(), postDate).toString();

                conn.query(query).rxExecute().doOnSuccess(rows -> {
                    String query2 = create.query(getMessageIdByHandleDate(), messageUser.getName(), postDate).toString();
                    conn.query(query2).rxExecute().doOnSuccess(msg -> {
                        Long id = 0L;
                        for (Row row : msg) {
                            id = row.getLong(0);
                        }
                        conn.close();
                        promise.complete(id);
                    }).subscribe();
                }).doOnError(err -> {
                    logger.error(String.format("%sError adding messaage: %s%s", ColorUtilConstants.RED, err,
                      ColorUtilConstants.RESET));
                    ws.sendText(err.toString()).subscribeAsCompletionStage().isDone();
                    err.printStackTrace();
                    conn.close();
                }).subscribe(rows -> {
                    //
                }, err -> {
                    if (err != null && err.getMessage() != null) {
                        err.printStackTrace();
                    }
                });
            }

            if (ar.failed()) {
                logger.error(String.format("%sFailed Adding Message: - %s%s", ColorUtilConstants.RED,
                  ar.cause().getMessage(), ColorUtilConstants.RESET));
            }
        });

        return promise.future();
    }

    protected long broadcast(WebSocketConnection connection, String message, Map<String, String> queryParams) {
        long c = connection.getOpenConnections().stream().filter(session -> {
            if (connection.id().equals(session.id())) {
                return false;
            }
            CompletableFuture<Void> complete = session.sendText(message).subscribe().asCompletionStage();
            if (complete.isCompletedExceptionally()) {
                logger.info(String.format("%sUnable to send message: %s%s%s", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                  queryParams.get("handle"), ": Exception in broadcast",
                  ColorUtilConstants.RESET));
            }
            return true;
        }).count();
        return c;
    }

    protected WebSocketConnection getThisWebSocket(WebSocketConnection connection) {
        return connection.getOpenConnections().stream()
          .filter(s -> s.id().equals(connection.id())).findFirst().orElse(connection);
    }
}