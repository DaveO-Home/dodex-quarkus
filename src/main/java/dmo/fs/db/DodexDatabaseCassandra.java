package dmo.fs.db;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmo.fs.utils.DodexUtil;
import io.quarkus.runtime.configuration.ProfileManager;
import io.reactivex.disposables.Disposable;


public class DodexDatabaseCassandra extends DbCassandraBase implements DodexCassandra {
	private final static Logger logger = LoggerFactory.getLogger(DodexDatabaseCassandra.class.getName());
	protected Disposable disposable;
	protected Properties dbProperties = new Properties();
	protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
	protected Map<String, String> dbMap = new ConcurrentHashMap<>();
	protected JsonNode defaultNode;
	protected String webEnv = System.getenv("VERTXWEB_ENVIRONMENT");
	protected DodexUtil dodexUtil = new DodexUtil();

	public DodexDatabaseCassandra(Map<String, String> dbOverrideMap, Properties dbOverrideProps)
			throws IOException {
		super();

		defaultNode = dodexUtil.getDefaultNode();

		webEnv = "prod".equals(webEnv) || !ProfileManager.getLaunchMode().isDevOrTest() ? "prod" : "dev";

		dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
		dbProperties = dodexUtil.mapToProperties(dbMap);

		if (dbOverrideProps != null && dbOverrideProps.size() > 0) {
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
		webEnv = "prod".equals(webEnv) || !ProfileManager.getLaunchMode().isDevOrTest() ? "prod" : "dev";
		dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
		dbProperties = dodexUtil.mapToProperties(dbMap);

		dbProperties.setProperty("foreign_keys", "false");
	}

	@Override
	public MessageUser createMessageUser() {
		return new MessageUserImpl();
	}
}