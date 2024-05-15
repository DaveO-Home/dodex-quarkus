
package dmo.fs.db.wsnext.mariadb;

import dmo.fs.db.wsnext.DbDefinitionBase;
import dmo.fs.db.wsnext.DodexDatabase;

public abstract class DbMariadb extends DbDefinitionBase implements DodexDatabase {
	protected static final String CHECKLOGINSQL = "select 1 from information_schema.tables where table_name='LOGIN';";
    public static final String CHECKUSERSQL = "select 1 from information_schema.tables where table_name='users' and table_schema = 'test';";
    protected static final String CHECKMESSAGESSQL = "select 1 from information_schema.tables where table_name='messages';";
    protected static final String CHECKUNDELIVEREDSQL = "select 1 from information_schema.tables where table_name='undelivered';";

    @SuppressWarnings("unchecked")
    public abstract <T> T getPool4();

    protected enum CreateTable {
		CREATEUSERS(
			"CREATE TABLE users (" +
				"id INT NOT NULL AUTO_INCREMENT," +
				"name VARCHAR(255) CHARACTER SET  utf8mb4 collate  utf8mb4_bin NOT NULL COMMENT 'Dodex Users'," +
				"password VARCHAR(255) NOT NULL," +
				"ip VARCHAR(255) NOT NULL," +
				"last_login DATETIME NOT NULL," +
				"PRIMARY KEY (id)," +
				"UNIQUE INDEX name_password_UNIQUE (name ASC, password ASC));"),
		CREATEMESSAGES(
			"CREATE TABLE messages (" +
				"id INT NOT NULL AUTO_INCREMENT," +
				"message MEDIUMTEXT NOT NULL," +
				"from_handle VARCHAR(255) CHARACTER SET utf8mb4 NOT NULL," +
				"post_date DATETIME NOT NULL," +
				"PRIMARY KEY (id));"),
		CREATEUNDELIVERED(
			"CREATE TABLE undelivered (" +
				"user_id INT NOT NULL," +
				"message_id INT NOT NULL," +
				"INDEX fk_undelivered_users_idx (user_id ASC)," +
				"INDEX fk_undelivered_messages_idx (message_id ASC)," +
				"CONSTRAINT fk_undelivered_users " +
					"FOREIGN KEY (user_id) " +
					"REFERENCES users (id) " +
					"ON DELETE NO ACTION " +
					"ON UPDATE NO ACTION," +
				"CONSTRAINT fk_undelivered_messages " +
					"FOREIGN KEY (message_id) " +
					"REFERENCES messages (id) " +
					"ON DELETE NO ACTION " +
					"ON UPDATE NO ACTION);"),
		CREATEGROUPS(
				"CREATE TABLE IF NOT EXISTS groups (" +
						"ID INTEGER primary key auto_increment NOT NULL," +
						"NAME VARCHAR(24) NOT NULL," +
						"OWNER INTEGER NOT NULL DEFAULT 0," +
						"CREATED DATETIME NOT NULL DEFAULT current_timestamp()," +
						"UPDATED DATETIME DEFAULT NULL ON UPDATE current_timestamp()," +
						"UNIQUE KEY unique_on_name (NAME))"
		),
		CREATEMEMBER(
				"CREATE TABLE IF NOT EXISTS member (" +
						"GROUP_ID INTEGER NOT NULL DEFAULT 0," +
						"USER_ID INTEGER NOT NULL DEFAULT 0," +
						"PRIMARY KEY (GROUP_ID,USER_ID)," +
						"KEY U_fk_MEMBER_USER_idx (USER_ID)," +
						"CONSTRAINT U_fk_MEMBER_GROUP FOREIGN KEY (GROUP_ID) REFERENCES groups (ID) ON DELETE NO ACTION ON UPDATE NO ACTION," +
						"CONSTRAINT U_fk_MEMBER_USER FOREIGN KEY (USER_ID) REFERENCES users (ID) ON DELETE NO ACTION ON UPDATE NO ACTION)"
		);

        String sql;

        CreateTable(String sql) {
            this.sql = sql;
        }
    };

	protected DbMariadb() {
		super();
	}

	public String getCreateTable(String table) {
		return CreateTable.valueOf("CREATE"+table.toUpperCase()).sql;
	}
}
