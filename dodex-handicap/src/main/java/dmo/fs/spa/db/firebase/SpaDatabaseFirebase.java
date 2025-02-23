package dmo.fs.spa.db.firebase;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.quarkus.Server;
import dmo.fs.spa.db.SpaDbConfiguration;
import dmo.fs.spa.utils.SpaLogin;
import dmo.fs.spa.utils.SpaLoginImpl;
import dmo.fs.utils.DodexUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class SpaDatabaseFirebase extends DbFirebaseBase implements SpaFirebase {
	protected static final Logger logger = LoggerFactory.getLogger(SpaDatabaseFirebase.class.getName());
	protected Properties dbProperties;
	protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
	protected Map<String, String> dbMap;
	protected JsonNode defaultNode;
	protected String webEnv = Server.isProduction() ? "prod" : "dev";
	protected DodexUtil dodexUtil = new DodexUtil();

	public SpaDatabaseFirebase(Map<String, String> dbOverrideMap, Properties dbOverrideProps)
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

		SpaDbConfiguration.mapMerge(dbMap, dbOverrideMap);
	}

	public SpaDatabaseFirebase() throws InterruptedException, IOException, SQLException {
		super();

		defaultNode = dodexUtil.getDefaultNode();
		dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
		dbProperties = dodexUtil.mapToProperties(dbMap);

		dbProperties.setProperty("foreign_keys", "true");
	}

	@Override
	public SpaLogin createSpaLogin() {
		return new SpaLoginImpl();
	}
}
