
package dmo.fs.spa.db;

public abstract class DbMariadb extends SqlBuilder implements SpaDatabase {
    protected static final String CHECKLOGINSQL = "select 1 from information_schema.tables where table_name='LOGIN';";

	private enum CreateTable {
		CREATELOGIN(
			"CREATE TABLE LOGIN (" +
				"id INT NOT NULL AUTO_INCREMENT," +
				"name VARCHAR(255) CHARACTER SET  utf8mb4 collate  utf8mb4_bin NOT NULL COMMENT 'Dodex Users'," +
				"password VARCHAR(255) NOT NULL," +
				"last_login DATETIME NOT NULL," +
				"PRIMARY KEY (id)," +
				"UNIQUE INDEX name_password_UNIQUE (name ASC, password ASC));");

        final String sql;

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
