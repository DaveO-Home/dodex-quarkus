package dmo.fs.spa.db;

import dmo.fs.spa.utils.SpaLogin;
import io.vertx.core.Future;
import io.vertx.mutiny.core.Promise;

public interface SpaDatabase {

	SpaLogin createSpaLogin();

	Promise<SpaLogin> getLogin(SpaLogin spaLogin);

	Promise<SpaLogin> addLogin(SpaLogin spaLogin);
	
	Promise<SpaLogin> removeLogin(SpaLogin spaLogin);

	Future<Void> databaseSetup();

	static <T> void setupSql(T pool) {
		//
	}
}