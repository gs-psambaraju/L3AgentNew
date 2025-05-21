package com.l3agent.service.impl;

import com.l3agent.model.llm.LLMMetadata;
import com.l3agent.model.llm.LLMRequest;
import com.l3agent.model.llm.LLMResponse;
import com.l3agent.repository.LLMMetadataRepository;
import com.l3agent.service.LLMMetadataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Default implementation of the LLMMetadataService.
 */
@Service
public class DefaultLLMMetadataService implements LLMMetadataService {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultLLMMetadataService.class);
    
    @Autowired
    private LLMMetadataRepository metadataRepository;
    
    @Value("${l3agent.database.enabled:true}")
    private boolean databaseEnabled;
    
    @Override
    @Transactional
    public LLMMetadata recordInteraction(LLMRequest request, LLMResponse response) {
        if (!databaseEnabled) {
            logger.debug("Database disabled, skipping metadata recording");
            return LLMMetadata.fromRequestAndResponse(request, response);
        }
        
        logger.info("Recording LLM interaction: requestId={}, responseId={}", 
                request.getRequestId(), response.getResponseId());
        
        try {
            LLMMetadata metadata = LLMMetadata.fromRequestAndResponse(request, response);
            return metadataRepository.save(metadata);
        } catch (DataAccessException e) {
            logger.error("Database error recording LLM interaction", e);
            // Return the metadata but don't throw exception to avoid impacting core functionality
            return LLMMetadata.fromRequestAndResponse(request, response);
        } catch (Exception e) {
            logger.error("Error recording LLM interaction", e);
            throw new RuntimeException("Failed to record LLM interaction", e);
        }
    }
    
    @Override
    @Transactional
    public Optional<LLMMetadata> updateFeedback(String requestId, int rating, String comments) {
        if (!databaseEnabled) {
            logger.debug("Database disabled, skipping feedback update");
            return Optional.empty();
        }
        
        logger.info("Updating feedback for LLM interaction: requestId={}, rating={}", 
                requestId, rating);
        
        try {
            Optional<LLMMetadata> metadataOpt = metadataRepository.findByRequestId(requestId);
            
            if (metadataOpt.isPresent()) {
                LLMMetadata metadata = metadataOpt.get();
                metadata.setFeedbackRating(rating);
                metadata.setFeedbackComments(comments);
                return Optional.of(metadataRepository.save(metadata));
            }
            
            logger.warn("LLM interaction not found for feedback update: requestId={}", requestId);
            return Optional.empty();
        } catch (DataAccessException e) {
            logger.error("Database error updating feedback", e);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error updating feedback for LLM interaction", e);
            throw new RuntimeException("Failed to update feedback for LLM interaction", e);
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<LLMMetadata> getMetadataByRequestId(String requestId) {
        if (!databaseEnabled) {
            logger.debug("Database disabled, skipping metadata retrieval");
            return Optional.empty();
        }
        
        logger.debug("Getting metadata by requestId: {}", requestId);
        try {
            return metadataRepository.findByRequestId(requestId);
        } catch (DataAccessException e) {
            logger.error("Database error retrieving metadata", e);
            return Optional.empty();
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<LLMMetadata> getMetadataByResponseId(String responseId) {
        if (!databaseEnabled) {
            logger.debug("Database disabled, skipping metadata retrieval");
            return Optional.empty();
        }
        
        logger.debug("Getting metadata by responseId: {}", responseId);
        try {
            return metadataRepository.findByResponseId(responseId);
        } catch (DataAccessException e) {
            logger.error("Database error retrieving metadata", e);
            return Optional.empty();
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<LLMMetadata> getMetadataForTicket(String ticketId) {
        if (!databaseEnabled) {
            logger.debug("Database disabled, skipping metadata retrieval");
            return new ArrayList<>();
        }
        
        logger.debug("Getting metadata for ticket: {}", ticketId);
        try {
            return metadataRepository.findByTicketId(ticketId);
        } catch (DataAccessException e) {
            logger.error("Database error retrieving metadata", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<LLMMetadata> getMetadataForConversation(String conversationId) {
        if (!databaseEnabled) {
            logger.debug("Database disabled, skipping metadata retrieval");
            return new ArrayList<>();
        }
        
        logger.debug("Getting metadata for conversation: {}", conversationId);
        try {
            return metadataRepository.findByConversationId(conversationId);
        } catch (DataAccessException e) {
            logger.error("Database error retrieving metadata", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<LLMMetadata> getMetadataForModel(String modelName) {
        if (!databaseEnabled) {
            logger.debug("Database disabled, skipping metadata retrieval");
            return new ArrayList<>();
        }
        
        logger.debug("Getting metadata for model: {}", modelName);
        try {
            return metadataRepository.findByModelName(modelName);
        } catch (DataAccessException e) {
            logger.error("Database error retrieving metadata", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<LLMMetadata> getMetadataInTimeRange(Instant start, Instant end) {
        if (!databaseEnabled) {
            logger.debug("Database disabled, skipping metadata retrieval");
            return new ArrayList<>();
        }
        
        logger.debug("Getting metadata in time range: {} to {}", start, end);
        try {
            return metadataRepository.findByTimestampBetween(start, end);
        } catch (DataAccessException e) {
            logger.error("Database error retrieving metadata", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public double getAverageLatencyForModel(String modelName) {
        if (!databaseEnabled) {
            logger.debug("Database disabled, skipping metric calculation");
            return 0.0;
        }
        
        logger.debug("Calculating average latency for model: {}", modelName);
        try {
            Double result = metadataRepository.calculateAverageLatencyForModel(modelName);
            return result != null ? result : 0.0;
        } catch (DataAccessException e) {
            logger.error("Database error calculating metrics", e);
            return 0.0;
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public double getAverageTokensForModel(String modelName) {
        if (!databaseEnabled) {
            logger.debug("Database disabled, skipping metric calculation");
            return 0.0;
        }
        
        logger.debug("Calculating average tokens for model: {}", modelName);
        try {
            Double result = metadataRepository.calculateAverageTokensForModel(modelName);
            return result != null ? result : 0.0;
        } catch (DataAccessException e) {
            logger.error("Database error calculating metrics", e);
            return 0.0;
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public double getAverageFeedbackRatingForModel(String modelName) {
        if (!databaseEnabled) {
            logger.debug("Database disabled, skipping metric calculation");
            return 0.0;
        }
        
        logger.debug("Calculating average feedback rating for model: {}", modelName);
        try {
            Double result = metadataRepository.calculateAverageFeedbackRatingForModel(modelName);
            return result != null ? result : 0.0;
        } catch (DataAccessException e) {
            logger.error("Database error calculating metrics", e);
            return 0.0;
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<LLMMetadata> searchMetadata(String searchText, boolean searchInPrompt, boolean searchInResponse) {
        if (!databaseEnabled) {
            logger.debug("Database disabled, skipping metadata search");
            return new ArrayList<>();
        }
        
        logger.debug("Searching metadata: text='{}', inPrompt={}, inResponse={}", 
                searchText, searchInPrompt, searchInResponse);
        
        if (!searchInPrompt && !searchInResponse) {
            return new ArrayList<>();
        }
        
        try {
            Stream<LLMMetadata> resultStream = Stream.empty();
            
            if (searchInPrompt) {
                resultStream = Stream.concat(resultStream, 
                        metadataRepository.searchPromptText(searchText).stream());
            }
            
            if (searchInResponse) {
                resultStream = Stream.concat(resultStream, 
                        metadataRepository.searchResponseText(searchText).stream());
            }
            
            return resultStream
                    .distinct()
                    .collect(Collectors.toList());
        } catch (DataAccessException e) {
            logger.error("Database error searching metadata", e);
            return new ArrayList<>();
        }
    }
} 