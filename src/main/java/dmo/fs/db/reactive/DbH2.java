
package dmo.fs.db.reactive;

public abstract class DbH2 extends DbReactiveSqlBase implements DodexReactiveDatabase {
	public static final String CHECKUSERSQL = "SELECT table_name FROM  INFORMATION_SCHEMA.TABLES where table_name = 'USERS' and table_type = 'TABLE'";
    protected static final String CHECKMESSAGESSQL = "SELECT table_name FROM  INFORMATION_SCHEMA.TABLES where table_name = 'MESSAGES'";
    protected static final String CHECKUNDELIVEREDSQL = "SELECT table_name FROM  INFORMATION_SCHEMA.TABLES where table_name = 'UNDELIVERED'";

	private enum CreateTable {
		CREATEUSERS("create table users (id int IDENTITY NOT NULL PRIMARY KEY, name varchar(255) not null unique, password varchar(255) not null unique, ip varchar(255) not null, last_login TIMESTAMP not null)"),
		CREATEMESSAGES("create table messages (id int IDENTITY NOT NULL PRIMARY KEY, message clob not null, from_handle varchar(255) not null, post_date TIMESTAMP not null)"),
		CREATEUNDELIVERED("create table undelivered (user_id int, message_id int, CONSTRAINT undelivered_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id), CONSTRAINT undelivered_message_id_foreign FOREIGN KEY (message_id) REFERENCES messages (id))");

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
