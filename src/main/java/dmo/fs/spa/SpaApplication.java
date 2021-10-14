package dmo.fs.spa;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.enterprise.inject.spi.CDI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.db.DbConfiguration;
import dmo.fs.router.CassandraRouter;
import dmo.fs.router.FirebaseRouter;
import dmo.fs.spa.db.SpaCassandra;
import dmo.fs.spa.db.SpaDatabase;
import dmo.fs.spa.db.SpaDbConfiguration;
import dmo.fs.spa.db.SpaFirebase;
import dmo.fs.spa.utils.SpaLogin;
import dmo.fs.spa.utils.SpaLoginImpl;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.core.Vertx;
import io.vertx.reactivex.core.eventbus.EventBus;

public class SpaApplication {
    private static final Logger logger = LoggerFactory.getLogger(SpaApplication.class.getName());
    private EventBus eb;
    private SpaDatabase spaDatabase;
    private Vertx vertx;
    private SpaCassandra spaCassandra;
    private Boolean isCassandra = false;
    private Boolean isFirebase = false;
    private SpaFirebase spaFirebase;

    public SpaApplication() throws InterruptedException, IOException, SQLException {
        Object spaDb =  SpaDbConfiguration.getSpaDb();
        if(spaDb instanceof SpaDatabase) {
            spaDatabase = (SpaDatabase)spaDb;
        } else if(spaDb instanceof SpaCassandra) {
            spaCassandra = (SpaCassandra)spaDb;
            isCassandra = true;
            CassandraRouter cassandraRouter = CDI.current().select(CassandraRouter.class).get();
            eb = cassandraRouter.getEb();
        } else if(spaDb instanceof SpaFirebase) {
            spaFirebase = SpaDbConfiguration.getSpaDb();
            FirebaseRouter firebaseRouter = CDI.current().select(FirebaseRouter.class).get();
            spaFirebase.setDbf(firebaseRouter.getDbf());
            isFirebase = true;
        } else {
            throw new InterruptedException(String.format("%s - %s","Database not supported", SpaDbConfiguration.getSpaDb()));
        }
    }

    public Future<Void> setDatabase(SpaDatabase spaDatabase) {
        if (DbConfiguration.isUsingCassandra() || DbConfiguration.isUsingFirebase()) {
            io.vertx.core.Promise<Void> setupPromise = io.vertx.core.Promise.promise();
            setupPromise.complete();
            return setupPromise.future();
        }
        return spaDatabase.databaseSetup();
    }

    public Promise<SpaLogin> getLogin(String queryData) throws InterruptedException, ExecutionException, SQLException {
        SpaLogin spaLogin = createSpaLogin();
        JsonObject loginObject = new JsonObject(String.join("", "{\"data\":", queryData, "}"));
        String name;
        String password;
        if(!queryData.contains("[")) {
            name = loginObject.getJsonObject("data").getString("name");
            password = loginObject.getJsonObject("data").getString("password");
        } else {
            JsonArray data = loginObject.getJsonArray("data");
            name = data.getJsonObject(0).getString("value");
            password = data.getJsonObject(1).getString("value");
        }

        spaLogin.setName(name);
        spaLogin.setPassword(password);
        
        if (isCassandra.equals(true)) {
            return spaCassandra.getLogin(spaLogin, eb);
        } else if (isFirebase.equals(true)) {
            return spaFirebase.getLogin(spaLogin);
        }
        return spaDatabase.getLogin(spaLogin);
    }

    public Promise<SpaLogin> addLogin(String bodyData) throws InterruptedException, ExecutionException {
        SpaLogin spaLogin = createSpaLogin();
        JsonObject loginObject = new JsonObject(String.join("", "{\"data\":", bodyData, "}"));
        String userName = null;
        String password = null;
        JsonArray data = loginObject.getJsonArray("data");
        int size = loginObject.getJsonArray("data").getList().size();

        for (int i = 0; i < size; i++) {
            String name = data.getJsonObject(i).getString("name");
            switch (name) {
                case "username":
                    userName = data.getJsonObject(i).getString("value");
                    break;
                case "password":
                    password = data.getJsonObject(i).getString("value");
                    break;
                default:
                    break;
            }
        }

        spaLogin.setName(userName);
        spaLogin.setPassword(password);
        spaLogin.setId(0l);
        spaLogin.setLastLogin(new Date());
        spaLogin.setStatus("0");
        if (isCassandra.equals(true)) {
            return spaCassandra.addLogin(spaLogin, eb);
        } else if (isFirebase.equals(true)) {
            spaLogin.setId("0");
            return spaFirebase.addLogin(spaLogin);
        }

        return spaDatabase.addLogin(spaLogin);
    }

    public Promise<SpaLogin> unregisterLogin(String queryData) throws InterruptedException, ExecutionException {
        SpaLogin spaLogin = createSpaLogin();
        Map<String, String> queryMap = mapQuery(queryData);

        String name = queryMap.get("user");
        String password = queryMap.get("password");

        spaLogin.setName(name);
        spaLogin.setPassword(password);
        
        if (isCassandra.equals(true)) {
            return spaCassandra.removeLogin(spaLogin, eb);
        } else if (isFirebase.equals(true)) {
            return spaFirebase.removeLogin(spaLogin);
        }
        return spaDatabase.removeLogin(spaLogin);
    }

    public Map<String, String> mapQuery(String queryString) {
        return Arrays.stream(queryString.split("&")).map(s -> s.split("="))
                .collect(Collectors.toMap(s -> s[0], s -> s[1]));
    }

    // @Override
	public SpaLogin createSpaLogin() {
		return new SpaLoginImpl();
	}

    public Vertx getVertx() {
		return vertx;
	}
    
	public void setVertx(Vertx vertx) {
		this.vertx = vertx;
	}
}