package dmo.fs.db.wsnext;

import dmo.fs.db.MessageUser;
import io.quarkus.websockets.next.WebSocketConnection;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.sqlclient.Pool;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface DodexDatabase {

	static String getAllUsers() { return null; }

	static String getUserByName() { return null; }
    
    static String getUserById() { return null; }

    static String getInsertUser() { return null; }
    
	static String getRemoveUndelivered() { return null; }

	static String getRemoveMessage() { return null; }

    static String getUndeliveredMessage() { return null; }
	
	static String getDeleteUser() { return null; }

	Promise<MessageUser> addUser(MessageUser messageUser) throws SQLException, InterruptedException;

	Promise<Long> deleteUser(MessageUser messageUser) throws SQLException, InterruptedException;

	Promise<Long> addMessage(MessageUser messageUser, String message) throws SQLException, InterruptedException;

	Promise<Void> addUndelivered(List<String> undelivered, Long messageId) throws SQLException;

	Promise<Long> getUserIdByName(String name) throws InterruptedException, SQLException;

	Promise<Void> addUndelivered(Long userId, Long messageId) throws SQLException, InterruptedException;

	Promise<Map<String, Integer>> processUserMessages(WebSocketConnection ws, MessageUser messageUser);

	<T> T getPool();

	MessageUser createMessageUser();

	Promise<MessageUser> selectUser(MessageUser messageUser) throws InterruptedException, SQLException;

	Promise<StringBuilder> buildUsersJson(MessageUser messageUser) throws InterruptedException, SQLException;

	Promise<Pool> databaseSetup();

	<T> void setupSql(T pool);
}