package dmo.fs.db;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.websocket.Session;

import org.neo4j.driver.Driver;

import io.vertx.mutiny.core.Promise;

public interface DodexNeo4j {

	Promise<MessageUser> deleteUser(Session ws, MessageUser messageUser) throws InterruptedException, ExecutionException;

    MessageUser updateUser(MessageUser messageUser);

	Promise<MessageUser> addMessage(Session ws, MessageUser messageUser, String message, List<String> undelivered) throws InterruptedException, ExecutionException;

	Promise<Map<String, Integer>> processUserMessages(Session ws, MessageUser messageUser) throws Exception;

	MessageUser createMessageUser();

	Promise<MessageUser> selectUser(MessageUser messageUser, Session ws) throws InterruptedException, SQLException, ExecutionException;

	Promise<StringBuilder> buildUsersJson(Session ws, MessageUser messageUser) throws InterruptedException;

    Promise<Driver> databaseSetup();

	void setDriver(Driver driver);

}
