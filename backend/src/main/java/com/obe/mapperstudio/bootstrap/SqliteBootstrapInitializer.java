package com.obe.mapperstudio.bootstrap;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * Runs before datasource/JPA initialization. Resolves SQLite DB file location,
 * optionally copies a classpath seed file when DB is absent, and bootstraps schema/data.
 */
public class SqliteBootstrapInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String ENV_DB_PATH = "mapper.sqlite.path";
    private static final String ENV_DB_CLASSPATH_SEED = "mapper.sqlite.classpath-seed";
    private static final String ENV_BOOTSTRAP_ENABLED = "mapper.sqlite.bootstrap-enabled";
    private static final String SYS_SQLITE_URL = "MAPPER_SQLITE_URL";
    private static final String SQLITE_DRIVER = "org.sqlite.JDBC";

    private static final List<String> SCHEMA_SCRIPTS = List.of(
            "classpath:sql/ce-ddl_sqlite.sql",
            "classpath:sql/obe-ddl_sqlite.sql"
    );

    private static final String BASE_SEED_SCRIPT = "classpath:sql/ce-seed-mapping-studio_sqlite.sql";
    private static final String UPSERT_SEED_SCRIPT = "classpath:sql/ce-seed-mapping-studio-upsert_sqlite.sql";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment env = applicationContext.getEnvironment();
        String dbPathRaw = env.getProperty(ENV_DB_PATH, "./data/mapper-studio.db");
        String seedLocation = env.getProperty(ENV_DB_CLASSPATH_SEED, "").trim();
        boolean bootstrapEnabled = Boolean.parseBoolean(env.getProperty(ENV_BOOTSTRAP_ENABLED, "true"));

        try {
            Path dbPath = Path.of(dbPathRaw).toAbsolutePath().normalize();
            Path parent = dbPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            if (Files.notExists(dbPath)) {
                if (!seedLocation.isBlank()) {
                    Resource resource = new DefaultResourceLoader().getResource(seedLocation);
                    if (resource.exists()) {
                        try (InputStream in = resource.getInputStream()) {
                            Files.copy(in, dbPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } else {
                        Files.createFile(dbPath);
                    }
                } else {
                    Files.createFile(dbPath);
                }
            }

            String jdbcUrl = "jdbc:sqlite:" + dbPath;
            System.setProperty(SYS_SQLITE_URL, jdbcUrl);

            if (bootstrapEnabled) {
                bootstrapSqliteDatabase(jdbcUrl);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to prepare SQLite database before startup", e);
        }
    }

    private void bootstrapSqliteDatabase(String jdbcUrl) {
        try {
            Class.forName(SQLITE_DRIVER);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SQLite JDBC driver not found", e);
        }

        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(SQLITE_DRIVER);
        ds.setUrl(jdbcUrl);

        for (String script : SCHEMA_SCRIPTS) {
            executeScript(ds, script);
        }

        if (isBaseSeedRequired(jdbcUrl)) {
            executeScript(ds, BASE_SEED_SCRIPT);
        }
        executeScript(ds, UPSERT_SEED_SCRIPT);
    }

    private boolean isBaseSeedRequired(String jdbcUrl) {
        String sql = "SELECT COUNT(*) FROM ce_config";
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1) == 0;
            }
            return true;
        } catch (Exception ignored) {
            return true;
        }
    }

    private void executeScript(DriverManagerDataSource ds, String location) {
        Resource resource = new DefaultResourceLoader().getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("SQL bootstrap resource not found: " + location);
        }
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setContinueOnError(false);
        populator.addScript(resource);
        DatabasePopulatorUtils.execute(populator, ds);
    }
}
