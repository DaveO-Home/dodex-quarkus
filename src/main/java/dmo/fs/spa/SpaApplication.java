package dmo.fs.spa;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.spa.db.SpaDatabase;
import dmo.fs.spa.db.SpaDbConfiguration;
import dmo.fs.spa.utils.SpaLogin;
import dmo.fs.spa.utils.SpaLoginImpl;
import dmo.fs.utils.DodexUtil;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.core.Vertx;

public class SpaApplication {
    private static final Logger logger = LoggerFactory.getLogger(SpaApplication.class.getName());
    private SpaDatabase spaDatabase;
    private Vertx vertx;

    public SpaApplication() throws InterruptedException, IOException, SQLException {
        // spaDatabase = SpaDbConfiguration.getSpaDb();

        // if(!(spaDatabase instanceof SpaDatabase)) {
        //     throw new InterruptedException(String.format("%s - %s","Database not supported", SpaDbConfiguration.getSpaDb()));
        // }
    }

    public void setDatabase(SpaDatabase spaDatabase) throws InterruptedException {
        this.spaDatabase = spaDatabase;
        if(!(spaDatabase instanceof SpaDatabase)) {
            DodexUtil du = new DodexUtil();
		    String defaultDbName = null;
            try {
                defaultDbName = du.getDefaultDb();
            } catch (IOException e) {
                e.printStackTrace();
            }
            throw new InterruptedException(String.format("%s - %s","Database not supported", defaultDbName));
        }
    }

    public Promise<SpaLogin> getLogin(String queryData) {
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
        
        return spaDatabase.getLogin(spaLogin);
    }

    public Promise<SpaLogin> addLogin(String bodyData) {
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

        return spaDatabase.addLogin(spaLogin);
    }

    public Promise<SpaLogin> unregisterLogin(String queryData) {
        SpaLogin spaLogin = createSpaLogin();
        Map<String, String> queryMap = mapQuery(queryData);

        String name = queryMap.get("user");
        String password = queryMap.get("password");

        spaLogin.setName(name);
        spaLogin.setPassword(password);

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