
package dmo.fs.db.wsnext;

import dmo.fs.db.MessageUser;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.groups.UniSubscribe;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.db2client.DB2Pool;
import io.vertx.mutiny.mysqlclient.MySQLClient;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.*;
import org.jooq.DSLContext;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.jooq.impl.DSL.*;

public abstract class DbDefinitionBase {
    protected static final Logger logger = LoggerFactory.getLogger(DbDefinitionBase.class.getSimpleName());
    protected static final String QUERYUSERS = "select * from users where password=$";
    protected static final String QUERYMESSAGES = "select * from messages where id=$";
    protected static final String QUERYUNDELIVERED = "Select message_id, name, message, from_handle, post_date from users, undelivered, messages where users.id = user_id and messages.id = message_id and users.id = $1";

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
    protected Pool pool;
    protected boolean qmark = true;

    public <T> void setupSql(T pool) {
        // Non-Blocking Drivers
        if (pool instanceof PgPool) {
            this.pool = (PgPool) pool;
            qmark = false;
        } else if (pool instanceof MySQLPool) {
            this.pool = (MySQLPool) pool;
        } else if (pool instanceof DB2Pool) {
            this.pool = (DB2Pool) pool;
        } else {
            this.pool = (Pool) pool;
        }

        Settings settings = new Settings().withRenderNamedParamPrefix("$"); // making compatible with Vertx4/Postgres

        create = DSL.using(DodexUtil.getSqlDialect(), settings);
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
            .from(table("users")).where(field("NAME").ne("$")));
    }

    public static String getAllUsers() {
        return GETALLUSERS;
    }

    protected static String setupMessageByHandleDate() {
        return create.renderNamedParams(
          select(field("ID"))
            .from(table("messages")).where(field("FROM_HANDLE").eq("$").and(field("POST_DATE").eq("$"))));
    }

    public String getMessageIdByHandleDate() {
        return GETMESSAGEIDBYHANDLEDATE;
    }

    protected static String setupUserByName() {
        return create.renderNamedParams(
          select(field("ID"), field("NAME"), field("PASSWORD"), field("IP"), field("LAST_LOGIN"))
            .from(table("users")).where(field("NAME").eq("$")));
    }

    public static String getUserByName() {
        return GETUSERBYNAME;
    }

    protected static String setupUserById() {
        return create.renderNamedParams(
          select(field("ID"), field("NAME"), field("PASSWORD"), field("IP"), field("LAST_LOGIN"))
            .from(table("users")).where(field("NAME").eq("$")).and(field("PASSWORD").eq("$")));
    }

    public static String getUserById() {
        return GETUSERBYID;
    }

    protected static String setupInsertUser() {
        return create.renderNamedParams(
          insertInto(table("users")).columns(field("NAME"), field("PASSWORD"), field("IP"), field("LAST_LOGIN"))
            .values("$", "$", "$", "$").returning(field("ID")));
    }

    public static String getInsertUser() {
        return GETINSERTUSER;
    }

    protected static String setupMariaInsertUser() {
        return create.renderNamedParams(
          insertInto(table("users")).columns(field("NAME"), field("PASSWORD"), field("IP"), field("LAST_LOGIN"))
            .values("$", "$", "$", "$"));
    }

    public static String getMariaInsertUser() {
        return GETMARIAINSERTUSER;
    }

    protected static String setupUpdateUser() {
        return create.renderNamedParams(insertInto(table("users"))
          .columns(field("ID"), field("NAME"), field("PASSWORD"), field("IP"), field("LAST_LOGIN"))
          .values("$1", "$2", "$3", "$4", "$5").onConflict(field("PASSWORD")).doUpdate()
          .set(field("LAST_LOGIN"), "$5").returning(field("ID")));
    }

    public static String getUpdateUser() {
        return GETUPDATEUSER;
    }

    public static String setupSqliteUpdateUser() {
        return "update users set last_login = ? where id = ?";
    }

    public static String getSqliteUpdateUser() {
        return GETSQLITEUPDATEUSER;
    }

    public static String setupCustomDeleteUsers() {
        return "DELETE FROM users WHERE id = ? and NOT EXISTS (SELECT mid FROM (SELECT DISTINCT users.id AS mid FROM users INNER JOIN undelivered ON user_id = users.id) AS u )";
    }

    public static String getCustomDeleteUsers() {
        return GETCUSTOMDELETEUSERS;
    }

    public static String setupCustomDeleteMessage() {
        return "DELETE FROM messages WHERE id = ? and NOT EXISTS (SELECT mid FROM (SELECT DISTINCT messages.id AS mid FROM messages INNER JOIN undelivered ON message_id = messages.id and messages.id = ?) AS m )";
    }

    public static String getCustomDeleteMessages() {
        return GETCUSTOMDELETEMESSAGES;
    }

    protected static String setupRemoveUndelivered() {
        return create.renderNamedParams(
          deleteFrom(table("undelivered")).where(field("USER_ID").eq("$1"), field("MESSAGE_ID").eq("$2")));
    }

    public static String getRemoveUndelivered() {
        return GETREMOVEUNDELIVERED;
    }

    protected static String setupRemoveUserUndelivered() {
        return create.renderNamedParams(deleteFrom(table("undelivered")).where(field("USER_ID").eq("$")));
    }

    public static String getRemoveUserUndelivered() {
        return GETREMOVEUSERUNDELIVERED;
    }

    protected static String setupRemoveMessage() {
        return create
          .renderNamedParams(
            deleteFrom(table("messages")).where(create.renderNamedParams(field("ID").eq("$1")
              .and(create.renderNamedParams(notExists(select().from(table("messages"))
                .join(table("undelivered")).on(field("ID").eq(field("MESSAGE_ID")))
                .and(field("ID").eq("$2"))))))));
    }

    public static String getRemoveMessage() {
        return GETREMOVEMESSAGE;
    }

    protected static String setupRemoveUsers() {
        return create.renderNamedParams(deleteFrom(table("users")).where(create.renderNamedParams(
          field("ID").eq("$").and(create.renderNamedParams(notExists(select().from(table("users"))
            .join(table("undelivered")).on(field("ID").eq(field("USER_ID"))).and(field("ID").eq("$"))))))));
    }

    public static String getRemoveUsers() {
        return GETREMOVEUSERS;
    }

    protected static String setupUndeliveredMessage() {
        return create.renderNamedParams(select(field("USER_ID"), field("MESSAGE_ID")).from(table("messages"))
          .join(table("undelivered")).on(field("ID").eq(field("MESSAGE_ID"))).and(field("ID").eq("$"))
          .and(field("USER_ID").eq("$")));
    }

    public static String getUndeliveredMessage() {
        return GETUNDELIVEREDMESSAGE;
    }

    protected static String setupUserUndelivered() {
        return create.renderNamedParams(select(field("USER_ID"), field("MESSAGE_ID"), field("MESSAGE"),
          field("POST_DATE"), field("FROM_HANDLE")).from(table("users")).join(table("undelivered"))
          .on(field("users.ID").eq(field("USER_ID")).and(field("users.ID").eq("$")))
          .join(table("messages")).on(field("messages.ID").eq(field("MESSAGE_ID"))));
    }

    public static String getUserUndelivered() {
        return GETUSERUNDELIVERED;
    }

    protected static String setupDeleteUser() {
        return create.renderNamedParams(deleteFrom(table("users"))
          .where(field("NAME").eq("$1"), field("PASSWORD").eq("$2")).returning(field("ID")));
    }

    public static String getDeleteUser() {
        return GETDELETEUSER;
    }

    protected static String setupMariaDeleteUser() {
        return create.renderNamedParams(
          deleteFrom(table("users")).where(field("NAME").eq("$1"), field("PASSWORD").eq("$2")));
    }

    public static String getMariaDeleteUser() {
        return GETMARIADELETEUSER;
    }

    protected static String setupDeleteUserById() {
        return create.renderNamedParams(deleteFrom(table("users")).where(field("ID").eq("$1")).returning(field("ID")));
    }

    public static String getDeleteUserById() {
        return GETDELETEUSERBYID;
    }

    protected static String setupAddMessage() {
        return create.renderNamedParams(
          insertInto(table("messages")).columns(field("MESSAGE"), field("FROM_HANDLE"), field("POST_DATE"))
            .values("$", "$", "$").returning(field("ID")));
    }

    public static String getAddMessage() {
        return GETADDMESSAGE;
    }

    protected static String setupMariaAddMessage() {
        return create.renderNamedParams(insertInto(table("messages"))
          .columns(field("MESSAGE"), field("FROM_HANDLE"), field("POST_DATE")).values("$", "$", "$"));
    }

    public static String getMariaAddMessage() {
        return GETMARIAADDMESSAGE;
    }

    protected static String setupAddUndelivered() {
        return create.renderNamedParams(
          insertInto(table("undelivered")).columns(field("USER_ID"), field("MESSAGE_ID")).values("$", "$"));
    }

    public static String getAddUndelivered() {
        return GETADDUNDELIVERED;
    }

    protected static String setupUserNames() {
        return create.renderNamedParams(
          select(field("ID"), field("NAME"), field("PASSWORD"), field("IP"), field("LAST_LOGIN"))
            .from(table("users")).where(field("NAME").ne("$")));
    }

    public static String getUserNames() {
        return GETUSERNAMES;
    }

    @SuppressWarnings("unchecked")
    public Promise<MessageUser> addUser(MessageUser messageUser) {
        Promise<MessageUser> promise = Promise.promise();
        Timestamp current = new Timestamp(new Date().getTime());
        OffsetDateTime time = OffsetDateTime.now();

        Object lastLogin = DbConfiguration.isUsingPostgres() ? time : current;

        Tuple parameters = Tuple.of(messageUser.getName(), messageUser.getPassword(), messageUser.getIp(), lastLogin);

        pool.getConnection().onItem().invoke(conn -> {
            String sql = getInsertUser();

            if (DbConfiguration.isUsingMariadb() || DbConfiguration.isUsingIbmDB2()) {
                sql = getMariaInsertUser();
                if (DbConfiguration.isUsingIbmDB2()) {
                    sql = String.format("%s %s%s", "select id from FINAL TABLE(", sql, ")");
                }
            }

            conn.preparedQuery(sql).execute(parameters).onItem().transform(rows -> {
                for (Row row : rows) {
                    messageUser.setId(row.getLong(0));
                }

                if (DbConfiguration.isUsingMariadb()) {
                    messageUser.setId(rows.property(MySQLClient.LAST_INSERTED_ID));
                } else if (DbConfiguration.isUsingCubrid()) {
                    messageUser.setId(rows.property((PropertyKind<Row>) JDBCPool.GENERATED_KEYS).getLong(0));
                }

                messageUser.setLastLogin(current);
                promise.tryComplete(messageUser);
                return conn.close().subscribeAsCompletionStage();
            }).onFailure().invoke(error -> {
                logger.error("{}Error Adding user: {}{}", ColorUtilConstants.RED, error, ColorUtilConstants.RESET);
            }).subscribeAsCompletionStage().isDone();
        }).onFailure().invoke(error -> {
            logger.error("{}Error Adding user-database connection error: {}{}", ColorUtilConstants.RED, error,
              ColorUtilConstants.RESET);
        }).subscribeAsCompletionStage().isDone();

        return promise;
    }

    public Promise<Integer> updateUser(MessageUser messageUser) {
        Promise<Integer> promise = Promise.promise();

        pool.getConnection().call(conn -> {
            Tuple parameters = getTupleParameters(messageUser);

            String sql = DbConfiguration.isUsingIbmDB2() || DbConfiguration.isUsingSqlite3()
              || DbConfiguration.isUsingMariadb() || DbConfiguration.isUsingH2() ?
              getSqliteUpdateUser() : getUpdateUser();

            conn.preparedQuery(sql).execute(parameters).map(rows -> {
                conn.close().subscribeAsCompletionStage().isDone();
                promise.complete(rows.rowCount());
                return null;
            }).onFailure().invoke(err -> {
                logger.error(String.format("%sError Updating user: %s%s", ColorUtilConstants.RED, err,
                  ColorUtilConstants.RESET));
                conn.close().subscribeAsCompletionStage().isDone();
            }).subscribeAsCompletionStage().isDone();
            return null;
        }).subscribeAsCompletionStage().isDone();
        return promise;
    }

    protected Tuple getTupleParameters(MessageUser messageUser) {
        Timestamp timeStamp = new Timestamp(new Date().getTime());
        long date = new Date().getTime();
        OffsetDateTime time = OffsetDateTime.now();
        Tuple parameters;

        if (DbConfiguration.isUsingIbmDB2() ||
          DbConfiguration.isUsingSqlite3() ||
          DbConfiguration.isUsingMariadb() ||
          DbConfiguration.isUsingH2()) {
            parameters = Tuple.of(
              DbConfiguration.isUsingIbmDB2() || DbConfiguration.isUsingMariadb() ||
                DbConfiguration.isUsingH2() ? timeStamp : date,
              messageUser.getId());
            return parameters;
        }

        parameters = Tuple.of(messageUser.getId(), messageUser.getName(), messageUser.getPassword(),
          messageUser.getIp(), DbConfiguration.isUsingCubrid() ? timeStamp : time,
          DbConfiguration.isUsingCubrid() ? timeStamp : time);

        return parameters;
    }

    public Promise<Long> deleteUser(MessageUser messageUser) {
        Promise<Long> promise = Promise.promise();

        pool.getConnection().call(conn -> {
            Tuple parameters = Tuple.of(messageUser.getName(), messageUser.getPassword());

            String sql = getDeleteUser();
            if (DbConfiguration.isUsingMariadb()) {
                sql = getMariaDeleteUser();
            }

            conn.preparedQuery(sql).execute(parameters).call(rows -> {
                Long id = 0L;
                for (Row row : rows) {
                    id = row.getLong(0);
                }
                long count = rows.rowCount();
                messageUser.setId(id > 0L ? id : count);
                conn.close().subscribeAsCompletionStage().isDone();
                promise.complete(count);
                return Uni.createFrom().item(rows);
            }).onFailure().invoke(err -> {
                String errMessage = null;
                if (err != null && err.getMessage() == null) {
                    errMessage = String.format("%s%s", "User deleted - but returned: ", err.getMessage());
                } else {
                    errMessage = String.format("%s%s", "Error deleting user: ", err);
                }
                logger.error(String.format("%s%s%s", ColorUtilConstants.RED, errMessage, ColorUtilConstants.RESET));
                promise.complete(-1L);
                conn.close().subscribeAsCompletionStage().isDone();
            }).subscribeAsCompletionStage().isDone();
            return Uni.createFrom().item(conn);
        }).onFailure().invoke(err -> {
            err.printStackTrace();
        }).subscribeAsCompletionStage().isDone();

        return promise;
    }

    @SuppressWarnings("unchecked")
    public Promise<Long> addMessage(MessageUser messageUser, String message) {
        Promise<Long> promise = Promise.promise();
        Timestamp timeStamp = new Timestamp(new Date().getTime());
        OffsetDateTime time = OffsetDateTime.now();
        long date = new Date().getTime();
        LocalDateTime localTime = LocalDateTime.now();

        Object dateTime = time;
        if (DbConfiguration.isUsingIbmDB2()) {
            dateTime = timeStamp;
        } else if (DbConfiguration.isUsingSqlite3()) {
            dateTime = date;
        } else if (DbConfiguration.isUsingMariadb() || DbConfiguration.isUsingCubrid()) {
            dateTime = timeStamp;
        }

        Tuple parameters = Tuple.of(message, messageUser.getName(), dateTime);

        pool.getConnection().invoke(conn -> {
            conn.begin().onItem().call(tx -> {
                String sql = getAddMessage();
                if (DbConfiguration.isUsingIbmDB2()) {
                    sql = String.format("%s%s%s", "SELECT id FROM FINAL TABLE (", getMariaAddMessage(), ")");
                } else if (DbConfiguration.isUsingMariadb()) {
                    sql = getMariaAddMessage();
                }

                conn.preparedQuery(sql).execute(parameters).onItem().invoke(rows -> {
                    Long id = 0L;
                    for (Row row : rows) {
                        id = row.getLong(0);
                    }
                    if (DbConfiguration.isUsingMariadb()) {
                        id = rows.property(MySQLClient.LAST_INSERTED_ID);
                    } else if (DbConfiguration.isUsingCubrid()) {
                        id = rows.property((PropertyKind<Row>) JDBCPool.GENERATED_KEYS).getLong(0);
                    }
                    CompletableFuture<Void> committed = tx.commit().subscribeAsCompletionStage();
                    committed.thenRun(() -> {
                        conn.close().subscribeAsCompletionStage().isDone();
                        logger.info("Conn closed: {}", committed.isDone());
                    });
                    promise.complete(id);
                }).onFailure().invoke(err -> {
                    logger.error(String.format("%sError adding message: %s%s", ColorUtilConstants.RED,
                      err.getCause().getMessage(), ColorUtilConstants.RESET));
                    CompletableFuture<Void> rollback = tx.rollback().subscribeAsCompletionStage();
                    rollback.thenRun(() ->
                      conn.close().subscribeAsCompletionStage().isDone());
                }).subscribeAsCompletionStage().isDone();
                return Uni.createFrom().item(true);
            }).onFailure().invoke(err -> {
                logger.error(String.format("%sTransaction Error adding message: %s%s", ColorUtilConstants.RED, err,
                  ColorUtilConstants.RESET));
            }).subscribeAsCompletionStage().isDone();
        }).onFailure().invoke(err -> {
            logger.error(String.format("%sConnection Error adding message: %s%s", ColorUtilConstants.RED, err,
              ColorUtilConstants.RESET));
        }).subscribeAsCompletionStage().isDone();

        return promise;
    }

    public Promise<Void> addUndelivered(Long userId, Long messageId) {
        Promise<Void> promise = Promise.promise();

        Tuple parameters = Tuple.of(userId, messageId);
        pool.getConnection().map(conn -> {
            conn.preparedQuery(getAddUndelivered()).execute(parameters).invoke(rows -> {
                conn.close().subscribeAsCompletionStage().isDone();
                promise.complete();
            }).onFailure().invoke(err -> {
                logger.error(String.format("%sAdd Undelivered Error: %s%s", ColorUtilConstants.RED,
                  err.getCause().getMessage(), ColorUtilConstants.RESET));
                conn.close().subscribeAsCompletionStage().isDone();
            }).subscribeAsCompletionStage().isDone();
            return Uni.createFrom().item(null);
        }).subscribeAsCompletionStage().isDone();

        return promise;
    }

    public Promise<Void> addUndelivered(List<String> undelivered, Long messageId) {
        Promise<Void> promise = Promise.promise();

        for (String name : undelivered) {
            Promise<Long> future = getUserIdByName(name);
            future.future().subscribeAsCompletionStage().thenCompose(userId -> {
                addUndelivered(userId, messageId).future().invoke(() -> {
                    promise.tryComplete();
                }).onFailure().invoke(Throwable::printStackTrace).subscribeAsCompletionStage().isDone();
                return null;
            });
        }

        return promise;
    }

    public Promise<Long> getUserIdByName(String name) {
        Promise<Long> promise = Promise.promise();

        pool.getConnection().call(conn -> {
            conn.preparedQuery(getUserByName()).execute(Tuple.of(name)).call(rows -> {
                Long id = 0L;
                for (Row row : rows) {
                    id = row.getLong(0);
                }
                promise.complete(id);
                conn.close().subscribeAsCompletionStage().isDone();
                return Uni.createFrom().item(id);
            }).onFailure().invoke(err -> {
                logger.error(String.format("%sError finding user by name: %s - %s%s", ColorUtilConstants.RED, name,
                  err.getCause().getMessage(), ColorUtilConstants.RESET));
                conn.close().subscribeAsCompletionStage().isDone();
            }).subscribeAsCompletionStage().isDone();
            return Uni.createFrom().item(conn);
        }).subscribeAsCompletionStage().isDone();
        return promise;
    }

    public abstract MessageUser createMessageUser();

    public Promise<MessageUser> selectUser(MessageUser messageUser) {
        MessageUser resultUser = createMessageUser();
        Promise<MessageUser> promise = Promise.promise();
        Tuple parameters = Tuple.of(messageUser.getName(), URLDecoder.decode(messageUser.getPassword(), StandardCharsets.UTF_8));

        pool.getConnection().onItem().ifNotNull().invoke(conn -> {
            UniSubscribe<RowSet<Row>> data = conn.preparedQuery(getUserById()).execute(parameters).onFailure()
              .invoke(error -> {
                  logger.error("{}Error getting user-database connection: {}{}", ColorUtilConstants.RED, error,
                    ColorUtilConstants.RESET);
              }).subscribe();

            data.asCompletionStage().thenComposeAsync(rows -> {
                if (rows.size() == 0) {
                    Promise<MessageUser> newUserPromise = addUser(messageUser);
                    MessageUser addedUser = newUserPromise.futureAndAwait();
                    promise.complete(addedUser);
                    conn.closeAndForget();
                } else {
                    for (Row row : rows) {
                        resultUser.setId(row.getLong(0));
                        resultUser.setName(row.getString(1));
                        resultUser.setPassword(row.getString(2));
                        resultUser.setIp(row.getString(3));
                        Timestamp ts = null;
                        if (DbConfiguration.isUsingPostgres()) {
                            ts = Timestamp.valueOf(
                              LocalDateTime.ofInstant(row.getOffsetDateTime(4).toInstant(), ZoneOffset.UTC));
                        } else if (row.getValue(4) instanceof LocalDateTime) {
                            ts = Timestamp.valueOf(row.getLocalDateTime(4));
                        }
                        resultUser.setLastLogin(ts != null ? ts : row.getValue(4));
                        Promise<Integer> updatePromise = updateUser(resultUser);
                        updatePromise.futureAndAwait();
                        promise.complete(resultUser);
                        conn.closeAndForget();
                    }
                }
                return CompletableFuture.completedFuture(rows);
            });
        }).onFailure().invoke(error -> {
            logger.error("{}Error Retriving/Adding User: {}{}", ColorUtilConstants.RED, error,
              ColorUtilConstants.RESET);
        }).subscribeAsCompletionStage().isDone();

        return promise;
    }

    public Promise<StringBuilder> buildUsersJson(MessageUser messageUser) {
        Promise<StringBuilder> promise = Promise.promise();

        pool.getConnection().invoke(conn -> {
            conn.preparedQuery(getAllUsers()).execute(Tuple.of(messageUser.getName())).onFailure().invoke(error -> {
                logger.error("{}Error executing getAllUsers: {}{}", ColorUtilConstants.RED, error,
                  ColorUtilConstants.RESET);
            }).subscribe().asCompletionStage().thenComposeAsync(rows -> {
                JsonArray jsonArray = new JsonArray();

                for (Row row : rows) {
                    jsonArray.add(new JsonObject().put("name", row.getString(1)));
                }
                promise.complete(new StringBuilder(jsonArray.toString()));
                conn.close().onFailure().invoke(err -> {
                    logger.error(String.format("%sError building user json: %s%s", ColorUtilConstants.RED,
                      err.getCause().getMessage(), ColorUtilConstants.RESET));
                    err.printStackTrace();
                }).subscribeAsCompletionStage().isDone();
                return null;
            });
        }).onFailure().invoke(err -> {
            logger.error(String.format("%sError with database connection: %s%s", ColorUtilConstants.RED,
              err.getCause().getMessage(), ColorUtilConstants.RESET));
            err.printStackTrace();
        }).subscribeAsCompletionStage().isDone();

        return promise;
    }

    public Promise<Map<String, Integer>> processUserMessages(WebSocketConnection ws, MessageUser messageUser) {
        RemoveUndelivered removeUndelivered = new RemoveUndelivered();
        RemoveMessage removeMessage = new RemoveMessage();
        CompletePromise completePromise = new CompletePromise();
        Promise<Void> promise = Promise.promise();
        removeUndelivered.setUserId(messageUser.getId());

        /*
         * Get all undelivered messages for current user
         */
        completePromise.setPromise(promise);

        Tuple parameters = Tuple.of(messageUser.getId());

        SqlClientHelper.inTransactionUni(pool, tx -> {
            removeUndelivered.setSqlConnection(tx);
            removeMessage.setSqlConnection(tx);

            tx.preparedQuery(getUserUndelivered()).execute(parameters).call(rows -> {
                for (Row row : rows) {
                    DateFormat formatDate = DateFormat.getDateInstance(DateFormat.DEFAULT,
                      java.util.Locale.getDefault());

                    String message = row.getString(2);
                    String handle = row.getString(4);
                    messageUser.setLastLogin(row.getValue(3));

                    // Send messages back to client
                    ws.sendText(handle + formatDate.format(messageUser.getLastLogin()) + " " + message)
                      .subscribe().asCompletionStage();
                    removeUndelivered.getMessageIds().add(row.getLong(1));
                    removeMessage.getMessageIds().add(row.getLong(1));
                }
                return Uni.createFrom().item(tx);
            }).onFailure().call(err -> {
                err.printStackTrace();
                tx.close().subscribeAsCompletionStage().isDone();
                return null;
            }).eventually(removeUndelivered).call(rows -> {
                Promise<Void> undeliveredPromise = Promise.promise();
                removeMessage.setPromise(undeliveredPromise);
                return Uni.createFrom().item(rows);
            }).eventually(removeMessage).subscribeAsCompletionStage().isDone();
            return Uni.createFrom().item(tx);
        }).onFailure().call(err -> {
            err.printStackTrace();
            return null;
        }).subscribeAsCompletionStage().isDone();

        return removeUndelivered.getReturnPromise();
    }

    class CompletePromise implements Runnable {
        Promise<Void> promise;

        @Override
        public void run() {
            if (promise != null) {
                promise.tryComplete();
            }
        }

        public Promise<Void> getPromise() {
            return promise;
        }

        public void setPromise(Promise<Void> promise) {
            this.promise = promise;
        }
    }

    class RemoveUndelivered implements Runnable {
        List<Long> messageIds = new ArrayList<>();
        CompletePromise completePromise = new CompletePromise();
        Long userId;
        int count;
        Promise<Void> promise;
        Promise<Map<String, Integer>> returnPromise = Promise.promise();
        Map<String, Integer> counts = new ConcurrentHashMap<>();
        SqlClient client;

        @Override
        public void run() {
            completePromise.setPromise(promise);

            for (Long messageId : messageIds) {
                Tuple parameters = Tuple.of(userId, messageId);
                client.preparedQuery(getRemoveUndelivered()).execute(parameters).invoke(rows -> {
                    for (Row row : rows) {
                        logger.info(row.toJson().toString());
                    }
                    count += rows.rowCount() == 0 ? 1 : rows.rowCount();

                    if (messageIds.size() == count) {
                        try {
                            completePromise.run();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        counts.put("messages", count);
                        returnPromise.complete(counts);
                    }
                }).onFailure().invoke(err -> {
                    client.close().subscribeAsCompletionStage().isDone();
                    logger.error("Deleting Undelivered: {}", err.getCause().getMessage());
                }).subscribeAsCompletionStage().isDone();
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

        public Promise<Map<String, Integer>> getReturnPromise() {
            return returnPromise;
        }

        public void setReturnPromise(Promise<Map<String, Integer>> returnPromise) {
            this.returnPromise = returnPromise;
        }

        public void setSqlConnection(SqlClient client) {
            this.client = client;
        }
    }

    class RemoveMessage implements Runnable {
        int count;
        List<Long> messageIds = new ArrayList<>();
        CompletePromise completePromise = new CompletePromise();
        Promise<Void> promise;
        SqlClient client;

        @Override
        public void run() {
            completePromise.setPromise(promise);

            for (Long messageId : messageIds) {
                if (DbConfiguration.isUsingSqlite3()) {
                    try { // Sqlite3 needs a delay???
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        logger.error(String.join("", "Await: ", e.getMessage()));
                    }
                }

                Tuple parameters = Tuple.of(messageId, messageId);
                String sql;
                if (DbConfiguration.isUsingIbmDB2() || DbConfiguration.isUsingMariadb()
                  || DbConfiguration.isUsingSqlite3() || DbConfiguration.isUsingH2()) {
                    sql = getCustomDeleteMessages();
                } else {
                    parameters = Tuple.of(messageId);
                    sql = getRemoveMessage();
                }

                client.preparedQuery(sql).execute(parameters).invoke(rows -> {
                    count += rows.rowCount() == 0 ? 1 : rows.rowCount();

                    if (messageIds.size() == count) {
                        client.close().subscribeAsCompletionStage().isDone();
                    }
                }).onFailure().invoke(err -> {
                    logger.error(String.format("%sDeleting Messages: %s%s", ColorUtilConstants.RED, err,
                      ColorUtilConstants.RESET));
                }).eventually(completePromise).onFailure().invoke(err -> {
                    err.printStackTrace();
                }).subscribeAsCompletionStage().isDone();
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

        public void setSqlConnection(SqlClient client) {
            this.client = client;
        }
    }

    public void setIsTimestamp(Boolean isTimestamp) {
        this.isTimestamp = isTimestamp;
    }

    public boolean getisTimestamp() {
        return this.isTimestamp;
    }

    public static DSLContext getCreate() {
        return create;
    }
}
