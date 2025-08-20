package dmo.fs.db.dodex.cubrid;

import com.fasterxml.jackson.databind.JsonNode;
import dmo.fs.db.dodex.CreateDatabaseImpl;
import dmo.fs.db.dodex.utils.DodexUtil;
import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.AgroalDataSourceConfiguration;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Promise;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.reactivex.jdbcclient.JDBCPool;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import static io.agroal.api.configuration.AgroalConnectionFactoryConfiguration.TransactionIsolation.SERIALIZABLE;
import static io.agroal.api.configuration.AgroalConnectionPoolConfiguration.ConnectionValidator.defaultValidator;
import static java.time.Duration.ofSeconds;

public class DodexDatabaseCubrid extends DbCubrid {
    protected static final Logger logger = LoggerFactory.getLogger(DodexDatabaseCubrid.class.getName());
    private PoolOptions poolOptions;
    private JDBCConnectOptions connectOptions;
    protected Properties dbProperties;
    protected Map<String, String> dbOverrideMap = new ConcurrentHashMap<>();
    protected Map<String, String> dbMap;
    protected JsonNode defaultNode;
    protected String webEnv = DodexUtil.getEnv();
    protected DodexUtil dodexUtil = new DodexUtil();
    protected Pool pool;

    public DodexDatabaseCubrid(Map<String, String> dbOverrideMap, Properties dbOverrideProps) throws IOException {
        super();

        defaultNode = dodexUtil.getDefaultNode();

        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
        dbProperties = dodexUtil.mapToProperties(dbMap);

        if (dbOverrideProps != null) {
            this.dbProperties = dbOverrideProps;
        }
        if (dbOverrideMap != null) {
            this.dbOverrideMap = dbOverrideMap;
        }

        CreateDatabaseImpl.mapMerge(dbMap, this.dbOverrideMap);
        databaseSetup();
    }

    public DodexDatabaseCubrid() throws IOException {
        super();
        defaultNode = dodexUtil.getDefaultNode();

        dbMap = dodexUtil.jsonNodeToMap(defaultNode, webEnv);
        dbProperties = dodexUtil.mapToProperties(dbMap);

        if (DodexUtil.getVertxR() == null) {
            DodexUtil.setVertxR(Vertx.vertx());
        }

        poolOptions =
          new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

        connectOptions = new JDBCConnectOptions()
          .setJdbcUrl(dbMap.get("url") + dbMap.get("host") + dbMap.get("dbname") + "?charSet=UTF-8")
          .setUser(dbProperties.getProperty("user"))
          .setPassword(dbProperties.getProperty("password"))
          .setIdleTimeout(1)
        ;
        databaseSetup();
    }

    @Override
    public Promise<Pool> databaseSetup() {
        if ("dev".equals(webEnv)) {
            CreateDatabaseImpl.configureTestDefaults(dbMap, dbProperties);
        } else {
            CreateDatabaseImpl.configureDefaults(dbMap, dbProperties); // Prod
        }

        poolOptions =
          new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

        connectOptions = new JDBCConnectOptions()
          .setJdbcUrl(dbMap.get("url") + dbMap.get("host") + dbMap.get("dbname") + "?charSet=UTF-8")
          .setUser(dbProperties.getProperty("user"))
          .setPassword(dbProperties.getProperty("password"))
          .setIdleTimeout(1)
        ;

        if (DodexUtil.getVertxR() == null) {
            DodexUtil.setVertxR(Vertx.vertx());
        }

        AgroalDataSourceConfigurationSupplier configuration = getConfiguration();

        try (AgroalDataSource dataSource = AgroalDataSource.from(configuration)) {
            Connection connection = dataSource.getConnection();
            String[] types = {"TABLE"};

            Multi.createFrom().items(tables)
              .onItem().transform(table -> {
                  boolean isNext;
                  try {
                      isNext = connection.getMetaData().getTables(null, null, table, types).next();
                      if (!isNext) {
                          connection.createStatement().execute(getCreateTable(table));
                      }
                  } catch (SQLException se) {
                      throw new RuntimeException(Arrays.toString(se.getStackTrace()));
                  }

                  return new String[]{String.valueOf(isNext), table};
              }).onFailure().invoke(Throwable::printStackTrace)
              .subscribe().with(data -> {
                  if ("false".equals(data[0])) {
                      if ("users".equals(data[1])) {
                          logger.info("Using database: {}", dbMap.get("filename"));
                      }
                      logger.info("{} Table Added in db {}.",
                        data[1].substring(0, 1).toUpperCase(Locale.US) + data[1].substring(1), dbMap.get("dbname").split(":")[0]);
                  }
              });
            connection.close();
        } catch (SQLException e) {
            logger.info("Agroal Connection Error: {}", e.getMessage());
        }

        Promise<Pool> promise = Promise.promise();

        promise.complete(pool);
        return promise;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConnectOptions() {
        return (T) connectOptions;
    }

    @Override
    public PoolOptions getPoolOptions() {
        return poolOptions;
    }

    @SuppressWarnings("unchecked")
    public <T> T getPool() {
        return (T) pool;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getPool4() {
        PoolOptions poolOptions = new PoolOptions().setMaxSize(Runtime.getRuntime().availableProcessors() * 5);

        JDBCConnectOptions connectOptions;
        connectOptions = new JDBCConnectOptions()
          .setJdbcUrl(dbMap.get("url") + dbMap.get("host") + dbMap.get("dbname") + "?charSet=UTF-8")
          .setUser(dbProperties.getProperty("user"))
          .setPassword(dbProperties.getProperty("password"))
          .setIdleTimeout(1)
        ;

        Vertx vertx = DodexUtil.getVertxR();

        return (T) JDBCPool.pool(vertx, connectOptions, poolOptions);
    }

    @Override
    public Uni<String> checkOnTables() throws InterruptedException, SQLException {
        return null;
    }

    @Override
    public void setVertx(io.vertx.mutiny.core.Vertx vertx) {
        //
    }

    @Override
    public io.vertx.mutiny.core.Vertx getVertx() {
        return null;
    }

    @Override
    public void setVertxR(Vertx vertx) {
        //
    }

    @Override
    public Vertx getVertxR() {
        return null;
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
              .jdbcUrl(dbMap.get("url") + dbMap.get("host") + dbMap.get("dbname") + "?charSet=UTF-8")
              .connectionProviderClassName("cubrid.jdbc.driver.CUBRIDDriver")
              .autoCommit(true)
              .jdbcTransactionIsolation(SERIALIZABLE)
              .principal(new NamePrincipal(dbProperties.getProperty("user")))
//              .credential(new SimplePassword(dbProperties.getProperty("password")))
            )
          );
    }
}
