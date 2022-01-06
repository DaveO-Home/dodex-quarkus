package dmo.fs.router;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.admin.CleanOrphanedUsers;
import dmo.fs.db.DbConfiguration;
import dmo.fs.db.DodexDatabase;
import dmo.fs.db.MessageUser;
import dmo.fs.kafka.KafkaEmitterDodex;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.quarkus.runtime.configuration.ProfileManager;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Context;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.reactivex.jdbcclient.JDBCPool;

public abstract class DodexRouterBase {
    private static final Logger logger = LoggerFactory.getLogger(DodexRouterBase.class.getName());
    protected final boolean isProduction = !ProfileManager.getLaunchMode().isDevOrTest();
    protected boolean isReactive;
    protected boolean isSetupDone;
    protected boolean isInitialized;
    protected DodexDatabase dodexDatabase;
    protected Promise<Pool> dbPromise;
    protected io.vertx.core.Promise<JDBCPool> dbPromiseReactive;
    protected final Promise<Pool> cleanupPromise = Promise.promise();
    protected Map<String, Session> sessions = new ConcurrentHashMap<>();
    protected String remoteAddress;
    private final KafkaEmitterDodex ke = DodexRouter.getKafkaEmitterDodex();

    @Inject
    Vertx vertx;

    protected DodexRouterBase() {
    }

    @OnOpen
    public abstract void onOpen(Session session) throws InterruptedException, IOException, SQLException;

    @OnClose
    public abstract void onClose(Session session);

    @OnError
    public abstract void onError(Session session, Throwable throwable);

    protected void broadcast(Session session, String message) {
        sessions.values().stream().filter(s -> !s.getId().equals(session.getId())).forEach(s -> {
            s.getAsyncRemote().sendObject(message, result -> {
                if (result.getException() != null) {
                    logger.info(String.format("%sUnable to send message: %s%s%s%s", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                            s.getRequestParameterMap().get("handle").get(0), ": ", result.getException().getMessage(),
                            ColorUtilConstants.RESET));
                }
            });
        });
    }

    protected void doConnection(Session session) {
        final MessageUser messageUser = setMessageUser(session);

        dbPromise.future().subscribeAsCompletionStage().thenComposeAsync(pool -> {
            if (!isSetupDone) {
                dodexDatabase.setupSql(pool);
                isSetupDone = true;
                cleanupPromise.complete(pool);
            }
            try {
                Promise<MessageUser> promise = dodexDatabase.selectUser(messageUser, session);

                promise.future().subscribeAsCompletionStage().thenComposeAsync(resultUser -> {
                    try {
                        Promise<StringBuilder> userJson = dodexDatabase.buildUsersJson(resultUser);
                        /**
                         * Send list of registered users with connected notification
                         */
                        userJson.future().invoke(json -> {
                            session.getAsyncRemote().sendObject("connected:" + json); // Users for private messages
                        }).subscribeAsCompletionStage();
                        /*
                         * Send undelivered messages and remove user related messages.
                         */
                        dodexDatabase.processUserMessages(session, resultUser).future().invoke(map -> {
                            int messageCount = map.get("messages");
                            if (messageCount > 0) {
                                logger.info(String.format("%sMessages Delivered: %d to %s%s",
                                        ColorUtilConstants.BLUE_BOLD_BRIGHT, messageCount, resultUser.getName(),
                                        ColorUtilConstants.RESET));
                                if(ke != null) {
                                    ke.setValue("delivered", messageCount);
                                }
                            }
                        }).subscribeAsCompletionStage();
                    } catch (InterruptedException | SQLException e) {
                        e.printStackTrace();
                    }
                    return null;
                });
            } catch (InterruptedException | SQLException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    protected void doMessage(Session session, String message) {
        final MessageUser messageUser = setMessageUser(session);
        final ArrayList<String> onlineUsers = new ArrayList<>();
        // Checking if message or command
        final Map<String, String> returnObject = DodexUtil.commandMessage(message);
        final String selectedUsers = returnObject.get("selectedUsers");
        // message with command stripped out
        final String computedMessage = returnObject.get("message");
        final String command = returnObject.get("command");

        if (!"".equals(command) && ";removeuser".equals(command)) {
            try {
                dodexDatabase.deleteUser(session, messageUser);
            } catch (InterruptedException | SQLException e) {
                e.printStackTrace();
                session.getAsyncRemote().sendObject("Your Previous handle did not delete: " + e.getMessage());
            }
        }
        if (computedMessage.length() > 0) {
            sessions.values().stream().filter(s -> !s.getId().equals(session.getId()) && session.isOpen())
                .forEach(s -> {
                    final String handle = s.getRequestParameterMap().get("handle").get(0);
                    // broadcast
                    if ("".equals(selectedUsers) && "".equals(command)) {
                        s.getAsyncRemote().sendObject(messageUser.getName() + ": " + computedMessage);
                        if (ke != null) {
                            ke.setValue(1);
                        }
                    // private message
                    } else if (Arrays.stream(selectedUsers.split(",")).anyMatch(h -> {
                        boolean isMatched = false;
                        if (!isMatched) {
                            isMatched = h.contains(handle);
                        }
                        return isMatched;
                    })) {
                        s.getAsyncRemote().sendObject(messageUser.getName() + ": " + computedMessage, result -> {
                            if (result.getException() != null && logger.isInfoEnabled()) {
                                logger.info(
                                        String.format("%Websocket-connection...Unable to send message: %s%s%s%s",
                                                ColorUtilConstants.BLUE_BOLD_BRIGHT,
                                                s.getRequestParameterMap().get("handle").get(0), ": ",
                                                result.getException(), ColorUtilConstants.RESET));
                            }
                        });
                        // keep track of delivered messages
                        onlineUsers.add(handle);
                    }
                });

            if ("".equals(selectedUsers) && !"".equals(command)) {
                session.getAsyncRemote().sendObject("Private user not selected");
            } else {
                session.getAsyncRemote().sendObject("ok");
                if(!onlineUsers.isEmpty()) {
                    if(ke != null) {
                        ke.setValue("private", onlineUsers.size());
                    }
                }
            }
        }

        // calculate difference between selected and online users
        if (!"".equals(selectedUsers)) {
            final List<String> selected = Arrays.asList(selectedUsers.split(","));
            final List<String> disconnectedUsers = selected.stream().filter(user -> !onlineUsers.contains(user))
                    .collect(Collectors.toList());
            // Save private message to send when to-user logs in
            if (!disconnectedUsers.isEmpty()) {
                Promise<Long> futureId = null;
                try {
                    futureId = dodexDatabase.addMessage(session, messageUser, computedMessage);
                    futureId.future().onFailure().invoke(err -> {
                        err.printStackTrace();
                    }).subscribeAsCompletionStage().thenComposeAsync(id -> {
                        try {
                            dodexDatabase.addUndelivered(session, disconnectedUsers, id);
                            if(ke != null) {
                                ke.setValue("undelivered", disconnectedUsers.size());
                            }
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }

                        return null;
                    });
                } catch (InterruptedException | SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    protected MessageUser setMessageUser(Session session) {
        final MessageUser messageUser = dodexDatabase.createMessageUser();
        final Session sess = sessions.get(session.getId());
        String handle = "";
        String id = "";

        handle = sess.getRequestParameterMap().get("handle").get(0);
        id = sess.getRequestParameterMap().get("id").get(0);

        messageUser.setName(handle);
        messageUser.setPassword(id);
        messageUser.setIp(remoteAddress == null ? "Unknown" : remoteAddress);

        return messageUser;
    }

    protected void setup() throws InterruptedException, IOException, SQLException {
        dodexDatabase = DbConfiguration.getDefaultDb();
        dbPromise = dodexDatabase.databaseSetup();

        /**
         * Optional auto user cleanup - config in "application-conf.json". When client
         * changes handle when server is down, old users and undelivered messages will
         * be orphaned.
         * 
         * Defaults: off - when turned on 1. execute on start up and every 7 days
         * thereafter. 2. remove users who have not logged in for 90 days.
         */

        final Optional<Context> context = Optional.ofNullable(vertx.getOrCreateContext());
        if (context.isPresent()) {
            final Optional<JsonObject> jsonObject = Optional.ofNullable(vertx.getOrCreateContext().config());
            try {
                JsonObject config = jsonObject.isPresent() ? jsonObject.get() : new JsonObject();
                if (config.isEmpty()) {
                    ObjectMapper jsonMapper = new ObjectMapper();
                    JsonNode node;

                    try (InputStream in = getClass().getResourceAsStream("/application-conf.json")) {
                        node = jsonMapper.readTree(in);
                    }
                    config = JsonObject.mapFrom(node);
                }
                final Optional<Boolean> runClean = Optional.ofNullable(config.getBoolean("clean.run"));
                if (runClean.isPresent() && runClean.get().equals(true)) {
                    final CleanOrphanedUsers clean = new CleanOrphanedUsers();
                    clean.setDatabase(dodexDatabase);
                    clean.setPromise(cleanupPromise);
                    clean.startClean(config);
                }
            } catch (final Exception exception) {
                logger.info(String.format("%sContext Configuration failed...%s%s", ColorUtilConstants.RED_BOLD_BRIGHT,
                        exception.getMessage(), ColorUtilConstants.RESET));
                exception.printStackTrace();
            }
        }

        String defaultDb = new DodexUtil().getDefaultDb();
        String startupMessage = "In Production with database: " + defaultDb;

        startupMessage = "dev".equals(DodexUtil.getEnv()) ? "In Development with database: " + defaultDb
                : startupMessage;
        logger.info(String.format("%sStarting Web Socket...%s%s", ColorUtilConstants.BLUE_BOLD_BRIGHT, startupMessage,
                ColorUtilConstants.RESET));
    }

    public boolean isReactive() {
        return isReactive;
    }

    public void setReactive(boolean isReactive) {
        this.isReactive = isReactive;
    }
}
