
package dmo.fs.spa.db;

import static org.jooq.impl.DSL.deleteFrom;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.insertInto;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.update;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Date;

import org.jooq.DSLContext;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.db.DbConfiguration;
import dmo.fs.spa.utils.SpaLogin;
import dmo.fs.utils.ColorUtilConstants;
import dmo.fs.utils.DodexUtil;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Promise;
import io.vertx.mutiny.db2client.DB2Pool;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;

public abstract class SqlBuilder {
    private static final Logger logger = LoggerFactory.getLogger(SqlBuilder.class.getName());
    protected static final String QUERYLOGIN = "select * from LOGIN where name=?";

    private static DSLContext create;

    private static String GETLOGINBYNP;
    private static String GETLOGINBYNAME;
    private static String GETINSERTLOGIN;
    private static String GETREMOVELOGIN;
    private static String GETUPDATELOGIN;
    private static String GETLOGINBYID;
    private static String GETSQLITEUPDATELOGIN;
    private Boolean isTimestamp;
    private static Pool pool;
    private static Boolean qmark = true;

    public static <T> void setupSql(T pool4) {
        // Non-Blocking Drivers
        if (pool4 instanceof PgPool) {
            pool = (PgPool) pool4;
            qmark = false;
        } else if (pool4 instanceof MySQLPool) {
            pool = (MySQLPool) pool4;
        } else if (pool4 instanceof DB2Pool) {
            pool = (DB2Pool) pool4;
        }

        Settings settings = new Settings().withRenderNamedParamPrefix("$"); // making compatible with Vertx4/Postgres

        create = DSL.using(DodexUtil.getSqlDialect(), settings);

        GETLOGINBYNP = setupLoginByNamePassword().replaceAll("\\$\\d", "?");
        GETLOGINBYNAME = qmark ? setupLoginByName().replaceAll("\\$\\d", "?") : setupLoginByName();
        GETINSERTLOGIN = qmark ? setupInsertLogin().replaceAll("\\$\\d", "?") : setupInsertLogin();
        GETREMOVELOGIN = setupRemoveLogin().replaceAll("\\$\\d", "?");
        GETLOGINBYID = qmark ? setupLoginById().replaceAll("\\$\\d", "?") : setupLoginById();
        GETUPDATELOGIN = qmark ? setupUpdateLogin().replaceAll("\\$\\d*", "?") : setupUpdateLogin();
        GETSQLITEUPDATELOGIN = qmark ? setupSqliteUpdateLogin().replaceAll("\\$\\d*", "?") : setupSqliteUpdateLogin();
    }

    private static String setupLoginByNamePassword() {
        return create.renderNamedParams(select(field("ID"), field("NAME"), field("PASSWORD"), field("LAST_LOGIN"))
                .from(table("LOGIN")).where(field("NAME").eq("$")).and(field("PASSWORD").eq("$")));
    }

    public String getLoginByNamePassword() {
        return GETLOGINBYNP;
    }

    private static String setupLoginByName() {
        return create.renderNamedParams(select(field("ID"), field("NAME"), field("PASSWORD"), field("LAST_LOGIN"))
                .from(table("LOGIN")).where(field("NAME").eq("$")));
    }

    public String getUserByName() {
        return GETLOGINBYNAME;
    }

    private static String setupLoginById() {
        return create.renderNamedParams(select(field("ID"), field("NAME"), field("PASSWORD"), field("LAST_LOGIN"))
                .from(table("LOGIN")).where(field("NAME").eq("$")));
    }

    public String getUserById() {
        return GETLOGINBYID;
    }

    private static String setupInsertLogin() {
        return create.renderNamedParams(
                insertInto(table("LOGIN")).columns(field("NAME"), field("PASSWORD"), field("LAST_LOGIN"))
                        .values("$", "$", "$").returning(field("ID")));
    }

    public String getInsertLogin() {
        return GETINSERTLOGIN;
    }

    private static String setupUpdateLogin() {
        return create.renderNamedParams(
                update(table("LOGIN")).set(field("LAST_LOGIN"), "$").where(field("ID").eq("$")).returning());
    }

    public String getUpdateLogin() {
        return GETUPDATELOGIN;
    }

    public static String setupSqliteUpdateLogin() {
        return "update LOGIN set last_login = $ where id = $";
    }

    public String getSqliteUpdateLogin() {
        return GETSQLITEUPDATELOGIN;
    }

    private static String setupRemoveLogin() {
        return create
                .renderNamedParams(deleteFrom(table("LOGIN")).where(field("NAME").eq("$"), field("PASSWORD").eq("$")));
    }

    public String getRemoveLogin() {
        return GETREMOVELOGIN;
    }

    public abstract SpaLogin createSpaLogin();

    public Promise<SpaLogin> getLogin(SpaLogin spaLogin) {
        Promise<SpaLogin> promise = Promise.promise();

        SpaLogin resultLogin = createSpaLogin();
        resultLogin.setStatus("0");

        pool.getConnection().onItem().call(conn -> {
            conn.query(create.query(getLoginByNamePassword(), spaLogin.getName(), spaLogin.getPassword()).toString())
                .execute().onItem().call(rows -> {
                    if (rows.size() == 0) {
                        if (!(spaLogin.getPassword().equals(resultLogin.getPassword()))) {
                            resultLogin.setStatus("-1");
                            resultLogin.setId(0L);
                            resultLogin.setName(spaLogin.getName());
                            resultLogin.setPassword(spaLogin.getPassword());
                            resultLogin.setLastLogin(new Date());
                        } else {
                            resultLogin.setStatus("0");
                        }
                    } else {
                        for (Row row : rows) {
                            try {
                                resultLogin.setId(row.getLong(0));
                                resultLogin.setName(row.getString(1));
                                resultLogin.setPassword(row.getString(2));
                                resultLogin.setLastLogin(row.getValue(3));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    if (rows.size() > 0 && "0".equals(resultLogin.getStatus())) {
                        conn.close().subscribeAsCompletionStage().isDone();

                        Promise<Integer> customPromise = updateCustomLogin(resultLogin);
                        customPromise.future().onItem().call(updated -> {
                            if (updated > -1) {
                                resultLogin.setStatus("0");
                                promise.complete(resultLogin);
                            } else {
                                logger.error(String.format("%s%s%s%s", ColorUtilConstants.RED_BOLD_BRIGHT,
                                        "Login Update failed...code: ", updated, ColorUtilConstants.RESET));
                                resultLogin.setStatus("-99");
                                promise.complete(resultLogin);
                            }
                            return Uni.createFrom().item(updated);
                        }).onFailure().invoke(err -> {
                            logger.error(String.format("%sError, Updating Last Login: %s -- %s%s", ColorUtilConstants.RED,
                                spaLogin.getName(), err.getCause().getMessage(), ColorUtilConstants.RESET));
                        }).subscribeAsCompletionStage().isDone();
                    } else {
                        promise.complete(resultLogin);
                        conn.close().subscribeAsCompletionStage().isDone();
                    }
                    
                    return Uni.createFrom().item(resultLogin);
                }).onFailure().invoke(err -> {
                    resultLogin.setStatus("-99");
                    promise.complete(resultLogin);
                    conn.close().subscribeAsCompletionStage().isDone();
                    logger.error(String.format("%sError retrieving user: %s -- %s%s", ColorUtilConstants.RED,
                            spaLogin.getName(), err.getCause().getMessage(), ColorUtilConstants.RESET));
                }).subscribeAsCompletionStage().isDone();
            return Uni.createFrom().item(promise);
        }).onFailure().invoke(err -> {
            logger.error(String.format("%sError, Get Login Connection: %s -- %s%s", ColorUtilConstants.RED,
                spaLogin.getName(), err.getCause().getMessage(), ColorUtilConstants.RESET));
        }).subscribeAsCompletionStage().isDone();

        return promise;
    }

    public Promise<SpaLogin> addLogin(SpaLogin spaLogin) {
        Promise<SpaLogin> promise = Promise.promise();
        Timestamp current = new Timestamp(new Date().getTime());
        OffsetDateTime time = OffsetDateTime.now();

        Object lastLogin = DbConfiguration.isUsingPostgres() ? time : current;
        spaLogin.setStatus("0");

        pool.getConnection().onItem().call(conn -> {
            Tuple parameters = Tuple.of(spaLogin.getName(), spaLogin.getPassword(), lastLogin);

            conn.preparedQuery(getInsertLogin()).execute(parameters).onItem().call(rows -> {
                for (Row row : rows) {
                    spaLogin.setId(row.getLong(0));
                    spaLogin.setLastLogin(current);
                }
                promise.complete(spaLogin);
                conn.close().subscribeAsCompletionStage().isDone();
                return null;
            }).onFailure().invoke(err -> {
                spaLogin.setStatus("-4");
                promise.complete(spaLogin);
                logger.error(String.format("%sError adding login: %s%s", ColorUtilConstants.RED, err,
                        ColorUtilConstants.RESET));
                conn.close().subscribeAsCompletionStage().isDone();
            }).subscribeAsCompletionStage().isDone();

            return Uni.createFrom().item(promise);
        }).onFailure().invoke(Throwable::printStackTrace).subscribeAsCompletionStage().isDone();

        return promise;
    }

    public Promise<SpaLogin> removeLogin(SpaLogin spaLogin) {
        Promise<SpaLogin> promise = Promise.promise();

        pool.getConnection().onItem().call(conn -> {
            String query = create.query(getRemoveLogin(), spaLogin.getName(), spaLogin.getPassword()).toString();

            conn.query(query).execute().onItem().call(rows -> {
                for (Row row : rows) {
                    spaLogin.setId(row.getLong(0));
                }
                Integer count = Integer.valueOf(rows.rowCount());

                spaLogin.setStatus(count.toString());
                if (spaLogin.getId() == null) {
                    spaLogin.setId(-1L);
                }
                if (spaLogin.getLastLogin() == null) {
                    spaLogin.setLastLogin(new Date());
                }
                promise.complete(spaLogin);

                conn.close().subscribeAsCompletionStage().isDone();
                return Uni.createFrom().item(promise);
            }).onFailure().invoke(err -> {
                logger.error(String.format("%sError deleting login: %s%s", ColorUtilConstants.RED, err,
                        ColorUtilConstants.RESET));
                spaLogin.setStatus("-4");
                promise.complete(spaLogin);
                conn.close().subscribeAsCompletionStage().isDone();
            }).subscribeAsCompletionStage().isDone();
            return Uni.createFrom().item(conn);
        }).onFailure().invoke(Throwable::printStackTrace).subscribeAsCompletionStage().isDone();

        return promise;
    }

    private Promise<Integer> updateCustomLogin(SpaLogin spaLogin) {
        Promise<Integer> promise = Promise.promise();

        pool.getConnection().onItem().call(conn -> {
            Timestamp timeStamp = new Timestamp(new Date().getTime());
            OffsetDateTime time = OffsetDateTime.now();
            long date = new Date().getTime();
            String sql = getUpdateLogin();
            LocalDateTime localTime = LocalDateTime.now();

            Object dateTime = time;
            if (DbConfiguration.isUsingIbmDB2()) {
                dateTime = localTime;
            } else if(DbConfiguration.isUsingSqlite3()) {
                dateTime = date;
            } else if(DbConfiguration.isUsingMariadb() || DbConfiguration.isUsingCubrid()) {
                dateTime = timeStamp;
            }

            spaLogin.setLastLogin(timeStamp);

            if (DbConfiguration.isUsingCubrid()) {
                // Cubrid fails with NullPointer using "preparedQuery"
                String query = create.query(getSqliteUpdateLogin(), dateTime, spaLogin.getId()).toString();

                conn.query(query).execute().onItem().call(rows -> {
                    conn.close().subscribeAsCompletionStage().isDone();
                    promise.complete(rows.rowCount());
                    return Uni.createFrom().item(rows);
                }).onFailure().invoke(err -> {
                    logger.error(String.format("%sError Updating login: %s%s", ColorUtilConstants.RED, err,
                            ColorUtilConstants.RESET));
                    err.printStackTrace();
                    conn.close().subscribeAsCompletionStage().isDone();
                    promise.complete(-99);
                    err.printStackTrace();
                }).subscribeAsCompletionStage().isDone();
            } else {
                conn.preparedQuery(sql).execute(Tuple.of(dateTime, spaLogin.getId())).onItem().call(rows -> {
                    promise.complete(rows.rowCount());
                    conn.close().subscribeAsCompletionStage().isDone();
                    return Uni.createFrom().item(rows);
                }).onFailure().invoke(err -> {
                    logger.error(String.format("%sError Updating login: %s%s", ColorUtilConstants.RED, err,
                            ColorUtilConstants.RESET));
                    promise.complete(-99);
                    err.printStackTrace();
                    conn.close().subscribeAsCompletionStage().isDone();
                }).subscribeAsCompletionStage().isDone();
            }
            return Uni.createFrom().item(promise);
        }).onFailure().invoke(Throwable::printStackTrace).subscribeAsCompletionStage().isDone();

        return promise;
    }

    public void setIsTimestamp(Boolean isTimestamp) {
        this.isTimestamp = isTimestamp;
    }

    public Boolean getIsTimestamp() {
        return isTimestamp;
    }
}
