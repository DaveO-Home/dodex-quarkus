
package dmo.fs.db.wsnext.h2;

import dmo.fs.db.wsnext.DbDefinitionBase;
import dmo.fs.db.wsnext.DodexDatabase;

public abstract class DbH2Next extends DbDefinitionBase implements DodexDatabase {
	public static final String CHECKUSERSQL = "SELECT table_name FROM  INFORMATION_SCHEMA.TABLES where table_name = 'USERS' and table_schema = 'PUBLIC'";
    protected static final String CHECKMESSAGESSQL = "SELECT table_name FROM  INFORMATION_SCHEMA.TABLES where table_name = 'MESSAGES'";
    protected static final String CHECKUNDELIVEREDSQL = "SELECT table_name FROM  INFORMATION_SCHEMA.TABLES where table_name = 'UNDELIVERED'";
	protected static final String CHECKGROUPSSQL = "SELECT table_name FROM INFORMATION_SCHEMA.TABLES WHERE table_type='BASE TABLE' AND table_name = 'GROUPS'";
	protected static final String CHECKMEMBERSQL = "SELECT table_name FROM INFORMATION_SCHEMA.TABLES WHERE table_type='BASE TABLE' AND table_name = 'MEMBER'";
	protected enum CreateTable {
		CREATEUSERS("create table users (id int auto_increment NOT NULL PRIMARY KEY, name varchar(255) not null unique, password varchar(255) not null unique, ip varchar(255) not null, last_login TIMESTAMP not null)"),
		CREATEMESSAGES("create table messages (id int auto_increment NOT NULL PRIMARY KEY, message clob not null, from_handle varchar(255) not null, post_date TIMESTAMP not null)"),
		CREATEUNDELIVERED("create table undelivered (user_id int, message_id int, CONSTRAINT undelivered_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id), CONSTRAINT undelivered_message_id_foreign FOREIGN KEY (message_id) REFERENCES messages (id))"),
		CREATEGROUPS("CREATE TABLE IF NOT EXISTS groups (" +
				"ID INTEGER primary key auto_increment NOT NULL," +
				"NAME CHARACTER(24) UNIQUE NOT NULL," +
				"OWNER INTEGER NOT NULL," +
				"CREATED TIMESTAMP NOT NULL," +
				"UPDATED TIMESTAMP)"),
		CREATEMEMBER("CREATE TABLE IF NOT EXISTS member (" +
				"GROUP_ID INTEGER NOT NULL DEFAULT 0," +
				"USER_ID INTEGER NOT NULL DEFAULT 0," +
				"PRIMARY KEY (GROUP_ID,USER_ID)," +
				"CONSTRAINT U_fk_MEMBER_GROUP FOREIGN KEY (GROUP_ID) REFERENCES groups (ID)," +
				"CONSTRAINT U_fk_MEMBER_USER FOREIGN KEY (USER_ID) REFERENCES users (ID))");

		String sql;		

        CreateTable(String sql) {
            this.sql = sql;
        }
    };

	protected DbH2Next() {
		super();
	}

	public String getCreateTable(String table) {
		return CreateTable.valueOf("CREATE"+table.toUpperCase()).sql;
	}
}
