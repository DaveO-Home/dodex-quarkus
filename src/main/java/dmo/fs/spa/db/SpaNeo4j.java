package dmo.fs.spa.db;

import java.sql.SQLException;
import java.util.concurrent.ExecutionException;

import org.neo4j.driver.Driver;

import dmo.fs.spa.utils.SpaLogin;
import io.vertx.mutiny.core.Promise;

public interface SpaNeo4j {

	SpaLogin createSpaLogin();

	Promise<SpaLogin> getLogin(SpaLogin spaLogin) throws ExecutionException, InterruptedException, SQLException;

	Promise<SpaLogin> addLogin(SpaLogin spaLogin) throws InterruptedException, ExecutionException;
	
	Promise<SpaLogin> removeLogin(SpaLogin spaLogin) throws InterruptedException, ExecutionException;

	Promise<Void> databaseSetup();

	void setDriver(Driver driver);

}
