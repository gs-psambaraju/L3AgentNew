package com.l3agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.l3agent.model.Ticket;
import com.l3agent.model.TicketMessage;
import com.l3agent.service.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A file-based implementation of the TicketService interface.
 * Stores tickets as JSON files in a specified directory.
 */
@Service
public class FileBasedTicketService implements TicketService {
    private static final Logger logger = LoggerFactory.getLogger(FileBasedTicketService.class);
    
    private final ObjectMapper objectMapper;
    private final Path ticketsDirectory;
    private final Map<String, Ticket> ticketCache = new ConcurrentHashMap<>();
    
    /**
     * Constructs a new FileBasedTicketService.
     * 
     * @param ticketsDirectory The directory to store ticket files in
     */
    public FileBasedTicketService(@Value("${l3agent.tickets.directory:./data/tickets}") String ticketsDirectory) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.ticketsDirectory = Paths.get(ticketsDirectory);
        
        try {
            // Create the tickets directory if it doesn't exist
            if (!Files.exists(this.ticketsDirectory)) {
                Files.createDirectories(this.ticketsDirectory);
                logger.info("Created tickets directory: {}", this.ticketsDirectory);
            }
            
            // Load existing tickets into the cache
            loadTickets();
        } catch (IOException e) {
            logger.error("Error initializing ticket service", e);
            throw new RuntimeException("Error initializing ticket service", e);
        }
    }
    
    @Override
    public Ticket createTicket(Ticket ticket) {
        // Set default values if not provided
        if (ticket.getTicketId() == null) {
            ticket.setTicketId(UUID.randomUUID().toString());
        }
        if (ticket.getCreatedAt() == null) {
            ticket.setCreatedAt(Instant.now());
        }
        
        // Save the ticket to a file
        saveTicket(ticket);
        
        // Add the ticket to the cache
        ticketCache.put(ticket.getTicketId(), ticket);
        
        return ticket;
    }
    
    @Override
    public Optional<Ticket> getTicket(String ticketId) {
        return Optional.ofNullable(ticketCache.get(ticketId));
    }
    
    @Override
    public List<Ticket> getAllTickets() {
        return new ArrayList<>(ticketCache.values());
    }
    
    @Override
    public Optional<Ticket> addMessageToTicket(String ticketId, TicketMessage message) {
        return getTicket(ticketId).map(ticket -> {
            // Set default values if not provided
            if (message.getMessageId() == null) {
                message.setMessageId(UUID.randomUUID().toString());
            }
            if (message.getTimestamp() == null) {
                message.setTimestamp(Instant.now());
            }
            
            // Add the message to the ticket
            ticket.addMessage(message);
            
            // Save the updated ticket
            saveTicket(ticket);
            
            return ticket;
        });
    }
    
    @Override
    public Optional<Ticket> updateTicketStatus(String ticketId, Ticket.TicketStatus status) {
        return getTicket(ticketId).map(ticket -> {
            // Update the ticket status
            ticket.setStatus(status);
            
            // Save the updated ticket
            saveTicket(ticket);
            
            return ticket;
        });
    }
    
    /**
     * Saves a ticket to a file.
     * 
     * @param ticket The ticket to save
     */
    private void saveTicket(Ticket ticket) {
        Path ticketFile = ticketsDirectory.resolve(ticket.getTicketId() + ".json");
        try {
            objectMapper.writeValue(ticketFile.toFile(), ticket);
            logger.debug("Saved ticket {} to {}", ticket.getTicketId(), ticketFile);
        } catch (IOException e) {
            logger.error("Error saving ticket {}", ticket.getTicketId(), e);
            throw new RuntimeException("Error saving ticket", e);
        }
    }
    
    /**
     * Loads all tickets from the tickets directory into the cache.
     */
    private void loadTickets() throws IOException {
        try {
            Files.list(ticketsDirectory)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            Ticket ticket = objectMapper.readValue(path.toFile(), Ticket.class);
                            ticketCache.put(ticket.getTicketId(), ticket);
                            logger.debug("Loaded ticket {} from {}", ticket.getTicketId(), path);
                        } catch (IOException e) {
                            logger.error("Error loading ticket from {}", path, e);
                        }
                    });
        } catch (IOException e) {
            logger.error("Error listing ticket files", e);
            throw e;
        }
    }
} 