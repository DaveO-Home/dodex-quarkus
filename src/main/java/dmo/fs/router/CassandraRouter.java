package dmo.fs.router;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;

import dmo.fs.quarkus.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.admin.CleanOrphanedUsers;
import dmo.fs.db.DbConfiguration;
import dmo.fs.db.DodexCassandra;
import dmo.fs.db.MessageUser;
import dmo.fs.kafka.KafkaEmitterDodex;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import dmo.fs.utils.ParseQueryUtilHelper;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Context;
import io.vertx.reactivex.core.Promise;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.shareddata.LocalMap;
import io.vertx.reactivex.core.shareddata.SharedData;

@IfBuildProperty(name = "DEFAULT_DB", stringValue = "cassandra")
@ApplicationScoped
public class CassandraRouter {
    private static final Logger logger = LoggerFactory.getLogger(CassandraRouter.class.getName());
    private DodexCassandra dodexCassandra;
    private final Vertx vertx = Server.vertx;
    private EventBus eb = vertx.eventBus();
    private static final String LOGFORMAT = "{}{}{}";
    private String remoteAddress;
    private final Promise<Void> databasePromise = Promise.promise();
    protected Map<String, Session> sessions;
    static final SharedData sd = Server.vertx.sharedData();
    static final LocalMap<String, String> wsChatSessions = sd.getLocalMap("ws.dodex.sessions");
    private final KafkaEmitterDodex ke = DodexRouter.getKafkaEmitterDodex();

    protected CassandraRouter() {
    }

    void onStart(@Observes StartupEvent event) throws InterruptedException, IOException, SQLException {
        setWebSocket(null);
        logger.info(String.format("%sCassandra Router Started%s", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                ColorUtilConstants.RESET));
    }

    public void setWebSocket(final HttpServer server) throws InterruptedException, IOException, SQLException {
        dodexCassandra = DbConfiguration.getDefaultDb();
        dodexCassandra.setVertx(Server.vertx);
        databasePromise.complete();
        /**
         * Optional auto user cleanup - config in "application-conf.json". When client
         * changes handle when server is down, old users and undelivered messages will
         * be orphaned.
         * 
         * Defaults: off - when turned on 1. execute on start up and every 7 days
         * thereafter. 2. remove users who have not logged in for 90 days.
         */
        final Optional<Context> context = Optional.ofNullable(Vertx.currentContext());
        if (context.isPresent()) {
            final Optional<JsonObject> jsonObject = Optional.ofNullable(Vertx.currentContext().config());
            try {
                JsonObject config = jsonObject.orElseGet(JsonObject::new);
                final Optional<Boolean> runClean = Optional.ofNullable(config.getBoolean("clean.run"));
                if (runClean.isPresent() && runClean.get()) {
                    final CleanOrphanedUsers clean = new CleanOrphanedUsers();
                    clean.startClean(config);
                }
            } catch (final Exception exception) {
                logger.error(LOGFORMAT, ColorUtilConstants.RED_BOLD_BRIGHT, "Context Configuration failed...",
                        ColorUtilConstants.RESET);
            }
        }

        String startupMessage = "In Production";

        startupMessage = "dev".equals(DodexUtil.getEnv()) ? "In Development" : startupMessage;
        logger.info(LOGFORMAT, ColorUtilConstants.BLUE_BOLD_BRIGHT, startupMessage, ColorUtilConstants.RESET);
    }

    public void setCassandraHandler(Session session) throws UnsupportedEncodingException {
        wsChatSessions.put(session.getId(),
                URLDecoder.decode(session.getRequestURI().toString(), StandardCharsets.UTF_8));
        final MessageUser messageUser = dodexCassandra.createMessageUser();

        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String data) {
                try {
                    String handle = URLDecoder.decode(
                            ParseQueryUtilHelper.getQueryMap(session.getQueryString()).get("handle"),
                            StandardCharsets.UTF_8.name());
                    logger.info(LOGFORMAT, ColorUtilConstants.BLUE_BOLD_BRIGHT, handle, ColorUtilConstants.RESET);
                } catch (final UnsupportedEncodingException e) {
                    logger.error(LOGFORMAT, ColorUtilConstants.RED_BOLD_BRIGHT, e.getMessage(),
                            ColorUtilConstants.RESET);
                }

                if (!"/dodex".equals(session.getRequestURI().getPath())) {
                    try {
                        session.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    final ArrayList<String> onlineUsers = new ArrayList<>();
                    final String message = data;
                    // Checking if message or command
                    final Map<String, String> returnObject = DodexUtil.commandMessage(message);
                    // message with command stripped out
                    String[] computedMessage = { "" };
                    String[] command = { "" };

                    computedMessage[0] = returnObject.get("message");
                    command[0] = returnObject.get("command");

                    Promise<JsonObject> promise = Promise.promise();
                    promise.complete(null);
                    Future<JsonObject> completed = null;

                    if (command[0].length() > 0 && ";removeuser".equals(command[0])) {
                        try {
                            completed = dodexCassandra.deleteUser(session, eb, messageUser);
                        } catch (InterruptedException | SQLException e) {
                            e.printStackTrace();
                            session.getAsyncRemote()
                                    .sendObject("Your Previous handle did not delete: " + e.getMessage());
                        }
                    } else {
                        completed = promise.future();
                    }
                    if (completed != null) {
                        completed.onSuccess(result -> {
                            String selectedUsers = "";
                            if (computedMessage[0].length() > 0) {
                                // private users to send message
                                selectedUsers = returnObject.get("selectedUsers");
                                final Set<String> websockets = sessions.keySet();
                                Map<String, String> query = null;

                                for (final String websocket : websockets) {
                                    final Session webSocket = sessions.get(websocket);
                                    if (webSocket.isOpen()) {
                                        if (!websocket.equals(session.getId())) {
                                            // broadcast message
                                            query = ParseQueryUtilHelper
                                                    .getQueryMap(wsChatSessions.get(webSocket.getId()));
                                            final String handle = query.get("handle");
                                            if (selectedUsers.length() == 0 && command[0].length() == 0) {
                                                webSocket.getAsyncRemote()
                                                        .sendObject(messageUser.getName() + ": " + computedMessage[0]);
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
                                                webSocket.getAsyncRemote()
                                                        .sendObject(messageUser.getName() + ": " + computedMessage[0]);
                                                // keep track of delivered messages
                                                onlineUsers.add(handle);
                                            }
                                        } else {
                                            if (selectedUsers.length() == 0 && command[0].length() > 0) {
                                                session.getAsyncRemote().sendObject("Private user not selected");
                                            } else {
                                                session.getAsyncRemote().sendObject("ok");
                                            }
                                        }
                                    }
                                }
                            }

                            // calculate difference between selected and online users
                            if (selectedUsers.length() > 0) {
                                final List<String> selected = Arrays.asList(selectedUsers.split(","));
                                final List<String> disconnectedUsers = selected.stream()
                                        .filter(user -> !onlineUsers.contains(user)).collect(Collectors.toList());
                                // Save private message to send when to-user logs in
                                if (!disconnectedUsers.isEmpty()) {
                                    Future<JsonObject> future = null;
                                    try {
                                        future = dodexCassandra.addMessage(session, messageUser, computedMessage[0],
                                                disconnectedUsers, eb);
                                        future.onSuccess(key -> {
                                            if (key != null) {
                                                logger.info("Message processes: {}", key);
                                            }
                                            if(ke != null) {
                                                ke.setValue("undelivered", disconnectedUsers.size());
                                            }
                                        }).onFailure(exe -> {
                                            exe.printStackTrace();
                                        });
                                    } catch (final SQLException | InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                                if(!onlineUsers.isEmpty()) {
                                    if(ke != null) {
                                        ke.setValue("private", onlineUsers.size());
                                    }
                                }
                            }

                        });
                    }
                }
            }
        });
        /*
         * websocket.onConnection()
         */
        String handle = "";
        String id = "";
        Map<String, String> query = null;

        query = ParseQueryUtilHelper.getQueryMap(wsChatSessions.get(session.getId()));

        handle = query.get("handle");
        id = query.get("id");
        messageUser.setName(handle);
        messageUser.setPassword(id);
        messageUser.setIp(remoteAddress);

        try {
            Future<MessageUser> future = dodexCassandra.selectUser(messageUser, session, eb);
            future.onSuccess(mUser -> {
                try {
                    Future<JsonObject> userJson = dodexCassandra.buildUsersJson(session, eb, mUser);

                    userJson.onSuccess(json -> {
                        // Users for private messages
                        session.getAsyncRemote().sendObject("connected:" + json.getMap().get("msg"));
                        /*
                         * Send undelivered messages and remove user related messages.
                         */
                        try {
                            dodexCassandra.processUserMessages(session, eb, mUser).onComplete(fut -> {
                                JsonObject undelivered = fut.result();
                                JsonArray undeliveredArray = undelivered.getJsonArray("msg");
                                Integer size = undeliveredArray.size();
                                for (int i = 0; i < size; i++) {
                                    JsonObject msg = undeliveredArray.getJsonObject(i);

                                    String when = new SimpleDateFormat("MM/dd-HH:ss", Locale.getDefault())
                                            .format(new Date(msg.getLong("postdate")));
                                    session.getAsyncRemote().sendObject(
                                            msg.getString("fromhandle") + ":" + when + " " + msg.getValue("message"));
                                }
                                if (size > 0) {
                                    logger.info(String.format("%sMessages Delivered: %d to %s%s",
                                            ColorUtilConstants.BLUE_BOLD_BRIGHT, size, mUser.getName(),
                                            ColorUtilConstants.RESET));
                                    if(ke != null) {
                                        ke.setValue("delivered", size);
                                    }
                                }
                                dodexCassandra.deleteDelivered(session, eb, mUser).onComplete(result -> {
                                    //
                                }).onFailure(Throwable::printStackTrace);
                            }).onFailure(Throwable::printStackTrace);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                } catch (InterruptedException | SQLException e) {
                    e.printStackTrace();
                }
            });

        } catch (InterruptedException | SQLException e) {
            e.printStackTrace();
        }
    }

    public EventBus getEb() {
        return eb;
    }

    public void setEb(EventBus eventBus) {
        eb = eventBus;
    }

    public DodexCassandra getDodexCassandra() {
        return dodexCassandra;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public Map<String, Session> getSessions() {
        return sessions;
    }

    public void setSessions(Map<String, Session> sessions) {
        this.sessions = sessions;
    }

    public void setDodexCassandra(DodexCassandra dodexCassandra) {
        this.dodexCassandra = dodexCassandra;
    }

    public Promise<Void> getDatabasePromise() {
        return databasePromise;
    }

    public static void removeWsChatSession(Session session) {
        wsChatSessions.remove(session.getId());
    }
}
