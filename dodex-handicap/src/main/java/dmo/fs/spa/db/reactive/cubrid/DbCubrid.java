
package dmo.fs.spa.db.reactive.cubrid;

import dmo.fs.spa.db.reactive.SpaDatabaseReactive;
import dmo.fs.spa.db.reactive.SqlBuilder;

import java.util.Locale;

public abstract class DbCubrid extends SqlBuilder implements SpaDatabaseReactive {
	protected final static String CHECKLOGINSQL = "SELECT class_name FROM _db_class WHERE class_name = 'login'";

	private enum CreateTable {

		CREATELOGIN(
			"CREATE TABLE login" +
				"(id INTEGER AUTO_INCREMENT(1, 1) NOT NULL," +
				"[name] CHARACTER VARYING (255) COLLATE utf8_en_cs NOT NULL," +
				"[password] CHARACTER VARYING (255) NOT NULL," +
				"last_login TIMESTAMP," +
				"CONSTRAINT pk_login_id PRIMARY KEY(id)) " +
				"COLLATE iso88591_bin " +
				"REUSE_OID;");

        final String sql;

        CreateTable(String sql) {
            this.sql = sql;
        }
    }

	protected DbCubrid() {
		super();
	}

	public String getCreateTable(String table) {
		return CreateTable.valueOf("CREATE"+table.toUpperCase(Locale.US)).sql;
	}
}
