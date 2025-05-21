package com.l3agent.mcp.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a response from the Model Control Plane.
 */
public class MCPResponse {
    private String responseId;
    private String answer;
    private List<ToolResponse> toolResults;
    private Map<String, Object> metadata;

    public MCPResponse() {
        this.responseId = UUID.randomUUID().toString();
        this.toolResults = new ArrayList<>();
        this.metadata = new HashMap<>();
    }

    public MCPResponse(String answer) {
        this.responseId = UUID.randomUUID().toString();
        this.answer = answer;
        this.toolResults = new ArrayList<>();
        this.metadata = new HashMap<>();
    }

    public MCPResponse(String answer, List<ToolResponse> toolResults) {
        this.responseId = UUID.randomUUID().toString();
        this.answer = answer;
        this.toolResults = toolResults != null ? toolResults : new ArrayList<>();
        this.metadata = new HashMap<>();
    }

    public MCPResponse(String responseId, String answer, List<ToolResponse> toolResults, Map<String, Object> metadata) {
        this.responseId = responseId != null ? responseId : UUID.randomUUID().toString();
        this.answer = answer;
        this.toolResults = toolResults != null ? toolResults : new ArrayList<>();
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    public String getResponseId() {
        return responseId;
    }

    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<ToolResponse> getToolResults() {
        return toolResults;
    }

    public void setToolResults(List<ToolResponse> toolResults) {
        this.toolResults = toolResults;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public void addToolResult(ToolResponse result) {
        if (this.toolResults == null) {
            this.toolResults = new ArrayList<>();
        }
        this.toolResults.add(result);
    }

    public void addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MCPResponse that = (MCPResponse) o;
        return Objects.equals(responseId, that.responseId) && 
               Objects.equals(answer, that.answer) && 
               Objects.equals(toolResults, that.toolResults) && 
               Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(responseId, answer, toolResults, metadata);
    }

    @Override
    public String toString() {
        return "MCPResponse{" +
                "responseId='" + responseId + '\'' +
                ", answer='" + answer + '\'' +
                ", toolResults=" + toolResults +
                ", metadata=" + metadata +
                '}';
    }
} 