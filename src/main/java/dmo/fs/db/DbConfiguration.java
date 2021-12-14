package dmo.fs.db;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.db.reactive.DodexDatabaseCubrid;
import dmo.fs.db.reactive.DodexDatabaseH2;
import dmo.fs.db.reactive.DodexReactiveDatabase;
import dmo.fs.db.reactive.DodexDatabaseSqlite3;
import dmo.fs.utils.DodexUtil;
import io.quarkus.runtime.configuration.ProfileManager;

public abstract class DbConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(DbConfiguration.class.getName());
    private static Map<String, String> map = new ConcurrentHashMap<>();
    protected static Properties properties = new Properties();

    protected static Boolean isUsingSqlite3 = false;
    protected static Boolean isUsingPostgres = false;
    protected static Boolean isUsingMariadb = false;
    protected static Boolean isUsingIbmDB2 = false;
    protected static Boolean isUsingH2 = false;
    protected static Boolean isUsingCubrid = false;
    protected static Boolean isUsingCassandra = false;
    protected static Boolean isUsingFirebase = false;
    protected static Boolean isUsingNeo4j = false;
    private static String defaultDb = "sqlite3";
    private static boolean overrideDefaultDb = false;
    private static DodexUtil dodexUtil = new DodexUtil();
    private static DodexDatabase dodexDatabase;
    protected static DodexCassandra dodexCassandra;
    private static DodexFirebase dodexFirebase;
    private static DodexNeo4j dodexNeo4j;
    private static DodexReactiveDatabase dodexReactiveDatabase;
    private static final boolean isProduction = !ProfileManager.getLaunchMode().isDevOrTest();

    private enum DbTypes {
        POSTGRES("postgres"),
        SQLITE3("sqlite3"),
        MARIADB("mariadb"),
        IBMDB2("ibmdb2"),
        H2("h2"),
        CASSANDRA("cassandra"),
        CUBRID("cubrid"),
        NEO4J("neo4j"),
        FIREBASE("firebase");

        String db;

        DbTypes(String db) {
            this.db = db;
        }
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

    public static boolean isUsingCassandra() {
        return isUsingCassandra;
    }

    public static boolean isUsingFirebase() {
        return isUsingFirebase;
    }

    public static boolean isUsingH2() {
        return isUsingH2;
    }

    public static boolean isUsingNeo4j() {
        return isUsingNeo4j;
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
        if(!overrideDefaultDb) {
            defaultDb = dodexUtil.getDefaultDb().toLowerCase();
        }

        if(defaultDb.equals(DbTypes.POSTGRES.db) && dodexDatabase == null) {
            dodexDatabase = new DodexDatabasePostgres();
            isUsingPostgres = true;
        } 
        else if(defaultDb.equals(DbTypes.SQLITE3.db) && dodexReactiveDatabase == null) {
            dodexReactiveDatabase = new DodexDatabaseSqlite3();
            isUsingSqlite3 = true;
            return (T) dodexReactiveDatabase;
        } else if(defaultDb.equals(DbTypes.H2.db) && dodexReactiveDatabase == null) {
            dodexReactiveDatabase = new DodexDatabaseH2();
            isUsingH2 = true;
            return (T) dodexReactiveDatabase;
        }
        else if(defaultDb.equals(DbTypes.MARIADB.db) && dodexDatabase == null) {
            dodexDatabase = new DodexDatabaseMariadb();
            isUsingMariadb = true;
        } 
        else if(defaultDb.equals(DbTypes.IBMDB2.db) && dodexDatabase == null) {
            dodexDatabase = new DodexDatabaseIbmDB2();
            isUsingIbmDB2 = true;
        }
        else if(defaultDb.equals(DbTypes.CASSANDRA.db) && dodexCassandra == null) {
            dodexCassandra = new DodexDatabaseCassandra();
            isUsingCassandra = true;
            return (T) dodexCassandra;
        }
        else if(defaultDb.equals(DbTypes.CUBRID.db) && dodexReactiveDatabase == null) {
            dodexReactiveDatabase = new DodexDatabaseCubrid();
            isUsingCubrid = true;
            return (T) dodexReactiveDatabase;
        } else if(defaultDb.equals(DbTypes.FIREBASE.db)) {
            dodexFirebase = dodexFirebase == null? new DodexDatabaseFirebase(): dodexFirebase;
            isUsingFirebase = true;
            return (T) dodexFirebase;
        } else if(defaultDb.equals(DbTypes.NEO4J.db)) {
            dodexNeo4j = new DodexDatabaseNeo4j();
            isUsingNeo4j = true;
            return (T) dodexNeo4j;
        }
    
        return (T) dodexDatabase;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getDefaultDb(Map<String, String> overrideMap, Properties overrideProps)
            throws InterruptedException, IOException, SQLException {
        defaultDb = dodexUtil.getDefaultDb();
        
            if(defaultDb.equals(DbTypes.POSTGRES.db) && dodexDatabase == null) {
                dodexDatabase = new DodexDatabasePostgres(overrideMap, overrideProps);
                isUsingPostgres = true;
            } 
            else if(defaultDb.equals(DbTypes.SQLITE3.db) && dodexReactiveDatabase == null) {
                dodexReactiveDatabase = new DodexDatabaseSqlite3(overrideMap, overrideProps);
                isUsingSqlite3 = true;
                return (T) dodexReactiveDatabase;
            } else if(defaultDb.equals(DbTypes.H2.db) && dodexReactiveDatabase == null) {
                dodexReactiveDatabase = new DodexDatabaseH2();
                isUsingH2 = true;
                return (T) dodexReactiveDatabase;
            }
            else if(defaultDb.equals(DbTypes.MARIADB.db) && dodexDatabase == null) {
                dodexDatabase = new DodexDatabaseMariadb(overrideMap, overrideProps);
                isUsingMariadb = true;
            } 
            else if(defaultDb.equals(DbTypes.IBMDB2.db) && dodexDatabase == null) {
                dodexDatabase = new DodexDatabaseIbmDB2(overrideMap, overrideProps);
                isUsingIbmDB2 = true;
            } else if(defaultDb.equals(DbTypes.CASSANDRA.db) && dodexCassandra == null) {
                dodexCassandra = new DodexDatabaseCassandra(overrideMap, overrideProps);
                isUsingCassandra = true;
                return (T) dodexCassandra;
            } else if(defaultDb.equals(DbTypes.CUBRID.db) && dodexReactiveDatabase == null) {
                dodexReactiveDatabase = new DodexDatabaseCubrid(overrideMap, overrideProps);
                isUsingCubrid = true;
            } else if(defaultDb.equals(DbTypes.FIREBASE.db)) {
                dodexFirebase = dodexFirebase == null? new DodexDatabaseFirebase(overrideMap, overrideProps): dodexFirebase;
                isUsingFirebase = true;
                return (T) dodexFirebase;
            } else if(defaultDb.equals(DbTypes.NEO4J.db)) {
                dodexNeo4j = new DodexDatabaseNeo4j(overrideMap, overrideProps);
                isUsingNeo4j = true;
                return (T) dodexNeo4j;
            }
           
        return (T) dodexDatabase;
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

}