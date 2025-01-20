
package dmo.fs.db.dodex.sqlite3;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.dodex.CreateDatabaseImpl;
import dmo.fs.db.dodex.utils.DodexUtil;
import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.AgroalDataSourceConfiguration;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.mutiny.core.Promise;
import io.vertx.reactivex.jdbcclient.JDBCPool;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.reactivex.core.Vertx;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import static io.agroal.api.configuration.AgroalConnectionFactoryConfiguration.TransactionIsolation.SERIALIZABLE;
import static io.agroal.api.configuration.AgroalConnectionPoolConfiguration.ConnectionValidator.defaultValidator;
import static java.time.Duration.ofSeconds;

public class DodexDatabaseSqlite3 extends DbSqlite3 {
    protected final static Logger logger =
      LoggerFactory.getLogger(DodexDatabaseSqlite3.class.getName());
    private PoolOptions poolOptions;
    private JDBCConnectOptions connectOptions;
    protected Properties dbProperties;
    protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
    protected Map<String, String> dbMap;
    protected JsonNode defaultNode;
    protected String webEnv = DodexUtil.getEnv();
    protected DodexUtil dodexUtil = new DodexUtil();
    protected Pool pool4;
    protected Boolean isCreateTables = false;
    protected Promise<String> returnPromise = Promise.promise();

    public DodexDatabaseSqlite3(Map<String, String> dbOverrideMap, Properties dbOverrideProps)
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

        assert dbOverrideMap != null;
        CreateDatabaseImpl.mapMerge(dbMap, dbOverrideMap);
        databaseSetup();
    }

    public DodexDatabaseSqlite3() throws IOException {
        super();
        defaultNode = dodexUtil.getDefaultNode();
        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
        dbProperties = dodexUtil.mapToProperties(dbMap);

        dbProperties.setProperty("foreign_keys", "true");
        databaseSetup();
    }

    public DodexDatabaseSqlite3(Boolean isCreateTables)
      throws IOException {
        super();
        defaultNode = dodexUtil.getDefaultNode();

        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
        dbProperties = dodexUtil.mapToProperties(dbMap);

        dbProperties.setProperty("foreign_keys", "true");
        this.isCreateTables = isCreateTables;
        logger.info("In (true) sqlite: {}", defaultNode);
    }

    public Uni<String> checkOnTables() {
        if (isCreateTables) {
            databaseSetup();
        }
        return returnPromise.future();
    }

    public Promise<Pool> databaseSetup() {
        Promise<Pool> poolPromise = Promise.promise();
        if ("dev".equals(webEnv)) {
            CreateDatabaseImpl.configureTestDefaults(dbMap, dbProperties);
        } else {
            CreateDatabaseImpl.configureDefaults(dbMap, dbProperties); // Using prod (./dodex.db)
        }

        poolOptions =
          new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

        connectOptions = new JDBCConnectOptions()
          .setJdbcUrl(dbMap.get("url") + dbMap.get("filename") + "?foreign_keys=on;")
          .setIdleTimeout(1)
        // .setCachePreparedStatements(true)
        ;
        if (DodexUtil.getVertxR() == null) {
            DodexUtil.setVertxR(Vertx.vertx());
        }

        AgroalDataSourceConfigurationSupplier configuration = getConfiguration();

        try (AgroalDataSource dataSource = AgroalDataSource.from(configuration)) {
            Connection connection = dataSource.getConnection();
            String[] types = {"table"};

            Multi.createFrom().items(tables)
              .onItem().transform(table -> {
                  boolean isNext;
                  try {
                      isNext = connection.getMetaData().getTables(".", "sqlite_master", table, types).next();

                      if (!isNext) {
                          connection.createStatement().execute(getCreateTable(table));
                      }
                  } catch (SQLException se) {
                      throw new RuntimeException(se.getMessage());
                  }

                  return new String[]{String.valueOf(isNext), table};
              }).onFailure().invoke(Throwable::printStackTrace)
              .subscribe().with(data -> {
                  if ("false".equals(data[0])) {
                      if ("users".equals(data[1])) {
                          logger.info("Using database: {}", dbMap.get("filename"));
                      }
                      logger.info("{} Table Added.", data[1].substring(0, 1).toUpperCase() + data[1].substring(1));
                  }
              });
            connection.close();
        } catch (SQLException e) {
            logger.info("Agroal Connection Error: {}", e.getMessage());
        }
        poolPromise.complete(pool4);

        return poolPromise;
    }

    private AgroalDataSourceConfigurationSupplier getConfiguration() {
        return new AgroalDataSourceConfigurationSupplier()
          .dataSourceImplementation(AgroalDataSourceConfiguration.DataSourceImplementation.AGROAL)
          .metricsEnabled(false)
          .connectionPoolConfiguration(cp -> cp
            .minSize(1)
            .maxSize(1)
            .initialSize(1)
            .connectionValidator(defaultValidator())
            .acquisitionTimeout(ofSeconds(5))
            .leakTimeout(ofSeconds(5))
            .validationTimeout(ofSeconds(50))
            .reapTimeout(ofSeconds(500))
            .connectionFactoryConfiguration(cf -> cf
              .jdbcUrl(dbMap.get("url") + dbMap.get("filename") + "?foreign_keys=on;")
              .connectionProviderClassName("org.sqlite.JDBC")
              .autoCommit(true)
              .jdbcTransactionIsolation(SERIALIZABLE)
            )
          );
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getPool4() {
        PoolOptions poolOptions = new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

        JDBCConnectOptions connectOptions;
        connectOptions = new JDBCConnectOptions()
          .setJdbcUrl(dbMap.get("url") + dbMap.get("filename") + "?foreign_keys=on;")
          .setIdleTimeout(1)
        ;

        Vertx vertx = DodexUtil.getVertxR();

        return (T) JDBCPool.pool(vertx, connectOptions, poolOptions);
    }

    public io.vertx.mutiny.core.Vertx getVertx() {
        return null;
    }


    @SuppressWarnings("unchecked")
    public <T> T getConnectOptions() {
        return (T) connectOptions;
    }

    public PoolOptions getPoolOptions() {
        return poolOptions;
    }
}
