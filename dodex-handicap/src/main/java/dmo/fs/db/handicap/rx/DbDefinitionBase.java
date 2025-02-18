
package dmo.fs.db.handicap.rx;

import dmo.fs.db.handicap.utils.DodexUtil;
import golf.handicap.db.rx.PopulateCourse;
import golf.handicap.db.rx.PopulateGolfer;
import golf.handicap.db.rx.PopulateGolferScores;
import golf.handicap.db.rx.PopulateScore;
import io.vertx.mysqlclient.impl.MySQLPoolImpl;
import io.vertx.pgclient.impl.PgPoolImpl;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.sqlclient.Pool;
import org.jooq.DSLContext;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;

public abstract class DbDefinitionBase {
    protected final static Logger logger = LoggerFactory.getLogger(DbDefinitionBase.class.getName());

    protected static DSLContext create;

    protected Boolean isTimestamp;
    protected Vertx vertx;
    protected static Pool pool;
    protected static boolean qmark = true;

    public static <T> void setupSql(T pool4) throws IOException, SQLException {
        // Non-Blocking Drivers
        if (((Pool)pool4).getDelegate() instanceof PgPoolImpl) {
            pool = (Pool)pool4;
            qmark = false;
        } else if (((Pool)pool4).getDelegate() instanceof MySQLPoolImpl) {
            pool = (Pool)pool4;
        } else {
            pool = (Pool)pool4;
        }
        Settings settings = new Settings().withRenderNamedParamPrefix("$"); // making compatible with Vertx4/Postgres

        create = DSL.using(DodexUtil.getSqlDialect(), settings);
        PopulateGolfer.setQMark(qmark);
        PopulateGolfer.setSqlPool(pool);
        PopulateGolfer.setDslContext(create);
        PopulateGolfer.buildSql();
        PopulateCourse.buildSql();
        PopulateScore.buildSql();
        PopulateGolferScores.buildSql();
    }

    public void setIsTimestamp(Boolean isTimestamp) {
        this.isTimestamp = isTimestamp;
    }

    public boolean getisTimestamp() {
        return this.isTimestamp;
    }

    public Vertx getVertxR() {
        return vertx;
    }

    public void setVertxR(Vertx vertx) {
        this.vertx = vertx;
    }

    public static DSLContext getCreate() {
        return create;
    }
}
