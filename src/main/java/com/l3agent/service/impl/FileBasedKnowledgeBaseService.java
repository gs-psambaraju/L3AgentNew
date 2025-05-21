package com.l3agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.l3agent.model.KnowledgeArticle;
import com.l3agent.service.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A file-based implementation of the KnowledgeBaseService interface.
 * Manages knowledge base articles stored as files in a specified directory.
 */
@Service
public class FileBasedKnowledgeBaseService implements KnowledgeBaseService {
    private static final Logger logger = LoggerFactory.getLogger(FileBasedKnowledgeBaseService.class);
    
    private final Path knowledgeDirectory;
    private final ObjectMapper objectMapper;
    private final Map<String, KnowledgeArticle> articleCache = new ConcurrentHashMap<>();
    
    /**
     * Constructs a new FileBasedKnowledgeBaseService.
     * 
     * @param knowledgeDirectory The directory containing the knowledge base articles
     */
    public FileBasedKnowledgeBaseService(@Value("${l3agent.knowledge.directory:./data/knowledge}") String knowledgeDirectory) {
        this.knowledgeDirectory = Paths.get(knowledgeDirectory);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        
        try {
            // Create the knowledge directory if it doesn't exist
            if (!Files.exists(this.knowledgeDirectory)) {
                Files.createDirectories(this.knowledgeDirectory);
                logger.info("Created knowledge directory: {}", this.knowledgeDirectory);
            }
            
            // Load existing articles into the cache
            loadArticles();
        } catch (IOException e) {
            logger.error("Error initializing knowledge base service", e);
            throw new RuntimeException("Error initializing knowledge base service", e);
        }
    }
    
    @Override
    public List<KnowledgeArticle> searchArticles(String query, int maxResults) {
        logger.info("Searching knowledge articles for: {}", query);
        
        Pattern pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
        
        return articleCache.values().stream()
                .filter(article -> 
                        (article.getTitle() != null && pattern.matcher(article.getTitle()).find()) ||
                        (article.getContent() != null && pattern.matcher(article.getContent()).find()))
                .limit(maxResults)
                .collect(Collectors.toList());
    }
    
    @Override
    public Optional<KnowledgeArticle> getArticle(String articleId) {
        return Optional.ofNullable(articleCache.get(articleId));
    }
    
    @Override
    public List<KnowledgeArticle> getArticlesByTags(List<String> tags, int maxResults) {
        logger.info("Getting knowledge articles with tags: {}", tags);
        
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }
        
        Set<String> lowercaseTags = tags.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        
        return articleCache.values().stream()
                .filter(article -> {
                    if (article.getTags() == null) {
                        return false;
                    }
                    
                    Set<String> articleLowercaseTags = article.getTags().stream()
                            .map(String::toLowerCase)
                            .collect(Collectors.toSet());
                    
                    return !Collections.disjoint(lowercaseTags, articleLowercaseTags);
                })
                .limit(maxResults)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<RelevantArticle> findRelevantArticles(String issueDescription, int maxResults) {
        logger.info("Finding relevant articles for issue: {}", issueDescription);
        
        // For POC: simple relevance calculation based on term frequency
        // In a real implementation, we would use more sophisticated relevance algorithms
        
        // Extract keywords from the issue description
        Set<String> keywords = extractKeywords(issueDescription);
        
        // Calculate relevance scores for each article
        List<RelevantArticle> relevantArticles = new ArrayList<>();
        
        for (KnowledgeArticle article : articleCache.values()) {
            double score = calculateRelevanceScore(article, keywords);
            
            if (score > 0) {
                relevantArticles.add(new RelevantArticle(article, score));
            }
        }
        
        // Sort by relevance score (descending)
        relevantArticles.sort((a1, a2) -> Double.compare(a2.getRelevanceScore(), a1.getRelevanceScore()));
        
        // Return top results
        return relevantArticles.stream()
                .limit(maxResults)
                .collect(Collectors.toList());
    }
    
    /**
     * Loads knowledge articles from the knowledge directory into the cache.
     */
    private void loadArticles() throws IOException {
        try {
            Files.list(knowledgeDirectory)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            KnowledgeArticle article = objectMapper.readValue(path.toFile(), KnowledgeArticle.class);
                            articleCache.put(article.getArticleId(), article);
                            logger.debug("Loaded article: {} - {}", article.getArticleId(), article.getTitle());
                        } catch (IOException e) {
                            logger.error("Error loading article from {}", path, e);
                        }
                    });
            
            logger.info("Loaded {} knowledge articles", articleCache.size());
        } catch (IOException e) {
            logger.error("Error listing knowledge article files", e);
            throw e;
        }
    }
    
    /**
     * Extracts keywords from text.
     * This is a simplified implementation for the POC.
     * 
     * @param text The text to extract keywords from
     * @return A set of extracted keywords
     */
    private Set<String> extractKeywords(String text) {
        if (text == null) {
            return Collections.emptySet();
        }
        
        // Split on non-alphanumeric characters and convert to lowercase
        return Arrays.stream(text.split("[^a-zA-Z0-9]+"))
                .filter(word -> word.length() > 2) // Only words with at least 3 characters
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }
    
    /**
     * Calculates a relevance score for an article based on keyword matches.
     * This is a simplified implementation for the POC.
     * 
     * @param article The article to calculate the score for
     * @param keywords The keywords to match against
     * @return The calculated relevance score
     */
    private double calculateRelevanceScore(KnowledgeArticle article, Set<String> keywords) {
        if (keywords.isEmpty() || article.getContent() == null) {
            return 0;
        }
        
        // Extract keywords from article title and content
        Set<String> titleKeywords = extractKeywords(article.getTitle());
        Set<String> contentKeywords = extractKeywords(article.getContent());
        
        // Count matches in title (weighted 3x) and content
        long titleMatches = titleKeywords.stream()
                .filter(keywords::contains)
                .count();
        
        long contentMatches = contentKeywords.stream()
                .filter(keywords::contains)
                .count();
        
        // Calculate score
        return (titleMatches * 3.0 + contentMatches) / keywords.size();
    }
} 