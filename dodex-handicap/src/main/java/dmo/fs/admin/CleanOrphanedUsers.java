package dmo.fs.admin;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.db.wsnext.DbDefinitionBase;
import dmo.fs.db.wsnext.DodexDatabase;
import dmo.fs.db.MessageUser;
import dmo.fs.utils.ColorUtilConstants;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple5;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.SqlClient;
import io.vertx.mutiny.sqlclient.SqlClientHelper;
import io.vertx.mutiny.sqlclient.Tuple;

/**
 * Optional auto user cleanup - config in "application-conf.json". When client
 * changes handle when server is down, old users and undelivered messages will
 * be orphaned.
 * 
 * Defaults: off - when turned on 1. execute on start up and every 7 days
 * thereafter. 2. remove users who have not logged in for 90 days.
 */
public class CleanOrphanedUsers extends DbDefinitionBase {
    protected static Logger logger = LoggerFactory.getLogger(CleanOrphanedUsers.class.getName());

    protected static Integer age;
    protected static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    protected Promise<Pool> promise;
    protected SqlClient client;
    
    public void startClean(JsonObject config) {

        long delay = config.getLong("clean.delay");
        long period = config.getLong("clean.period");
        age = config.getInteger("clean.age");

        Multi.createFrom().ticks().onExecutor(scheduler)
                .startingAfter(java.time.Duration.ofMillis(delay))
                .every(java.time.Duration.ofDays(period))
                .onItem().invoke(clean)
                .onFailure().invoke(err -> err.printStackTrace()).subscribe().with(l -> logger.getName());
    }

    protected final Runnable clean = new Runnable() {
        @Override
        public void run() {
            runClean();
        }

        protected void runClean() {
            promise.future().onItem().call(pool -> {
                Promise<List<Tuple5<Integer, String, String, String, Object>>> users = null;
                try {
                    users = getUsers(pool);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                users.future().call(list -> {
                    List<Integer> possibleUsers = getPossibleOrphanedUsers(list);

                    cleanUsers(client, possibleUsers);

                    return Uni.createFrom().item(list);
                }).subscribeAsCompletionStage();
                return null;
            }).subscribeAsCompletionStage();
        }

        protected Promise<List<Tuple5<Integer, String, String, String, Object>>> getUsers(Pool pool) throws SQLException {
            List<Tuple5<Integer, String, String, String, Object>> listOfUsers = new ArrayList<>();
            Promise<List<Tuple5<Integer, String, String, String, Object>>> usersPromise = Promise.promise();
            GotUsers gotUsers = new GotUsers();

            gotUsers.setPromise(usersPromise);
            gotUsers.setListOfUsers(listOfUsers);
            SqlClientHelper.inTransactionUni(pool, tx -> {
                client = tx;
                Tuple parameters = Tuple.of("DUMMY");
                tx.preparedQuery(getAllUsers()).execute(parameters).onItem().call(rows -> {
                    for (Row row : rows) {
                        Uni.combine().all()
                                .unis(Uni.createFrom().item(row.getInteger(0)),
                                        Uni.createFrom().item(row.getString(1)),
                                        Uni.createFrom().item(row.getString(2)),
                                        Uni.createFrom().item(row.getString(3)),
                                        Uni.createFrom().item(row.getValue(4)))
                                .asTuple().subscribeAsCompletionStage().thenComposeAsync(tuple5 -> {
                                    listOfUsers.add(tuple5);
                                    return null;
                                });
                    }
                    return null;
                }).subscribeAsCompletionStage();
                return Uni.createFrom().item(tx);
            }).invoke(gotUsers).subscribeAsCompletionStage();

            return gotUsers.getPromise();
        }

        protected List<Integer> getPossibleOrphanedUsers(List<Tuple5<Integer, String, String, String, Object>> users) {
            List<Integer> orphaned = new ArrayList<>();

            users.iterator().forEachRemaining(user -> {
                Long days = getLastLogin(user.getItem5());
                if (days >= age) {
                    orphaned.add(user.getItem1());
                }
            });

            return orphaned;
        }

        protected Long getLastLogin(Object lastLogin) {
            Long diffInDays = 0l;
            try {
                Long currentDate = new Date().getTime();
                Long loginDate;

                if (lastLogin instanceof OffsetDateTime) {
                    loginDate = ((OffsetDateTime) lastLogin).toInstant().toEpochMilli();
                    currentDate = OffsetDateTime.now().toInstant().toEpochMilli();
                } else if (lastLogin instanceof Date) {
                    loginDate = ((Date) lastLogin).getTime();
                } else {
                    loginDate = ((Timestamp) lastLogin).getTime();
                }

                long diff = currentDate - loginDate;
                diffInDays = diff / (1000 * 60 * 60 * 24);
                return diffInDays;
            } catch (Exception e) {
                logger.error(e.getCause().getMessage());
            }
            return diffInDays;
        }

        Object value;

        protected void cleanUsers(SqlClient client, List<Integer> users) {
            List<Integer> messageIds = new ArrayList<>();

            users.iterator().forEachRemaining(userId -> {
                CleanObjects cleanObjects = new CleanObjects();
                Future.future(prom -> {
                    cleanObjects.setPromise(prom);
                    Tuple parameters = Tuple.of(userId);
                    client.preparedQuery(getUserUndelivered()).execute(parameters).onItem().call(rows -> {
                        for (Row row : rows) {
                            Integer messageId = row.getInteger(1);
                            messageIds.add(messageId);
                        }
                        return Uni.createFrom().item(rows);
                    }).invoke(cleanObjects).onFailure().invoke(err -> {
                        logger.error(String.format("%s%s%s%s", ColorUtilConstants.RED_BOLD_BRIGHT,
                                "Error cleaning user list: ", err.getMessage(), ColorUtilConstants.RESET));
                        err.printStackTrace();
                    }).subscribeAsCompletionStage();

                    prom.future().onSuccess(result -> {
                        cleanUndelivered(client, userId, messageIds, users);
                        value = result;
                    });

                });

            });
            if (value == null) {
                cleanRemainingUsers(client, users);
                client.close();
                logger.info("{}{}{}", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                                        "Scheduled message cleanup finished.",
                                        ColorUtilConstants.RESET);
            }
        }

        protected int cleanUndelivered(SqlClient client, Integer userId, List<Integer> messageIds, List<Integer> users) {
            int count[] = { 0 };
            messageIds.iterator().forEachRemaining(messageId -> {
                CleanObjects cleanObjects = new CleanObjects();

                Future.future(prom -> {
                    cleanObjects.setPromise(prom);
                    client.preparedQuery(getRemoveUndelivered()).execute(Tuple.of(userId, messageId)).onItem()
                            .call(rows -> {
                                count[0] = rows.rowCount();
                                cleanObjects.setCount(rows.rowCount());
                                return Uni.createFrom().item(rows);
                            }).invoke(cleanObjects).invoke(rows -> {
                            }).onFailure().invoke(err -> {
                                logger.error(String.join("", ColorUtilConstants.RED_BOLD_BRIGHT,
                                        "Error removing undelivered record: ", err.getMessage(),
                                        ColorUtilConstants.RESET));
                            }).subscribeAsCompletionStage();

                    prom.future().onSuccess(result -> {
                        if (result != null) {
                            count[0] += (Integer) result;
                        }
                        logger.warn("Clean Messages: {}--{}", messageIds, users);
                        cleanMessage(client, messageIds, users);
                    });
                });
            });
            return count[0];
        }

        protected int cleanMessage(SqlClient client, List<Integer> messageIds, List<Integer> users) {
            int count[] = { 0 };
            int numOfIds = messageIds.size();
            messageIds.iterator().forEachRemaining(messageId -> {
                CleanObjects cleanObjects = new CleanObjects();

                Future.future(prom -> {
                    cleanObjects.setPromise(prom);
                    client.preparedQuery(getRemoveMessage()).execute(Tuple.of(messageId)).onItem().call(rows -> {
                        count[0] = count[0] += rows.rowCount();
                        if (numOfIds == count[0]) {
                            cleanObjects.setCount(count[0]);
                            cleanObjects.run();
                            cleanRemainingUsers(client, users);
                        }
                        return Uni.createFrom().item(count[0]);
                    }).onFailure().invoke(err -> {
                        logger.error(String.join("", ColorUtilConstants.RED_BOLD_BRIGHT, " Error removing message:",
                                err.getMessage(), ColorUtilConstants.RESET));
                    }).subscribeAsCompletionStage();
                });
            });
            return count[0];
        }

        protected Future<Object> cleanUser(SqlClient client, Integer userId) {
            CleanObjects cleanObjects = new CleanObjects();

            return Future.future(prom -> {
                cleanObjects.setPromise(prom);
                client.preparedQuery(getRemoveUsers()).execute(Tuple.of(userId)).onItem().call(rows -> {
                    cleanObjects.setCount(rows.rowCount());
                    return Uni.createFrom().item(rows);
                }).invoke(cleanObjects).onFailure().invoke(err -> {
                    logger.error(String.join("", ColorUtilConstants.RED_BOLD_BRIGHT, ":Error deleting user: ",
                            userId.toString(), " : ", err.getMessage(), ColorUtilConstants.RESET));
                }).subscribeAsCompletionStage();
            });
        }

        protected int cleanRemainingUsers(SqlClient client, List<Integer> users) {
            int count[] = { 0 };
            users.iterator().forEachRemaining(userId -> {
                cleanUser(client, userId).onSuccess(result -> {
                    if (result != null) {
                        count[0] += (Integer) result;
                    }
                });
            });

            return count[0];
        }

    };

    class GotUsers implements Runnable {
        List<Tuple5<Integer, String, String, String, Object>> listOfUsers;
        Promise<List<Tuple5<Integer, String, String, String, Object>>> promise;

        @Override
        public void run() {
            promise.complete(listOfUsers);
        }

        public void setPromise(Promise<List<Tuple5<Integer, String, String, String, Object>>> promise) {
            this.promise = promise;
        }

        public Promise<List<Tuple5<Integer, String, String, String, Object>>> getPromise() {
            return promise;
        }

        public void setListOfUsers(List<Tuple5<Integer, String, String, String, Object>> listOfUsers) {
            this.listOfUsers = listOfUsers;
        }
    }

    class CleanObjects implements Runnable {
        Object object;
        io.vertx.core.Promise<Object> promise;
        Integer count = 0;

        @Override
        public void run() {
            promise.complete(count);
        }

        public void setPromise(io.vertx.core.Promise<Object> prom) {
            this.promise = prom;
        }

        public io.vertx.core.Promise<Object> getPromise() {
            return promise;
        }

        public void setObject(Object object) {
            this.object = object;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public Integer getCount() {
            return count;
        }
    }

    @Override
    public MessageUser createMessageUser() {
        return null;
    }

    public void setDatabase(DodexDatabase dodexDatabase) {
        // implmented by DbDefinitionBase
    }

    public void setPromise(Promise<Pool> cleanupPromise) {
        this.promise = cleanupPromise;
    }
}
