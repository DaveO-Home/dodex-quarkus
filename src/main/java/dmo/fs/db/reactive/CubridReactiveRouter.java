package dmo.fs.db.reactive;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.websocket.Session;

import dmo.fs.quarkus.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.admin.CleanOrphanedUsers;
import dmo.fs.db.MessageUser;
import dmo.fs.db.MessageUserImpl;
import dmo.fs.kafka.KafkaEmitterDodex;
import dmo.fs.router.DodexRouter;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import dmo.fs.utils.ParseQueryUtilHelper;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Context;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.shareddata.LocalMap;
import io.vertx.reactivex.core.shareddata.SharedData;

public class CubridReactiveRouter extends DbCubridOverride {
    private static final Logger logger = LoggerFactory.getLogger(DodexReactiveRouter.class.getName());
    private static Vertx vertxReactive = Server.vertx;
    private static DodexReactiveDatabase dodexDatabase;
    private static final String LOGFORMAT = "{}{}{}";
    private static SharedData sd = vertxReactive.sharedData();
    private static final LocalMap<String, String> wsChatSessions = sd.getLocalMap("ws.dodex.sessions");
    private String remoteAddress;
    private final KafkaEmitterDodex ke = DodexRouter.getKafkaEmitterDodex();

    public void doConnection(Session session, String remoteAddress) throws UnsupportedEncodingException {
        this.remoteAddress = remoteAddress;
        String handle = "";
        String id = "";
        Map<String, String> query = null;
        String queryString = URLDecoder.decode(session.getQueryString(), StandardCharsets.UTF_8.name());
        wsChatSessions.put(session.getId(), queryString);
        final MessageUser messageUser = createMessageUser();
        query = ParseQueryUtilHelper.getQueryMap(queryString);

        handle = query.get("handle");
        id = query.get("id");

        messageUser.setName(handle);
        messageUser.setPassword(id);
        messageUser.setIp(remoteAddress == null ? "unknown" : remoteAddress);

        final Future<MessageUser> future = selectUser(messageUser, session);

        future.onSuccess(mUser -> {
            final Future<StringBuilder> userJson = buildUsersJson(mUser);
            userJson.onSuccess(json -> {
                session.getAsyncRemote().sendText("connected:" + json); // Users for private messages
                /*
                 * Send undelivered messages and remove user related messages.
                 */
                processUserMessages(session, mUser).onComplete(fut -> {
                    final int messageCount = fut.result().get("messages");
                    if (messageCount > 0) {
                        logger.info(
                            String.format("%sMessages Delivered: %d to %s%s", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                                    messageCount, mUser.getName(), ColorUtilConstants.RESET));
                        if(ke != null) {
                            ke.setValue("delivered", messageCount);
                        }
                    }
                });
            });
        });

    }

    public void doMessage(Session session, Map<String, Session> sessions, String message) {
        final MessageUser messageUser = setMessageUser(session, sessions);
        final ArrayList<String> onlineUsers = new ArrayList<>();
        // Checking if message or command
        final Map<String, String> returnObject = DodexUtil.commandMessage(message);
        final String selectedUsers = returnObject.get("selectedUsers");
        // message with command stripped out
        final String computedMessage = returnObject.get("message");
        final String command = returnObject.get("command");

        if (!"".equals(command) && ";removeuser".equals(command)) {
            deleteUser(session, messageUser);
        }

        if (computedMessage.length() > 0) {
            sessions.values().stream().filter(s -> !s.getId().equals(session.getId()) && session.isOpen())
                .forEach(s -> {
                    final String handle = s.getRequestParameterMap().get("handle").get(0);
                    // broadcast
                    if ("".equals(selectedUsers) && "".equals(command)) {
                        s.getAsyncRemote().sendObject(messageUser.getName() + ": " + computedMessage);
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
                if (ke != null && "".equals(selectedUsers) && "".equals(command)) {
                    ke.setValue(1);
                } 
            }
        }

        // calculate difference between selected and online users
        if (selectedUsers.length() > 0) {
            final List<String> selected = Arrays.asList(selectedUsers.split(","));
            final List<String> disconnectedUsers = selected.stream().filter(user -> !onlineUsers.contains(user))
                    .collect(Collectors.toList());
            // Save private message to send when to-user logs in
            if (!disconnectedUsers.isEmpty()) {
                Future<Long> future = null;
                future = addMessage(session, messageUser, computedMessage);
                future.onSuccess(key -> {
                    addUndelivered(session, disconnectedUsers, key);
                    if(ke != null) {
                        ke.setValue("undelivered", disconnectedUsers.size());
                    }
                });
            }
            if(!onlineUsers.isEmpty()) {
                if(ke != null) {
                    ke.setValue("private", onlineUsers.size());
                }
            }
        }
    }

    public void setup() {
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
                final JsonObject config = jsonObject.isPresent() ? jsonObject.get() : new JsonObject();
                final Optional<Boolean> runClean = Optional.ofNullable(config.getBoolean("clean.run"));
                if (runClean.isPresent() && runClean.get().equals(true)) {
                    final CleanOrphanedUsers clean = new CleanOrphanedUsers();
                    clean.startClean(config);
                }
            } catch (final Exception exception) {
                logger.error(LOGFORMAT, ColorUtilConstants.RED_BOLD_BRIGHT, "Context Configuration failed...",
                        ColorUtilConstants.RESET);
            }
        }
    }

    public static DodexReactiveDatabase getDodexDatabase() {
        return dodexDatabase;
    }

    public static void setDodexDatabase(DodexReactiveDatabase reactiveDodexDatabase) {
        dodexDatabase = reactiveDodexDatabase;
    }

    private MessageUser setMessageUser(Session session, Map<String, Session> sessions) {
        final MessageUser messageUser = createMessageUser();
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

    @Override
    public MessageUser createMessageUser() {
        return new MessageUserImpl();
    }

    public static void removeWsChatSession(Session session) {
        wsChatSessions.remove(session.getId());
    }
}
