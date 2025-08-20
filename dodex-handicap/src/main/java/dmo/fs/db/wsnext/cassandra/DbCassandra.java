
package dmo.fs.db.wsnext.cassandra;

import java.util.Locale;

public abstract class DbCassandra extends DbCassandraBase implements DodexCassandra {
	
	protected enum CreateTable {
		CreateDummy("");

        String sql;

        CreateTable(String sql) {
            this.sql = sql;
        }
    }

	protected DbCassandra() {
		super();
	}

	public String getCreateTable(String table) {
		return CreateTable.valueOf("CREATE"+table.toUpperCase(Locale.US)).sql;
	}
}
