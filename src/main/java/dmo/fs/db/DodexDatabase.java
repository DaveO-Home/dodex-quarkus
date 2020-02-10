package dmo.fs.db;

import java.sql.SQLException;
import java.util.List;

import javax.websocket.Session;

import org.davidmoten.rx.jdbc.Database;
import org.davidmoten.rx.jdbc.pool.NonBlockingConnectionPool;

import io.vertx.core.http.ServerWebSocket;

public interface DodexDatabase {

	public String getAllUsers();

	public String getUserByName();

    public String getInsertUser();
    
	public String getRemoveUndelivered();

	public String getRemoveMessage();

    public String getUndeliveredMessage();
	
	public String getDeleteUser();

	public Long addUser(Session sesson, Database db, MessageUser messageUser) throws SQLException, InterruptedException;

	public long deleteUser(Session session, Database db, MessageUser messageUser) throws SQLException, InterruptedException;

	public long addMessage(Session session, MessageUser messageUser, String message, Database db) throws SQLException, InterruptedException;

	public int addUndelivered(Session session, List<String> undelivered, Long messageId, Database db) throws SQLException;

	public Long getUserIdByName(String name, Database db) throws InterruptedException, SQLException;

	public void addUndelivered(Long userId, Long messageId, Database db) throws SQLException, InterruptedException;

	public int processUserMessages(Session session, Database db, MessageUser messageUser);

	public Database getDatabase();

	public NonBlockingConnectionPool getPool();

	public MessageUser createMessageUser();

	public MessageUser selectUser(MessageUser messageUser, Session session, Database db) throws InterruptedException, SQLException;

	public StringBuilder buildUsersJson(Database db, MessageUser messageUser) throws InterruptedException, SQLException;

}