package dmo.fs.db.wsnext.neo4j;

import dmo.fs.db.MessageUser;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Promise;
import io.quarkus.websockets.next.WebSocketConnection;
import org.neo4j.driver.Driver;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public interface DodexNeo4j {

	Promise<MessageUser> deleteUser(WebSocketConnection ws, MessageUser messageUser) throws InterruptedException, ExecutionException;

    Uni<MessageUser> updateUser(MessageUser messageUser);

	Promise<MessageUser> addMessage(MessageUser messageUser, String message, List<String> undelivered) throws InterruptedException, ExecutionException;

	Promise<Map<String, Integer>> processUserMessages(WebSocketConnection ws, MessageUser messageUser) throws Exception;

	MessageUser createMessageUser();

	Promise<MessageUser> selectUser(MessageUser messageUser) throws InterruptedException, SQLException, ExecutionException;

	Promise<StringBuilder> buildUsersJson(MessageUser messageUser) throws InterruptedException;

    Promise<Driver> databaseSetup();

	void setDriver(Driver driver);

}
