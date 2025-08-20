
package dmo.fs.db.dodex.sqlite3;

import dmo.fs.db.dodex.CreateDatabase;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;

import java.util.Locale;

public abstract class DbSqlite3 implements CreateDatabase {
  public final static String[] tables = {"users", "messages", "undelivered", "login", "groups", "member"};

  @Override
  public Uni<String> checkOnTables() {
    return null;
  }

  @Override
  public <T> T getPool4() {
    return null;
  }

  @Override
  public void setVertx(Vertx vertx) {
    //
  }

  @Override
  public void setVertxR(io.vertx.reactivex.core.Vertx vertx) {
    //
  }

  @Override
  public io.vertx.reactivex.core.Vertx getVertxR() {
    return null;
  }

  protected enum CreateTable {
    CREATEUSERS("create table IF NOT EXISTS users (" +
        "id integer primary key," +
        " name text not null unique," +
        " password text not null unique," +
        " ip text not null, last_login DATETIME not null)"),
    CREATEMESSAGES("create table IF NOT EXISTS messages (" +
        "id integer primary key," +
        " message text not null," +
        " from_handle text not null," +
        " post_date DATETIME not null)"),
    CREATEUNDELIVERED("create table IF NOT EXISTS undelivered (" +
        "user_id integer," +
        " message_id integer," +
        " CONSTRAINT undelivered_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id)," +
        " CONSTRAINT undelivered_message_id_foreign FOREIGN KEY (message_id) REFERENCES messages (id))"),
    CREATELOGIN("create table IF NOT EXISTS login (" +
        "id integer primary key," +
        " name text not null unique," +
        " password text not null," +
        " last_login DATETIME not null)"),
    CREATEGROUPS("CREATE TABLE IF NOT EXISTS groups (" +
        "id integer primary key autoincrement NOT NULL," +
        "name varchar(100) NOT NULL UNIQUE," +
        "owner integer NOT NULL DEFAULT 0," +
        "created datetime NOT NULL DEFAULT (datetime('now','localtime'))," +
        "updated datetime DEFAULT NULL)"),
    CREATEMEMBER("CREATE TABLE IF NOT EXISTS member (" +
        "group_id integer," +
        "user_id integer," +
        "CONSTRAINT member_primary_keys PRIMARY KEY (group_id, user_id) on conflict ignore," +
        "CONSTRAINT member_user_id_foreign FOREIGN KEY (user_id)" +
        "REFERENCES users (id)," +
        "CONSTRAINT member_group_id_foreign FOREIGN KEY (group_id)" +
        "REFERENCES groups (id))");

    final String sql;

    CreateTable(String sql) {
      this.sql = sql;
    }
  }

  public String getCreateTable(String table) {
    return CreateTable.valueOf("CREATE" + table.toUpperCase(Locale.US)).sql;
  }

//    protected enum CheckTable {
//        CHECKUSERSSQL("SELECT name FROM sqlite_master WHERE type='table' AND name='users'"),
//        CHECKMESSAGESSQL("SELECT name FROM sqlite_master WHERE type='table' AND name='messages'"),
//        CHECKUNDELIVEREDSQL("SELECT name FROM sqlite_master WHERE type='table' AND name='undelivered'"),
//        CHECKGROUPSSQL("SELECT name FROM sqlite_master WHERE type='table' AND name='groups'"),
//        CHECKMEMBERSQL("SELECT name FROM sqlite_master WHERE type='table' AND name='member'"),
//        CHECKGOLFERSQL("SELECT name FROM sqlite_master WHERE type='table' AND name = 'golfer"),
//        CHECKCOURSESQL("SELECT name FROM sqlite_master WHERE type='table' AND name = 'course'"),
//        CHECKRATINGSSQL("SELECT name FROM sqlite_master WHERE type='table' AND name = 'ratings'"),
//        CHECKSCORESSSQL("SELECT name FROM sqlite_master WHERE type='table' AND name = 'scores'"),
//        CHECKHANDICAPSQL("SELECT name FROM sqlite_master WHERE type='table' AND name in ('golfer', 'course', 'scores', 'ratings')")
//        ;
//
//        String check;
//
//        CheckTable(String check) {
//            this.check = check;
//        }
//    }
//
//    protected String getCheckTable(String table) {
//        return CheckTable.valueOf("CHECK" + table.toUpperCase() + "SQL").check;
//    }
}
