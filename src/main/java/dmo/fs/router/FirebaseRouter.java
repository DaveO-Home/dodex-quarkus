package dmo.fs.router;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.db.DbConfiguration;
import dmo.fs.db.DodexFirebase;
import dmo.fs.db.MessageUser;
import dmo.fs.kafka.KafkaEmitterDodex;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import dmo.fs.utils.FirebaseUser;
import dmo.fs.utils.ParseQueryUtilHelper;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.shareddata.LocalMap;
import io.vertx.mutiny.core.shareddata.SharedData;
import io.vertx.reactivex.core.Promise;

@IfBuildProperty(name = "DEFAULT_DB", stringValue = "firebase")
@ApplicationScoped
public class FirebaseRouter {
    private static final Logger logger = LoggerFactory.getLogger(FirebaseRouter.class.getName());
    private static Vertx vertx = Vertx.vertx();
    private Map<String, Session> clients = new ConcurrentHashMap<>();
    private DodexFirebase dodexFirebase;
    private static final String DODEX_PROJECT_ID = "dodex-firebase";
    Firestore dbf;
    private FirestoreOptions firestoreOptions;
    private static final String LOGFORMAT = "{}{}{}";
    private static final SharedData sd = vertx.sharedData();
    private static final LocalMap<Object, Object> wsChatSessions = sd.getLocalMap("ws.dodex.sessions");
    private String remoteAddress;
    private final KafkaEmitterDodex ke = DodexRouter.getKafkaEmitterDodex();

    public FirebaseRouter(final Vertx vertx) {
        this.vertx = vertx;
        try {
            firestoreOptions = FirestoreOptions.getDefaultInstance().toBuilder().setProjectId(DODEX_PROJECT_ID)
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .build();
            dbf = firestoreOptions.getService();
            dbf.collection("users").get(); // dummy call to get firestore ready, helps on initial thread blockage
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void onStart(@Observes StartupEvent event) throws InterruptedException, IOException, SQLException {
        setWebSocket(null);
        logger.info(String.format("%sFirebase Router Started%s", ColorUtilConstants.BLUE_BOLD_BRIGHT,
                ColorUtilConstants.RESET));
    }

    public void setWebSocket(final HttpServer server) throws InterruptedException, IOException, SQLException {
        /**
         * You can customize the db config here by: Map = db configuration, Properties =
         * credentials e.g. Map overrideMap = new Map(); Properties overrideProperties =
         * new Properties(); set override or additional values... dodexDatabase =
         * DbConfiguration.getDefaultDb(overrideMap, overrideProperties);
         */

        dodexFirebase = DbConfiguration.getDefaultDb();
        dodexFirebase.setFirestore(dbf);

        String startupMessage = "In Production";

        startupMessage = "dev".equals(DodexUtil.getEnv()) ? "In Development" : startupMessage;
        logger.info(LOGFORMAT, ColorUtilConstants.BLUE_BOLD_BRIGHT, startupMessage, ColorUtilConstants.RESET);
    }

    public void setFirebaseHandler(Session session) throws IOException {
        try {
            String handle = URLDecoder.decode(ParseQueryUtilHelper.getQueryMap(session.getQueryString()).get("handle"),
                    StandardCharsets.UTF_8.name());
            logger.info(LOGFORMAT, ColorUtilConstants.BLUE_BOLD_BRIGHT, "Firebase Handle: " + handle,
                    ColorUtilConstants.RESET);
        } catch (final UnsupportedEncodingException e) {
            logger.error(String.join("", ColorUtilConstants.RED_BOLD_BRIGHT, e.getMessage(), ColorUtilConstants.RESET));
        }

        if (!"/dodex".equals(session.getRequestURI().getPath())) {
            session.close();
        } else {
            final MessageUser messageUser = dodexFirebase.createMessageUser();
            try {
                wsChatSessions.put(session.getId(),
                        URLDecoder.decode(session.getRequestURI().toString(), StandardCharsets.UTF_8.name()));
            } catch (final UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            clients.put(session.getId(), session);

            session.addMessageHandler(new MessageHandler.Whole<String>() {
                @Override
                public void onMessage(String message) {
                    final ArrayList<String> onlineUsers = new ArrayList<>();

                    // Checking if message or command
                    Map<String, String> returnObject = DodexUtil.commandMessage(message);

                    // message with command stripped out
                    String[] computedMessage = { "" };
                    String[] command = { "" };

                    computedMessage[0] = returnObject.get("message");
                    command[0] = returnObject.get("command");

                    Promise<MessageUser> promise = Promise.promise();
                    promise.complete(null);
                    Future<MessageUser> continued = null;

                    if (";removeuser".equals(command[0])) {
                        try {
                            continued = dodexFirebase.deleteUser(session, messageUser);
                        } catch (InterruptedException | ExecutionException e) {
                            e.printStackTrace();
                            session.getAsyncRemote().sendText("Your Previous handle did not delete: " + e.getMessage());
                        }
                    } else {
                        continued = promise.future();
                    }
                    if (continued != null) {
                        continued.onSuccess(result -> {
                            String selectedUsers = "";
                            if (computedMessage[0].length() > 0) {
                                // private users to send message
                                selectedUsers = returnObject.get("selectedUsers");
                                final Set<String> websockets = clients.keySet();
                                Map<String, String> query = null;

                                for (final String websocket : websockets) {
                                    final Session webSocket = clients.get(websocket);
                                    if (webSocket.isOpen()) {
                                        if (!websocket.equals(session.getId())) {
                                            // broadcast message
                                            query = ParseQueryUtilHelper
                                                    .getQueryMap((String) wsChatSessions.get(webSocket.getId()));
                                            final String handle = query.get("handle");
                                            if (selectedUsers.length() == 0 && command[0].length() == 0) {
                                                webSocket.getAsyncRemote()
                                                        .sendText(messageUser.getName() + ": " + computedMessage[0]);
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
                                                        .sendText(messageUser.getName() + ": " + computedMessage[0]);
                                                // keep track of delivered messages
                                                onlineUsers.add(handle);
                                            }
                                        } else {
                                            if (selectedUsers.length() == 0 && command[0].length() > 0) {
                                                session.getAsyncRemote().sendText("Private user not selected");
                                            } else {
                                                session.getAsyncRemote().sendText("ok");
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
                                    try {

                                        dodexFirebase.addMessage(session, messageUser, computedMessage[0],
                                                disconnectedUsers);
                                        if(ke != null) {
                                            ke.setValue("undelivered", disconnectedUsers.size());
                                        }

                                    } catch (ExecutionException | InterruptedException e) {
                                        e.printStackTrace();
                                        session.getAsyncRemote()
                                                .sendText("Message delivery failure: " + e.getMessage());
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
            });
            /*
             * websocket.onConnection()
             */
            String handle = "";
            String id = "";
            Map<String, String> query = null;

            query = ParseQueryUtilHelper.getQueryMap((String) wsChatSessions.get(session.getId()));

            handle = query.get("handle");
            id = query.get("id");

            messageUser.setName(handle);
            messageUser.setPassword(id);
            messageUser.setIp(remoteAddress);

            try {
                Future<FirebaseUser> future = dodexFirebase.selectUser(messageUser, session);
                future.onSuccess(firebaseUser -> {
                    try {
                        Future<StringBuilder> userJson = dodexFirebase.buildUsersJson(session, messageUser);

                        userJson.onSuccess(json -> {
                            session.getAsyncRemote().sendText("connected:" + json); // Users for private messages
                            /*
                             * Send undelivered messages and remove user related messages.
                             */
                            try {
                                dodexFirebase.processUserMessages(session, firebaseUser).onComplete(counts -> {
                                    final int messageCount = counts.result().get("messages");
                                    if (messageCount > 0) {
                                        logger.info(String.format("%sMessages Delivered: %d to %s%s",
                                                ColorUtilConstants.BLUE_BOLD_BRIGHT, messageCount,
                                                firebaseUser.getName(), ColorUtilConstants.RESET));
                                        if(ke != null) {
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
        }
    }

    public Firestore getDbf() {
        return dbf;
    }

    public Vertx getVertx() {
        return vertx;
    }

    public void setVertx(Vertx vertx) {
        this.vertx = vertx;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public static void removeWsChatSession(Session session) {
        wsChatSessions.remove(session.getId());
    }
}
