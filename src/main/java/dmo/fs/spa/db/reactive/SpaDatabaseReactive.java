package dmo.fs.spa.db.reactive;

import java.sql.SQLException;

import dmo.fs.spa.utils.SpaLogin;
import io.vertx.core.Future;

public interface SpaDatabaseReactive {

	SpaLogin createSpaLogin();

	Future<SpaLogin> getLogin(SpaLogin spaLogin) throws InterruptedException, SQLException;

	Future<SpaLogin> addLogin(SpaLogin spaLogin) throws InterruptedException, SQLException;
	
	Future<SpaLogin> removeLogin(SpaLogin spaLogin) throws InterruptedException, SQLException;

    Future<Void> databaseSetup() throws InterruptedException, SQLException;

    <T> T getPool();
}