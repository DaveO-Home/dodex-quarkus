package dmo.fs.db;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.websocket.Session;

import org.davidmoten.rx.jdbc.Database;
import org.davidmoten.rx.jdbc.pool.NonBlockingConnectionPool;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public interface DodexDatabase {

	String getAllUsers();

	String getUserByName();

    String getInsertUser();
    
	String getRemoveUndelivered();

	String getRemoveMessage();

    String getUndeliveredMessage();
	
	String getDeleteUser();

    String getDbName() throws IOException;

	Future<MessageUser> addUser(Session session, Database db, MessageUser messageUser) throws SQLException, InterruptedException;

	Future<Long> deleteUser(Session session, Database db, MessageUser messageUser) throws SQLException, InterruptedException;

	Future<Long> addMessage(Session session, MessageUser messageUser, String message, Database db) throws SQLException, InterruptedException;

	Future<Void> addUndelivered(Session session, List<String> undelivered, Long messageId, Database db) throws SQLException;

	Future<Long> getUserIdByName(String name, Database db) throws InterruptedException, SQLException;

	Future<Void> addUndelivered(Long userId, Long messageId, Database db) throws SQLException, InterruptedException;

	Future<Map<String, Integer>> processUserMessages(Session session, Database db, MessageUser messageUser);

	Database getDatabase();

	NonBlockingConnectionPool getPool();

	MessageUser createMessageUser();

	Future<MessageUser> selectUser(MessageUser messageUser, Session session, Database db) throws InterruptedException, SQLException;

	Future<StringBuilder> buildUsersJson(Database db, MessageUser messageUser) throws InterruptedException, SQLException;

	void setVertx(Vertx vertx);

	Vertx getVertx();

	void callSetupSql() throws SQLException;
}