package dmo.fs.db.router.wsnext;

import dmo.fs.admin.CleanOrphanedUsers;
import dmo.fs.db.MessageUser;
import dmo.fs.db.wsnext.DbConfiguration;
import dmo.fs.db.wsnext.cassandra.DodexCassandra;
import dmo.fs.kafka.KafkaEmitterDodex;
import dmo.fs.quarkus.Server;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import dmo.fs.utils.ParseQueryUtilHelper;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Context;
import io.vertx.reactivex.core.Promise;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.shareddata.LocalMap;
import io.vertx.reactivex.core.shareddata.SharedData;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@IfBuildProperty(name = "DEFAULT_DB", stringValue = "cassandra")
@WebSocket(path = "/dodex")
public class CassandraRouter {
  protected static final Logger logger = LoggerFactory.getLogger(CassandraRouter.class.getName());
  protected DodexCassandra dodexCassandra;
  protected final Vertx vertx = Server.vertx;
  protected EventBus eb = vertx.eventBus();
  protected static final String LOGFORMAT = "{}{}{}";
  protected String remoteAddress;
  protected final Promise<Void> databasePromise = Promise.promise();
  protected Map<String, WebSocketConnection> sessions;
  static final SharedData sd = Server.vertx.sharedData();
  static final LocalMap<String, String> wsChatSessions = sd.getLocalMap("ws.dodex.sessions");
  protected final KafkaEmitterDodex ke = DodexRouter.getKafkaEmitterDodex();
  protected Map<String, String> queryParams;
  protected Map<String, Map<String, String>> sessionsNext = new ConcurrentHashMap<>();

  protected CassandraRouter() throws SQLException, IOException, InterruptedException {
    setWebSocket();
    logger.info(String.format("%sCassandra Router Started%s", ColorUtilConstants.BLUE_BOLD_BRIGHT,
      ColorUtilConstants.RESET));
  }

  @Inject
  WebSocketConnection connection;

  @OnOpen()
  public String onOpen() {
    queryParams = connection.handshakeRequest().query().transform(q -> {
      String queryString = URLDecoder.decode(q, StandardCharsets.UTF_8);
      return ParseQueryUtilHelper.getQueryMap(queryString);
    });
    sessionsNext.put(connection.id(), queryParams);
    wsChatSessions.put(connection.id(),
      URLDecoder.decode(connection.handshakeRequest().query(), StandardCharsets.UTF_8));
    final MessageUser messageUser = dodexCassandra.createMessageUser();

    Map<String, String> query = null;

    query = ParseQueryUtilHelper.getQueryMap(wsChatSessions.get(connection.id()));

    messageUser.setName(query.get("handle"));
    messageUser.setPassword(query.get("id"));
    messageUser.setIp(remoteAddress == null ? "unknown" : remoteAddress);

    try {
      WebSocketConnection currentWebSocket = getThisWebSocket(connection);
      Future<MessageUser> future = dodexCassandra.selectUser(messageUser, currentWebSocket, eb);
      future.onSuccess(mUser -> {
        try {
          Future<JsonObject> userJson = dodexCassandra.buildUsersJson(currentWebSocket, eb, mUser);

          userJson.onSuccess(json -> {
            // Users for protected messages
            currentWebSocket.sendText("connected:" + json.getMap().get("msg")).subscribeAsCompletionStage().isDone();
            /*
             * Send undelivered messages and remove user related messages.
             */
            try {
              dodexCassandra.processUserMessages(currentWebSocket, eb, mUser).onComplete(fut -> {
                JsonObject undelivered = fut.result();
                JsonArray undeliveredArray = undelivered.getJsonArray("msg");
                int size = undeliveredArray.size();
                for (int i = 0; i < size; i++) {
                  JsonObject msg = undeliveredArray.getJsonObject(i);

                  String when = new SimpleDateFormat("MM/dd-HH:ss", Locale.getDefault())
                    .format(new Date(msg.getLong("postdate")));
                  currentWebSocket.sendText(
                      msg.getString("fromhandle") + ":" + when + " " + msg.getValue("message"))
                    .subscribeAsCompletionStage().isDone();
                }
                if (size > 0) {
                  logger.info(String.format("%sMessages Delivered: %d to %s%s",
                    ColorUtilConstants.BLUE_BOLD_BRIGHT, size, mUser.getName(),
                    ColorUtilConstants.RESET));
                  if (ke != null) {
                    ke.setValue("delivered", size);
                  }
                }
                dodexCassandra.deleteDelivered(currentWebSocket, eb, mUser).onComplete(result -> {
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
    return null;
  }

  @OnTextMessage()
  public String onMessage(String message) {
    final MessageUser messageUser = dodexCassandra.createMessageUser();
    sessions = connection.getOpenConnections().stream().collect(Collectors.toConcurrentMap(WebSocketConnection::id, v -> v));
    queryParams =
      ParseQueryUtilHelper.getQueryMap(URLDecoder.decode(connection.handshakeRequest().query(), StandardCharsets.UTF_8));
    logger.info(LOGFORMAT, ColorUtilConstants.BLUE_BOLD_BRIGHT, queryParams.get("handle"), ColorUtilConstants.RESET);
    WebSocketConnection currentWebSocket = getThisWebSocket(connection);

    messageUser.setName(queryParams.get("handle"));
    messageUser.setPassword(queryParams.get("id"));
    messageUser.setIp(remoteAddress == null ? "unknown" : remoteAddress);

    final ArrayList<String> onlineUsers = new ArrayList<>();
    // Checking if message or command
    final Map<String, String> returnObject = DodexUtil.commandMessage(message);
    // message with command stripped out
    final String computedMessage = returnObject.get("message");
    final String command = returnObject.get("command");

    Promise<JsonObject> promise = Promise.promise();
    promise.complete(null);

    if (!command.isEmpty() && ";removeuser".equals(command)) {
      try {
        dodexCassandra.deleteUser(currentWebSocket, eb, messageUser);
      } catch (InterruptedException | SQLException e) {
        e.printStackTrace();
        connection
          .sendText("Your Previous handle did not delete: " + e.getMessage()).subscribeAsCompletionStage().isDone();
      }
    }

    final String selectedUsers = returnObject.get("selectedUsers");
    sessions = connection.getOpenConnections().stream().collect(Collectors.toConcurrentMap(WebSocketConnection::id, v -> v));
    if (!computedMessage.isEmpty()) {
      // broadcast
      if ("".equals(selectedUsers) && command.isEmpty()) {
        long count = broadcast(connection, messageUser.getName() + ": " + computedMessage, queryParams);
        String handles = "handle";
        handles = count == 1 ? handles : handles + "s";

        currentWebSocket.sendText(String.format("%d %s received your broadcast", count, handles)).subscribe().asCompletionStage();

        if (ke != null) {
          ke.setValue(1);
        }
      }

      sessions.values().stream().filter(s -> !s.id().equals(getThisWebSocket(connection).id()) /*&& getThisWebSocket(session).isOpen()*/)
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

      if ("".equals(selectedUsers) && !command.isEmpty()) {
        currentWebSocket.sendText("Private user not selected").subscribe().asCompletionStage();
      } else {
        if (!command.isEmpty()) {
          currentWebSocket.sendText("ok").subscribe().asCompletionStage();
        }
        if (!onlineUsers.isEmpty()) {
          if (ke != null) {
            ke.setValue("private", onlineUsers.size());
          }
        }
      }
    }

    // calculate difference between selected and online users
    if (!selectedUsers.isEmpty()) {
      final List<String> selected = Arrays.asList(selectedUsers.split(","));
      final List<String> disconnectedUsers = selected.stream()
        .filter(user -> !onlineUsers.contains(user)).collect(Collectors.toList());
      // Save protected message to send when to-user logs in
      if (!disconnectedUsers.isEmpty()) {
        Future<JsonObject> future;
        try {
          future = dodexCassandra.addMessage(currentWebSocket, messageUser, computedMessage,
            disconnectedUsers, eb);
          future.onSuccess(key -> {
            if (key != null) {
              logger.info("Message processes: {}", key);
            }
            if (ke != null) {
              ke.setValue("undelivered", disconnectedUsers.size());
            }
          }).onFailure(exe -> {
            exe.printStackTrace();
          });
        } catch (final SQLException | InterruptedException e) {
          e.printStackTrace();
        }
      }
      if (!onlineUsers.isEmpty()) {
        if (ke != null) {
          ke.setValue("protected", onlineUsers.size());
        }
      }
    }

    return null;
  }

  public void setWebSocket() throws InterruptedException, IOException, SQLException {
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

  public void setDodexCassandra(DodexCassandra dodexCassandra) {
    this.dodexCassandra = dodexCassandra;
  }

  public Promise<Void> getDatabasePromise() {
    return databasePromise;
  }

}
