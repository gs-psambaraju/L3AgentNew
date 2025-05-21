package com.l3agent.repository;

import com.l3agent.model.llm.LLMMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Null implementation of LLMMetadataRepository that does nothing.
 * Used when the database is disabled.
 */
@Repository
@ConditionalOnProperty(name = "l3agent.database.enabled", havingValue = "false")
@SuppressWarnings("deprecation")
public class NullLLMMetadataRepository implements LLMMetadataRepository {
    private static final Logger logger = LoggerFactory.getLogger(NullLLMMetadataRepository.class);
    
    public NullLLMMetadataRepository() {
        logger.info("Using NullLLMMetadataRepository - database operations will be no-ops");
    }
    
    @Override
    public Optional<LLMMetadata> findByRequestId(String requestId) {
        return Optional.empty();
    }
    
    @Override
    public Optional<LLMMetadata> findByResponseId(String responseId) {
        return Optional.empty();
    }
    
    @Override
    public List<LLMMetadata> findByTicketId(String ticketId) {
        return new ArrayList<>();
    }
    
    @Override
    public List<LLMMetadata> findByConversationId(String conversationId) {
        return new ArrayList<>();
    }
    
    @Override
    public List<LLMMetadata> findByMessageId(String messageId) {
        return new ArrayList<>();
    }
    
    @Override
    public List<LLMMetadata> findByModelName(String modelName) {
        return new ArrayList<>();
    }
    
    @Override
    public List<LLMMetadata> findByProvider(String provider) {
        return new ArrayList<>();
    }
    
    @Override
    public List<LLMMetadata> findByTimestampBetween(Instant start, Instant end) {
        return new ArrayList<>();
    }
    
    @Override
    public Double calculateAverageLatencyForModel(String modelName) {
        return 0.0;
    }
    
    @Override
    public Double calculateAverageTokensForModel(String modelName) {
        return 0.0;
    }
    
    @Override
    public List<LLMMetadata> findByFeedbackRating(Integer rating) {
        return new ArrayList<>();
    }
    
    @Override
    public Double calculateAverageFeedbackRatingForModel(String modelName) {
        return 0.0;
    }
    
    @Override
    public List<LLMMetadata> searchPromptText(String text) {
        return new ArrayList<>();
    }
    
    @Override
    public List<LLMMetadata> searchResponseText(String text) {
        return new ArrayList<>();
    }
    
    @Override
    public void flush() {
        // No-op
    }
    
    @Override
    public <S extends LLMMetadata> S saveAndFlush(S entity) {
        return entity;
    }
    
    @Override
    public <S extends LLMMetadata> List<S> saveAllAndFlush(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        entities.forEach(result::add);
        return result;
    }
    
    @Override
    public void deleteAllInBatch(Iterable<LLMMetadata> entities) {
        // No-op
    }
    
    @Override
    public void deleteAllByIdInBatch(Iterable<String> ids) {
        // No-op
    }
    
    @Override
    public void deleteAllInBatch() {
        // No-op
    }
    
    @Override
    public LLMMetadata getOne(String id) {
        return null;
    }
    
    @Override
    public LLMMetadata getById(String id) {
        return null;
    }
    
    @Override
    public LLMMetadata getReferenceById(String id) {
        return null;
    }
    
    @Override
    public <S extends LLMMetadata> List<S> findAll(Example<S> example) {
        return new ArrayList<>();
    }
    
    @Override
    public <S extends LLMMetadata> List<S> findAll(Example<S> example, Sort sort) {
        return new ArrayList<>();
    }
    
    @Override
    public <S extends LLMMetadata> List<S> saveAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        entities.forEach(result::add);
        return result;
    }
    
    @Override
    public List<LLMMetadata> findAll() {
        return new ArrayList<>();
    }
    
    @Override
    public List<LLMMetadata> findAllById(Iterable<String> ids) {
        return new ArrayList<>();
    }
    
    @Override
    public <S extends LLMMetadata> S save(S entity) {
        return entity;
    }
    
    @Override
    public Optional<LLMMetadata> findById(String id) {
        return Optional.empty();
    }
    
    @Override
    public boolean existsById(String id) {
        return false;
    }
    
    @Override
    public long count() {
        return 0;
    }
    
    @Override
    public void deleteById(String id) {
        // No-op
    }
    
    @Override
    public void delete(LLMMetadata entity) {
        // No-op
    }
    
    @Override
    public void deleteAllById(Iterable<? extends String> ids) {
        // No-op
    }
    
    @Override
    public void deleteAll(Iterable<? extends LLMMetadata> entities) {
        // No-op
    }
    
    @Override
    public void deleteAll() {
        // No-op
    }
    
    @Override
    public List<LLMMetadata> findAll(Sort sort) {
        return new ArrayList<>();
    }
    
    @Override
    public Page<LLMMetadata> findAll(Pageable pageable) {
        return Page.empty();
    }
    
    @Override
    public <S extends LLMMetadata> Optional<S> findOne(Example<S> example) {
        return Optional.empty();
    }
    
    @Override
    public <S extends LLMMetadata> Page<S> findAll(Example<S> example, Pageable pageable) {
        return Page.empty();
    }
    
    @Override
    public <S extends LLMMetadata> long count(Example<S> example) {
        return 0;
    }
    
    @Override
    public <S extends LLMMetadata> boolean exists(Example<S> example) {
        return false;
    }
    
    @Override
    public <S extends LLMMetadata, R> R findBy(Example<S> example, Function<FetchableFluentQuery<S>, R> queryFunction) {
        return null;
    }
} 