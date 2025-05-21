package com.l3agent.service.retrieval;

/**
 * Represents a query for content retrieval.
 * Can contain text, embedding vector, or both, along with query context.
 */
public class RetrievalQuery {
    
    private String text;
    private float[] embedding;
    private String contentType;
    private QueryType queryType;
    
    /**
     * Types of queries that can be performed.
     */
    public enum QueryType {
        CONCEPTUAL,    // Looking for concepts, abstractions, capabilities
        IMPLEMENTATION, // Looking for specific implementation details
        MIXED          // Both conceptual and implementation details
    }
    
    /**
     * Creates a query with just text.
     */
    public static RetrievalQuery textOnly(String text, String contentType) {
        RetrievalQuery query = new RetrievalQuery();
        query.text = text;
        query.contentType = contentType;
        query.queryType = detectQueryType(text);
        return query;
    }
    
    /**
     * Creates a query with just an embedding.
     */
    public static RetrievalQuery embeddingOnly(float[] embedding, String contentType) {
        RetrievalQuery query = new RetrievalQuery();
        query.embedding = embedding;
        query.contentType = contentType;
        query.queryType = QueryType.MIXED; // Default for embedding-only queries
        return query;
    }
    
    /**
     * Creates a query with both text and embedding.
     */
    public static RetrievalQuery combined(String text, float[] embedding, String contentType) {
        RetrievalQuery query = new RetrievalQuery();
        query.text = text;
        query.embedding = embedding;
        query.contentType = contentType;
        query.queryType = detectQueryType(text);
        return query;
    }
    
    /**
     * Detects the type of query based on text analysis.
     */
    private static QueryType detectQueryType(String text) {
        if (text == null || text.isEmpty()) {
            return QueryType.MIXED;
        }
        
        String lowerText = text.toLowerCase();
        
        // Check for conceptual query patterns
        boolean isConceptual = lowerText.contains("how to") ||
                lowerText.contains("what is") ||
                lowerText.contains("explain") ||
                lowerText.contains("purpose of") ||
                lowerText.contains("function of") ||
                lowerText.contains("architecture") ||
                lowerText.contains("design") ||
                lowerText.contains("capability") ||
                lowerText.contains("responsibility") ||
                lowerText.contains("concept");
        
        // Check for implementation query patterns
        boolean isImplementation = lowerText.contains("implementation") ||
                lowerText.contains("code for") ||
                lowerText.contains("where is") ||
                lowerText.contains("how is") ||
                lowerText.contains("defined") ||
                lowerText.contains("declared") ||
                lowerText.contains("function") ||
                lowerText.contains("method") ||
                lowerText.contains("class") ||
                lowerText.contains("interface");
        
        // Determine query type based on pattern matching
        if (isConceptual && !isImplementation) {
            return QueryType.CONCEPTUAL;
        } else if (!isConceptual && isImplementation) {
            return QueryType.IMPLEMENTATION;
        } else {
            return QueryType.MIXED;
        }
    }
    
    // Getters and setters
    
    public String getText() {
        return text;
    }
    
    public void setText(String text) {
        this.text = text;
        if (text != null) {
            this.queryType = detectQueryType(text);
        }
    }
    
    public float[] getEmbedding() {
        return embedding;
    }
    
    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }
    
    public String getContentType() {
        return contentType;
    }
    
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
    
    public QueryType getQueryType() {
        return queryType;
    }
    
    public void setQueryType(QueryType queryType) {
        this.queryType = queryType;
    }
    
    public boolean hasText() {
        return text != null && !text.isEmpty();
    }
    
    public boolean hasEmbedding() {
        return embedding != null && embedding.length > 0;
    }
} 