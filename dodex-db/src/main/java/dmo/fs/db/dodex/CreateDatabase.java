package dmo.fs.db.dodex;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

import java.sql.SQLException;

public interface CreateDatabase {
	Uni<String> checkOnTables() throws InterruptedException, SQLException;

	<T> T getPool4();

	void setVertx(Vertx vertx);

	Vertx getVertx();

	void setVertxR(io.vertx.reactivex.core.Vertx vertx);

	io.vertx.reactivex.core.Vertx getVertxR();

	Promise<Pool> databaseSetup();

	<T> T getConnectOptions();

	PoolOptions getPoolOptions();
}