package dmo.fs.db.reactive;

import dmo.fs.db.reactive.cubrid.DodexDatabaseCubrid;
import dmo.fs.db.reactive.sqlite3.DodexDatabaseSqlite3;
import dmo.fs.quarkus.Server;
import dmo.fs.utils.DodexUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public abstract class DbConfiguration {
    protected static final Logger logger = LoggerFactory.getLogger(DbConfiguration.class.getName());
    protected static final Map<String, String> map = new ConcurrentHashMap<>();
    protected static Properties properties = new Properties();

    protected static Boolean isUsingSqlite3 = false;
    protected static Boolean isUsingCubrid = false;

    protected static String defaultDb = DodexUtil.defaultDb;
    protected static boolean overrideDefaultDb;
    protected static final DodexUtil dodexUtil = new DodexUtil();
    protected static DodexReactiveDatabase dodexReactiveDatabase;
    protected static final boolean isProduction = Server.isProduction();

    public enum DbTypes {
        SQLITE3("sqlite3"),
        CUBRID("cubrid");

        public final String db;

        DbTypes(String db) {
            this.db = db;
        }
    }

    public static boolean isUsingSqlite3() {
        return isUsingSqlite3;
    }

    public static boolean isUsingCubrid() {
        return isUsingCubrid;
    }

    public static boolean isProduction() {
        return isProduction;
    }

    // @SuppressWarnings("unchecked")
    public static <T> T getDefaultDb(String db) throws InterruptedException, IOException, SQLException {
        defaultDb = db;
        overrideDefaultDb = true;
        return getDefaultDb();
    }

    @SuppressWarnings("unchecked")
    public static <T> T getDefaultDb() throws InterruptedException, IOException, SQLException {
        if (!overrideDefaultDb) {
            defaultDb = dodexUtil.getDefaultDb().toLowerCase();
        }
        if (defaultDb.equals(DbTypes.SQLITE3.db)) {
            dodexReactiveDatabase = new DodexDatabaseSqlite3();
            isUsingSqlite3 = true;
            return (T) dodexReactiveDatabase;
        } else if (defaultDb.equals(DbTypes.CUBRID.db)) {
            dodexReactiveDatabase = new DodexDatabaseCubrid();
            isUsingCubrid = true;
            return (T) dodexReactiveDatabase;
        }

        return (T) dodexReactiveDatabase;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getDefaultDb(Map<String, String> overrideMap, Properties overrideProps)
      throws InterruptedException, IOException, SQLException {
        defaultDb = dodexUtil.getDefaultDb();

        if (defaultDb.equals(DbTypes.SQLITE3.db)) {
            dodexReactiveDatabase = new DodexDatabaseSqlite3(overrideMap, overrideProps);
            isUsingSqlite3 = true;
            return (T) dodexReactiveDatabase;
        } else if (defaultDb.equals(DbTypes.CUBRID.db)) {
            dodexReactiveDatabase = new DodexDatabaseCubrid(overrideMap, overrideProps);
            isUsingCubrid = true;
        }

        return (T) dodexReactiveDatabase;
    }

    public static void configureDefaults(Map<String, String> overrideMap, Properties overrideProps) {
        if (overrideProps != null && !overrideProps.isEmpty()) {
            properties = overrideProps;
        }
        mapMerge(map, overrideMap);
    }

    public static void configureTestDefaults(Map<String, String> overrideMap, Properties overrideProps) {
        if (overrideProps != null && !overrideProps.isEmpty()) {
            properties = overrideProps;
        }
        mapMerge(map, overrideMap);

    }

    public static void mapMerge(Map<String, String> map1, Map<String, String> map2) {
        map2.forEach((key, value) -> map1
          .merge(key, value, (v1, v2) -> v2));  // let duplicate key in map2 win
    }

}
