package dmo.fs.db.handicap;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import io.vertx.sqlclient.PoolOptions;

import java.sql.SQLException;

public interface HandicapDatabase {
	Uni<String> checkOnTables() throws InterruptedException, SQLException;

	<T> T getPool4();

	void setVertx(Vertx vertx);

	Vertx getVertx();

	void setVertxR(io.vertx.rxjava3.core.Vertx vertx);

	io.vertx.rxjava3.core.Vertx getVertxR();

	static <T> void setupSql(T pool) {
        //
	}

	<T> void setConnectOptions(T connectOptions);

	void setPoolOptions(PoolOptions poolOptions);
}