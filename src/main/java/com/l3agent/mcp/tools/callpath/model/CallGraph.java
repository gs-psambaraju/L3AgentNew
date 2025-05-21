package com.l3agent.mcp.tools.callpath.model;

import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.DepthFirstIterator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents the call graph structure for method invocations.
 * Uses JGraphT for the underlying graph implementation.
 */
public class CallGraph {
    
    private final DefaultDirectedGraph<MethodNode, DefaultEdge> graph;
    private MethodNode rootNode;
    
    public CallGraph() {
        this.graph = new DefaultDirectedGraph<>(DefaultEdge.class);
    }
    
    /**
     * Sets the root node for the call graph.
     * 
     * @param rootNode The method node that serves as the entry point
     */
    public void setRootNode(MethodNode rootNode) {
        this.rootNode = rootNode;
        if (!graph.containsVertex(rootNode)) {
            graph.addVertex(rootNode);
        }
    }
    
    /**
     * Adds a method call relationship to the graph.
     * 
     * @param caller The method that makes the call
     * @param callee The method being called
     */
    public void addMethodCall(MethodNode caller, MethodNode callee) {
        if (!graph.containsVertex(caller)) {
            graph.addVertex(caller);
        }
        if (!graph.containsVertex(callee)) {
            graph.addVertex(callee);
        }
        
        // Check if the edge already exists to avoid duplicates
        if (!graph.containsEdge(caller, callee)) {
            graph.addEdge(caller, callee);
        }
    }
    
    /**
     * Gets all method nodes in the graph.
     * 
     * @return A set of all method nodes
     */
    public Set<MethodNode> getNodes() {
        return graph.vertexSet();
    }
    
    /**
     * Gets all direct callees for a method.
     * 
     * @param caller The method making calls
     * @return A list of directly called methods
     */
    public List<MethodNode> getCallees(MethodNode caller) {
        List<MethodNode> callees = new ArrayList<>();
        if (graph.containsVertex(caller)) {
            for (DefaultEdge edge : graph.outgoingEdgesOf(caller)) {
                callees.add(graph.getEdgeTarget(edge));
            }
        }
        return callees;
    }
    
    /**
     * Gets all methods that call the specified method.
     * 
     * @param callee The method being called
     * @return A list of methods that call the specified method
     */
    public List<MethodNode> getCallers(MethodNode callee) {
        List<MethodNode> callers = new ArrayList<>();
        if (graph.containsVertex(callee)) {
            for (DefaultEdge edge : graph.incomingEdgesOf(callee)) {
                callers.add(graph.getEdgeSource(edge));
            }
        }
        return callers;
    }
    
    /**
     * Converts the call graph to a JSON Graph Format representation.
     * 
     * @return A map containing the JSON representation of the graph
     */
    public Map<String, Object> toJsonGraph() {
        Map<String, Object> result = new HashMap<>();
        
        // Create nodes array
        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<MethodNode, Integer> nodeIndices = new HashMap<>();
        
        int index = 0;
        for (MethodNode node : graph.vertexSet()) {
            Map<String, Object> nodeMap = new HashMap<>();
            nodeMap.put("id", index);
            nodeMap.put("className", node.getClassName());
            nodeMap.put("methodName", node.getMethodName());
            nodeMap.put("signature", node.getSignature());
            nodeMap.put("displayName", node.getDisplayName());
            nodeMap.put("isInterface", node.isInterface());
            nodeMap.put("isAbstract", node.isAbstract());
            nodeMap.put("package", node.getPackageName());
            
            if (node.getSourceFile() != null) {
                nodeMap.put("sourceFile", node.getSourceFile());
            }
            
            if (node.getLineNumber() > 0) {
                nodeMap.put("lineNumber", node.getLineNumber());
            }
            
            // Mark the root node
            if (node.equals(rootNode)) {
                nodeMap.put("isRoot", true);
            }
            
            nodes.add(nodeMap);
            nodeIndices.put(node, index);
            index++;
        }
        
        // Create edges array
        List<Map<String, Object>> edges = new ArrayList<>();
        
        for (DefaultEdge edge : graph.edgeSet()) {
            MethodNode source = graph.getEdgeSource(edge);
            MethodNode target = graph.getEdgeTarget(edge);
            
            Map<String, Object> edgeMap = new HashMap<>();
            edgeMap.put("source", nodeIndices.get(source));
            edgeMap.put("target", nodeIndices.get(target));
            edges.add(edgeMap);
        }
        
        // Create the final structure
        result.put("graph", new HashMap<String, Object>() {{
            put("directed", true);
            put("type", "callgraph");
            put("label", "Method Call Graph");
        }});
        
        result.put("nodes", nodes);
        result.put("edges", edges);
        
        // Add some metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("nodeCount", nodes.size());
        metadata.put("edgeCount", edges.size());
        if (rootNode != null) {
            metadata.put("rootMethod", rootNode.getFullyQualifiedName());
        }
        result.put("metadata", metadata);
        
        return result;
    }
    
    /**
     * Gets a hierarchical representation of the call graph starting from the root.
     * 
     * @param maxDepth The maximum depth of calls to include
     * @return A hierarchical map representation of the call graph
     */
    public Map<String, Object> toHierarchy(int maxDepth) {
        if (rootNode == null) {
            return new HashMap<>();
        }
        
        return buildHierarchy(rootNode, new HashSet<>(), 0, maxDepth);
    }
    
    private Map<String, Object> buildHierarchy(MethodNode node, Set<MethodNode> visited, int currentDepth, int maxDepth) {
        if (currentDepth > maxDepth || visited.contains(node)) {
            return new HashMap<>();
        }
        
        visited.add(node);
        
        Map<String, Object> nodeMap = new HashMap<>();
        nodeMap.put("name", node.getDisplayName());
        nodeMap.put("fullName", node.getFullyQualifiedName());
        nodeMap.put("className", node.getClassName());
        nodeMap.put("methodName", node.getMethodName());
        nodeMap.put("isInterface", node.isInterface());
        nodeMap.put("isAbstract", node.isAbstract());
        
        List<Map<String, Object>> children = new ArrayList<>();
        for (MethodNode callee : getCallees(node)) {
            if (!visited.contains(callee)) {
                children.add(buildHierarchy(callee, new HashSet<>(visited), currentDepth + 1, maxDepth));
            } else {
                // For cycles, just add a reference node
                Map<String, Object> refNode = new HashMap<>();
                refNode.put("name", callee.getDisplayName());
                refNode.put("reference", true);
                children.add(refNode);
            }
        }
        
        if (!children.isEmpty()) {
            nodeMap.put("children", children);
        }
        
        return nodeMap;
    }
    
    /**
     * Gets the root node of the call graph.
     * 
     * @return The root method node
     */
    public MethodNode getRootNode() {
        return rootNode;
    }
    
    /**
     * Gets the total number of nodes in the graph.
     * 
     * @return The node count
     */
    public int getNodeCount() {
        return graph.vertexSet().size();
    }
    
    /**
     * Gets the total number of edges (method calls) in the graph.
     * 
     * @return The edge count
     */
    public int getEdgeCount() {
        return graph.edgeSet().size();
    }
} 