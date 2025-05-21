package com.l3agent.mcp.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents the response from a tool execution.
 */
public class ToolResponse {
    private boolean success;
    private String message;
    private Object data;
    private List<String> warnings;
    private List<String> errors;

    public ToolResponse() {
        this.warnings = new ArrayList<>();
        this.errors = new ArrayList<>();
    }

    public ToolResponse(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.warnings = new ArrayList<>();
        this.errors = new ArrayList<>();
    }

    public ToolResponse(boolean success, String message, Object data, List<String> warnings, List<String> errors) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.warnings = warnings != null ? warnings : new ArrayList<>();
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public void addWarning(String warning) {
        if (this.warnings == null) {
            this.warnings = new ArrayList<>();
        }
        this.warnings.add(warning);
    }

    public void addError(String error) {
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }
        this.errors.add(error);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ToolResponse that = (ToolResponse) o;
        return success == that.success && 
               Objects.equals(message, that.message) && 
               Objects.equals(data, that.data) && 
               Objects.equals(warnings, that.warnings) && 
               Objects.equals(errors, that.errors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, message, data, warnings, errors);
    }

    @Override
    public String toString() {
        return "ToolResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", data=" + data +
                ", warnings=" + warnings +
                ", errors=" + errors +
                '}';
    }
} 