package dmo.fs.router;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.websocket.MessageHandler;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.davidmoten.rx.jdbc.Database;

import dmo.fs.admin.CleanOrphanedUsers;
import dmo.fs.db.DbConfiguration;
import dmo.fs.db.DodexDatabase;
import dmo.fs.db.MessageUser;
import dmo.fs.utils.ConsoleColors;
import dmo.fs.utils.DodexUtil;
import io.quarkus.runtime.configuration.ProfileManager;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

@ServerEndpoint("/dodex")         
public class DodexRouter {
    private Vertx vertx;
    private final static Logger logger = LoggerFactory.getLogger(DodexRouter.class.getName());
    private DodexDatabase dodexDatabase = null;
    private Map<String, Session> sessions = new ConcurrentHashMap<>();
    private Database db = null;
    private final boolean isProduction = !ProfileManager.getLaunchMode().isDevOrTest();

    public DodexRouter() throws InterruptedException, IOException, SQLException {
        System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tF %1$tT] [%4$s] %5$s %3$s %n");
        System.setProperty("dmo.fs.level", "INFO");
        System.setProperty("org.jooq.no-logo", "true");
        this.vertx = Vertx.vertx();
        String value = System.getenv("VERTXWEB_ENVIRONMENT");
        
        if (isProduction) {
			DodexUtil.setEnv("prod");
		} else {
			DodexUtil.setEnv(value == null? "dev": value);
		}
        setup(vertx);
    }

    @OnOpen
    public void onOpen(Session session) {
        sessions.put(session.getId(), session);

        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
                doMessage(session, message);
            }
        });

        logger.info("{0}Websocket-connection...{2}{1} ",
            new Object[] { 
                ConsoleColors.BLUE_BOLD_BRIGHT,
                session.getRequestParameterMap().get("handle").get(0),
                ConsoleColors.RESET
            });

        doConnection(session);
        broadcast(session, "User " + session.getRequestParameterMap().get("handle").get(0) + " joined");
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session.getId());
        logger.info("{0}Closing ws-connection to client: {2}{1} ", new Object[] {
            ConsoleColors.BLUE_BOLD_BRIGHT,session.getRequestParameterMap().get("handle").get(0), ConsoleColors.RESET });
        broadcast(session, "User " + session.getRequestParameterMap().get("handle").get(0) + " left");
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        sessions.remove(session.getId());
        logger.info("{0}Websocket-failure...{2}{1} ",
            new Object[] { 
                ConsoleColors.BLUE_BOLD_BRIGHT,
                "User " + session.getRequestParameterMap().get("handle").get(0) + " left on error: " + throwable,
                ConsoleColors.RESET
            });
    }

    private void broadcast(Session session, String message) {
        sessions.values()
            .stream()
            .filter(s -> !s.getId().equals(session.getId()))
            .forEach(s -> {
                s.getAsyncRemote().sendObject(message, result ->  {
                    if (result.getException() != null) {
                        logger.info("{0}Websocket-connection...{2}{1} ",
                            new Object[] { 
                                ConsoleColors.BLUE_BOLD_BRIGHT,
                                "Unable to send message: " + s.getRequestParameterMap().get("handle").get(0) + ": " + result.getException(),
                                ConsoleColors.RESET
                            });
                    }
                });
            });
    }

    private void setup(Vertx vertx) throws InterruptedException, IOException, SQLException {
        /**
         * You can customize the db config here by: 
         *  Map = db configuration, 
         *  Properties = credentials 
         * e.g. Map overrideMap = new Map(); 
         *      Properties overrideProperties = new Properties(); 
         * set override or additional values... 
         * dodexDatabase = DbConfiguration.getDefaultDb(overrideMap, overrideProperties);
         */
        dodexDatabase = DbConfiguration.getDefaultDb();
        db = dodexDatabase.getDatabase();
        /**
         * Optional auto user cleanup - config in "application-conf.json". When client
         * changes handle when server is down, old users and undelivered messages will
         * be orphaned.
         * 
         * Defaults: off - when turned on 1. execute on start up and every 7 days
         * thereafter. 2. remove users who have not logged in for 90 days.
         */

        final Optional<Context> context = Optional.ofNullable(Vertx.currentContext());
        if(context.isPresent()) {
            final Optional<JsonObject> jsonObject = Optional.ofNullable(Vertx.currentContext().config());
            try {
                final JsonObject config = jsonObject.isPresent() ? jsonObject.get() : new JsonObject();
                final Optional<Boolean> runClean = Optional.ofNullable(config.getBoolean("clean.run"));
                if (runClean.isPresent() && runClean.get()) {
                    final CleanOrphanedUsers clean = new CleanOrphanedUsers();
                    clean.startClean(config);
                }
            } catch (final Exception exception) {
                logger.info("{0}Context Configuration failed...{1}{2} ",
                        new Object[] { ConsoleColors.RED_BOLD_BRIGHT, exception.getMessage(), ConsoleColors.RESET });
            }
        }

        String startupMessage = "In Production";

        startupMessage = DodexUtil.getEnv().equals("dev") ? "In Development" : startupMessage;
        logger.info("{0}Starting Web Socket...{1}{2} ",
                new Object[] { ConsoleColors.BLUE_BOLD_BRIGHT, startupMessage, ConsoleColors.RESET });
    }

    private void doConnection(Session session) {
        final MessageUser messageUser = setMessageUser(session);
        MessageUser resultUser = null;

        StringBuilder userJson = new StringBuilder();
        try {
            resultUser = dodexDatabase.selectUser(messageUser, session, db);
            userJson = dodexDatabase.buildUsersJson(db, messageUser);
        } catch (InterruptedException | SQLException e) {
            e.printStackTrace();
        }
        
        session.getAsyncRemote().sendObject("connected:" + userJson); // Users for private messages
        /*
        * Send undelivered messages and remove user related messages.
        */
        dodexDatabase.processUserMessages(session, db, resultUser);
    }

    private void doMessage(Session session, String message) {
        final MessageUser messageUser = setMessageUser(session);
        final DodexUtil dodexUtil = new DodexUtil();
        final ArrayList<String> onlineUsers = new ArrayList<>();
        // Checking if message or command
        final Map<String, String> returnObject = dodexUtil.commandMessage(session, message, messageUser);
        final String selectedUsers = returnObject.get("selectedUsers");
        // message with command stripped out
        final String computedMessage = returnObject.get("message");
        final String command = returnObject.get("command");

        if (command != null && command.equals(";removeuser")) {
            try {
                dodexDatabase.deleteUser(session, db, messageUser);
            } catch (InterruptedException | SQLException e) {
                e.printStackTrace();
                session.getAsyncRemote().sendObject("Your Previous handle did not delete: " + e.getMessage());
            }
        }

        if (computedMessage.length() > 0) {
            sessions.values()
                .stream()
                .filter(s -> !s.getId().equals(session.getId()) && session.isOpen())
                .forEach(s -> {
                    final String handle = s.getRequestParameterMap().get("handle").get(0);
                    // broadcast
                    if (selectedUsers == null && command == null) {
                        s.getAsyncRemote().sendObject(messageUser.getName() + ": " + computedMessage);
                    // private message
                    } else if (Arrays.stream(selectedUsers.split(",")).anyMatch(h -> {
                        boolean isMatched = false;
                        if (!isMatched) {
                            isMatched = h.contains(handle);
                        }
                        return isMatched;
                    })) {
                        s.getAsyncRemote().sendObject(messageUser.getName() + ": " + computedMessage, result ->  {
                            if (result.getException() != null) {
                                logger.info("{0}Websocket-connection...{2}{1} ",
                                    new Object[] { 
                                        ConsoleColors.BLUE_BOLD_BRIGHT,
                                        "Unable to send message: " + s.getRequestParameterMap().get("handle").get(0) + ": " + result.getException(),
                                        ConsoleColors.RESET
                                    });
                            }
                        });
                        // keep track of delivered messages
                        onlineUsers.add(handle);
                    }
                });

            if (selectedUsers == null && command != null) {
                session.getAsyncRemote().sendObject("Private user not selected");
            } else {
                session.getAsyncRemote().sendObject("ok");
            }
        }

        // calculate difference between selected and online users
        if (selectedUsers != null) {
            final List<String> selected = Arrays.asList(selectedUsers.split(","));
            final List<String> disconnectedUsers = selected.stream()
                    .filter(user -> !onlineUsers.contains(user)).collect(Collectors.toList());
            // Save private message to send when to-user logs in
            if (disconnectedUsers.size() > 0) {
                long key = 0;
                try {
                    key = dodexDatabase.addMessage(session, messageUser, computedMessage, db);
                } catch (InterruptedException | SQLException e) {
                    e.printStackTrace();
                }
                try {
                    dodexDatabase.addUndelivered(session, disconnectedUsers, key, db);
                } catch (final SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private MessageUser setMessageUser(Session session) {
        final MessageUser messageUser = dodexDatabase.createMessageUser();
        final Session sess = sessions.get(session.getId());
        String handle = "";
        String id = "";

        handle = sess.getRequestParameterMap().get("handle").get(0);
        id = sess.getRequestParameterMap().get("id").get(0);
        
        messageUser.setName(handle);
        messageUser.setPassword(id);
        messageUser.setIp("Unknown");

        return messageUser;
    }
}
