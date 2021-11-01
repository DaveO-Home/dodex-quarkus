
package dmo.fs.db;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.websocket.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.utils.ColorUtilConstants;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.ext.eventbus.bridge.tcp.BridgeEvent;

public abstract class DbCassandraBase {
	private static final Logger logger = LoggerFactory.getLogger(DbCassandraBase.class.getName());
	private Map<String, Promise<MessageUser>> mUserPromises = new ConcurrentHashMap<>();
	private Map<String, Promise<JsonObject>> mJsonPromises = new ConcurrentHashMap<>();
	private String vertxConsumer = "";
	private static final String AKKA = "akka";
	private static final String SELECT_USER = "selectuser";
	private static final String DELETE_USER = "deleteuser";
	private static final String ALL_USERS = "allusers";
	private static final String DELETE_DELIVERED = "deletedelivered";
	private static final String DELIVER_MESS = "delivermess";
	private static final String ADD_MESSAGE = "addmessage";
	private Vertx vertx;
	@Inject
	EventBus eb;

	public Future<JsonObject> deleteUser(Session session, EventBus eb, MessageUser messageUser) {
		Promise<JsonObject> promise = Promise.promise();

		mJsonPromises.put(session.getId() + DELETE_USER, promise);
		JsonObject mess = setMessage(DELETE_USER, messageUser, session);
		JsonObject jsonPayLoad = mess.put("msg", mess);

		eb.send(AKKA, jsonPayLoad);

		return promise.future();
	}

	public Future<JsonObject> addMessage(Session session, MessageUser messageUser, String message,
			List<String> undelivered, EventBus eb) {
		Promise<JsonObject> promise = Promise.promise();

		mJsonPromises.put(session.getId() + ADD_MESSAGE, promise);
		JsonObject jsonPayload = setMessage(ADD_MESSAGE, messageUser, session);

		jsonPayload.put("users", undelivered).put("message", message);
		eb.send(AKKA, jsonPayload);

		return promise.future();
	}

	public abstract MessageUser createMessageUser();

	public Future<MessageUser> selectUser(MessageUser messageUser, Session session, EventBus eb) {
		Promise<MessageUser> promise = Promise.promise();
		// This promise will be completed in the eb.consumer listener - see getEbConsumer
		mUserPromises.put(session.getId() + SELECT_USER, promise);

		JsonObject jsonPayload = setMessage(SELECT_USER, messageUser, session);

		// Only one handler for all event bridge sends - see setEbConsumer
		if ("".equals(vertxConsumer)) {
			vertxConsumer = "vertx";
		}
		// Send database request to the Akka client micro-service -
		// the response is passed back to the requester via the promise completed in the
		// consumer handler
		eb.send(AKKA, jsonPayload);

		return promise.future();
	}

	public Future<JsonObject> buildUsersJson(Session session, EventBus eb, MessageUser messageUser) {
		Promise<JsonObject> promise = Promise.promise();

		mJsonPromises.put(session.getId() + ALL_USERS, promise);
		JsonObject jsonPayload = setMessage(ALL_USERS, messageUser, session);

		eb.send(AKKA, jsonPayload);

		// wait for user json before sending back to newly connected user
		return promise.future();
	}

	public Future<JsonObject> deleteDelivered(Session session, EventBus eb, MessageUser messageUser) {
		Promise<JsonObject> promise = Promise.promise();

		mJsonPromises.put(session.getId() + DELETE_DELIVERED, promise);
		JsonObject jsonPayload = setMessage(DELETE_DELIVERED, messageUser, session);

		eb.send(AKKA, jsonPayload);

		// wait for user json before sending back to newly connected user
		return promise.future();
	}

	public Future<JsonObject> processUserMessages(Session session, EventBus eb, MessageUser messageUser) {
		Promise<JsonObject> promise = Promise.promise();

		mJsonPromises.put(session.getId() + DELIVER_MESS, promise);
		JsonObject jsonPayload = setMessage(DELIVER_MESS, messageUser, session);

		eb.send(AKKA, jsonPayload);

		return promise.future();
	}

	private JsonObject setMessage(String cmd, MessageUser messageUser, Session session) {
		JsonObject message = new JsonObject();

		message.put("cmd", cmd).put("ip", messageUser.getIp()).put("password", messageUser.getPassword())
				.put("name", messageUser.getName()).put("ws", session.getId() + cmd);
		return message;
	}

	public Vertx getVertx() {
		return vertx;
	}

	public void setVertx(Vertx vertx) {
		this.vertx = vertx;
	}

	public Handler<BridgeEvent> getEbConsumer() {
		return event -> {
			if (event.type().equals(BridgeEventType.RECEIVE) || event.type().equals(BridgeEventType.SEND)) {
				String cmd = event.getRawMessage().getJsonObject("body").getString("cmd");
				String ws = event.getRawMessage().getJsonObject("body").getString("ws");

				switch (cmd) {
					case "string":
						logger.info("{}{}{}", ColorUtilConstants.BLUE_BOLD_BRIGHT,
								event.getRawMessage().getJsonObject("body").getString("msg"), ColorUtilConstants.RESET);
						break;
					case SELECT_USER:
						JsonObject message = event.getRawMessage().getJsonObject("body").getJsonObject("msg");
						MessageUser resultUser = createMessageUser();
						resultUser.setId(-1l);
						resultUser.setIp(message.getString("ip") == null ? "" : message.getString("ip"));
						resultUser.setPassword(message.getString("password"));
						resultUser.setName(message.getString("name"));
						resultUser.setLastLogin(new Timestamp(message.getLong("last_login")));
						mUserPromises.get(ws).tryComplete(resultUser);
						mUserPromises.remove(ws);
						break;
					case ALL_USERS:
					case DELIVER_MESS:
						JsonArray usersArray = event.getRawMessage().getJsonObject("body").getJsonArray("msg");
						JsonObject payload = new JsonObject().put("msg", usersArray);
						mJsonPromises.get(ws).tryComplete(payload);
						mJsonPromises.remove(ws);
						break;
					case ADD_MESSAGE:
					case DELETE_DELIVERED:
					case DELETE_USER:
						payload = new JsonObject().mergeIn(event.getRawMessage().getJsonObject("body"), true);
						mJsonPromises.get(ws).tryComplete(payload.getJsonObject("msg"));
						mJsonPromises.remove(ws);
						break;
					default:
						break;
				}
			} else {
				if (event.type().equals(BridgeEventType.SOCKET_CLOSED)) {
					logger.error("Unexpected Bridge Type received {}", event.type());
				}
			}
			event.complete(true);
		};
	}
}
