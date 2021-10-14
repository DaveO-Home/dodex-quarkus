package dmo.fs.spa.db;

import java.sql.SQLException;
import java.util.concurrent.ExecutionException;

import com.google.cloud.firestore.Firestore;

import dmo.fs.spa.utils.SpaLogin;
import io.vertx.mutiny.core.Promise;
import io.vertx.reactivex.core.Vertx;

public interface SpaFirebase {

	SpaLogin createSpaLogin();

	Promise<SpaLogin> getLogin(SpaLogin spaLogin) throws ExecutionException, InterruptedException, SQLException;

	Promise<SpaLogin> addLogin(SpaLogin spaLogin) throws InterruptedException, ExecutionException;
	
	Promise<SpaLogin> removeLogin(SpaLogin spaLogin) throws InterruptedException, ExecutionException;

    void setVertx(Vertx vertx);

	Vertx getVertx();

	void setDbf(Firestore dbf);
}