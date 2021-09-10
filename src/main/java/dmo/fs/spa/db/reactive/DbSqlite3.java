
package dmo.fs.spa.db.reactive;

public abstract class DbSqlite3 extends SqlBuilder implements SpaDatabaseReactive {
	public static final String CHECKLOGINSQL = "SELECT name FROM sqlite_master WHERE type='table' AND name='login'";

	private enum CreateTable {
		CREATELOGIN("create table login (id integer primary key, name text not null unique, password text not null, last_login DATETIME not null)");

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
