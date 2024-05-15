package dmo.fs;

import io.quarkus.test.junit.QuarkusTest;

import org.junit.jupiter.api.Disabled;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.db.wsnext.postgres.DbPostgres;
import dmo.fs.db.MessageUser;
import dmo.fs.utils.ColorUtilConstants;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.db2client.DB2Pool;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowIterator;
import io.vertx.db2client.DB2ConnectOptions;
import io.vertx.sqlclient.PoolOptions;

@QuarkusTest
public class DbQuarkusTest  extends DbPostgres {
	private static final Logger logger = LoggerFactory.getLogger(DbQuarkusTest.class.getName());
    @Disabled("Disabled until VertxExtension works with reactivex")
    // @Test
    public void testTestEndpoint() {
        given()
          .when().get("/test")
          .then()
             .statusCode(200).body(containsString("dodex--open"))
            ;
    }

    public Promise<Pool> databaseSetup() {

		Promise<Pool> promise = Promise.promise();
		DB2Pool pool = getConfiguredPool();

        pool.getConnection().flatMap(conn -> {
			conn.query(CHECKUSERSQL).execute().flatMap(row -> {
				RowIterator<Row> ri = row.iterator();
				String val = null;
				while (ri.hasNext()) {
					val = ri.next().getString(0);
				}
				if (val == null) {
					final String usersSql = getCreateTable("USERS");
					conn.query(usersSql).execute().onFailure().invoke(error -> {
						logger.error("{}Users Table Error: {}{}", ColorUtilConstants.RED, error,
								ColorUtilConstants.RESET);
					}).onItem().invoke(c -> {
						if (c.next().rowCount() > 0) {
							logger.info("{}Users Table Added.{}", ColorUtilConstants.BLUE_BOLD_BRIGHT,
									ColorUtilConstants.RESET);
						}
					}).subscribeAsCompletionStage().complete(null);
				}
				return Uni.createFrom().item(conn);
			}).onFailure().invoke(error -> {
				logger.error("{}Users Table Error: {}{}", ColorUtilConstants.RED, error, ColorUtilConstants.RESET);
			}).subscribeAsCompletionStage().complete(null);
			return Uni.createFrom().item(conn);
		}).flatMap(conn -> {
			promise.complete(pool);
			conn.close().onFailure().invoke(err -> err.printStackTrace()).subscribeAsCompletionStage().complete(null);
			return Uni.createFrom().item(pool);
		}).subscribeAsCompletionStage().complete(null);

		return promise;
	}

	private static DB2Pool getConfiguredPool() {

		PoolOptions poolOptions = new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

		DB2ConnectOptions connectOptions;
		connectOptions = new DB2ConnectOptions()
				.setHost("//localhost")
				.setPort(25010)
				.setUser("user")
				.setPassword("password")
				.setDatabase("/test")
				.setSsl(false);

		Vertx vertx = Vertx.vertx();
		return DB2Pool.pool(vertx, connectOptions, poolOptions);
	}

	@Override
	public <T> T getPool() {
		return null;
	}

	@Override
	public MessageUser createMessageUser() {
		return null;
	}
}
