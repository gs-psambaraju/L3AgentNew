package com.l3agent.mcp.tools.callpath.model;

import java.util.Objects;

/**
 * Represents a method node in the call graph.
 */
public class MethodNode {
    private final String className;
    private final String methodName;
    private final String signature;
    private final boolean isInterface;
    private final boolean isAbstract;
    private String packageName;
    private String sourceFile;
    private int lineNumber;
    
    public MethodNode(String className, String methodName, String signature, boolean isInterface, boolean isAbstract) {
        this.className = className;
        this.methodName = methodName;
        this.signature = signature;
        this.isInterface = isInterface;
        this.isAbstract = isAbstract;
        this.packageName = className.contains(".") ? 
                className.substring(0, className.lastIndexOf('.')) : "";
    }
    
    /**
     * Returns the fully qualified method identifier.
     * 
     * @return A string in the format className.methodName(signature)
     */
    public String getFullyQualifiedName() {
        return className + "." + methodName + signature;
    }
    
    /**
     * Returns a more readable display name for the method.
     * 
     * @return A string in format className.methodName
     */
    public String getDisplayName() {
        String simpleClassName = className.contains(".") ? 
                className.substring(className.lastIndexOf('.') + 1) : className;
        return simpleClassName + "." + methodName;
    }
    
    public String getClassName() {
        return className;
    }
    
    public String getMethodName() {
        return methodName;
    }
    
    public String getSignature() {
        return signature;
    }
    
    public boolean isInterface() {
        return isInterface;
    }
    
    public boolean isAbstract() {
        return isAbstract;
    }
    
    public String getPackageName() {
        return packageName;
    }
    
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }
    
    public String getSourceFile() {
        return sourceFile;
    }
    
    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }
    
    public int getLineNumber() {
        return lineNumber;
    }
    
    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodNode that = (MethodNode) o;
        return Objects.equals(className, that.className) && 
               Objects.equals(methodName, that.methodName) && 
               Objects.equals(signature, that.signature);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(className, methodName, signature);
    }
    
    @Override
    public String toString() {
        return getFullyQualifiedName();
    }
} 