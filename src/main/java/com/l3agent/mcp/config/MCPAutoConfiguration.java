package com.l3agent.mcp.config;

import com.l3agent.mcp.MCPRequestHandler;
import com.l3agent.mcp.MCPToolInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.util.Map;

/**
 * Auto-configuration for the Model Control Plane.
 * Automatically registers all tools on application startup.
 */
@Configuration
public class MCPAutoConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(MCPAutoConfiguration.class);
    
    @Autowired
    private MCPRequestHandler mcpRequestHandler;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Value("${l3agent.mcp.auto-register-tools:true}")
    private boolean autoRegisterTools;
    
    /**
     * Register all MCPToolInterface beans when the application context is refreshed.
     */
    @EventListener
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!autoRegisterTools) {
            logger.info("Auto-registration of MCP tools is disabled");
            return;
        }
        
        logger.info("Registering MCP tools...");
        
        Map<String, MCPToolInterface> toolBeans = applicationContext.getBeansOfType(MCPToolInterface.class);
        
        if (toolBeans.isEmpty()) {
            logger.warn("No MCP tools found to register");
            return;
        }
        
        for (Map.Entry<String, MCPToolInterface> entry : toolBeans.entrySet()) {
            String beanName = entry.getKey();
            MCPToolInterface tool = entry.getValue();
            
            logger.info("Registering MCP tool: {} ({}) - {}", 
                    tool.getName(), beanName, tool.getDescription());
            
            mcpRequestHandler.registerTool(tool);
        }
        
        logger.info("Registered {} MCP tools", toolBeans.size());
    }
} 