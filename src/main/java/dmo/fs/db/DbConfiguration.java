package dmo.fs.db;

import java.io.IOException;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.ApplicationScoped;

import org.davidmoten.rx.jdbc.ConnectionProvider;

import dmo.fs.utils.DodexUtil;
import io.quarkus.runtime.configuration.ProfileManager;

@ApplicationScoped
public abstract class DbConfiguration {

    private static Map<String, String> map = new ConcurrentHashMap<>();
    private static Properties properties = new Properties();

    private static Boolean isUsingSqlite3 = false;
    private static Boolean isUsingPostgres = false;
    private static Boolean isUsingCubrid = false;
    private static Boolean isUsingMariadb = false;
    private static Boolean isUsingIbmDB2 = false;
    private static String defaultDb = "sqlite3";
    private static DodexUtil dodexUtil = new DodexUtil();
    private static DodexDatabase dodexDatabase;
    private static final boolean isProduction = !ProfileManager.getLaunchMode().isDevOrTest();
    
    private enum DbTypes {
        POSTGRES("postgres"),
        SQLITE3("sqlite3"),
        CUBRID("cubrid"),
        MARIADB("mariadb"),
        IBMDB2("ibmdb2");

        String db;

        DbTypes(String db) {
            this.db = db;
        }
    };

    public static ConnectionProvider getSqlite3ConnectionProvider() {
        isUsingSqlite3 = true;
        return ConnectionProvider.from(map.get("url") + map.get("filename"), properties);
    }

    public static ConnectionProvider getPostgresConnectionProvider() {
        isUsingPostgres = true;
        return ConnectionProvider.from(map.get("url") + map.get("host") + map.get("dbname"), properties);
    }

    public static ConnectionProvider getCubridConnectionProvider() throws SQLException {
        isUsingCubrid = true;
        
        Driver driver = new cubrid.jdbc.driver.CUBRIDDriver();
        DriverManager.registerDriver(driver);

        return ConnectionProvider.from(map.get("url") + map.get("host") + map.get("dbname") + "?charSet=utf8",
                properties.get("user").toString(), properties.get("password").toString());
    }

    public static ConnectionProvider getMariadbConnectionProvider() throws SQLException {
        isUsingMariadb = true;
        return ConnectionProvider.from(map.get("url") + map.get("host") + map.get("dbname") + "?charSet=utf8",
                properties.get("user").toString(), properties.get("password").toString());
    }

    public static ConnectionProvider getIbmDb2ConnectionProvider() {
        isUsingIbmDB2 = true;
        return ConnectionProvider.from(map.get("url") + map.get("host") + map.get("dbname"), properties);
    }

    public static boolean isUsingSqlite3() {
        return isUsingSqlite3;
    }

    public static boolean isUsingPostgres() {
        return isUsingPostgres;
    }

    public static boolean isUsingCubrid() {
        return isUsingCubrid;
    }

    public static boolean isUsingMariadb() {
        return isUsingMariadb;
    }

    public static boolean isUsingIbmDB2() {
        return isUsingIbmDB2;
    }

    public static boolean isProduction() {

        return isProduction;
    }

    public static DodexDatabase getDefaultDb() throws InterruptedException, IOException, SQLException {
        defaultDb = dodexUtil.getDefaultDb().toLowerCase(Locale.US);
        
        try {
            if(defaultDb.equals(DbTypes.POSTGRES.db) && dodexDatabase == null) {
                dodexDatabase = new DodexDatabasePostgres();
            } else if(defaultDb.equals(DbTypes.SQLITE3.db) && dodexDatabase == null) {
                dodexDatabase = new DodexDatabaseSqlite3();
            } else if(defaultDb.equals(DbTypes.CUBRID.db) && dodexDatabase == null) {
                dodexDatabase = new DodexDatabaseCubrid();
            } else if(defaultDb.equals(DbTypes.MARIADB.db) && dodexDatabase == null) {
                dodexDatabase = new DodexDatabaseMariadb();
            } else if(defaultDb.equals(DbTypes.IBMDB2.db) && dodexDatabase == null) {
                dodexDatabase = new DodexDatabaseIbmDB2();
            }
        } catch (Exception exception) { 
            throw exception;
        }
        return dodexDatabase;
    }

    public static DodexDatabase getDefaultDb(Map<String, String>overrideMap, Properties overrideProps) throws InterruptedException, IOException, SQLException {
        defaultDb = dodexUtil.getDefaultDb();
        
        try {
            if(defaultDb.equals(DbTypes.POSTGRES.db) && dodexDatabase == null) {
                dodexDatabase = new DodexDatabasePostgres(overrideMap, overrideProps);
            } else if(defaultDb.equals(DbTypes.SQLITE3.db) && dodexDatabase == null) {
                dodexDatabase = new DodexDatabaseSqlite3(overrideMap, overrideProps);
            } else if(defaultDb.equals(DbTypes.CUBRID.db) && dodexDatabase == null) {
                dodexDatabase = new DodexDatabaseCubrid(overrideMap, overrideProps);
            } else if(defaultDb.equals(DbTypes.MARIADB.db) && dodexDatabase == null) {
                dodexDatabase = new DodexDatabaseMariadb(overrideMap, overrideProps);
            } else if(defaultDb.equals(DbTypes.IBMDB2.db) && dodexDatabase == null) {
                dodexDatabase = new DodexDatabaseIbmDB2(overrideMap, overrideProps);
            }
        } catch (Exception exception) { 
            throw exception;
        }
        return dodexDatabase;
    }

    public static void configureDefaults(Map<String, String>overrideMap, Properties overrideProps) {
        if(overrideProps != null && overrideProps.size() > 0) {
            properties = overrideProps;
        }
        mapMerge(map, overrideMap);
    }

    public static void configureTestDefaults(Map<String, String>overrideMap, Properties overrideProps) {
        if(overrideProps != null && overrideProps.size() > 0) {
            properties = overrideProps;
        }
        mapMerge(map, overrideMap);

    }

    public static void mapMerge(Map<String,String> map1, Map<String, String> map2) {
        map2.forEach((key, value) -> map1
            .merge( key, value, (v1, v2) -> v2));  // let duplicate key in map2 win
    }

    public static String getDbName() {
        return defaultDb;
    }
}
