package dmo.fs.db.dodex;

import dmo.fs.db.dodex.cubrid.DodexDatabaseCubrid;
import dmo.fs.db.dodex.db2.DodexDatabaseIbmDB2;
import dmo.fs.db.dodex.h2.HandicapDatabaseH2;
import dmo.fs.db.dodex.mariadb.HandicapDatabaseMariadb;
import dmo.fs.db.dodex.postgres.HandicapDatabasePostgres;
import dmo.fs.db.dodex.sqlite3.DodexDatabaseSqlite3;
import dmo.fs.db.dodex.utils.DodexUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public abstract class CreateDatabaseImpl {
    static Logger logger = LoggerFactory.getLogger(CreateDatabaseImpl.class.getName());
    protected static final Map<String, String> map = new ConcurrentHashMap<>();
    protected static Properties properties = new Properties();

    protected static Boolean isUsingPostgres = false;
    protected static Boolean isUsingMariadb = false;
    protected static Boolean isUsingH2 = false;
    protected static Boolean isUsingSqlite3 = false;
    protected static Boolean isUsingCubrid = false;
    protected static Boolean isUsingIbmDB2 = false;
    protected static String defaultDb = DodexUtil.defaultDb;
    protected static final DodexUtil dodexUtil = new DodexUtil();
    protected static CreateDatabase dodexDatabase;

    protected enum DbTypes {
        POSTGRES("postgres"),
        MARIADB("mariadb"),
        IBMDB2("ibmdb2"),
        SQLITE3("sqlite3"),
        CUBRID("cubrid"),
        H2("h2");

        final String db;

        DbTypes(String db) {
            this.db = db;
        }
    }

    public static boolean isUsingPostgres() {
        return isUsingPostgres;
    }

    public static boolean isUsingMariadb() {
        return isUsingMariadb;
    }

    public static boolean isUsingH2() {
        return isUsingH2;
    }

    public static boolean isUsingSqlite3() {
        return isUsingSqlite3;
    }

    public static boolean isUsingCubrid() {
        return isUsingCubrid;
    }

    public static boolean isUsingIbmDB2() {
        return isUsingIbmDB2;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getDefaultDb() throws InterruptedException, IOException, SQLException {
        defaultDb = dodexUtil.getDefaultDb().toLowerCase();

        if (defaultDb.equals(DbTypes.POSTGRES.db) && dodexDatabase == null) {
            dodexDatabase = new HandicapDatabasePostgres();
            isUsingPostgres = true;
        } else if (defaultDb.equals(DbTypes.MARIADB.db) && dodexDatabase == null) {
            dodexDatabase = new HandicapDatabaseMariadb();
            isUsingMariadb = true;
        } else if (defaultDb.equals(DbTypes.H2.db) && dodexDatabase == null) {
            dodexDatabase = new HandicapDatabaseH2();
            isUsingH2 = true;
        } else if (defaultDb.equals(DbTypes.SQLITE3.db) && dodexDatabase == null) {
            dodexDatabase = new DodexDatabaseSqlite3();
            isUsingSqlite3 = true;
        } else if (defaultDb.equals(DbTypes.CUBRID.db) && dodexDatabase == null) {
            dodexDatabase = new DodexDatabaseCubrid();
            isUsingCubrid = true;
        } else if (defaultDb.equals(DbTypes.IBMDB2.db) && dodexDatabase == null) {
            dodexDatabase = new DodexDatabaseIbmDB2();
            isUsingIbmDB2 = true;
        }

        return (T) dodexDatabase;
    }

    /*
        Used for JOOQ Generate - set to false so DSL is not executed
     */
    @SuppressWarnings("unchecked")
    public static <T> T getDefaultDb(Boolean isCreateTables) throws InterruptedException, IOException, SQLException {
        defaultDb = dodexUtil.getDefaultDb().toLowerCase();

        if (defaultDb.equals(DbTypes.POSTGRES.db) && isCreateTables) {
            dodexDatabase = new HandicapDatabasePostgres(true);
            isUsingPostgres = true;
        } else if (defaultDb.equals(DbTypes.MARIADB.db) && isCreateTables) {
            dodexDatabase = new HandicapDatabaseMariadb(true);
            isUsingMariadb = true;
        } else if (defaultDb.equals(DbTypes.H2.db) && isCreateTables) {
            dodexDatabase = new HandicapDatabaseH2(true);
            isUsingH2 = true;
        } else if (defaultDb.equals(DbTypes.SQLITE3.db) && dodexDatabase == null) {
            dodexDatabase = new DodexDatabaseSqlite3();
            isUsingSqlite3 = true;
        } else if (defaultDb.equals(DbTypes.CUBRID.db) && dodexDatabase == null) {
            dodexDatabase = new DodexDatabaseCubrid();
            isUsingCubrid = true;
        } else if (defaultDb.equals(DbTypes.IBMDB2.db) && dodexDatabase == null) {
            dodexDatabase = new DodexDatabaseIbmDB2();
            isUsingIbmDB2 = true;
        }

        return (T) dodexDatabase;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getDefaultDb(Map<String, String> overrideMap, Properties overrideProps)
      throws InterruptedException, IOException, SQLException {
        defaultDb = dodexUtil.getDefaultDb();

        if (defaultDb.equals(DbTypes.POSTGRES.db) && dodexDatabase == null) {
            dodexDatabase = new HandicapDatabasePostgres(overrideMap, overrideProps);
            isUsingPostgres = true;
        } else if (defaultDb.equals(DbTypes.MARIADB.db) && dodexDatabase == null) {
            dodexDatabase = new HandicapDatabaseMariadb(overrideMap, overrideProps);
            isUsingMariadb = true;
        } else if (defaultDb.equals(DbTypes.H2.db) && dodexDatabase == null) {
            dodexDatabase = new HandicapDatabaseH2(overrideMap, overrideProps);
            isUsingH2 = true;
        } else if (defaultDb.equals(DbTypes.SQLITE3.db) && dodexDatabase == null) {
            dodexDatabase = new DodexDatabaseSqlite3();
            isUsingSqlite3 = true;
        } else if (defaultDb.equals(DbTypes.CUBRID.db) && dodexDatabase == null) {
            dodexDatabase = new DodexDatabaseCubrid();
            isUsingCubrid = true;
        } else if (defaultDb.equals(DbTypes.IBMDB2.db) && dodexDatabase == null) {
            dodexDatabase = new DodexDatabaseIbmDB2();
            isUsingIbmDB2 = true;
        }
        return (T) dodexDatabase;
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