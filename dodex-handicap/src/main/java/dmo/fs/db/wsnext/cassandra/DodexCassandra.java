package dmo.fs.db.wsnext.cassandra;

import dmo.fs.db.MessageUser;
import io.quarkus.websockets.next.WebSocketConnection;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.ext.eventbus.bridge.tcp.BridgeEvent;

import java.sql.SQLException;
import java.util.List;

public interface DodexCassandra {

	Future<JsonObject> deleteUser(WebSocketConnection ws, EventBus eb, MessageUser messageUser) throws SQLException, InterruptedException;

	Future<JsonObject> addMessage(WebSocketConnection ws, MessageUser messageUser, String message, List<String> undelivered, EventBus eb) throws SQLException, InterruptedException;

	Future<JsonObject> deleteDelivered(WebSocketConnection ws, EventBus eb, MessageUser messageUser);

	Future<JsonObject> processUserMessages(WebSocketConnection ws, EventBus eb, MessageUser messageUser) throws Exception;

	MessageUser createMessageUser();

	Future<MessageUser> selectUser(MessageUser messageUser, WebSocketConnection ws, EventBus eb) throws InterruptedException, SQLException;

	Future<JsonObject> buildUsersJson(WebSocketConnection ws, EventBus eb, MessageUser messageUser) throws InterruptedException, SQLException;

	void setVertx(Vertx vertx);

	Vertx getVertx();

	Handler<BridgeEvent> getEbConsumer();
}