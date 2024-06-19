package dmo.fs.db.wsnext.neo4j;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.reactive.DbConfiguration;
import dmo.fs.db.MessageUser;
import dmo.fs.db.MessageUserImpl;
import dmo.fs.quarkus.Server;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Promise;
import jakarta.ws.rs.core.Response.Status;
import org.neo4j.driver.*;
import org.neo4j.driver.async.AsyncSession;
import org.neo4j.driver.exceptions.NoSuchRecordException;
import org.neo4j.driver.reactive.ReactiveSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

public class DodexDatabaseNeo4j extends DbNeo4j {
	protected final static Logger logger = LoggerFactory.getLogger(DodexDatabaseNeo4j.class.getName());
	protected Properties dbProperties = new Properties();
	protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
	protected Map<String, String> dbMap;
	protected JsonNode defaultNode;
	protected String webEnv = Server.isProduction() ? "prod" : "dev";
	protected DodexUtil dodexUtil = new DodexUtil();

	public DodexDatabaseNeo4j(Map<String, String> dbOverrideMap, Properties dbOverrideProps)
			throws IOException {
		super();

		defaultNode = dodexUtil.getDefaultNode();
		dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
		dbProperties = dodexUtil.mapToProperties(dbMap);

		if (dbOverrideProps != null && !dbOverrideProps.isEmpty()) {
			this.dbProperties = dbOverrideProps;
		}
		if (dbOverrideMap != null) {
			this.dbOverrideMap = dbOverrideMap;
		}

		assert dbOverrideMap != null;
		DbConfiguration.mapMerge(dbMap, dbOverrideMap);
	}

	public DodexDatabaseNeo4j() throws IOException {
		super();

		defaultNode = dodexUtil.getDefaultNode();
		dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
		dbProperties = dodexUtil.mapToProperties(dbMap);
	}

	@Override
	public MessageUser createMessageUser() {
		return new MessageUserImpl();
	}

	@Override
	public Promise<Driver> databaseSetup() {
		if ("dev".equals(webEnv)) {
			// dbMap.put("dbname", "myDbname"); // this wiil be merged into the default map
			DbConfiguration.configureTestDefaults(dbMap, dbProperties);
		} else {
			DbConfiguration.configureDefaults(dbMap, dbProperties); // Prod
		}

		Promise<Driver> promise = Promise.promise();
		Driver driver = getDriver(dbMap, dbProperties);

		AsyncSession session = driver.session(AsyncSession.class); // driver.asyncSession();

		session.runAsync(getCheckConstraints())
			.thenCompose(fn -> fn.singleAsync())
			.handle((record, exception) -> {
				if (exception != null) {
					Throwable source = exception;
					if (exception instanceof CompletionException) {
						source = exception.getCause();
					}
					Status status = Status.INTERNAL_SERVER_ERROR;
					if (source instanceof NoSuchRecordException) {
						status = Status.NOT_FOUND;
					}
					logger.error("Error Checking Constraints: {}{}{}", ColorUtilConstants.RED,
							status.getReasonPhrase(), ColorUtilConstants.RESET);
					source.printStackTrace();
					return -1;
				} else {
					return record.get(0).asInt();
				}
			}).whenCompleteAsync((count, exception) -> {
				if (count < 4) {
					session
					.runAsync(getCheckApoc())
					.thenCompose(fn -> fn.singleAsync())
					.handle((record, exception2) -> {
						if (exception2 != null) {
							Throwable source = exception2;
							if (exception2 instanceof CompletionException) {
								source = ((CompletionException) exception2).getCause();
							}
							Status status = Status.INTERNAL_SERVER_ERROR;
							if (source instanceof NoSuchRecordException) {
								status = Status.NOT_FOUND;
							}
							logger.error("Error Checking Apoc procedures: {}{}{}", ColorUtilConstants.RED,
									status.getReasonPhrase(), ColorUtilConstants.RESET);
							source.printStackTrace();
							return -1;
						} else {
							session.closeAsync();
							return record.get(0).asInt();
						}
					}).whenCompleteAsync((apocCount, exception3) -> {
						if(apocCount != -1) {
							if(apocCount == 1) {
								apocConstraints(driver, promise);
							} else {
								createConstraints(driver, promise);
							}
						}
					});
				} else {
					promise.complete(driver);
				}
			});

		return promise;
	}
	// If apoc plugin installed
	protected void apocConstraints(Driver driver, Promise<Driver> promise) {
		Multi.createFrom().resource(() -> driver.session(ReactiveSession.class),
			session -> session.executeWrite(tx -> {
//				Result resultConstraints = tx.run(getCreateConstraints());
				return Multi.createFrom().item(tx.run(getCreateConstraints()))
						.map(record -> "constraints");
			}))
			.withFinalizer(session -> {
				promise.complete(driver);
				session.close();
				return Uni.createFrom().nothing();
			})
			.onFailure().invoke(Throwable::printStackTrace)
			.subscribe().asStream();
	}
	// If minimum install
	protected void createConstraints(Driver driver, Promise<Driver> promise) {
		Session session = driver.session();
		if (session != null) {
			try (Transaction tx = session.beginTransaction()) {
				tx.run(getUserNameConstraint());
				tx.run(getUserPasswordConstraint());
				tx.run(getMessageNameConstraint());
				tx.run(getMessagePasswordConstraint());
				tx.commit();
			} catch(Exception ex) {
				ex.printStackTrace();
			}
			session.close();
			promise.complete(driver);
		}
	}

	protected Driver getDriver(Map<String, String> dbMap, Properties dbProperties) {
		String uri = String.join(":", dbMap.get("protocol"),
				String.format("//%s", dbMap.get("host")),
				dbMap.get("port"));

		return GraphDatabase.driver(uri,
				AuthTokens.basic(dbProperties.getProperty("user"), dbProperties.getProperty("password")));
	}
}
