package dmo.fs.db.wsnext.firebase;

import com.google.cloud.firestore.Firestore;
import dmo.fs.db.MessageUser;
import dmo.fs.utils.FirebaseMessage;
import dmo.fs.utils.FirebaseUser;
import io.vertx.core.Future;
import io.vertx.reactivex.core.Vertx;
import io.quarkus.websockets.next.WebSocketConnection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public interface DodexFirebase {
	Future<MessageUser> deleteUser(WebSocketConnection ws, MessageUser messageUser) throws InterruptedException, ExecutionException;

    FirebaseUser updateUser(WebSocketConnection ws, FirebaseUser firebaseUser) throws InterruptedException, ExecutionException;

	Future<FirebaseMessage> addMessage(WebSocketConnection ws, MessageUser messageUser, String message, List<String> undelivered) throws InterruptedException, ExecutionException;

	Future<Map<String, Integer>> processUserMessages(WebSocketConnection ws, FirebaseUser firebaseUser) throws Exception;

	MessageUser createMessageUser();

	Future<FirebaseUser> selectUser(MessageUser messageUser, WebSocketConnection ws) throws InterruptedException, SQLException, ExecutionException;

	Future<StringBuilder> buildUsersJson(WebSocketConnection ws, MessageUser messageUser) throws InterruptedException;

	void setVertx(Vertx vertx);

	Vertx getVertx();

    void setFirestore(Firestore firestore);
}
