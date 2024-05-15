package dmo.fs.db.router.wsnext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dmo.fs.db.wsnext.admin.CleanOrphanedUsers;
import dmo.fs.db.wsnext.DodexDatabase;
import dmo.fs.db.MessageUser;
import dmo.fs.kafka.KafkaEmitterDodex;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.quarkus.runtime.configuration.ProfileManager;
import io.quarkus.websockets.next.WebSocketConnection;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Context;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.reactivex.jdbcclient.JDBCPool;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public abstract class DodexRouterBase {
    protected static final Logger logger = LoggerFactory.getLogger(DodexRouterBase.class.getSimpleName());
    protected final boolean isProduction = !ProfileManager.getLaunchMode().isDevOrTest();
    protected boolean isSetupDone;
    protected boolean isInitialized;
    protected DodexDatabase dodexDatabase;
    protected Promise<Pool> dbPromise;
    protected io.vertx.core.Promise<JDBCPool> dbPromiseReactive;
    protected final Promise<Pool> cleanupPromise = Promise.promise();
    protected Map<String, WebSocketConnection> sessions = new ConcurrentHashMap<>();
    protected Map<String, Map<String, String>> sessionsNext = new ConcurrentHashMap<>();
    protected String remoteAddress;
    protected final KafkaEmitterDodex ke = DodexRouter.getKafkaEmitterDodex();
    protected Map<String, String> queryParams;

    @Inject
    Vertx vertx;
    @Inject
    WebSocketConnection connection;

    protected DodexRouterBase() {
    }

    protected long broadcast(WebSocketConnection connection, String message, Map<String, String> queryParams) {
        long c = connection.getOpenConnections().stream().filter(session -> {
            if(connection.id().equals(session.id())) {
                return false;
            }
            CompletableFuture<Void> complete = session.sendText(message).subscribe().asCompletionStage();
            if(complete.isCompletedExceptionally()) {
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

    protected void doConnection(WebSocketConnection session) {
        final MessageUser messageUser = setMessageUser(session);
        WebSocketConnection ws = getThisWebSocket(session);

        dbPromise.future().subscribeAsCompletionStage().thenComposeAsync(pool -> {
            if (!isSetupDone) {
                dodexDatabase.setupSql(pool);
                isSetupDone = true;
                cleanupPromise.complete(pool);
            }
            try {
                Promise<MessageUser> promise = dodexDatabase.selectUser(messageUser);

                promise.future().subscribeAsCompletionStage().thenComposeAsync(resultUser -> {
                    try {
                        Promise<StringBuilder> userJson = dodexDatabase.buildUsersJson(resultUser);
                        /*
                         * Send list of registered users with connected notification
                         */
                        userJson.future().invoke(json -> {
                            ws.sendTextAndAwait("connected:" + json); // Users for protected messages
                        }).subscribeAsCompletionStage().isDone();
                        /*
                         * Send undelivered messages and remove user related messages.
                         */
                        dodexDatabase.processUserMessages(ws, resultUser).future().invoke(map -> {
                            int messageCount = map.get("messages");
                            if (messageCount > 0) {
                                logger.info(String.format("%sMessages Delivered: %d to %s%s",
                                        ColorUtilConstants.BLUE_BOLD_BRIGHT, messageCount, resultUser.getName(),
                                        ColorUtilConstants.RESET));
                                if(ke != null) {
                                    ke.setValue("delivered", messageCount);
                                }
                            }
                        }).subscribeAsCompletionStage().isDone();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null;
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    protected void doMessage(WebSocketConnection session, String message) {
        WebSocketConnection ws = getThisWebSocket(session);
        final MessageUser messageUser = setMessageUser(ws);
        final ArrayList<String> onlineUsers = new ArrayList<>();
        // Checking if message or command
        final Map<String, String> returnObject = DodexUtil.commandMessage(message);
        final String selectedUsers = returnObject.get("selectedUsers");
        // message with command stripped out
        final String computedMessage = returnObject.get("message");
        final String command = returnObject.get("command");

        if (";removeuser".equals(command)) {
            try {
                dodexDatabase.deleteUser(messageUser);
            } catch (InterruptedException | SQLException e) {
                e.printStackTrace();
                session.sendText("Your Previous handle did not delete: " + e.getMessage()).subscribe().asCompletionStage();
            }
        }

        sessions = session.getOpenConnections().stream().collect(Collectors.toConcurrentMap(WebSocketConnection::id, v -> v));
        if (!computedMessage.isEmpty()) {
            // broadcast
            if ("".equals(selectedUsers) && "".equals(command)) {
                long count = broadcast(session,messageUser.getName() + ": " + computedMessage, queryParams);
                String handles = "handle";
                handles = count == 1 ? handles : handles + "s";

                ws.sendText(String.format("%d %s received your broadcast", count, handles)).subscribe().asCompletionStage();

                if (ke != null) {
                    ke.setValue(1);
                }
            }

            sessions.values().stream().filter(s -> !s.id().equals(getThisWebSocket(session).id()) /*&& getThisWebSocket(session).isOpen()*/)
                .forEach(s -> {
                    final String handle = sessionsNext.get(s.id()).get("handle");
                    // private message
                    if (Arrays.stream(selectedUsers.split(",")).anyMatch(h -> h.contains(handle))) {
                        CompletableFuture<Void> complete = s.sendText(messageUser.getName() + ": " + computedMessage)
                            .subscribe().asCompletionStage();
                            if(complete.isCompletedExceptionally()) {
                                if (logger.isInfoEnabled()) {
                                    logger.info(
                                        String.format("%sWebsocket-connection...Unable to send message: %s%s%s%s",
                                            ColorUtilConstants.BLUE_BOLD_BRIGHT,
                                                sessionsNext.get(s.id()).get("handle"), ": ",
                                                "", ColorUtilConstants.RESET));
                                }
                            }
                        // keep track of delivered messages
                        onlineUsers.add(handle);
                    }
                });

            if ("".equals(selectedUsers) && !"".equals(command)) {
                session.sendText("Private user not selected").subscribe().asCompletionStage();
            } else {
                if(!"".equals(command)) {
                    session.sendText("ok").subscribe().asCompletionStage();
                }
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
                    .toList();
            // Save protected message to send when to-user logs in
            if (!disconnectedUsers.isEmpty()) {
                Promise<Long> futureId;
                try {
                    futureId = dodexDatabase.addMessage(messageUser, computedMessage);
                    futureId.future().onFailure().invoke(Throwable::printStackTrace)
                            .subscribeAsCompletionStage().thenComposeAsync(id -> {
                        try {
                            dodexDatabase.addUndelivered(disconnectedUsers, id);
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

    protected MessageUser setMessageUser(WebSocketConnection session) {
        final MessageUser messageUser = dodexDatabase.createMessageUser();
        if(session == null) {
            return messageUser;
        }
        final Map<String, String> queryParams = sessionsNext.get(session.id());
        String handle = "";
        String id = "";

        handle = queryParams.get("handle");
        id = queryParams.get("id");

        messageUser.setName(handle);
        messageUser.setPassword(id);
        messageUser.setIp(remoteAddress == null ? "Unknown" : remoteAddress);

        return messageUser;
    }

    protected void setup() throws InterruptedException, IOException, SQLException {
//        dodexDatabase = DbConfiguration.getDefaultDb();
//        dbPromise = dodexDatabase.databaseSetup();

        /*
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
                JsonObject config = jsonObject.orElseGet(JsonObject::new);
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

    public DodexDatabase getDodexDatabase() {
        return dodexDatabase;
    }

}
