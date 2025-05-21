package com.l3agent.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MCPToolRegistryTest {
    
    private MCPToolRegistry registry;
    
    @Mock
    private MCPToolInterface mockTool1;
    
    @Mock
    private MCPToolInterface mockTool2;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        registry = new MCPToolRegistry();
        
        when(mockTool1.getName()).thenReturn("tool1");
        when(mockTool2.getName()).thenReturn("tool2");
    }
    
    @Test
    void registerTool_ValidTool_ReturnsTrue() {
        boolean result = registry.registerTool(mockTool1);
        
        assertTrue(result);
        assertEquals(1, registry.getToolCount());
    }
    
    @Test
    void registerTool_NullTool_ReturnsFalse() {
        boolean result = registry.registerTool(null);
        
        assertFalse(result);
        assertEquals(0, registry.getToolCount());
    }
    
    @Test
    void registerTool_EmptyName_ReturnsFalse() {
        when(mockTool1.getName()).thenReturn("");
        
        boolean result = registry.registerTool(mockTool1);
        
        assertFalse(result);
        assertEquals(0, registry.getToolCount());
    }
    
    @Test
    void registerTool_DuplicateName_ReturnsFalse() {
        registry.registerTool(mockTool1);
        
        // Create another mock with the same name
        MCPToolInterface duplicateTool = mock(MCPToolInterface.class);
        when(duplicateTool.getName()).thenReturn("tool1");
        
        boolean result = registry.registerTool(duplicateTool);
        
        assertFalse(result);
        assertEquals(1, registry.getToolCount());
    }
    
    @Test
    void unregisterTool_ExistingTool_ReturnsTrue() {
        registry.registerTool(mockTool1);
        
        boolean result = registry.unregisterTool("tool1");
        
        assertTrue(result);
        assertEquals(0, registry.getToolCount());
    }
    
    @Test
    void unregisterTool_NonExistingTool_ReturnsFalse() {
        boolean result = registry.unregisterTool("nonexistent");
        
        assertFalse(result);
        assertEquals(0, registry.getToolCount());
    }
    
    @Test
    void getTool_ExistingTool_ReturnsOptionalWithTool() {
        registry.registerTool(mockTool1);
        
        Optional<MCPToolInterface> result = registry.getTool("tool1");
        
        assertTrue(result.isPresent());
        assertSame(mockTool1, result.get());
    }
    
    @Test
    void getTool_NonExistingTool_ReturnsEmptyOptional() {
        Optional<MCPToolInterface> result = registry.getTool("nonexistent");
        
        assertFalse(result.isPresent());
    }
    
    @Test
    void getAllTools_MultipleTools_ReturnsAllTools() {
        registry.registerTool(mockTool1);
        registry.registerTool(mockTool2);
        
        List<MCPToolInterface> tools = registry.getAllTools();
        
        assertEquals(2, tools.size());
        assertTrue(tools.contains(mockTool1));
        assertTrue(tools.contains(mockTool2));
    }
    
    @Test
    void hasTool_ExistingTool_ReturnsTrue() {
        registry.registerTool(mockTool1);
        
        boolean result = registry.hasTool("tool1");
        
        assertTrue(result);
    }
    
    @Test
    void hasTool_NonExistingTool_ReturnsFalse() {
        boolean result = registry.hasTool("nonexistent");
        
        assertFalse(result);
    }
    
    @Test
    void clearAllTools_WithTools_ClearsRegistry() {
        registry.registerTool(mockTool1);
        registry.registerTool(mockTool2);
        
        registry.clearAllTools();
        
        assertEquals(0, registry.getToolCount());
        assertFalse(registry.hasTool("tool1"));
        assertFalse(registry.hasTool("tool2"));
    }
} 