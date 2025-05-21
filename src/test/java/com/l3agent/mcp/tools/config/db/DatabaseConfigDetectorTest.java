package com.l3agent.mcp.tools.config.db;

import com.l3agent.mcp.tools.config.model.DatabaseConfigReference;
import com.l3agent.mcp.tools.config.model.PropertyReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseConfigDetectorTest {

    private DatabaseConfigDetector detector;

    @BeforeEach
    void setUp() {
        detector = new DatabaseConfigDetector();
        // Set property paths for testing
        ReflectionTestUtils.setField(detector, "propertyPaths", "src/test/resources");
    }

    @Test
    void testDetectDatabaseConfig() {
        // Create a list of test property references
        List<PropertyReference> references = new ArrayList<>();
        
        // Add a database property
        PropertyReference dbRef = new PropertyReference("com.example.DatabaseService", "Service")
                .setPropertyName("spring.datasource.url")
                .setValue("jdbc:mysql://localhost:3306/testdb");
        references.add(dbRef);
        
        // Add a non-database property
        PropertyReference nonDbRef = new PropertyReference("com.example.WebService", "Service")
                .setPropertyName("server.port")
                .setValue("8080");
        references.add(nonDbRef);
        
        // Process the references
        List<PropertyReference> result = detector.detectDatabaseConfig(references);
        
        // Verify results
        assertEquals(2, result.size());
        
        // First reference should be converted to DatabaseConfigReference
        assertTrue(result.get(0) instanceof DatabaseConfigReference);
        DatabaseConfigReference enhancedRef = (DatabaseConfigReference) result.get(0);
        assertEquals("connection", enhancedRef.getPropertyType());
        assertEquals("MySQL", enhancedRef.getDatabaseType());
        
        // Second reference should remain unchanged
        assertSame(nonDbRef, result.get(1));
    }

    @Test
    void testEnhanceDatabaseReference() throws Exception {
        // Use reflection to access the private method
        java.lang.reflect.Method method = DatabaseConfigDetector.class.getDeclaredMethod(
                "enhanceDatabaseReference", PropertyReference.class);
        method.setAccessible(true);
        
        // Test with URL property
        PropertyReference urlRef = new PropertyReference("com.example.DatabaseService", "Service")
                .setPropertyName("spring.datasource.url")
                .setValue("jdbc:postgresql://localhost:5432/testdb");
        
        PropertyReference result = (PropertyReference) method.invoke(detector, urlRef);
        
        assertTrue(result instanceof DatabaseConfigReference);
        DatabaseConfigReference dbResult = (DatabaseConfigReference) result;
        assertEquals("connection", dbResult.getPropertyType());
        assertEquals("PostgreSQL", dbResult.getDatabaseType());
        
        // Test with password property
        PropertyReference passwordRef = new PropertyReference("com.example.DatabaseService", "Service")
                .setPropertyName("spring.datasource.password")
                .setValue("secret");
        
        result = (PropertyReference) method.invoke(detector, passwordRef);
        
        assertTrue(result instanceof DatabaseConfigReference);
        dbResult = (DatabaseConfigReference) result;
        assertEquals("authentication", dbResult.getPropertyType());
        assertTrue(dbResult.isSensitive());
    }

    @Test
    void testIsDatabaseProperty() throws Exception {
        // Use reflection to access the private method
        java.lang.reflect.Method method = DatabaseConfigDetector.class.getDeclaredMethod(
                "isDatabaseProperty", PropertyReference.class);
        method.setAccessible(true);
        
        // Test with database properties
        PropertyReference dbRef1 = new PropertyReference("test", "test")
                .setPropertyName("spring.datasource.url");
        assertTrue((boolean) method.invoke(detector, dbRef1));
        
        PropertyReference dbRef2 = new PropertyReference("test", "test")
                .setPropertyName("hibernate.dialect");
        assertTrue((boolean) method.invoke(detector, dbRef2));
        
        PropertyReference dbRef3 = new PropertyReference("test", "test")
                .setPropertyName("database.connection");
        assertTrue((boolean) method.invoke(detector, dbRef3));
        
        // Test with non-database properties
        PropertyReference nonDbRef1 = new PropertyReference("test", "test")
                .setPropertyName("server.port");
        assertFalse((boolean) method.invoke(detector, nonDbRef1));
        
        PropertyReference nonDbRef2 = new PropertyReference("test", "test")
                .setPropertyName("logging.level");
        assertFalse((boolean) method.invoke(detector, nonDbRef2));
    }
} 