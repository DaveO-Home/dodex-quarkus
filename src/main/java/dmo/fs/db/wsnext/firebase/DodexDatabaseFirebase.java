package dmo.fs.db.wsnext.firebase;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.reactive.DbConfiguration;
import dmo.fs.db.MessageUser;
import dmo.fs.db.MessageUserImpl;
import dmo.fs.utils.DodexUtil;
import io.quarkus.runtime.configuration.ProfileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;


public class DodexDatabaseFirebase extends DbFirebaseBase implements DodexFirebase {
	protected final static Logger logger = LoggerFactory.getLogger(DodexDatabaseFirebase.class.getSimpleName());

	protected Properties dbProperties;
	protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
	protected Map<String, String> dbMap;
	protected String webEnv = !ProfileManager.getLaunchMode().isDevOrTest() ? "prod" : "dev";
	protected JsonNode defaultNode;

	protected DodexUtil dodexUtil = new DodexUtil();

	public DodexDatabaseFirebase(Map<String, String> dbOverrideMap, Properties dbOverrideProps)
			throws IOException {
		super();

		defaultNode = dodexUtil.getDefaultNode();
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

	public DodexDatabaseFirebase() throws IOException {
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