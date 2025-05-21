package com.l3agent.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a support ticket in the L3Agent system.
 * This model aligns with the ticket input format specified in the data flow documentation.
 */
public class Ticket {
    private String ticketId;
    private String subject;
    private String description;
    private Priority priority;
    private Instant createdAt;
    private List<String> attachments = new ArrayList<>();
    private TicketStatus status = TicketStatus.NEW;
    private List<TicketMessage> conversation = new ArrayList<>();

    /**
     * Enumeration of possible ticket priorities.
     */
    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    /**
     * Enumeration of possible ticket statuses.
     */
    public enum TicketStatus {
        NEW, IN_PROGRESS, WAITING_FOR_INFO, RESOLVED, CLOSED
    }

    // Getters and Setters
    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<String> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<String> attachments) {
        this.attachments = attachments;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public List<TicketMessage> getConversation() {
        return conversation;
    }

    public void setConversation(List<TicketMessage> conversation) {
        this.conversation = conversation;
    }

    /**
     * Adds a message to the ticket conversation.
     * 
     * @param message The message to add
     */
    public void addMessage(TicketMessage message) {
        if (this.conversation == null) {
            this.conversation = new ArrayList<>();
        }
        this.conversation.add(message);
    }
} 