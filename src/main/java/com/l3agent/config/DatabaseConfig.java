package com.l3agent.config;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;

import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.lang.reflect.Method;

/**
 * Database configuration with proper shutdown hooks.
 */
@Configuration
@ConditionalOnProperty(name = "l3agent.database.enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseConfig {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
    
    @Autowired(required = false)
    private DataSource dataSource;
    
    /**
     * Clean shutdown of the database when the application context is closed.
     */
    @PreDestroy
    public void shutdown() {
        if (dataSource == null) {
            logger.debug("No datasource to shut down");
            return;
        }
        
        if (dataSource instanceof EmbeddedDatabase) {
            logger.info("Shutting down embedded database");
            ((EmbeddedDatabase) dataSource).shutdown();
            return;
        }
        
        // Handle H2 database special case
        try {
            logger.info("Attempting to properly close H2 database connections");
            // Try to get a connection and issue SHUTDOWN command for H2
            Connection conn = null;
            boolean isH2 = false;
            try {
                conn = dataSource.getConnection();
                String driverName = conn.getMetaData().getDriverName();
                isH2 = driverName != null && driverName.contains("H2");
                
                // Register JDBC driver shutdown hook for H2 specifically
                if (isH2) {
                    logger.info("H2 database detected, registering JDBC driver shutdown hook");
                    
                    // Force H2 to release file locks by actively closing connections
                    try {
                        // Get Connection Factory and close all connections
                        Class<?> jdbcUtils = Class.forName("org.h2.jdbc.JdbcUtils");
                        if (jdbcUtils != null) {
                            // Try to invoke close connections method if available
                            try {
                                Method method = jdbcUtils.getMethod("shutdownEmbeddedDbCluster", String.class);
                                if (method != null) {
                                    method.invoke(null, (Object)null);
                                    logger.info("Successfully invoked H2 connection cleanup");
                                }
                            } catch (NoSuchMethodException e) {
                                logger.debug("H2 shutdownEmbeddedDbCluster method not available");
                            } catch (Exception e) {
                                logger.warn("Error invoking H2 connection cleanup: {}", e.getMessage());
                            }
                        }
                    } catch (ClassNotFoundException e) {
                        logger.debug("JdbcUtils class not available for H2 cleanup");
                    }
                }
            } catch (SQLException e) {
                logger.warn("Error getting connection for database shutdown: {}", e.getMessage());
            } finally {
                // Close the connection we opened
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        logger.warn("Error closing connection: {}", e.getMessage());
                    }
                }
            }
            
            // If it's a connection pool, try to close it properly
            if (dataSource.getClass().getName().contains("hikari")) {
                closeHikariDataSource(dataSource);
            } else {
                // Generic attempt to find and invoke a close method
                closeGenericDataSource(dataSource);
            }
        } catch (Exception e) {
            logger.warn("Error during database shutdown: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Attempt to close a HikariCP dataSource via reflection
     */
    private void closeHikariDataSource(DataSource dataSource) {
        try {
            logger.info("Closing HikariCP datasource");
            Method closeMethod = dataSource.getClass().getMethod("close");
            closeMethod.invoke(dataSource);
            logger.info("Successfully closed HikariCP datasource");
        } catch (Exception e) {
            logger.warn("Could not close HikariCP datasource: {}", e.getMessage());
        }
    }
    
    /**
     * Generic attempt to close a dataSource by looking for close/shutdown methods
     */
    private void closeGenericDataSource(DataSource dataSource) {
        String[] methodNames = {"close", "shutdown", "destroy", "stop"};
        
        for (String methodName : methodNames) {
            try {
                Method method = dataSource.getClass().getMethod(methodName);
                method.invoke(dataSource);
                logger.info("Successfully closed datasource using {} method", methodName);
                return;
            } catch (NoSuchMethodException e) {
                // Method doesn't exist, try the next one
            } catch (Exception e) {
                logger.warn("Error calling {} method: {}", methodName, e.getMessage());
            }
        }
        
        logger.warn("Could not find a way to properly close the datasource");
    }
} 