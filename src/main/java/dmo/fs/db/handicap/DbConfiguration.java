package dmo.fs.db.handicap;

import dmo.fs.db.handicap.utils.DodexUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public abstract class DbConfiguration {
    static Logger logger = LoggerFactory.getLogger(DbConfiguration.class.getName());
    private static final Map<String, String> map = new ConcurrentHashMap<>();
    protected static Properties properties = new Properties();

    private static Boolean isUsingPostgres = false;
    private static Boolean isUsingMariadb = false;
    private static Boolean isUsingIbmDB2 = false;
    private static Boolean isUsingH2 = false;
    private static String defaultDb = "sqlite3";
    private static final DodexUtil dodexUtil = new DodexUtil();
    private static HandicapDatabase handicapDatabase;

    private enum DbTypes {
        POSTGRES("postgres"),
        MARIADB("mariadb"),
        IBMDB2("ibmdb2"),
        H2("h2");

        final String db;

        DbTypes(String db) {
            this.db = db;
        }
    };

    public static boolean isUsingPostgres() {
        return isUsingPostgres;
    }

    public static boolean isUsingMariadb() {
        return isUsingMariadb;
    }

    public static boolean isUsingIbmDB2() {
        return isUsingIbmDB2;
    }

    public static boolean isUsingH2() {
        return isUsingH2;
    }

    public static boolean isUsingSqlite3() {
        return false;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getDefaultDb() throws InterruptedException, IOException, SQLException {
        defaultDb = dodexUtil.getDefaultDb().toLowerCase();

        if(defaultDb.equals(DbTypes.POSTGRES.db) && handicapDatabase == null) {
            handicapDatabase = new HandicapDatabasePostgres();
            isUsingPostgres = true;
        }
        else if(defaultDb.equals(DbTypes.MARIADB.db) && handicapDatabase == null) {
            handicapDatabase = new HandicapDatabaseMariadb();
            isUsingMariadb = true;
        }
        else if(defaultDb.equals(DbTypes.H2.db) && handicapDatabase == null) {
            handicapDatabase = new HandicapDatabaseH2();
            isUsingH2 = true;
        }
        return (T) handicapDatabase;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getDefaultDb(Boolean isCreateTables) throws InterruptedException, IOException, SQLException {
        defaultDb = dodexUtil.getDefaultDb().toLowerCase();

        if(defaultDb.equals(DbTypes.POSTGRES.db) && isCreateTables) {
            handicapDatabase = new HandicapDatabasePostgres(isCreateTables);
            isUsingPostgres = true;
        }
        else if(defaultDb.equals(DbTypes.MARIADB.db) && isCreateTables) {
            handicapDatabase = new HandicapDatabaseMariadb(isCreateTables);
            isUsingMariadb = true;
        }
        else if(defaultDb.equals(DbTypes.H2.db)  && isCreateTables) {
            handicapDatabase = new HandicapDatabaseH2(isCreateTables);
            isUsingH2 = true;
        }
        return (T) handicapDatabase;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getDefaultDb(Map<String, String> overrideMap, Properties overrideProps)
            throws InterruptedException, IOException, SQLException {
        defaultDb = dodexUtil.getDefaultDb();

        if(defaultDb.equals(DbTypes.POSTGRES.db) && handicapDatabase == null) {
            handicapDatabase = new HandicapDatabasePostgres(overrideMap, overrideProps);
            isUsingPostgres = true;
        }
        else if(defaultDb.equals(DbTypes.MARIADB.db) && handicapDatabase == null) {
            handicapDatabase = new HandicapDatabaseMariadb(overrideMap, overrideProps);
            isUsingMariadb = true;
        }
        else if(defaultDb.equals(DbTypes.H2.db) && handicapDatabase == null) {
            handicapDatabase = new HandicapDatabaseH2(overrideMap, overrideProps);
            isUsingH2 = true;
        }
        return (T) handicapDatabase;
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