
package dmo.fs.spa.db;

public abstract class DbPostgres extends SqlBuilder implements SpaDatabase {
	public static final String CHECKLOGIN = "SELECT to_regclass('public.login')";

	private enum CreateTable {
		CREATELOGIN(
			"CREATE SEQUENCE public.login_id_seq INCREMENT 1 START 19 MINVALUE 1 MAXVALUE 2147483647 CACHE 1; " +	
			"ALTER SEQUENCE public.login_id_seq OWNER TO dummy;" +
			"CREATE TABLE public.login" +
				"(id integer NOT NULL DEFAULT nextval('login_id_seq'::regclass)," +
				"name character varying(255) COLLATE pg_catalog.\"default\"," +
				"password character varying(255) COLLATE pg_catalog.\"default\"," +
				"last_login timestamp with time zone," +
				"CONSTRAINT login_pkey PRIMARY KEY (id)," +
				"CONSTRAINT login_name_unique UNIQUE (name)," +
				"CONSTRAINT login_password_unique UNIQUE (password))" +
				"WITH (OIDS = FALSE) TABLESPACE pg_default;" +		
			"ALTER TABLE public.login OWNER to dummy;");

        String sql;

        CreateTable(String sql) {
            this.sql = sql;
        }
    }

	protected DbPostgres() {
		super();
	}

	public String getCreateTable(String table) {
		return CreateTable.valueOf("CREATE"+table.toUpperCase()).sql;
	}
}
