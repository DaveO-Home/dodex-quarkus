
package dmo.fs.db.generate;

public abstract class DbSqlite3 extends DbDefinitionBase implements HandicapDatabase {
	public final static String CHECKUSERSQL = "SELECT name FROM sqlite_master WHERE type='table' AND name='users'";
	protected final static String CHECKMESSAGESSQL = "SELECT name FROM sqlite_master WHERE type='table' AND name='messages'";
	protected final static String CHECKUNDELIVEREDSQL = "SELECT name FROM sqlite_master WHERE type='table' AND name='undelivered'";
	protected final static String CHECKHANDICAPSQL = "SELECT name FROM sqlite_master WHERE type='table' AND name in ('golfer', 'course', 'scores', 'ratings')";

	private enum CreateTable {
		CREATEUSERS(
				"create table users (id integer primary key, name text not null unique, password text not null unique, ip text not null, last_login DATETIME not null)"),
		CREATEMESSAGES(
				"create table messages (id integer primary key, message text not null, from_handle text not null, post_date DATETIME not null)"),
		CREATEUNDELIVERED(
				"create table undelivered (user_id integer, message_id integer, CONSTRAINT undelivered_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id), CONSTRAINT undelivered_message_id_foreign FOREIGN KEY (message_id) REFERENCES messages (id))"),
		CREATELOGIN(
				"create table login (id integer primary key, name text not null unique, password text not null, last_login DATETIME not null)"),
		CREATEGOLFER("CREATE TABLE IF NOT EXISTS golfer (" +
				"PIN CHARACTER(8) primary key NOT NULL," +
				"FIRST_NAME VARCHAR(32) NOT NULL," +
				"LAST_NAME VARCHAR(32) NOT NULL," +
				"HANDICAP FLOAT(4,1) DEFAULT 0.0," +
				"COUNTRY CHARACTER(2) DEFAULT 'US' NOT NULL," +
				"STATE CHARACTER(2) DEFAULT 'NV' NOT NULL," +
				"OVERLAP_YEARS INTEGER DEFAULT 1," +
				"PUBLIC INTEGER DEFAULT 0," +
				"LAST_LOGIN NUMERIC)"),
		CREATECOURSE("CREATE TABLE IF NOT EXISTS course (" +
				"COURSE_SEQ INTEGER primary key autoincrement NOT NULL," +
				"COURSE_NAME VARCHAR(128) NOT NULL," +
				"COURSE_COUNTRY VARCHAR(128) NOT NULL," +
				"COURSE_STATE CHARACTER(2) NOT NULL )"),
		CREATERATINGS("CREATE TABLE IF NOT EXISTS ratings (" +
				"COURSE_SEQ INTEGER NOT NULL," +
				"TEE INTEGER NOT NULL," +
				"TEE_COLOR VARCHAR(16)," +
				"TEE_RATING FLOAT(4,1) NOT NULL," +
				"TEE_SLOPE INTEGER NOT NULL," +
				"TEE_PAR INTEGER DEFAULT '72' NOT NULL, PRIMARY KEY (COURSE_SEQ, TEE)  FOREIGN KEY ( COURSE_SEQ ) REFERENCES course ( COURSE_SEQ ) ON UPDATE no action ON DELETE no action )"),
		CREATESCORES("CREATE TABLE IF NOT EXISTS scores (" +
				"PIN CHARACTER(8) NOT NULL," +
				"GROSS_SCORE INTEGER NOT NULL," +
				"NET_SCORE FLOAT(4,1)," +
				"ADJUSTED_SCORE INTEGER NOT NULL," +
				"TEE_TIME TEXT NOT NULL," +
				"HANDICAP FLOAT(4,1)," +
				"COURSE_SEQ INTEGER," +
				"COURSE_TEES INTEGER," +
				"USED CHARACTER(1), FOREIGN KEY ( COURSE_SEQ ) REFERENCES course ( COURSE_SEQ ) ON UPDATE no action ON DELETE no action,  FOREIGN KEY ( PIN ) REFERENCES golfer ( PIN ) ON UPDATE no action ON DELETE no action )"),
		CREATEGROUPS("CREATE TABLE IF NOT EXISTS groups (" +
			"id integer primary key autoincrement NOT NULL," +
			"name varchar(100) NOT NULL UNIQUE," +
			"owner integer NOT NULL DEFAULT 0," +
			"created datetime NOT NULL DEFAULT (datetime('now','localtime'))," +  //  DEFAULT (datetime('now','localtime')) - jooq problem
			"updated datetime DEFAULT NULL)"),
		CREATEMEMBER("CREATE TABLE IF NOT EXISTS member (" +
			"group_id integer," +
			"user_id integer," +
			"CONSTRAINT member_primary_keys PRIMARY KEY (group_id, user_id) on conflict ignore," +
			"CONSTRAINT member_user_id_foreign FOREIGN KEY (user_id)" +
			"REFERENCES users (id)," +
			"CONSTRAINT member_group_id_foreign FOREIGN KEY (group_id)" +
			"REFERENCES groups (id))");

		final String sql;

		CreateTable(String sql) {
			this.sql = sql;
		}
	}

	public DbSqlite3() {
		super();
	}

	public String getCreateTable(String table) {
		return CreateTable.valueOf("CREATE" + table.toUpperCase()).sql;
	}
}
