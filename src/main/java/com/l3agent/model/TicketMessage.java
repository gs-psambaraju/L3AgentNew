package com.l3agent.model;

import java.time.Instant;

/**
 * Represents a message in a ticket conversation.
 * This can be from a support engineer or the L3Agent.
 */
public class TicketMessage {
    private String messageId;
    private MessageSource source;
    private String content;
    private Instant timestamp;
    private MessageType type;
    
    /**
     * Enumeration of possible message sources.
     */
    public enum MessageSource {
        SUPPORT_ENGINEER, 
        L3AGENT
    }
    
    /**
     * Enumeration of possible message types.
     */
    public enum MessageType {
        QUESTION,
        ANSWER,
        INFORMATION,
        SOLUTION,
        FEEDBACK
    }
    
    // Getters and Setters
    public String getMessageId() {
        return messageId;
    }
    
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
    
    public MessageSource getSource() {
        return source;
    }
    
    public void setSource(MessageSource source) {
        this.source = source;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    public MessageType getType() {
        return type;
    }
    
    public void setType(MessageType type) {
        this.type = type;
    }
} 