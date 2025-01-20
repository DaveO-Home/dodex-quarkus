
package dmo.fs.db.dodex.cubrid;

import dmo.fs.db.dodex.CreateDatabase;

public abstract class DbCubrid implements CreateDatabase {
    protected final static String[] tables = {"users", "messages", "undelivered", "login", "groups", "member"};

    protected static final String CHECKUSERSQL = "SELECT class_name FROM _db_class WHERE class_name = 'users'";
    protected static final String CHECKMESSAGESQL = "SELECT class_name FROM _db_class WHERE class_name = 'messages'";
    protected static final String CHECKUNDELIVEREDSQL = "SELECT class_name FROM _db_class WHERE class_name = 'undelivered'";
    protected final static String CHECKLOGINSQL = "SELECT class_name FROM _db_class WHERE class_name = 'login'";

    protected enum CreateTable {

        CREATEUSERS(
          "CREATE TABLE users" +
            "(id INTEGER AUTO_INCREMENT(1, 1) NOT NULL," +
            "[name] CHARACTER VARYING (255) COLLATE utf8_en_cs NOT NULL," +
            "[password] CHARACTER VARYING (255) NOT NULL," +
            "ip CHARACTER VARYING (255) NOT NULL," +
            "last_login TIMESTAMP," +
            "CONSTRAINT pk_users_id PRIMARY KEY(id)) " +
            "COLLATE iso88591_bin " +
            "REUSE_OID;"),
        CREATEMESSAGES(
          "CREATE TABLE messages" +
            "(id INTEGER AUTO_INCREMENT(1, 1) NOT NULL," +
            "message CLOB," +
            "from_handle CHARACTER VARYING (255)," +
            "post_date TIMESTAMP," +
            "CONSTRAINT pk PRIMARY KEY(id)) " +
            "COLLATE iso88591_bin " +
            "REUSE_OID;"),
        CREATEUNDELIVERED(
          "CREATE TABLE undelivered " +
            "(user_id integer, message_id integer," +
            "FOREIGN KEY(message_id) REFERENCES messages(id) ON DELETE RESTRICT ON UPDATE RESTRICT," +
            "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT) " +
            "COLLATE iso88591_bin " +
            "REUSE_OID; "),
        CREATELOGIN(
          "CREATE TABLE login" +
            "(id INTEGER AUTO_INCREMENT(1, 1) NOT NULL," +
            "[name] CHARACTER VARYING (255) COLLATE utf8_en_cs NOT NULL," +
            "[password] CHARACTER VARYING (255) NOT NULL," +
            "last_login TIMESTAMP," +
            "CONSTRAINT pk_login_id PRIMARY KEY(id)) " +
            "COLLATE iso88591_bin " +
            "REUSE_OID;"),
        CREATEGROUPS(
          "CREATE TABLE IF NOT EXISTS groups (" +
            "id integer primary key auto_increment(1, 1) NOT NULL," +
            "[name] CHARACTER VARYING (100) COLLATE utf8_en_cs NOT NULL UNIQUE," +
            "owner integer NOT NULL DEFAULT 0," +
            "created TIMESTAMP NOT NULL DEFAULT SYSDATETIME," +
            "updated TIMESTAMP DEFAULT NULL, " +
            "CONSTRAINT unique_name UNIQUE ([name])) " +
            "COLLATE iso88591_bin " +
            "REUSE_OID;"),
        CREATEMEMBER(
          "CREATE TABLE IF NOT EXISTS member (" +
            "group_id integer," +
            "user_id integer," +
            "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT," +
            "FOREIGN KEY(group_id) REFERENCES groups (id)," +
            "CONSTRAINT member_primary_keys PRIMARY KEY (group_id, user_id)) " +
            "COLLATE iso88591_bin " +
            "REUSE_OID;");

        final String sql;

        CreateTable(String sql) {
            this.sql = sql;
        }
    }

    ;

    protected DbCubrid() {
        super();
    }

    public String getCreateTable(String table) {
        return CreateTable.valueOf("CREATE" + table.toUpperCase()).sql;
    }
}
