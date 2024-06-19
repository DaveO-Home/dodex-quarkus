package dmo.fs.db.wsnext.cassandra;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.wsnext.DbConfiguration;
import dmo.fs.db.MessageUser;
import dmo.fs.db.MessageUserImpl;
import dmo.fs.quarkus.Server;
import dmo.fs.utils.DodexUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class DodexDatabaseCassandra extends DbCassandraBase implements DodexCassandra {
	protected final static Logger logger = LoggerFactory.getLogger(DodexDatabaseCassandra.class.getName());
	protected Properties dbProperties;
	protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
	protected Map<String, String> dbMap;
	protected JsonNode defaultNode;
	protected String webEnv = Server.isProduction() ? "prod" : "dev";
	protected DodexUtil dodexUtil = new DodexUtil();

	public DodexDatabaseCassandra(Map<String, String> dbOverrideMap, Properties dbOverrideProps)
			throws IOException {
		super();

		defaultNode = dodexUtil.getDefaultNode();

		dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
		dbProperties = dodexUtil.mapToProperties(dbMap);

		if (dbOverrideProps != null && !dbOverrideProps.isEmpty()) {
			this.dbProperties = dbOverrideProps;
		}
		if (dbOverrideMap != null) {
			this.dbOverrideMap = dbOverrideMap;
		}

		dbProperties.setProperty("foreign_keys", "true");

		DbConfiguration.mapMerge(dbMap, dbOverrideMap);
	}

	public DodexDatabaseCassandra() throws InterruptedException, IOException, SQLException {
		super();

		defaultNode = dodexUtil.getDefaultNode();
		dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
		dbProperties = dodexUtil.mapToProperties(dbMap);

		dbProperties.setProperty("foreign_keys", "false");
	}

	@Override
	public MessageUser createMessageUser() {
		return new MessageUserImpl();
	}
}