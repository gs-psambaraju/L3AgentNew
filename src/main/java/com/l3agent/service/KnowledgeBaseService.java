package com.l3agent.service;

import com.l3agent.model.KnowledgeArticle;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for accessing and searching the knowledge base.
 */
public interface KnowledgeBaseService {
    
    /**
     * Searches for knowledge articles matching the given query.
     * 
     * @param query The search query
     * @param maxResults The maximum number of results to return
     * @return A list of matching knowledge articles
     */
    List<KnowledgeArticle> searchArticles(String query, int maxResults);
    
    /**
     * Retrieves a knowledge article by its ID.
     * 
     * @param articleId The ID of the article to retrieve
     * @return An Optional containing the article if found, empty otherwise
     */
    Optional<KnowledgeArticle> getArticle(String articleId);
    
    /**
     * Searches for knowledge articles with the given tags.
     * 
     * @param tags The tags to search for
     * @param maxResults The maximum number of results to return
     * @return A list of matching knowledge articles
     */
    List<KnowledgeArticle> getArticlesByTags(List<String> tags, int maxResults);
    
    /**
     * Retrieves the most relevant knowledge articles for a given issue.
     * 
     * @param issueDescription The description of the issue
     * @param maxResults The maximum number of results to return
     * @return A list of relevant knowledge articles
     */
    List<RelevantArticle> findRelevantArticles(String issueDescription, int maxResults);
    
    /**
     * Represents a knowledge article with its relevance score.
     */
    class RelevantArticle {
        private KnowledgeArticle article;
        private double relevanceScore;
        
        // Constructors
        public RelevantArticle() {}
        
        public RelevantArticle(KnowledgeArticle article, double relevanceScore) {
            this.article = article;
            this.relevanceScore = relevanceScore;
        }
        
        // Getters and Setters
        public KnowledgeArticle getArticle() {
            return article;
        }
        
        public void setArticle(KnowledgeArticle article) {
            this.article = article;
        }
        
        public double getRelevanceScore() {
            return relevanceScore;
        }
        
        public void setRelevanceScore(double relevanceScore) {
            this.relevanceScore = relevanceScore;
        }
    }
} 