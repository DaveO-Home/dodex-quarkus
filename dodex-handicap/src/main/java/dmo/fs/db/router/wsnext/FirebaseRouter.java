package dmo.fs.db.router.wsnext;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import dmo.fs.db.MessageUser;
import dmo.fs.db.wsnext.DbConfiguration;
import dmo.fs.db.wsnext.firebase.DodexFirebase;
import dmo.fs.kafka.KafkaEmitterDodex;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import dmo.fs.utils.FirebaseUser;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.websockets.next.*;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.mutiny.core.Vertx;
import io.vertx.reactivex.core.Promise;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
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

@IfBuildProperty(name = "DEFAULT_DB", stringValue = "firebase")
@WebSocket(path = "/dodex")
public class FirebaseRouter {
    protected static final Logger logger = LoggerFactory.getLogger(FirebaseRouter.class.getSimpleName());
    protected static Vertx vertx = DodexUtil.getVertx();
    protected static final ConcurrentHashMap<String, HashMap<String,String>> queryParams = new ConcurrentHashMap<>();
    protected static final String DODEX_PROJECT_ID = "dodex-firebase"; // ""dodex-6f42d";
    protected static final String LOGFORMAT = "{}{}{}";
    protected String remoteAddress;
    protected DodexFirebase dodexFirebase;
    Firestore dbf;

    protected static final KafkaEmitterDodex ke = CDI.current().select(KafkaEmitterDodex.class).isUnsatisfied() ? null :
        CDI.current().select(KafkaEmitterDodex.class).get();
    @Inject
    WebSocketConnection connection;

    public FirebaseRouter(final Vertx vertx) throws SQLException, IOException, InterruptedException {
        FirebaseRouter.vertx = vertx;
        FirestoreOptions firestoreOptions;
        try {
            firestoreOptions = FirestoreOptions.getDefaultInstance().toBuilder().setProjectId(DODEX_PROJECT_ID)
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .build();
            dbf = firestoreOptions.getService();
            dbf.collection("users").get(); // dummy call to get firestore ready, helps on initial thread blockage
        } catch (Exception e) {
            e.printStackTrace();
        }

        setWebSocket();
    }

    @OnOpen()
    public String onOpen() throws SQLException, IOException, InterruptedException {
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

        if (ke != null) {
            ke.setValue("sessions", connection.getOpenConnections().size());
        }

        final MessageUser messageUser = dodexFirebase.createMessageUser();

        String queryHandle = queryParams.get(connection.id()).get("handle");
        String id = queryParams.get(connection.id()).get("id");

        messageUser.setName(queryHandle);
        messageUser.setPassword(id);
        messageUser.setIp(remoteAddress);

        try {
            Future<FirebaseUser> future = dodexFirebase.selectUser(messageUser, connection);
            future.onSuccess(firebaseUser -> {
                try {
                    Future<StringBuilder> userJson = dodexFirebase.buildUsersJson(connection, messageUser);

                    userJson.onSuccess(json -> {
                        connection.sendText("connected:" + json).subscribe().asCompletionStage(); // Users for private messages
                        //
                         // Send undelivered messages and remove user related messages.
                        // 
                        try {
                            dodexFirebase.processUserMessages(connection, firebaseUser).onComplete(counts -> {
                                final int messageCount = counts.result().get("messages");
                                if (messageCount > 0) {
                                    logger.info(String.format("%sMessages Delivered: %d to %s%s",
                                        ColorUtilConstants.BLUE_BOLD_BRIGHT, messageCount,
                                        firebaseUser.getName(), ColorUtilConstants.RESET));
                                    if (ke != null) {
                                        ke.setValue("delivered", messageCount);
                                    }
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });

        } catch (InterruptedException | ExecutionException | SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    protected long broadcast(WebSocketConnection connection, String message, ConcurrentHashMap<String, HashMap<String,String>> queryParams) {
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

    private void setWebSocket() throws InterruptedException, IOException, SQLException {
        //
         // You can customize the db config here by: Map = db configuration, Properties =
         // credentials e.g. Map overrideMap = new Map(); Properties overrideProperties =
         // new Properties(); set override or additional values... dodexDatabase =
         // DbConfiguration.getDefaultDb(overrideMap, overrideProperties);
         //

        dodexFirebase = DbConfiguration.getDefaultDb();
        dodexFirebase.setFirestore(dbf);

        logger.info("{}Firebase Router Started{}",
          ColorUtilConstants.BLUE_BOLD_BRIGHT, ColorUtilConstants.RESET);
    }

    @OnPongMessage
    void pong(Buffer data) {
        logger.debug("Pong received: {}", data);
    }

    @OnClose
    public void onClose() {
        try {
            String handle = queryParams.get(connection.id()).get("handle");
            if (logger.isInfoEnabled()) {
                logger.info("{}Closing ws-connection to client: {}{}",
                  ColorUtilConstants.BLUE_BOLD_BRIGHT, handle, ColorUtilConstants.RESET);
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

    @OnTextMessage()
    public void onMessage(String message) throws ExecutionException, InterruptedException {
        final MessageUser messageUser = dodexFirebase.createMessageUser();
        String handle = queryParams.get(connection.id()).get("handle");
        logger.info(LOGFORMAT, ColorUtilConstants.BLUE_BOLD_BRIGHT, "Firebase Handle: " + handle,
            ColorUtilConstants.RESET);

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
        Future<MessageUser> continued = null;

        if (";removeuser".equals(command[0])) {
            messageUser.setName(queryParams.get(connection.id()).get("handle"));
            messageUser.setPassword(queryParams.get(connection.id()).get("id"));
            continued = dodexFirebase.deleteUser(connection, messageUser);
        } else {
            continued = promise.future();
        }
        if (continued != null) {
            continued.onSuccess(result -> {
                String selectedUsers = "";
                if (!computedMessage[0].isEmpty()) {
                    // private users to send message
                    selectedUsers = returnObject.get("selectedUsers");

                    if ("".equals(selectedUsers) && "".equals(command[0])) {
                        // broadcast
                        long count = broadcast(connection, handle + ": " + computedMessage[0], queryParams);
                        String handles = "handle";
                        handles = count == 1 ? handles : handles + "s";

                        connection.sendText(String.format("%d %s received your broadcast", count, handles)).subscribe().asCompletionStage();

                        if (ke != null) {
                            ke.setValue(1);
                        }
                    } else {
                        Map<String, WebSocketConnection> sessions = connection.getOpenConnections().stream().collect(Collectors.toMap(WebSocketConnection::id, v -> v));
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
                                        entry.getValue().sendText(queryParams.get(connection.id()).get("handle") + ": " + computedMessage[0])
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
                    // Save private message to send when to-user logs in
                    if (!disconnectedUsers.isEmpty()) {
                        try {

                            dodexFirebase.addMessage(connection, messageUser, computedMessage[0],
                                disconnectedUsers);
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
            });
        }
    }

    public Firestore getDbf() {
        return dbf;
    }

    public Vertx getVertx() {
        return vertx;
    }

    public void setVertx(Vertx vertx) {
        FirebaseRouter.vertx = vertx;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

}
