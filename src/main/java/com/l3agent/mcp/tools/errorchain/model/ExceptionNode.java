package com.l3agent.mcp.tools.errorchain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a node in an exception hierarchy.
 * Contains information about an exception class and its relationships to other exceptions.
 */
public class ExceptionNode {
    
    private String className;
    private List<ExceptionNode> parents;
    private List<ExceptionNode> children;
    private boolean isChecked;
    private List<String> commonMessages;
    
    /**
     * Creates a new exception node.
     * 
     * @param className The fully qualified name of the exception class
     */
    public ExceptionNode(String className) {
        this.className = className;
        this.parents = new ArrayList<>();
        this.children = new ArrayList<>();
        this.commonMessages = new ArrayList<>();
        
        // Determine if it's a checked exception based on class name
        // In a real implementation, this would use reflection
        this.isChecked = !className.contains("RuntimeException") && 
                          !className.contains("Error") &&
                          className.contains("Exception");
    }
    
    /**
     * Adds a parent exception to this node.
     * 
     * @param parent The parent exception node
     */
    public void addParent(ExceptionNode parent) {
        if (parent != null && !parents.contains(parent)) {
            parents.add(parent);
            parent.addChild(this);
        }
    }
    
    /**
     * Adds a child exception to this node.
     * 
     * @param child The child exception node
     */
    public void addChild(ExceptionNode child) {
        if (child != null && !children.contains(child)) {
            children.add(child);
        }
    }
    
    /**
     * Adds a common error message associated with this exception.
     * 
     * @param message The error message
     */
    public void addCommonMessage(String message) {
        if (message != null && !commonMessages.contains(message)) {
            commonMessages.add(message);
        }
    }
    
    /**
     * Gets the class name of the exception.
     * 
     * @return The fully qualified class name
     */
    public String getClassName() {
        return className;
    }
    
    /**
     * Gets the simple name of the exception (without package).
     * 
     * @return The simple class name
     */
    public String getSimpleName() {
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            return className.substring(lastDot + 1);
        }
        return className;
    }
    
    /**
     * Gets the parent exceptions of this exception.
     * 
     * @return List of parent exception nodes
     */
    public List<ExceptionNode> getParents() {
        return parents;
    }
    
    /**
     * Gets the child exceptions of this exception.
     * 
     * @return List of child exception nodes
     */
    public List<ExceptionNode> getChildren() {
        return children;
    }
    
    /**
     * Checks if this is a checked exception.
     * 
     * @return true if this is a checked exception, false otherwise
     */
    public boolean isChecked() {
        return isChecked;
    }
    
    /**
     * Gets the common error messages associated with this exception.
     * 
     * @return List of common error messages
     */
    public List<String> getCommonMessages() {
        return commonMessages;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ExceptionNode other = (ExceptionNode) obj;
        return className.equals(other.className);
    }
    
    @Override
    public int hashCode() {
        return className.hashCode();
    }
    
    @Override
    public String toString() {
        return className;
    }
} 