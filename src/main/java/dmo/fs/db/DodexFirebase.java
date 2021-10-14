package dmo.fs.db;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.websocket.Session;

import com.google.cloud.firestore.Firestore;

import dmo.fs.utils.FirebaseMessage;
import dmo.fs.utils.FirebaseUser;
import io.vertx.core.Future;
import io.vertx.reactivex.core.Vertx;

public interface DodexFirebase {

	Future<MessageUser> deleteUser(Session ws, MessageUser messageUser) throws InterruptedException, ExecutionException;

    FirebaseUser updateUser(Session ws, FirebaseUser firebaseUser) throws InterruptedException, ExecutionException;

	Future<FirebaseMessage> addMessage(Session ws, MessageUser messageUser, String message, List<String> undelivered) throws InterruptedException, ExecutionException;

	Future<Map<String, Integer>> processUserMessages(Session ws, FirebaseUser firebaseUser) throws Exception;

	MessageUser createMessageUser();

	Future<FirebaseUser> selectUser(MessageUser messageUser, Session ws) throws InterruptedException, SQLException, ExecutionException;

	Future<StringBuilder> buildUsersJson(Session ws, MessageUser messageUser) throws InterruptedException;

	void setVertx(Vertx vertx);

	Vertx getVertx();

    void setFirestore(Firestore firestore);
}