
package dmo.fs.db;

public abstract class DbMariadb extends DbDefinitionBase implements DodexDatabase {
	protected static final String CHECKLOGINSQL = "select 1 from information_schema.tables where table_name='LOGIN';";
    public static final String CHECKUSERSQL = "select 1 from information_schema.tables where table_name='USERS';";
    protected static final String CHECKMESSAGESSQL = "select 1 from information_schema.tables where table_name='MESSAGES';";
    protected static final String CHECKUNDELIVEREDSQL = "select 1 from information_schema.tables where table_name='UNDELIVERED';";

    @SuppressWarnings("unchecked")
    public abstract <T> T getPool4();

    private enum CreateTable {
		CREATEUSERS(
			"CREATE TABLE USERS (" +
				"id INT NOT NULL AUTO_INCREMENT," +
				"name VARCHAR(255) CHARACTER SET  utf8mb4 collate  utf8mb4_bin NOT NULL COMMENT 'Dodex Users'," +
				"password VARCHAR(255) NOT NULL," +
				"ip VARCHAR(255) NOT NULL," +
				"last_login DATETIME NOT NULL," +
				"PRIMARY KEY (id)," +
				"UNIQUE INDEX name_password_UNIQUE (name ASC, password ASC));"),
		CREATEMESSAGES(
			"CREATE TABLE MESSAGES (" +
				"id INT NOT NULL AUTO_INCREMENT," +
				"message MEDIUMTEXT NOT NULL," +
				"from_handle VARCHAR(255) CHARACTER SET utf8mb4 NOT NULL," +
				"post_date DATETIME NOT NULL," +
				"PRIMARY KEY (id));"),
		CREATEUNDELIVERED(
			"CREATE TABLE UNDELIVERED (" +
				"user_id INT NOT NULL," +
				"message_id INT NOT NULL," +
				"INDEX fk_undelivered_users_idx (user_id ASC)," +
				"INDEX fk_undelivered_messages_idx (message_id ASC)," +
				"CONSTRAINT fk_undelivered_users " +
					"FOREIGN KEY (user_id) " +
					"REFERENCES USERS (id) " +
					"ON DELETE NO ACTION " +
					"ON UPDATE NO ACTION," +
				"CONSTRAINT fk_undelivered_messages " +
					"FOREIGN KEY (message_id) " +
					"REFERENCES MESSAGES (id) " +
					"ON DELETE NO ACTION " +
					"ON UPDATE NO ACTION);");

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
