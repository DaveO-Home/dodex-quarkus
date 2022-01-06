package dmo.fs.db;

import java.sql.SQLException;
import java.util.List;

import javax.websocket.Session;

import io.vertx.reactivex.core.eventbus.EventBus;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.eventbus.bridge.tcp.BridgeEvent;

public interface DodexCassandra {

	Future<JsonObject> deleteUser(Session ws, EventBus eb, MessageUser messageUser) throws SQLException, InterruptedException;

	Future<JsonObject> addMessage(Session ws, MessageUser messageUser, String message, List<String> undelivered, EventBus eb) throws SQLException, InterruptedException;

	Future<JsonObject> deleteDelivered(Session ws, EventBus eb, MessageUser messageUser);

	Future<JsonObject> processUserMessages(Session ws, EventBus eb, MessageUser messageUser) throws Exception;

	MessageUser createMessageUser();

	Future<MessageUser> selectUser(MessageUser messageUser, Session ws, EventBus eb) throws InterruptedException, SQLException;

	Future<JsonObject> buildUsersJson(Session ws, EventBus eb, MessageUser messageUser) throws InterruptedException, SQLException;

	void setVertx(Vertx vertx);

	Vertx getVertx();

	Handler<BridgeEvent> getEbConsumer();
}