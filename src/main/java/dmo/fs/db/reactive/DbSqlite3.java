
package dmo.fs.db.reactive;

public abstract class DbSqlite3 extends DbReactiveSqlBase implements DodexReactiveDatabase {
	public static final String CHECKUSERSQL = "SELECT name FROM sqlite_master WHERE type='table' AND name='users'";
  protected static final String CHECKMESSAGESSQL = "SELECT name FROM sqlite_master WHERE type='table' AND name='messages'";
	protected static final String CHECKUNDELIVEREDSQL = "SELECT name FROM sqlite_master WHERE type='table' AND name='undelivered'";
	protected static final String CHECKGROUPSSQL = "SELECT name FROM sqlite_master WHERE type='table' AND name='groups'";
	protected static final String CHECKMEMBERSQL = "SELECT name FROM sqlite_master WHERE type='table' AND name='member'";

	protected enum CreateTable {
		CREATEUSERS("create table users (id integer primary key, name text not null unique, password text not null unique, ip text not null, last_login DATETIME not null)"),
		CREATEMESSAGES("create table messages (id integer primary key, message text not null, from_handle text not null, post_date DEFAULT CURRENT_DATETIME not null)"),
		CREATEUNDELIVERED("create table undelivered (user_id integer, message_id integer, CONSTRAINT undelivered_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id), CONSTRAINT undelivered_message_id_foreign FOREIGN KEY (message_id) REFERENCES messages (id))"),
		CREATEGROUPS("CREATE TABLE IF NOT EXISTS groups (" +
				"id integer primary key autoincrement NOT NULL," +
				"name varchar(100) NOT NULL UNIQUE," +
				"owner integer NOT NULL DEFAULT 0," +
				"created datetime NOT NULL DEFAULT (datetime('now','localtime'))," +
				"updated datetime DEFAULT NULL)"),
		CREATEMEMBER("CREATE TABLE IF NOT EXISTS member (" +
				"group_id integer," +
				"user_id integer," +
				"CONSTRAINT member_primary_keys PRIMARY KEY (group_id, user_id) on conflict ignore," +
				"CONSTRAINT member_user_id_foreign FOREIGN KEY (user_id)" +
				"REFERENCES users (id)," +
				"CONSTRAINT member_group_id_foreign FOREIGN KEY (group_id)" +
				"REFERENCES groups (id))");

		String sql;		

        CreateTable(String sql) {
            this.sql = sql;
        }
    };

	protected DbSqlite3() {
		super();
	}

	public String getCreateTable(String table) {
		return CreateTable.valueOf("CREATE"+table.toUpperCase()).sql;
	}
}
