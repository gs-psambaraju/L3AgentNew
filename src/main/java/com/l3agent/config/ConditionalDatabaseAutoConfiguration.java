package com.l3agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.JpaBaseConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Configuration filter that conditionally disables database auto-configurations
 * based on the l3agent.database.enabled property.
 */
@Component
public class ConditionalDatabaseAutoConfiguration implements AutoConfigurationImportFilter {
    private static final Logger logger = LoggerFactory.getLogger(ConditionalDatabaseAutoConfiguration.class);
    
    private final Environment environment;
    private final Set<String> excludedAutoConfigurations = new HashSet<>();
    
    public ConditionalDatabaseAutoConfiguration(Environment environment) {
        this.environment = environment;
        
        // Auto-configurations to exclude when database is disabled
        excludedAutoConfigurations.add(DataSourceAutoConfiguration.class.getName());
        excludedAutoConfigurations.add(HibernateJpaAutoConfiguration.class.getName());
        excludedAutoConfigurations.add(JpaRepositoriesAutoConfiguration.class.getName());
        excludedAutoConfigurations.add(DataSourceTransactionManagerAutoConfiguration.class.getName());
        excludedAutoConfigurations.add(TransactionAutoConfiguration.class.getName());
        excludedAutoConfigurations.add("org.springframework.boot.autoconfigure.orm.jpa.JpaBaseConfiguration$JpaWebConfiguration");
        excludedAutoConfigurations.add("org.springframework.boot.autoconfigure.orm.jpa.JpaBaseConfiguration$EntityManagerFactoryConfiguration");
    }
    
    @Override
    public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
        boolean databaseEnabled = environment.getProperty("l3agent.database.enabled", Boolean.class, true);
        
        if (!databaseEnabled) {
            logger.info("Database is disabled, skipping database-related auto-configurations");
        }
        
        boolean[] result = new boolean[autoConfigurationClasses.length];
        
        for (int i = 0; i < autoConfigurationClasses.length; i++) {
            // Allow auto-configuration if database is enabled, or if it's not a database-related auto-configuration
            result[i] = databaseEnabled || !excludedAutoConfigurations.contains(autoConfigurationClasses[i]);
            
            // Log which auto-configurations are being excluded 
            if (!databaseEnabled && !result[i]) {
                logger.debug("Excluding auto-configuration: {}", autoConfigurationClasses[i]);
            }
        }
        
        return result;
    }
} 