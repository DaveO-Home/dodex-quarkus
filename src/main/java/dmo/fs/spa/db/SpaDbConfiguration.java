package dmo.fs.spa.db;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.db.DbConfiguration;
import dmo.fs.spa.db.reactive.SpaDatabaseCubrid;
import dmo.fs.spa.db.reactive.SpaDatabaseH2;
import dmo.fs.spa.db.reactive.SpaDatabaseReactive;
import dmo.fs.spa.db.reactive.SpaDatabaseSqlite3;
import dmo.fs.spa.utils.SpaUtil;

public class SpaDbConfiguration extends DbConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(SpaDbConfiguration.class.getName());

    private static String defaultDb = "sqlite3";
    private static SpaDatabase spaDatabase;
    private static SpaCassandra spaCassandra;
    private static SpaDatabaseReactive spaDatabaseReactive;
    private static SpaDatabaseFirebase spaFirebase;
    private static SpaDatabaseNeo4j spaNeo4j;

    private enum DbTypes {
        POSTGRES("postgres"),
        SQLITE3("sqlite3"),
        CUBRID("cubrid"),
        MARIADB("mariadb"),
        H2("h2"),
        CASSANDRA("cassandra"),
        FIREBASE("firebase"),
        NEO4J("neo4j"),
        IBMDB2("ibmdb2");

        String db;

        DbTypes(String db) {
            this.db = db;
        }
    };

    SpaDbConfiguration() {
        super();
    }

    @SuppressWarnings("unchecked")
    public static <T> T getSpaDb() {
        try {
            defaultDb = SpaUtil.getDefaultDb().toLowerCase();

            if (defaultDb.equals(DbTypes.POSTGRES.db) && spaDatabase == null) {
                spaDatabase = new SpaDatabasePostgres();
                isUsingPostgres = true;
            } else if (defaultDb.equals(DbTypes.SQLITE3.db) && spaDatabaseReactive == null) {
                spaDatabaseReactive = new SpaDatabaseSqlite3();
                isUsingSqlite3 = true;
                return (T) spaDatabaseReactive;
            } else if (defaultDb.equals(DbTypes.MARIADB.db) && spaDatabase == null) {
                spaDatabase = new SpaDatabaseMariadb();
                isUsingMariadb = true;
            } else if (defaultDb.equals(DbTypes.H2.db) && spaDatabaseReactive == null) {
                spaDatabaseReactive = new SpaDatabaseH2();
                isUsingH2 = true;
                return (T) spaDatabaseReactive;
            } else if (defaultDb.equals(DbTypes.IBMDB2.db) && spaDatabase == null) {
                spaDatabase = new SpaDatabaseIbmDB2();
                isUsingIbmDB2 = true;
            } else if (defaultDb.equals(DbTypes.CUBRID.db) && spaDatabase == null) {
                spaDatabaseReactive = new SpaDatabaseCubrid();
                isUsingCubrid = true;
                return (T) spaDatabaseReactive;
            } else if(defaultDb.equals(DbTypes.CASSANDRA.db) && spaCassandra == null) {
                spaCassandra = new SpaDatabaseCassandra();
                isUsingCassandra = true;
                return (T) spaCassandra;
            } else if(defaultDb.equals(DbTypes.FIREBASE.db) && spaFirebase == null) {
                spaFirebase = new SpaDatabaseFirebase();
                isUsingFirebase = true;
                return (T) spaFirebase;
            } else if(defaultDb.equals(DbTypes.NEO4J.db) && spaNeo4j == null) {
                spaNeo4j = new SpaDatabaseNeo4j();
                isUsingNeo4j = true;
                return (T) spaNeo4j;
            }
        } catch (InterruptedException | IOException | SQLException e) {
            e.printStackTrace();
        }
        
        if(spaDatabaseReactive != null) {
            return (T) spaDatabaseReactive;
        } else if (spaCassandra != null) {
            return (T) spaCassandra;
        } else if (spaFirebase != null) {
            return (T) spaFirebase;
        } else if (spaNeo4j != null) {
            return (T) spaNeo4j;
        }
        return (T) spaDatabase;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getSpaDb(Map<String, String> overrideMap, Properties overrideProps) throws InterruptedException, SQLException {
        try {
            defaultDb = SpaUtil.getDefaultDb().toLowerCase();

            if (defaultDb.equals(DbTypes.POSTGRES.db) && spaDatabase == null) {
                spaDatabase = new SpaDatabasePostgres(overrideMap, overrideProps);
                isUsingPostgres = true;
            } else if (defaultDb.equals(DbTypes.SQLITE3.db) && spaDatabaseReactive == null) {
                spaDatabaseReactive = new SpaDatabaseSqlite3(overrideMap, overrideProps);
                isUsingSqlite3 = true;
                return (T) spaDatabaseReactive;
            } else if (defaultDb.equals(DbTypes.MARIADB.db) && spaDatabase == null) {
                spaDatabase = new SpaDatabaseMariadb(overrideMap, overrideProps);
                isUsingMariadb = true;
            } else if (defaultDb.equals(DbTypes.IBMDB2.db) && spaDatabase == null) {
                spaDatabase = new SpaDatabaseIbmDB2(overrideMap, overrideProps);
                isUsingIbmDB2 = true;
            } else if (defaultDb.equals(DbTypes.H2.db) && spaDatabaseReactive == null) {
                spaDatabaseReactive = new SpaDatabaseH2(overrideMap, overrideProps);
                isUsingH2 = true;
                return (T) spaDatabaseReactive;
            } else if (defaultDb.equals(DbTypes.CUBRID.db) && spaDatabase == null) {
                spaDatabaseReactive = new SpaDatabaseCubrid(overrideMap, overrideProps);
                isUsingCubrid = true;
                return (T) spaDatabaseReactive;
            } else if(defaultDb.equals(DbTypes.CASSANDRA.db) && spaCassandra == null) {
                spaCassandra = new SpaDatabaseCassandra(overrideMap, overrideProps);
                isUsingCassandra = true;
                return (T) dodexCassandra;
            } else if(defaultDb.equals(DbTypes.FIREBASE.db) && spaFirebase == null) {
                spaFirebase = new SpaDatabaseFirebase(overrideMap, overrideProps);
                isUsingFirebase = true;
                return (T) spaFirebase;
            } else if(defaultDb.equals(DbTypes.NEO4J.db) && spaNeo4j == null) {
                spaNeo4j = new SpaDatabaseNeo4j(overrideMap, overrideProps);
                isUsingNeo4j = true;
                return (T) spaNeo4j;
            }  
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(spaDatabaseReactive != null) {
            return (T) spaDatabaseReactive;
        } else if (spaCassandra != null) {
            return (T) spaCassandra;
        } else if (spaFirebase != null) {
            return (T) spaFirebase;
        } else if (spaNeo4j != null) {
            return (T) spaNeo4j;
        }
        return (T) spaDatabase;
    }
}