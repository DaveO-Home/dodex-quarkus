
package dmo.fs.db.reactive;

import dmo.fs.db.MessageUser;
import dmo.fs.db.openapi.GroupOpenApiSql;
import dmo.fs.db.openapi.GroupOpenApiSqlRx;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.quarkus.websockets.next.WebSocketConnection;
import io.reactivex.functions.Action;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.jdbcclient.JDBCPool;
import io.vertx.reactivex.sqlclient.*;
import io.vertx.sqlclient.PoolOptions;
import org.jooq.DSLContext;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.jooq.impl.DSL.*;

public abstract class DbReactiveSqlBase {
    protected static final Logger logger = LoggerFactory.getLogger(DbReactiveSqlBase.class.getName());
    protected static final String QUERYUSERS = "select * from USERS where password=$";
    protected static final String QUERYMESSAGES = "select * from MESSAGES where id=$";
    protected static final String QUERYUNDELIVERED = "Select message_id, name, message, from_handle, post_date from USERS, UNDELIVERED, MESSAGES where USERS.id = user_id and MESSAGES.id = message_id and USERS.id = $1";

    protected static DSLContext create;

    protected static String GETALLUSERS;
    protected static String GETUSERBYNAME;
    protected static String GETINSERTUSER;
    protected static String GETUPDATEUSER;
    protected static String GETREMOVEUNDELIVERED;
    protected static String GETREMOVEMESSAGE;
    protected static String GETUNDELIVEREDMESSAGE;
    protected static String GETDELETEUSER;
    protected static String GETADDMESSAGE;
    protected static String GETADDUNDELIVERED;
    protected static String GETUSERNAMES;
    protected static String GETUSERBYID;
    protected static String GETREMOVEUSERUNDELIVERED;
    protected static String GETUSERUNDELIVERED;
    protected static String GETDELETEUSERBYID;
    protected static String GETSQLITEUPDATEUSER;
    protected static String GETREMOVEUSERS;
    protected static String GETCUSTOMDELETEMESSAGES;
    protected static String GETCUSTOMDELETEUSERS;
    protected static String GETMARIAINSERTUSER;
    protected static String GETMARIAADDMESSAGE;
    protected static String GETMARIADELETEUSER;
    protected static String GETMESSAGEIDBYHANDLEDATE;
    protected Boolean isTimestamp;
    protected Vertx vertx;
    protected static Pool pool;
    protected static JDBCConnectOptions jdbcConnectOptions;
    protected static PoolOptions poolOptions;
    protected static boolean qmark = true;
    DateFormat sqlite3DateFormat = new SimpleDateFormat("yyyy-MM-dd"); // HH:mm:ss");

    public static <T> void setupSql(T jdbcPool) {
        if (jdbcPool instanceof JDBCPool) {
            pool = (JDBCPool) jdbcPool;
        }

        Settings settings = new Settings().withRenderNamedParamPrefix("$"); // making compatible with Vertx4/Postgres
        create = DSL.using(DodexUtil.getSqlDialect(), settings);

        /* @TODO: convert GroupOpenApiSql to mutiny */
        io.vertx.rxjava3.sqlclient.Pool poolRx =
          io.vertx.rxjava3.jdbcclient.JDBCPool.pool(io.vertx.rxjava3.core.Vertx.vertx(), jdbcConnectOptions, poolOptions);
        GroupOpenApiSql.setPool(poolRx);

        GroupOpenApiSqlRx.setCreate(create);
        GroupOpenApiSqlRx.setQmark(qmark);
        GroupOpenApiSqlRx.buildSql();

        // Postges works with "$"(numbered) - Others work with "?"(un-numbered)
        GETALLUSERS = qmark ? setupAllUsers().replaceAll("\\$\\d", "?") : setupAllUsers();
        GETUSERBYNAME = qmark ? setupUserByName().replaceAll("\\$\\d", "?") : setupUserByName();
        GETINSERTUSER = qmark ? setupInsertUser().replaceAll("\\$\\d", "?") : setupInsertUser();
        GETMARIAINSERTUSER = qmark ? setupMariaInsertUser().replaceAll("\\$\\d", "?") : setupMariaInsertUser();
        GETUPDATEUSER = qmark ? setupUpdateUser().replaceAll("\\$\\d{1,2}", "?") : setupUpdateUser();
        GETREMOVEUNDELIVERED = qmark ? setupRemoveUndelivered().replaceAll("\\$\\d", "?") : setupRemoveUndelivered();
        GETREMOVEMESSAGE = qmark ? setupRemoveMessage().replaceAll("\\$\\d", "?") : setupRemoveMessage();
        GETUNDELIVEREDMESSAGE = qmark ? setupUndeliveredMessage().replaceAll("\\$\\d", "?") : setupUndeliveredMessage();
        GETDELETEUSER = qmark ? setupDeleteUser().replaceAll("\\$\\d", "?") : setupDeleteUser();
        GETMARIADELETEUSER = qmark ? setupMariaDeleteUser().replaceAll("\\$\\d", "?") : setupMariaDeleteUser();
        GETADDMESSAGE = qmark ? setupAddMessage().replaceAll("\\$\\d", "?") : setupAddMessage();
        GETMARIAADDMESSAGE = qmark ? setupMariaAddMessage().replaceAll("\\$\\d", "?") : setupMariaAddMessage();
        GETADDUNDELIVERED = qmark ? setupAddUndelivered().replaceAll("\\$\\d", "?") : setupAddUndelivered();
        GETUSERNAMES = qmark ? setupUserNames().replaceAll("\\$\\d", "?") : setupUserNames();
        GETUSERBYID = qmark ? setupUserById().replaceAll("\\$\\d", "?") : setupUserById();
        GETREMOVEUSERUNDELIVERED = qmark ? setupRemoveUserUndelivered().replaceAll("\\$\\d", "?")
          : setupRemoveUserUndelivered();
        GETUSERUNDELIVERED = qmark ? setupUserUndelivered().replaceAll("\\$\\d", "?") : setupUserUndelivered();
        GETDELETEUSERBYID = qmark ? setupDeleteUserById().replaceAll("\\$\\d", "?") : setupDeleteUserById();
        GETSQLITEUPDATEUSER = setupSqliteUpdateUser();
        GETREMOVEUSERS = qmark ? setupRemoveUsers().replaceAll("\\$\\d", "?") : setupRemoveUsers();
        GETCUSTOMDELETEMESSAGES = setupCustomDeleteMessage();
        GETCUSTOMDELETEUSERS = setupCustomDeleteUsers();
        GETMESSAGEIDBYHANDLEDATE = qmark ? setupMessageByHandleDate().replaceAll("\\$\\d", "?") : setupRemoveUsers();
    }

    protected static String setupAllUsers() {
        return create.renderNamedParams(
          select(field("ID"), field("NAME"), field("PASSWORD"), field("IP"), field("LAST_LOGIN"))
            .from(table("USERS")).where(field("NAME").ne("$")));
    }

    public static String getAllUsers() {
        return GETALLUSERS;
    }

    protected static String setupMessageByHandleDate() {
        return create.renderNamedParams(
          select(field("ID"))
            .from(table("MESSAGES")).where(field("FROM_HANDLE").eq("$").and(field("POST_DATE").eq("$"))));
    }

    public String getMessageIdByHandleDate() {
        return GETMESSAGEIDBYHANDLEDATE;
    }

    protected static String setupUserByName() {
        return create.renderNamedParams(
          select(field("ID"), field("NAME"), field("PASSWORD"), field("IP"), field("LAST_LOGIN"))
            .from(table("USERS")).where(field("NAME").eq("$")));
    }

    public static String getUserByName() {
        return GETUSERBYNAME;
    }

    protected static String setupUserById() {
        return create.renderNamedParams(
          select(field("ID"), field("NAME"), field("PASSWORD"), field("IP"), field("LAST_LOGIN"))
            .from(table("USERS")).where(field("NAME").eq("$")).and(field("PASSWORD").eq("$")));
    }

    public static String getUserById() {
        return GETUSERBYID;
    }

    protected static String setupInsertUser() {
        return create.renderNamedParams(
          insertInto(table("USERS")).columns(field("NAME"), field("PASSWORD"), field("IP"), field("LAST_LOGIN"))
            .values("$", "$", "$", "$").returning(field("ID")));
    }

    public static String getInsertUser() {
        return GETINSERTUSER;
    }

    protected static String setupMariaInsertUser() {
        return create.renderNamedParams(
          insertInto(table("USERS")).columns(field("NAME"), field("PASSWORD"), field("IP"), field("LAST_LOGIN"))
            .values("$", "$", "$", "$"));
    }

    public static String getMariaInsertUser() {
        return GETMARIAINSERTUSER;
    }

    protected static String setupUpdateUser() {
        return create.renderNamedParams(insertInto(table("USERS"))
          .columns(field("ID"), field("NAME"), field("PASSWORD"), field("IP"), field("LAST_LOGIN"))
          .values("$1", "$2", "$3", "$4", "$5").onConflict(field("PASSWORD")).doUpdate()
          .set(field("LAST_LOGIN"), "$5").returning(field("ID")));
    }

    public static String getUpdateUser() {
        return GETUPDATEUSER;
    }

    public static String setupSqliteUpdateUser() {
        return "update USERS set last_login = ? where id = ?";
    }

    public static String getSqliteUpdateUser() {
        return GETSQLITEUPDATEUSER;
    }

    public static String setupCustomDeleteUsers() {
        return "DELETE FROM USERS WHERE id = ? and NOT EXISTS (SELECT mid FROM (SELECT DISTINCT USERS.id AS mid FROM USERS INNER JOIN UNDELIVERED ON user_id = USERS.id) AS u )";
    }

    public static String getCustomDeleteUsers() {
        return GETCUSTOMDELETEUSERS;
    }

    public static String setupCustomDeleteMessage() {
        return "DELETE FROM MESSAGES WHERE id = ? and NOT EXISTS (SELECT mid FROM (SELECT DISTINCT MESSAGES.id AS mid FROM MESSAGES INNER JOIN UNDELIVERED ON message_id = MESSAGES.id and MESSAGES.id = ?) AS m )";
    }

    public static String getCustomDeleteMessages() {
        return GETCUSTOMDELETEMESSAGES;
    }

    protected static String setupRemoveUndelivered() {
        return create.renderNamedParams(
          deleteFrom(table("UNDELIVERED")).where(field("USER_ID").eq("$1"), field("MESSAGE_ID").eq("$2")));
    }

    public static String getRemoveUndelivered() {
        return GETREMOVEUNDELIVERED;
    }

    protected static String setupRemoveUserUndelivered() {
        return create.renderNamedParams(deleteFrom(table("UNDELIVERED")).where(field("USER_ID").eq("$")));
    }

    public static String getRemoveUserUndelivered() {
        return GETREMOVEUSERUNDELIVERED;
    }

    protected static String setupRemoveMessage() {
        return create
          .renderNamedParams(
            deleteFrom(table("MESSAGES")).where(create.renderNamedParams(field("ID").eq("$1")
              .and(create.renderNamedParams(notExists(select().from(table("MESSAGES"))
                .join(table("UNDELIVERED")).on(field("ID").eq(field("MESSAGE_ID")))
                .and(field("ID").eq("$2"))))))));
    }

    public static String getRemoveMessage() {
        return GETREMOVEMESSAGE;
    }

    protected static String setupRemoveUsers() {
        return create.renderNamedParams(deleteFrom(table("USERS")).where(create.renderNamedParams(
          field("ID").eq("$").and(create.renderNamedParams(notExists(select().from(table("USERS"))
            .join(table("UNDELIVERED")).on(field("ID").eq(field("USER_ID"))).and(field("ID").eq("$"))))))));
    }

    public String getRemoveUsers() {
        return GETREMOVEUSERS;
    }

    protected static String setupUndeliveredMessage() {
        return create.renderNamedParams(select(field("USER_ID"), field("MESSAGE_ID")).from(table("MESSAGES"))
          .join(table("UNDELIVERED")).on(field("ID").eq(field("MESSAGE_ID"))).and(field("ID").eq("$"))
          .and(field("USER_ID").eq("$")));
    }

    public static String getUndeliveredMessage() {
        return GETUNDELIVEREDMESSAGE;
    }

    protected static String setupUserUndelivered() {
        return create.renderNamedParams(select(field("USER_ID"), field("MESSAGE_ID"), field("MESSAGE"),
          field("POST_DATE"), field("FROM_HANDLE")).from(table("USERS")).join(table("UNDELIVERED"))
          .on(field("USERS.ID").eq(field("USER_ID")).and(field("USERS.ID").eq("$")))
          .join(table("MESSAGES")).on(field("MESSAGES.ID").eq(field("MESSAGE_ID"))));
    }

    public static String getUserUndelivered() {
        return GETUSERUNDELIVERED;
    }

    protected static String setupDeleteUser() {
        return create.renderNamedParams(deleteFrom(table("USERS"))
          .where(field("NAME").eq("$1"), field("PASSWORD").eq("$2")).returning(field("ID")));
    }

    public static String getDeleteUser() {
        return GETDELETEUSER;
    }

    protected static String setupMariaDeleteUser() {
        return create.renderNamedParams(
          deleteFrom(table("USERS")).where(field("NAME").eq("$1"), field("PASSWORD").eq("$2")));
    }

    public static String getMariaDeleteUser() {
        return GETMARIADELETEUSER;
    }

    protected static String setupDeleteUserById() {
        return create.renderNamedParams(deleteFrom(table("USERS")).where(field("ID").eq("$1")).returning(field("ID")));
    }

    public static String getDeleteUserById() {
        return GETDELETEUSERBYID;
    }

    protected static String setupAddMessage() {
        return create.renderNamedParams(
          insertInto(table("MESSAGES")).columns(field("MESSAGE"), field("FROM_HANDLE"), field("POST_DATE"))
            .values("$", "$", "$").returning(field("ID")));
    }

    public static String getAddMessage() {
        return GETADDMESSAGE;
    }

    protected static String setupMariaAddMessage() {
        return create.renderNamedParams(insertInto(table("MESSAGES"))
          .columns(field("MESSAGE"), field("FROM_HANDLE"), field("POST_DATE")).values("$", "$", "$"));
    }

    public static String getMariaAddMessage() {
        return GETMARIAADDMESSAGE;
    }

    protected static String setupAddUndelivered() {
        return create.renderNamedParams(
          insertInto(table("UNDELIVERED")).columns(field("USER_ID"), field("MESSAGE_ID")).values("$", "$"));
    }

    public static String getAddUndelivered() {
        return GETADDUNDELIVERED;
    }

    protected static String setupUserNames() {
        return create.renderNamedParams(
          select(field("ID"), field("NAME"), field("PASSWORD"), field("IP"), field("LAST_LOGIN"))
            .from(table("USERS")).where(field("NAME").ne("$")));
    }

    public static String getUserNames() {
        return GETUSERNAMES;
    }

    public Future<MessageUser> addUser(WebSocketConnection ws, MessageUser messageUser) {
        Promise<MessageUser> promise = Promise.promise();
        Timestamp current = new Timestamp(new Date().getTime());
        OffsetDateTime time = OffsetDateTime.now();

        pool.getConnection(c -> {
            Tuple parameters = Tuple.of(messageUser.getName(), messageUser.getPassword(),
              messageUser.getIp() == null ? "unknown" : messageUser.getIp(), current);
            SqlConnection conn = c.result();
            String sql = getInsertUser();

            conn.preparedQuery(sql).rxExecute(parameters).doOnSuccess(rows -> {
                for (Row row : rows) {
                    messageUser.setId(row.getLong(0));
                }
                if (messageUser.getId() == null) {
                    messageUser.setId(rows.property(JDBCPool.GENERATED_KEYS).getLong(0));
                }

                messageUser.setLastLogin(current);
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
        });

        return promise.future();
    }

    public Future<Integer> updateUser(WebSocketConnection ws, MessageUser messageUser) {
        Promise<Integer> promise = Promise.promise();

        pool.getConnection(c -> {
            SqlConnection conn = c.result();

            Tuple parameters = getTupleParameters(messageUser);

            String sql = DbConfiguration.isUsingSqlite3() ? getSqliteUpdateUser() : getUpdateUser();

            conn.preparedQuery(sql).rxExecute(parameters).doOnSuccess(rows -> {
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

    protected Tuple getTupleParameters(MessageUser messageUser) {
        Timestamp timeStamp = new Timestamp(new Date().getTime());
        long date = new Date().getTime();
        OffsetDateTime time = OffsetDateTime.now();
        Tuple parameters;

        if (DbConfiguration.isUsingSqlite3()) {
            parameters = Tuple.of(date, messageUser.getId());
            return parameters;
        }

        parameters = Tuple.of(messageUser.getId(), messageUser.getName(), messageUser.getPassword(),
          messageUser.getIp(), DbConfiguration.isUsingCubrid() ? timeStamp : time,
          DbConfiguration.isUsingCubrid() ? timeStamp : time);

        return parameters;
    }

    public Future<Long> deleteUser(WebSocketConnection ws, MessageUser messageUser) {
        Promise<Long> promise = Promise.promise();

        pool.getConnection(c -> {
            Tuple parameters = Tuple.of(messageUser.getName(), messageUser.getPassword());
            SqlConnection conn = c.result();

            String sql = getDeleteUser();

            conn.preparedQuery(sql).rxExecute(parameters).doOnSuccess(rows -> {
                Long id = 0L;
                for (Row row : rows) {
                    id = row.getLong(0);
                }
                Long count = (long) rows.rowCount();
                messageUser.setId(id > 0L ? id : count);
                conn.close();
                promise.complete(count);
            }).doOnError(err -> {
                String errMessage = null;
                if (err != null && err.getMessage() == null) {
                    errMessage = String.format("%s%s", "User deleted - but returned: ", err.getMessage());
                } else {
                    errMessage = String.format("%s%s", "Error deleting user: ", err);
                }
                logger.error(String.format("%s%s%s", ColorUtilConstants.RED, errMessage, ColorUtilConstants.RESET));
                promise.complete(-1L);
                conn.close();
            }).subscribe(rows -> {
                //
            }, err -> {
                if (err != null && err.getMessage() != null) {
                    err.printStackTrace();
                }
            });
        });
        return promise.future();
    }

    public Future<Long> addMessage(WebSocketConnection ws, MessageUser messageUser, String message) {
        Promise<Long> promise = Promise.promise();

        OffsetDateTime time = OffsetDateTime.now();
        Timestamp current = Timestamp.valueOf(LocalDateTime.now());
        String currentTimeStamp = sqlite3DateFormat.format(current);
        Tuple parameters = Tuple.of(message, messageUser.getName(), currentTimeStamp);

        pool.getConnection(ar -> {
            if (ar.succeeded()) {
                SqlConnection conn = ar.result();

                String sql = getAddMessage();

                conn.preparedQuery(sql).rxExecute(parameters).doOnSuccess(rows -> {
                    Long id = 0L;
                    for (Row row : rows) {
                        id = row.getLong(0);
                    }
                    if (id == null) {
                        id = rows.property(JDBCPool.GENERATED_KEYS).getLong(0);
                    }

                    conn.close();
                    promise.complete(id);
                }).doOnError(err -> {
                    logger.error(String.format("%sError adding messaage: %s%s", ColorUtilConstants.RED, err,
                      ColorUtilConstants.RESET));
                    ws.sendText(err.toString()).subscribeAsCompletionStage().isDone();
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
                logger.error(String.format("%sError Adding Message: - %s%s", ColorUtilConstants.RED,
                  ar.cause().getMessage(), ColorUtilConstants.RESET));
            }
        });

        return promise.future();
    }

    public Future<Void> addUndelivered(Long userId, Long messageId) {
        Promise<Void> promise = Promise.promise();

        Tuple parameters = Tuple.of(userId, messageId);
        pool.getConnection(c -> {
            SqlConnection conn = c.result();

            conn.preparedQuery(getAddUndelivered()).execute(parameters, ar -> {
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

    public Future<Void> addUndelivered(WebSocketConnection ws, List<String> undelivered, Long messageId) {
        Promise<Void> promise = Promise.promise();
        try {
            for (String name : undelivered) {
                Future<Long> future = getUserIdByName(name);
                future.onSuccess(userId -> {
                    try {
                        Future<Void> future2 = addUndelivered(userId, messageId);
                        future2.onSuccess(handler -> {
                            promise.tryComplete();
                        });
                    } catch (Exception e) {
                        logger.error(String.join("", "AddUndelivered: ", e.getMessage()));
                    }
                });
            }
        } catch (Exception e) {
            ws.sendText(e.getMessage()).subscribeAsCompletionStage().isDone();
        }
        return promise.future();
    }

    public Future<Long> getUserIdByName(String name) {
        Promise<Long> promise = Promise.promise();

        pool.getConnection(c -> {
            SqlConnection conn = c.result();

            conn.preparedQuery(getUserByName()).execute(Tuple.of(name), ar -> {
                if (ar.succeeded()) {
                    RowSet<Row> rows = ar.result();
                    Long id = 0L;
                    for (Row row : rows) {
                        id = row.getLong(0);
                    }
                    conn.close();
                    promise.complete(id);
                } else {
                    logger.error(String.format("%sError finding user by name: %s - %s%s", ColorUtilConstants.RED,
                      name, ar.cause().getMessage(), ColorUtilConstants.RESET));
                    conn.close();
                }
            });
        });
        return promise.future();
    }

    public abstract MessageUser createMessageUser();

    public Future<MessageUser> selectUser(MessageUser messageUser, WebSocketConnection ws) {
        MessageUser resultUser = createMessageUser();
        Promise<MessageUser> promise = Promise.promise();
        Tuple parameters = Tuple.of(messageUser.getName(), messageUser.getPassword());

        pool.getConnection(c -> {
            SqlConnection conn = c.result();

            conn.preparedQuery(getUserById()).execute(parameters, ar -> {
                if (ar.succeeded()) {
                    Future<Integer> future1 = null;
                    RowSet<Row> rows = ar.result();

                    if (rows.size() == 0) {
                        try {
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
                        } catch (Exception ex) {
                            logger.error(ex.getMessage());
                        }
                    } else {
                        for (Row row : rows) {
                            resultUser.setId(row.getLong(0));
                            resultUser.setName(row.getString(1));
                            resultUser.setPassword(row.getString(2));
                            resultUser.setIp(row.getString(3));
                            resultUser.setLastLogin(row.getValue(4));
                        }
                    }

                    if (rows.size() > 0) {
                        try {
                            conn.close();
                            future1 = updateUser(ws, resultUser);
                            promise.complete(resultUser);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    if (rows.size() > 0) {
                        assert future1 != null;
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

    public Future<StringBuilder> buildUsersJson(MessageUser messageUser) {
        Promise<StringBuilder> promise = Promise.promise();

        pool.getConnection(c -> {
            SqlConnection conn = c.result();

            conn.preparedQuery(getAllUsers()).execute(Tuple.of(messageUser.getName()), ar -> {
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

    public Future<Map<String, Integer>> processUserMessages(WebSocketConnection ws, MessageUser messageUser) {
        RemoveUndelivered removeUndelivered = new RemoveUndelivered();
        RemoveMessage removeMessage = new RemoveMessage();
        CompletePromise completePromise = new CompletePromise();

        removeUndelivered.setUserId(messageUser.getId());

        /*
         * Get all undelivered messages for current user
         */
        Future<Void> future = Future.future(promise -> {
            completePromise.setPromise(promise);

            Tuple parameters = Tuple.of(messageUser.getId());

            pool.rxGetConnection().flatMapCompletable(conn -> conn.rxBegin().flatMapCompletable(
                tx -> conn.preparedQuery(getUserUndelivered()).rxExecute(parameters).doOnSuccess(rows -> {
                    for (Row row : rows) {
                        DateFormat formatDate = DateFormat.getDateInstance(DateFormat.DEFAULT,
                          Locale.getDefault());

                        String message = row.getString(2);
                        String handle = row.getString(4);

                        messageUser.setLastLogin(sqlite3DateFormat.parse(row.getLocalDate(3).toString()));
                        // Send messages back to client
                        ws.sendText(
                            handle + formatDate.format(messageUser.getLastLogin()) + " " + message)
                          .subscribeAsCompletionStage().isDone();
                        removeUndelivered.getMessageIds().add(row.getLong(1));
                        removeMessage.getMessageIds().add(row.getLong(1));
                    }
                }).doOnError(err -> {
                    logger.info(String.format("%sRetriveing Messages Error: %s%s", ColorUtilConstants.RED,
                      err.getMessage(), ColorUtilConstants.RESET));
                    err.printStackTrace();
                }).flatMapCompletable(
                  res -> tx.rxCommit().doFinally(completePromise).doOnSubscribe(onSubscribe -> {
                      tx.completion(x -> {
                          if (x.failed()) {
                              tx.rollback();
                              logger.error(
                                String.format("%sMessages Transaction Error: %s%s",
                                  ColorUtilConstants.RED, x.cause(), ColorUtilConstants.RESET));
                          }
                      });
                  })))
              .doOnError(err -> {
                  logger.info(String.format("%sDatabase for Messages Error: %s%s", ColorUtilConstants.RED,
                    err.getMessage(), ColorUtilConstants.RESET));
                  err.printStackTrace();
                  conn.close();
              })).subscribe();
        });

        future.compose(v -> {
            return Future.<Void>future(promise -> {
                removeUndelivered.setPromise(promise);
                try {
                    removeUndelivered.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (removeUndelivered.getCount() > 0) {
                    logger.info(String.join(ColorUtilConstants.BLUE_BOLD_BRIGHT,
                      Integer.toString(removeUndelivered.getCount()), " Messages Delivered", " to ",
                      messageUser.getName(), ColorUtilConstants.RESET));
                }
            });
        }).compose(v -> Future.<Void>future(promise -> {
            removeMessage.setPromise(promise);
            try {
                removeMessage.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

        return removeUndelivered.getPromise2().future();
    }

    class CompletePromise implements Action {
        Promise<Void> promise;

        @Override
        public void run() throws Exception {
            promise.tryComplete();
        }

        public Promise<Void> getPromise() {
            return promise;
        }

        public void setPromise(Promise<Void> promise) {
            this.promise = promise;
        }
    }

    class RemoveUndelivered implements Action {
        List<Long> messageIds = new ArrayList<>();
        CompletePromise completePromise = new CompletePromise();
        Long userId;
        int count;
        Promise<Void> promise;
        Promise<Map<String, Integer>> promise2 = Promise.promise();
        Map<String, Integer> counts = new ConcurrentHashMap<>();

        @Override
        public void run() throws Exception {
            completePromise.setPromise(promise);

            for (Long messageId : messageIds) {
                Tuple parameters = Tuple.of(userId, messageId);
                pool.getConnection(c -> {
                    SqlConnection conn = c.result();

                    conn.preparedQuery(getRemoveUndelivered()).execute(parameters, ar -> {
                        if (ar.succeeded()) {
                            RowSet<Row> rows = ar.result();
                            count += rows.rowCount() == 0 ? 1 : rows.rowCount();
                        } else {
                            logger.error("Deleting Undelivered: " + ar.cause().getMessage());
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

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public List<Long> getMessageIds() {
            return messageIds;
        }

        public void setMessageIds(List<Long> messageIds) {
            this.messageIds = messageIds;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public Promise<Void> getPromise() {
            return promise;
        }

        public void setPromise(Promise<Void> promise) {
            this.promise = promise;
        }

        public Promise<Map<String, Integer>> getPromise2() {
            return promise2;
        }

        public void setPromise2(Promise<Map<String, Integer>> promise2) {
            this.promise2 = promise2;
        }
    }

    class RemoveMessage implements Action {
        int count;
        List<Long> messageIds = new ArrayList<>();
        CompletePromise completePromise = new CompletePromise();
        Promise<Void> promise;

        @Override
        public void run() throws Exception {
            completePromise.setPromise(promise);

            for (Long messageId : messageIds) {
                if (DbConfiguration.isUsingSqlite3()) {
                    try { // Sqlite3 needs a delay???
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        logger.error(String.join("", "Await: ", e.getMessage()));
                    }
                }

                pool.getConnection(c -> {
                    Tuple parameters = Tuple.of(messageId, messageId);
                    String sql = null;
                    if (DbConfiguration.isUsingSqlite3()) {
                        sql = getCustomDeleteMessages();
                    } else {
                        parameters = Tuple.of(messageId);
                        sql = getRemoveMessage();
                    }
                    SqlConnection conn = c.result();

                    conn.preparedQuery(sql).rxExecute(parameters).doOnSuccess(rows -> {
                        count += rows.rowCount() == 0 ? 1 : rows.rowCount();

                        if (messageIds.size() == count) {
                            conn.close();
                        }
                    }).doOnError(err -> {
                        logger.error(String.format("%sDeleting Message2: %s%s", ColorUtilConstants.RED, err,
                          ColorUtilConstants.RESET));
                    }).doFinally(completePromise).subscribe(rows -> {
                    }, Throwable::printStackTrace);
                });
            }
        }

        public List<Long> getMessageIds() {
            return messageIds;
        }

        public void setMessageIds(List<Long> messageIds) {
            this.messageIds = messageIds;
        }

        public Promise<Void> getPromise() {
            return promise;
        }

        public void setPromise(Promise<Void> promise) {
            this.promise = promise;
        }
    }

    public void setIsTimestamp(Boolean isTimestamp) {
        this.isTimestamp = isTimestamp;
    }

    public boolean getisTimestamp() {
        return this.isTimestamp;
    }

    public Vertx getVertx() {
        return vertx;
    }

    public void setVertx(Vertx vertx) {
        this.vertx = vertx;
    }

    public static DSLContext getCreate() {
        return create;
    }

    public static void setJDBCConnectOptions(JDBCConnectOptions jdbcConnectOptions) {
        DbReactiveSqlBase.jdbcConnectOptions = jdbcConnectOptions;
    }

    public static void setPoolOptions(PoolOptions poolOptions) {
        DbReactiveSqlBase.poolOptions = poolOptions;
    }
}
