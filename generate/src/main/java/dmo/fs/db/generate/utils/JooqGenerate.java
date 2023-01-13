package dmo.fs.db.generate.utils;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.Generate;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Logging;
import org.jooq.meta.jaxb.Target;

import com.fasterxml.jackson.databind.JsonNode;

import dmo.fs.db.generate.DbConfiguration;
import dmo.fs.db.generate.HandicapDatabase;

public class JooqGenerate {
  public static void main(String[] args) throws IOException {
    DodexUtil dodexUtil = new DodexUtil();
    JsonNode defaultNode = dodexUtil.getDefaultNode();
    Map<String, String> dbMap = dodexUtil.jsonNodeToMap(defaultNode, "dev");
    System.setProperty("org.jooq.no-logo", "true");
    System.setProperty("org.jooq.no-tips", "true");
    final String defaultDb = dodexUtil.getDefaultDb();

    try {
      HandicapDatabase handicapDatabase = DbConfiguration.getDefaultDb(true);
      handicapDatabase.checkOnTables().onComplete(c -> {
        String dbUrl = null;
        String jooqMetaName = "org.jooq.meta.sqlite.SQLiteDatabase";
        String databaseDbname = "";
        if ("sqlite3".equals(defaultDb)) {
          dbUrl = dbMap.get("url") + dbMap.get("filename");
        } else if ("mariadb".equals(defaultDb)) {
          dbUrl = "jdbc:mariadb:" + "//" + dbMap.get("host") + ":" +dbMap.get("port") + "/"
              + dbMap.get("dbname") + "?user=" + dbMap.get("CRED:user")
              + "&password=" + dbMap.get("CRED:password");
          jooqMetaName = "org.jooq.meta.mariadb.MariaDBDatabase";
          databaseDbname = dbMap.get("dbname");
        } else if ("postgres".equals(defaultDb)) {
          dbUrl = dbMap.get("url") + "//" + dbMap.get("host") + ":" + dbMap.get("port")
              + "/" + dbMap.get("dbname") + "?user=" + dbMap.get("CRED:user")
              + "&password=" + dbMap.get("CRED:password");
          jooqMetaName = "org.jooq.meta.postgres.PostgresDatabase";
          databaseDbname = "public"; // dbMap.get("database");
        }
        else if ("h2".equals(defaultDb)) {
            dbUrl = dbMap.get("url")+dbMap.get("filename");
            jooqMetaName = "org.jooq.meta.h2.H2Database";
            databaseDbname = "";
        }
        System.out.println("Generate: "+dbUrl + " -- "+jooqMetaName + " -- "+databaseDbname);
        generateJooqObjects(dbUrl, jooqMetaName, databaseDbname);
        System.exit(0);
      });
    } catch (SQLException | IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }

  private static void generateJooqObjects(String jdbcUrl, String jooqMetaName, String databaseDbname) {
    try {
      // ForcedType ft = new ForcedType();
      // ft.setName("BLOB");
      // ft.withIncludeExpression("SEQ|NAME");

      boolean generateSequences = false;
      if("org.jooq.meta.postgres.PostgresDatabase".equals(jooqMetaName)) {
        generateSequences = true;
      }
      Jdbc jdbc = new Jdbc().withUrl(jdbcUrl);
      if("org.jooq.meta.h2.H2Database".equals(jooqMetaName)) {
          jdbc.withUser("sa").withPassword("sa");
      }
      Configuration configuration = new Configuration()
          .withJdbc(jdbc)
          .withGenerator(new Generator()
              .withName("org.jooq.codegen.KotlinGenerator")
              .withDatabase(new Database()
                  // .withForcedTypes(ft)
                  .withName(jooqMetaName)
                  .withOutputSchemaToDefault(true)
                  .withIncludeTables(true)
                  .withIncludePrimaryKeys(true)
                  .withInputSchema(databaseDbname)
                  .withExcludes(
                      "USERS|UNDELIVERED|MESSAGES|LOGIN|SQLITE_SEQUENCE"))
              .withGenerate(new Generate()
                  .withDeprecated(false)
              .withSequences(generateSequences)
              .withDaos(false)
//              .withEmptyCatalogs(true)
              )
              .withTarget(new Target()
                  .withPackageName("golf.handicap.generated")
                  .withClean(true)
                  .withDirectory("./src/main/kotlin")))
          .withLogging(Logging.WARN);
      GenerationTool.generate(configuration);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }
}
