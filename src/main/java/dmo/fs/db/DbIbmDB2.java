
package dmo.fs.db;

public abstract class DbIbmDB2 extends DbDefinitionBase implements DodexDatabase {
	protected static final String CHECKUSERSQL = "select tabname from syscat.tables where tabschema='DB2INST1' and tabname='USERS'";
    protected static final String CHECKMESSAGESQL = "select tabname from syscat.tables where tabschema='DB2INST1' and tabname='MESSAGES'";
    protected static final String CHECKUNDELIVEREDSQL = "select tabname from syscat.tables where tabschema='DB2INST1' and tabname='UNDELIVERED'";
    
	private enum CreateTable {
		CREATEUSERS(
			"CREATE TABLE users (" +
				"id INTEGER NOT NULL GENERATED ALWAYS AS IDENTITY, " +
				"name VARCHAR(255) NOT NULL, " +
				"password VARCHAR(255) NOT NULL, " +
				"ip VARCHAR(255) NOT NULL, " +
				"last_login TIMESTAMP(12) NOT NULL, " +
				"PRIMARY KEY (id))"),
		CREATEUSERSINDEX(
			"CREATE UNIQUE INDEX XUSERS " +
			"ON USERS " +
		  "(name ASC, password ASC)"),
		CREATEMESSAGES(
			"CREATE TABLE messages (" +
				"id INTEGER NOT NULL GENERATED ALWAYS AS IDENTITY," +
				"message VARCHAR(32672) NOT NULL," +
				"from_handle VARCHAR(255) NOT NULL," +
				"post_date TIMESTAMP(12) NOT NULL, " +
				"PRIMARY KEY (id))"),
		CREATEUNDELIVERED(
			"CREATE TABLE undelivered (" +
				"user_id INTEGER NOT NULL," +
				"message_id INTEGER NOT NULL, " +
				"FOREIGN KEY (USER_ID) " +
				"REFERENCES USERS (ID) ON DELETE RESTRICT, " +
				"FOREIGN KEY (MESSAGE_ID) " +
				"REFERENCES MESSAGES (ID) ON DELETE RESTRICT)"
				);

        String sql;

        CreateTable(String sql) {
            this.sql = sql;
        }
    };

	protected DbIbmDB2() {
		super();
	}

	public String getCreateTable(String table) {
		return CreateTable.valueOf("CREATE"+table.toUpperCase()).sql;
	}

	public String getUsersIndex(String table) {
		return CreateTable.valueOf("CREATE"+table.toUpperCase()+"INDEX").sql;
	}
}
