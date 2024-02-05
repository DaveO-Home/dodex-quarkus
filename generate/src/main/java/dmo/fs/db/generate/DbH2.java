
package dmo.fs.db.generate;

//import dmo.fs.db.handicap.HandicapDatabase;
//import dmo.fs.db.handicap.DbDefinitionBase;

public abstract class DbH2 extends DbDefinitionBase implements HandicapDatabase {
	public static final String CHECKUSERSQL = "SELECT table_name FROM  INFORMATION_SCHEMA.TABLES where table_name = 'USERS' and table_type = 'BASE TABLE' and TABLE_SCHEMA = 'PUBLIC'";
    protected static final String CHECKMESSAGESSQL = "SELECT table_name FROM  INFORMATION_SCHEMA.TABLES where table_name = 'MESSAGES'";
    protected static final String CHECKUNDELIVEREDSQL = "SELECT table_name FROM  INFORMATION_SCHEMA.TABLES where table_name = 'UNDELIVERED'";
	protected static final String CHECKHANDICAPSQL = "SELECT table_name FROM INFORMATION_SCHEMA.TABLES WHERE table_type='BASE TABLE' AND table_" +
            "" +
            "name in ('GOLFER', 'COURSE', 'SCORES', 'RATINGS')";
	protected static final String CHECKGOLFERSQL = "SELECT table_name FROM INFORMATION_SCHEMA.TABLES WHERE table_type='BASE TABLE' AND table_name = 'GOLFER'";
	protected static final String CHECKCOURSESQL = "SELECT table_name FROM INFORMATION_SCHEMA.TABLES WHERE table_type='BASE TABLE' AND table_name = 'COURSE'";
	protected static final String CHECKSCORESSQL = "SELECT table_name FROM INFORMATION_SCHEMA.TABLES WHERE table_type='BASE TABLE' AND table_name = 'SCORES'";
	protected static final String CHECKRATINGSSQL = "SELECT table_name FROM INFORMATION_SCHEMA.TABLES WHERE table_type='BASE TABLE' AND table_name = 'RATINGS'";
	protected final static String SELECTONE = "SELECT 1;";

	private enum CreateTable {
		CREATEUSERS("create table users (id int auto_increment NOT NULL PRIMARY KEY, name varchar(255) not null unique, password varchar(255) not null unique, ip varchar(255) not null, last_login TIMESTAMP not null)"),
		CREATEMESSAGES("create table messages (id int auto_increment NOT NULL PRIMARY KEY, message clob not null, from_handle varchar(255) not null, post_date TIMESTAMP not null)"),
		CREATEUNDELIVERED("create table undelivered (user_id int, message_id int, CONSTRAINT undelivered_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id), CONSTRAINT undelivered_message_id_foreign FOREIGN KEY (message_id) REFERENCES messages (id))"),
		CREATELOGIN("create table login (id integer primary key, name text not null unique, password text not null, last_login DATETIME not null)"),
		CREATEGOLFER("CREATE TABLE IF NOT EXISTS golfer (" +
							 "PIN CHARACTER(8) primary key NOT NULL," +
							 "FIRST_NAME VARCHAR(32) NOT NULL," +
							 "LAST_NAME VARCHAR(32) NOT NULL," +
							 "HANDICAP REAL DEFAULT 0.0," +
							 "COUNTRY CHARACTER(2) DEFAULT 'US' NOT NULL," +
							 "STATE CHARACTER(2) DEFAULT 'NV' NOT NULL," +
							 "OVERLAP_YEARS BOOLEAN," +
							 "PUBLIC BOOLEAN," +
							 "LAST_LOGIN NUMERIC)"),
		CREATECOURSE("CREATE TABLE IF NOT EXISTS course (" +
							 "COURSE_SEQ INTEGER primary key auto_increment NOT NULL," +
							 "COURSE_NAME VARCHAR(128) NOT NULL," +
							 "COURSE_COUNTRY VARCHAR(128) NOT NULL," +
							 "COURSE_STATE CHARACTER(2) NOT NULL )"),
		CREATERATINGS("CREATE TABLE IF NOT EXISTS ratings (" +
							  "COURSE_SEQ INTEGER NOT NULL," +
							  "TEE INTEGER NOT NULL," +
							  "TEE_COLOR VARCHAR(16)," +
							  "TEE_RATING REAL NOT NULL," +
							  "TEE_SLOPE INTEGER NOT NULL," +
							  "TEE_PAR INTEGER DEFAULT '72' NOT NULL, PRIMARY KEY (COURSE_SEQ, TEE)," +
							  "CONSTRAINT FK_COURSE_SEQ_RATING FOREIGN KEY ( COURSE_SEQ ) " +
							  "REFERENCES COURSE ( COURSE_SEQ ))"),
		CREATESCORES("CREATE TABLE IF NOT EXISTS scores (" +
							 "PIN CHARACTER(8) NOT NULL," +
							 "GROSS_SCORE INTEGER NOT NULL," +
							 "NET_SCORE REAL," +
							 "ADJUSTED_SCORE INTEGER NOT NULL," +
							 "TEE_TIME TEXT NOT NULL," +
							 "HANDICAP REAL," +
							 "COURSE_SEQ INTEGER," +
							 "COURSE_TEES INTEGER," +
							 "USED CHARACTER(1), CONSTRAINT FK_SCORES_SEQ_COURSE FOREIGN KEY ( COURSE_SEQ )" +
							 "REFERENCES COURSE ( COURSE_SEQ ), CONSTRAINT FK_GOLFER_PIN_SCORES FOREIGN KEY ( PIN ) " +
							 "REFERENCES GOLFER ( PIN ))");


		String sql;

        CreateTable(String sql) {
            this.sql = sql;
        }
    };

	protected DbH2() {
		super();
	}

	public String getCreateTable(String table) {
		return CreateTable.valueOf("CREATE"+table.toUpperCase()).sql;
	}
}