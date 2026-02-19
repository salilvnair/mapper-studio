package com.obe.mapperstudio.service.studio;

import com.obe.mapperstudio.api.dto.DbInitResponse;
import com.obe.mapperstudio.api.dto.DbInitStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DbInitializationService {

    private static final List<String> DEFAULT_SCHEMA_LOCATIONS = List.of(
            "classpath:sql/ce-ddl_sqlite.sql",
            "classpath:sql/obe-ddl_sqlite.sql"
    );

    private static final List<String> DEFAULT_DATA_LOCATIONS = List.of(
            "classpath:sql/ce-seed-mapping-studio_sqlite.sql",
            "classpath:sql/ce-seed-mapping-studio-upsert_sqlite.sql"
    );

    private final DataSource dataSource;
    private final ResourceLoader resourceLoader;
    private final JdbcTemplate jdbcTemplate;

    public DbInitResponse initialize() {
        List<String> executedSchema = new ArrayList<>();
        List<String> executedData = new ArrayList<>();

        executeSqlLocations(DEFAULT_SCHEMA_LOCATIONS, executedSchema);
        executeSqlLocations(DEFAULT_DATA_LOCATIONS, executedData);

        return new DbInitResponse(true, executedSchema, executedData, OffsetDateTime.now().toString());
    }

    public DbInitStatusResponse status() {
        boolean initialized = isInitialized();
        return new DbInitStatusResponse(
                initialized,
                initialized ? "DB entries initialized" : "DB entries not initialized",
                OffsetDateTime.now().toString()
        );
    }

    private void executeSqlLocations(List<String> locations, List<String> executedLocations) {
        for (String location : locations) {
            if (location == null || location.trim().isEmpty()) {
                continue;
            }
            Resource resource = resourceLoader.getResource(location.trim());
            if (!resource.exists()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL resource not found: " + location);
            }
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setContinueOnError(false);
            populator.addScript(resource);
            try {
                DatabasePopulatorUtils.execute(populator, dataSource);
            } catch (Exception e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Failed executing SQL resource: " + location + " | " + e.getMessage(),
                        e
                );
            }
            executedLocations.add(location);
        }
    }

    private boolean isInitialized() {
        try {
            Integer tableExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'ce_intent'",
                    Integer.class
            );
            if (tableExists == null || tableExists == 0) {
                return false;
            }
            Integer intentCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ce_intent WHERE intent_code = 'MAPPING_STUDIO' AND enabled = 1",
                    Integer.class
            );
            return intentCount != null && intentCount > 0;
        } catch (Exception ignored) {
            return false;
        }
    }
}
