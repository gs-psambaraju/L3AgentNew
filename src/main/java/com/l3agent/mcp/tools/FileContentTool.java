package com.l3agent.mcp.tools;

import com.l3agent.mcp.MCPToolInterface;
import com.l3agent.mcp.model.ToolParameter;
import com.l3agent.mcp.model.ToolResponse;
import com.l3agent.mcp.util.VectorServiceIntegration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tool for retrieving the content of a file from the codebase.
 */
@Component
public class FileContentTool implements MCPToolInterface {
    
    private static final Logger logger = LoggerFactory.getLogger(FileContentTool.class);
    
    private static final String TOOL_NAME = "file_content";
    
    @Autowired
    private VectorServiceIntegration vectorServiceIntegration;
    
    @Override
    public String getName() {
        return TOOL_NAME;
    }
    
    @Override
    public String getDescription() {
        return "Retrieves the content of a file from the codebase by its path";
    }
    
    @Override
    public List<ToolParameter> getParameters() {
        List<ToolParameter> params = new ArrayList<>();
        
        // File path parameter
        ToolParameter filePathParam = new ToolParameter(
            "filePath",
            "The path of the file to retrieve",
            "string",
            true,
            null
        );
        params.add(filePathParam);
        
        return params;
    }
    
    @Override
    public ToolResponse execute(Map<String, Object> parameters) {
        // Validate required parameters
        String filePath = (String) parameters.get("filePath");
        if (filePath == null || filePath.trim().isEmpty()) {
            logger.warn("Missing required parameter: filePath");
            return new ToolResponse(false, "Missing required parameter: filePath", null);
        }
        
        // Execute the file retrieval
        logger.info("Retrieving file content for: {}", filePath);
        return vectorServiceIntegration.getFileContent(filePath);
    }
} 