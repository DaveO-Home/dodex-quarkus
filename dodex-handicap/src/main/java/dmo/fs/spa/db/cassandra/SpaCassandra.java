package dmo.fs.spa.db.cassandra;

import io.vertx.reactivex.core.eventbus.EventBus;

import dmo.fs.spa.utils.SpaLogin;
import io.vertx.reactivex.core.Vertx;
import io.vertx.mutiny.core.Promise;

public interface SpaCassandra {

	SpaLogin createSpaLogin();

	Promise<SpaLogin> getLogin(SpaLogin spaLogin, EventBus eb) throws InterruptedException;

	Promise<SpaLogin> addLogin(SpaLogin spaLogin, EventBus eb) throws InterruptedException;
	
	Promise<SpaLogin> removeLogin(SpaLogin spaLogin, EventBus eb) throws InterruptedException;

	void setVertx(Vertx vertx);

	Vertx getVertx();

}