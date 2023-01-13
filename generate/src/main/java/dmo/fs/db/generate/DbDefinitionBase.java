
package dmo.fs.db.generate;

import dmo.fs.db.generate.utils.DodexUtil;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.jdbcclient.JDBCPool;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.pgclient.PgPool;
import io.vertx.rxjava3.sqlclient.Pool;
import org.jooq.DSLContext;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;

public abstract class DbDefinitionBase {
  private final static Logger logger = LoggerFactory.getLogger(DbDefinitionBase.class.getName());

  protected static DSLContext create;
  private Boolean isTimestamp;
  protected Vertx vertx;
  protected static Pool pool;

  public static <T> void setupSql(T pool4) throws IOException, SQLException {
    // Non-Blocking Drivers
    if (pool4 instanceof PgPool) {
        pool = (PgPool) pool4;
      boolean qmark = false;
    } else if (pool4 instanceof MySQLPool) {
      pool = (MySQLPool) pool4;
    } else if (pool4 instanceof JDBCPool) {
      pool = (JDBCPool) pool4;
    }

    Settings settings = new Settings().withRenderNamedParamPrefix("$"); // making compatible with Vertx4/Postgres

    create = DSL.using(DodexUtil.getSqlDialect(), settings);
  }

  public void setIsTimestamp(Boolean isTimestamp) {
    this.isTimestamp = isTimestamp;
  }

  public boolean getisTimestamp() {
    return this.isTimestamp;
  }

  public Vertx getVertx() {
    return vertx;
  }

  public void setVertx(Vertx vertx) {
    this.vertx = vertx;
  }

  public static DSLContext getCreate() {
    return create;
  }
}
