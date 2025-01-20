package dmo.fs.db.router.wsnext;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import dmo.fs.db.reactive.DbCubridSqlBase;
import dmo.fs.db.reactive.DodexReactiveBase;

import dmo.fs.quarkus.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.admin.CleanOrphanedUsers;
import dmo.fs.db.MessageUser;
import dmo.fs.db.MessageUserImpl;
import dmo.fs.kafka.KafkaEmitterDodex;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import dmo.fs.utils.ParseQueryUtilHelper;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Context;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.shareddata.LocalMap;
import io.vertx.reactivex.core.shareddata.SharedData;
import io.quarkus.websockets.next.*;

public class CubridRouterReactive extends DbCubridSqlBase {
    protected static final Logger logger = LoggerFactory.getLogger(CubridRouterReactive.class.getName());
    protected static Vertx vertxReactive = Server.vertx;
    protected static final String LOGFORMAT = "{}{}{}";
    protected static SharedData sd = vertxReactive.sharedData();
    protected static final LocalMap<String, String> wsChatSessions = sd.getLocalMap("ws.dodex.sessions");
    protected Map<String, Map<String, String>> sessionsNext = new ConcurrentHashMap<>();
    protected String remoteAddress;
    protected final KafkaEmitterDodex ke = DodexRouter.getKafkaEmitterDodex();
    protected Map<String, String> queryParams = null;

    public void doConnection(WebSocketConnection session, String remoteAddress, Map<String, Map<String, String>> sessionsNext) throws UnsupportedEncodingException {
        this.remoteAddress = remoteAddress;
        this.sessionsNext = sessionsNext;
        String queryString = URLDecoder.decode(session.handshakeRequest().query(), StandardCharsets.UTF_8);
        wsChatSessions.put(session.id(), queryString);
        final MessageUser messageUser = createMessageUser();
        queryParams = ParseQueryUtilHelper.getQueryMap(queryString);

        messageUser.setName(queryParams.get("handle"));
        messageUser.setPassword(queryParams.get("id"));
        messageUser.setIp(remoteAddress == null ? "unknown" : remoteAddress);

        final Future<MessageUser> future = selectUser(messageUser, session);

        future.onSuccess(mUser -> {
            final Future<StringBuilder> userJson = buildUsersJson(mUser);
            userJson.onSuccess(json -> {
                session.sendText("connected:" + json).subscribeAsCompletionStage().isDone(); // Users for protected messages
                /*
                 * Send undelivered messages and remove user related messages.
                 */
                processUserMessages(session, mUser).onComplete(fut -> {
                    final int messageCount = fut.result().get("messages");
                    if (messageCount > 0) {
                        logger.info(
                          String.format("%sMessages Delivered: %d to %s%s", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                            messageCount, mUser.getName(), ColorUtilConstants.RESET));
                        if (ke != null) {
                            ke.setValue("delivered", messageCount);
                        }
                    }
                });
            });
        });

    }

    public void doMessage(WebSocketConnection session, Map<String, WebSocketConnection> sessions, String message,
                          Map<String, Map<String, String>> sessionsNext) {
        queryParams = ParseQueryUtilHelper.getQueryMap(wsChatSessions.get(session.id()));
        final MessageUser messageUser = setMessageUser(session, sessions);
        final ArrayList<String> onlineUsers = new ArrayList<>();
        // Checking if message or command
        final Map<String, String> returnObject = DodexUtil.commandMessage(message);
        final String selectedUsers = returnObject.get("selectedUsers");
        // message with command stripped out
        final String computedMessage = returnObject.get("message");
        final String command = returnObject.get("command");


        if (";removeuser".equals(command)) {
            deleteUser(session, messageUser);
        }

        sessions = session.getOpenConnections().stream().collect(Collectors.toConcurrentMap(WebSocketConnection::id, v -> v));
        if (!computedMessage.isEmpty()) {
            // broadcast
            if ("".equals(selectedUsers) && "".equals(command)) {
                long count = broadcast(session, messageUser.getName() + ": " + computedMessage, queryParams);
                String handles = "handle";
                handles = count == 1 ? handles : handles + "s";

                session.sendText(String.format("%d %s received your broadcast", count, handles)).subscribe().asCompletionStage();

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
                      if (complete.isCompletedExceptionally()) {
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
                session.sendText("Private user not selected").subscribeAsCompletionStage().isDone();
            } else {
                session.sendText("ok").subscribeAsCompletionStage().isDone();
                if (ke != null && "".equals(selectedUsers)) {
                    ke.setValue(1);
                }
            }
        }

        // calculate difference between selected and online users
        if (!selectedUsers.isEmpty()) {
            final List<String> selected = Arrays.asList(selectedUsers.split(","));
            final List<String> disconnectedUsers = selected.stream().filter(user -> !onlineUsers.contains(user))
              .collect(Collectors.toList());
            // Save protected message to send when to-user logs in
            if (!disconnectedUsers.isEmpty()) {
                Future<Long> future = null;
                future = addMessage(session, messageUser, computedMessage);
                future.onSuccess(key -> {
                    addUndelivered(session, disconnectedUsers, key);
                    if (ke != null) {
                        ke.setValue("undelivered", disconnectedUsers.size());
                    }
                });
            }
            if (!onlineUsers.isEmpty()) {
                if (ke != null) {
                    ke.setValue("protected", onlineUsers.size());
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
                final JsonObject config = jsonObject.orElseGet(JsonObject::new);
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

//    public static DodexReactiveDatabase getDodexDatabase() {
//        return dodexDatabase;
//    }
//
//    public static void setDodexDatabase(DodexReactiveDatabase reactiveDodexDatabase) {
//        dodexDatabase = reactiveDodexDatabase;
//    }

    protected MessageUser setMessageUser(WebSocketConnection session, Map<String, WebSocketConnection> sessions) {
        final MessageUser messageUser = createMessageUser();

        messageUser.setName(queryParams.get("handle"));
        messageUser.setPassword(queryParams.get("id"));
        messageUser.setIp(remoteAddress == null ? "Unknown" : remoteAddress);

        return messageUser;
    }

    @Override
    public MessageUser createMessageUser() {
        return new MessageUserImpl();
    }

}
