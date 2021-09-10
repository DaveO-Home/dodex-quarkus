package dmo.fs.db.reactive;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.websocket.Session;

import dmo.fs.db.MessageUser;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.jdbcclient.JDBCPool;

public interface DodexReactiveDatabase {

	String getAllUsers();

	String getUserByName();
    
    String getUserById();

    String getInsertUser();
    
	String getRemoveUndelivered();

	String getRemoveMessage();

    String getUndeliveredMessage();
	
	String getDeleteUser();

	Future<MessageUser> addUser(Session session, MessageUser messageUser);

	Future<Long> deleteUser(Session session, MessageUser messageUser);

	Future<Long> addMessage(Session session, MessageUser messageUser, String message);

	Future<Void> addUndelivered(Session session, List<String> undelivered, Long messageId);

	Future<Long> getUserIdByName(String name) throws InterruptedException, SQLException;

	Future<Void> addUndelivered(Long userId, Long messageId) throws SQLException, InterruptedException;

	Future<Map<String, Integer>> processUserMessages(Session session, MessageUser messageUser);

	<T> T getPool();

	MessageUser createMessageUser();

	Future<MessageUser> selectUser(MessageUser messageUser, Session session) throws InterruptedException, SQLException;

	Future<StringBuilder> buildUsersJson(MessageUser messageUser) throws InterruptedException, SQLException;

	void setVertx(Vertx vertx);

	Vertx getVertx();

	Promise<JDBCPool> databaseSetup();
}