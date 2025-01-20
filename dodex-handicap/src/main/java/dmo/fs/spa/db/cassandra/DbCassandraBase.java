
package dmo.fs.spa.db.cassandra;

import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.spa.utils.SpaLogin;
import dmo.fs.utils.ColorUtilConstants;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Promise;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.eventbus.DeliveryContext;
import io.vertx.reactivex.core.eventbus.EventBus;

public abstract class DbCassandraBase {
	private static final Logger logger = LoggerFactory.getLogger(DbCassandraBase.class.getName());
	private final Map<String, Promise<SpaLogin>> mUserPromises = new ConcurrentHashMap<>();
	private static final String GETLOGIN = "getlogin";
	private static final String ADDLOGIN = "addlogin";
	private static final String REMOVELOGIN = "removelogin";

	private Vertx vertx;
	private String vertxConsumer = "";

	public Promise<SpaLogin> getLogin(SpaLogin spaLogin, EventBus eb) {
		if ("".equals(vertxConsumer)) {
			eb.addOutboundInterceptor(getEbConsumer());
			vertxConsumer = "vertx";
		}
		Promise<SpaLogin> promise = Promise.promise();

		mUserPromises.put(spaLogin.getPassword() + GETLOGIN, promise);
		JsonObject jsonPayload = setMessage(GETLOGIN, spaLogin);

		eb.send("akka", jsonPayload);

		return promise;
	}

	public Promise<SpaLogin> addLogin(SpaLogin spaLogin, EventBus eb) {
		Promise<SpaLogin> promise = Promise.promise();

		mUserPromises.put(spaLogin.getPassword() + ADDLOGIN, promise);
		JsonObject jsonPayload = setMessage(ADDLOGIN, spaLogin);

		eb.send("akka", jsonPayload);

		return promise;
	}

	public Promise<SpaLogin> removeLogin(SpaLogin spaLogin, EventBus eb) {

		Promise<SpaLogin> promise = Promise.promise();

		mUserPromises.put(spaLogin.getPassword() + REMOVELOGIN, promise);
		JsonObject jsonPayload = setMessage(REMOVELOGIN, spaLogin);

		eb.send("akka", jsonPayload);

		return promise;
	}

	private JsonObject setMessage(String cmd, SpaLogin spaLogin) {
		JsonObject message = new JsonObject();

		message.put("cmd", cmd).put("password", spaLogin.getPassword()).put("name", spaLogin.getName());
		return message;
	}

	public Vertx getVertx() {
		return vertx;
	}

	public void setVertx(Vertx vertx) {
		this.vertx = vertx;
	}

	public abstract SpaLogin createSpaLogin();

	public Handler<DeliveryContext<JsonObject>> getEbConsumer() {
		return event -> {
			if ("vertx".equals(event.message().address())) {
				String cmd = event.message().body().getString("cmd");

				switch (cmd) {
					case "string":
						logger.info("{}{}{}", ColorUtilConstants.BLUE_BOLD_BRIGHT,
								event.message().body().getJsonObject("msg"), ColorUtilConstants.RESET);
						break;
					case GETLOGIN:
					case ADDLOGIN:
					case REMOVELOGIN:
						JsonObject message = event.message().body().getJsonObject("msg");
						SpaLogin spaLogin = createSpaLogin();
						spaLogin.setId(0L);
						spaLogin.setPassword(message.getString("password"));
						spaLogin.setName(message.getString("name"));
						spaLogin.setLastLogin(new Timestamp(message.getLong("last_login")));
						spaLogin.setStatus(message.getString("status"));
						// Return Akka data to requester
						mUserPromises.get(spaLogin.getPassword() + cmd).tryComplete(spaLogin);
						mUserPromises.remove(spaLogin.getPassword() + cmd);
						break;
					default:
						break;
				}
			}
			event.next();
		};
	}
}
