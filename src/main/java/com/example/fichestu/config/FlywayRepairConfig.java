package com.example.fichestu.config;

import java.util.Arrays;

import org.flywaydb.core.api.MigrationInfo;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayRepairConfig {

    @Bean
    FlywayMigrationStrategy repairFailedMigrationsBeforeMigrate() {
        return flyway -> {
            boolean hasFailedMigration = Arrays.stream(flyway.info().all())
                .map(MigrationInfo::getState)
                .anyMatch(state -> state != null && state.isFailed());
            if (hasFailedMigration) {
                flyway.repair();
            }
            flyway.migrate();
        };
    }
}
