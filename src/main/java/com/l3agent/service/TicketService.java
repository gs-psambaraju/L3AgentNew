package com.l3agent.service;

import com.l3agent.model.Ticket;
import com.l3agent.model.TicketMessage;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for ticket management operations.
 */
public interface TicketService {
    
    /**
     * Creates a new ticket.
     * 
     * @param ticket The ticket to create
     * @return The created ticket
     */
    Ticket createTicket(Ticket ticket);
    
    /**
     * Retrieves a ticket by its ID.
     * 
     * @param ticketId The ID of the ticket to retrieve
     * @return An Optional containing the ticket if found, empty otherwise
     */
    Optional<Ticket> getTicket(String ticketId);
    
    /**
     * Retrieves all tickets.
     * 
     * @return A list of all tickets
     */
    List<Ticket> getAllTickets();
    
    /**
     * Adds a message to a ticket.
     * 
     * @param ticketId The ID of the ticket to add the message to
     * @param message The message to add
     * @return An Optional containing the updated ticket if found, empty otherwise
     */
    Optional<Ticket> addMessageToTicket(String ticketId, TicketMessage message);
    
    /**
     * Updates a ticket's status.
     * 
     * @param ticketId The ID of the ticket to update
     * @param status The new status
     * @return An Optional containing the updated ticket if found, empty otherwise
     */
    Optional<Ticket> updateTicketStatus(String ticketId, Ticket.TicketStatus status);
} 