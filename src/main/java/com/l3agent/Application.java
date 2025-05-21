package com.l3agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Enumeration;

/**
 * Main application entry point for the L3 Agent application.
 * This Spring Boot application provides agent tool calling and MCP server capabilities.
 */
@SpringBootApplication
public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(Application.class, args);
        
        // Register a JVM shutdown hook to ensure database connections are closed
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                logger.info("Application shutdown hook triggered - cleaning up database connections");
                
                // Deregister JDBC drivers to prevent memory leaks
                Enumeration<Driver> drivers = DriverManager.getDrivers();
                while (drivers.hasMoreElements()) {
                    Driver driver = drivers.nextElement();
                    try {
                        logger.info("Deregistering JDBC driver: {}", driver);
                        DriverManager.deregisterDriver(driver);
                    } catch (Exception e) {
                        logger.warn("Error deregistering JDBC driver {}: {}", driver, e.getMessage());
                    }
                }
                
                // For H2 database, try to close the connection cleanly
                try {
                    Class<?> h2DatabaseClass = Class.forName("org.h2.engine.Database");
                    if (h2DatabaseClass != null) {
                        logger.info("Attempting to close H2 database connections");
                        Class.forName("org.h2.Driver");
                        // The H2 driver's static shutdown hook should handle this,
                        // but we can help ensure it's triggered
                        System.gc();
                    }
                } catch (ClassNotFoundException e) {
                    // H2 not in classpath, ignore
                }
                
                logger.info("Database cleanup complete");
            } catch (Exception e) {
                logger.error("Error during shutdown hook execution", e);
            }
        }));
    }
}