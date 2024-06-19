package dmo.fs.db.router.wsnext;

import dmo.fs.db.MessageUser;
import dmo.fs.db.wsnext.DbConfiguration;
import dmo.fs.db.wsnext.neo4j.DodexNeo4j;
import dmo.fs.kafka.KafkaEmitterDodex;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.websockets.next.*;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import org.neo4j.driver.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@IfBuildProperty(name = "DEFAULT_DB", stringValue = "neo4j")
@WebSocket(path = "/dodex")
public class Neo4jRouter {
    protected static final Logger logger = LoggerFactory.getLogger(Neo4jRouter.class.getName());
    protected static Vertx vertx = DodexUtil.getVertx();
    protected DodexNeo4j dodexNeo4j;
    protected Promise<Driver> dbPromise;
    protected static final String LOGFORMAT = "{}{}{}";
    protected String remoteAddress;
    protected Driver driver;
    protected static final KafkaEmitterDodex ke = CDI.current().select(KafkaEmitterDodex.class).isUnsatisfied() ? null :
      CDI.current().select(KafkaEmitterDodex.class).get();
    protected static final ConcurrentHashMap<String, HashMap<String, String>> queryParams = new ConcurrentHashMap<>();

    public Neo4jRouter(final Vertx vertx) {
        Neo4jRouter.vertx = vertx;
    }

    @Inject
    WebSocketConnection connection;

    public Neo4jRouter() throws InterruptedException, IOException, SQLException {
        /*
         * You can customize the db config here by: Map = db configuration, Properties =
         * credentials e.g. Map overrideMap = new Map(); Properties overrideProperties =
         * new Properties(); set override or additional values... dodexDatabase =
         * DbConfiguration.getDefaultDb(overrideMap, overrideProperties);
         */
        dodexNeo4j = DbConfiguration.getDefaultDb();
        dbPromise = dodexNeo4j.databaseSetup();

        CompletableFuture<Driver> completableDriver = dbPromise.future().onItem().call(driver -> Uni.createFrom()
          .item(driver)).subscribeAsCompletionStage();
        try {
            setDriver(completableDriver.get());
            dodexNeo4j.setDriver(getDriver());
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

        String startupMessage = "In Production";

        startupMessage = "dev".equals(DodexUtil.getEnv()) ? "In Development" : startupMessage;
        logger.info(LOGFORMAT, ColorUtilConstants.BLUE_BOLD_BRIGHT, startupMessage, ColorUtilConstants.RESET);
    }

    @OnOpen()
    public void onOpen() {
        connection.handshakeRequest().query().transform(q -> {
            String query = URLDecoder.decode(q, StandardCharsets.UTF_8);

            String[] params = query.split("&");
            String[] handle = params[0].split("=");
            String[] id = params[1].split("=");

            HashMap<String, String> parameters = new HashMap<>();
            parameters.put(handle[0], handle[1]);
            parameters.put(id[0], id[1]);
            queryParams.put(connection.id(), parameters);
            return queryParams;
        });

        logger.info(String.join("", ColorUtilConstants.BLUE_BOLD_BRIGHT,
          queryParams.get(connection.id()).get("handle"), ColorUtilConstants.RESET));

        broadcast(connection, "User " + queryParams.get(connection.id()).get("handle") + " joined", queryParams);

        final MessageUser messageUser = dodexNeo4j.createMessageUser();

        String queryHandle = queryParams.get(connection.id()).get("handle");
        String id = queryParams.get(connection.id()).get("id");

        messageUser.setName(queryHandle);
        messageUser.setPassword(id);
        messageUser.setIp(remoteAddress);
        dodexNeo4j.setDriver(driver);

        try {
            WebSocketConnection ws = getThisWebSocket(connection);  // This is needed for mutiny?
            Promise<MessageUser> future = dodexNeo4j.selectUser(messageUser);

            future.future().onItem().call(messageUser2 -> {
                try {
                    Promise<StringBuilder> userJson = dodexNeo4j.buildUsersJson(messageUser2);

                    userJson.future().onItem().call(json -> {
                        ws.sendText("connected:" + json).subscribe().asCompletionStage();
                        /*
                         * Send undelivered messages and remove user related messages.
                         */
                        try {
                            dodexNeo4j.processUserMessages(ws, messageUser2).future().onItem().call(counts -> {
                                final int messageCount = counts.get("messages");
                                if (messageCount > 0) {
                                    logger.info(String.format("%sMessages Delivered: %d to %s%s",
                                      ColorUtilConstants.BLUE_BOLD_BRIGHT, messageCount,
                                      messageUser.getName(), ColorUtilConstants.RESET));
                                    if (ke != null) {
                                        ke.setValue("delivered", messageCount);
                                    }
                                }
                                return null;
                            }).subscribeAsCompletionStage().isDone();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return null;
                    }).subscribeAsCompletionStage().isDone();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return null;
            }).subscribeAsCompletionStage().isDone();

        } catch (InterruptedException | ExecutionException | SQLException e) {
            e.printStackTrace();
        }
    }

    @OnTextMessage()
    public void onMessage(String message) {
        final MessageUser messageUser = dodexNeo4j.createMessageUser();
        String handle = queryParams.get(connection.id()).get("handle");
        final ArrayList<String> onlineUsers = new ArrayList<>();

        // Checking if message or command
        Map<String, String> returnObject = DodexUtil.commandMessage(message);

        // message with command stripped out
        String[] computedMessage = {""};
        String[] command = {""};

        computedMessage[0] = returnObject.get("message");
        command[0] = returnObject.get("command");

        Promise<MessageUser> promise = Promise.promise();
        promise.complete(null);
        Promise<MessageUser> continued = null;
        messageUser.setName(queryParams.get(connection.id()).get("handle"));
        messageUser.setPassword(queryParams.get(connection.id()).get("id"));

        if (";removeuser".equals(command[0])) {
            try {
                continued = dodexNeo4j.deleteUser(connection, messageUser);
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                connection.sendText("Your Previous handle did not delete: " + e.getMessage())
                  .subscribe().asCompletionStage();
            }
        } else {
            continued = promise;
        }
        if (continued != null) {
            continued.future().onItem().call(result -> {
                String selectedUsers = "";
                if (!computedMessage[0].isEmpty()) {
                    // protected users to send message
                    selectedUsers = returnObject.get("selectedUsers");
                    if ("".equals(selectedUsers) && "".equals(command[0])) {
                        // broadcast
                        long count = broadcast(connection, handle + ": " + computedMessage[0], queryParams);
                        String handles = "handle";
                        handles = count == 1 ? handles : handles + "s";

                        connection.sendText(String.format("%d %s received your broadcast", count, handles))
                          .subscribe().asCompletionStage();

                        if (ke != null) {
                            ke.setValue(1);
                        }
                    } else {
                        Map<String, WebSocketConnection> sessions = connection.getOpenConnections().stream()
                          .collect(Collectors.toMap(WebSocketConnection::id, v -> v));
                        for (Map.Entry<String, WebSocketConnection> entry : sessions.entrySet()) {
                            WebSocketConnection privateWebSocket = entry.getValue();

                            if (privateWebSocket.isOpen()) {
                                if (!privateWebSocket.id().equals(connection.id())) {
                                    // private message
                                    if (Arrays.stream(selectedUsers.split(",")).anyMatch(h -> {
                                        boolean isMatched = false;
                                        isMatched = h.contains(queryParams.get(entry.getKey()).get("handle"));
                                        return isMatched;
                                    })) {
                                        entry.getValue().sendText(queryParams.get(connection.id())
                                            .get("handle") + ": " + computedMessage[0])
                                          .subscribe().asCompletionStage();
                                        // keep track of delivered messages
                                        onlineUsers.add(queryParams.get(entry.getKey()).get("handle"));
                                    }
                                } else {
                                    if (selectedUsers.isEmpty() && !command[0].isEmpty()) {
                                        connection.sendText("Private user not selected")
                                          .subscribe().asCompletionStage();
                                    } else {
                                        connection.sendText("ok").subscribe().asCompletionStage();
                                    }
                                }
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
                        try {
                            dodexNeo4j.addMessage(messageUser, computedMessage[0], disconnectedUsers);
                            if (ke != null) {
                                ke.setValue("undelivered", disconnectedUsers.size());
                            }

                        } catch (ExecutionException | InterruptedException e) {
                            e.printStackTrace();
                            connection.sendText("Message delivery failure: " + e.getMessage())
                              .subscribe().asCompletionStage();
                        }
                    }
                    if (!onlineUsers.isEmpty()) {
                        if (ke != null) {
                            ke.setValue("protected", onlineUsers.size());
                        }
                    }
                }
                return null;
            }).subscribeAsCompletionStage().isDone();
        }
    }

    @OnClose
    public void onClose(WebSocketConnection session) {
        try {
            String handle = queryParams.get(connection.id()).get("handle");
            if (logger.isInfoEnabled()) {
                logger.info(String.format("%sClosing ws-connection to client: %s%s", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                  handle, ColorUtilConstants.RESET));
            }

            broadcast(connection, "User " + handle + " left", queryParams);
            queryParams.remove(connection.id());

            if (ke != null) {
                ke.setValue("sessions", connection.getOpenConnections().size());
            }
        } catch (Exception e) {
            logger.error("Closing Error: {}", e.getMessage());
        }
    }

    protected long broadcast(WebSocketConnection connection, String message, ConcurrentHashMap<String, HashMap<String, String>> queryParams) {
        long c = connection.getOpenConnections().stream().filter(session -> {
            if (connection.id().equals(session.id())) {
                return false;
            }
            CompletableFuture<Void> complete = session.sendText(message).subscribe().asCompletionStage();
            if (complete.isCompletedExceptionally()) {
                logger.info(String.format("%sUnable to send message: %s%s%s", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                  queryParams.get(connection.id()).get("handle"), ": Exception in broadcast",
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

    public Vertx getVertx() {
        return vertx;
    }

    public void setVertx(Vertx vertx) {
        Neo4jRouter.vertx = vertx;
    }

    public Driver getDriver() {
        return driver;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }
}
