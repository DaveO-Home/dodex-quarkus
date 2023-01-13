package dmo.fs.db.generate;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import io.vertx.core.Future;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.http.ServerWebSocket;

public interface HandicapDatabase {
	Future<String> checkOnTables() throws InterruptedException, SQLException;

	<T> T getPool4();

	void setVertx(Vertx vertx);

	Vertx getVertx();
}