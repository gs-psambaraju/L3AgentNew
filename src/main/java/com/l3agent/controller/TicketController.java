package com.l3agent.controller;

import com.l3agent.model.Ticket;
import com.l3agent.model.TicketMessage;
import com.l3agent.service.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for ticket management.
 * Provides endpoints for creating, retrieving, and updating tickets.
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {
    private static final Logger logger = LoggerFactory.getLogger(TicketController.class);
    
    @Autowired
    private TicketService ticketService;
    
    /**
     * Creates a new ticket.
     * 
     * @param ticket The ticket to create
     * @return The created ticket
     */
    @PostMapping
    public ResponseEntity<Ticket> createTicket(@RequestBody Ticket ticket) {
        logger.info("Creating new ticket: {}", ticket.getSubject());
        Ticket createdTicket = ticketService.createTicket(ticket);
        return new ResponseEntity<>(createdTicket, HttpStatus.CREATED);
    }
    
    /**
     * Retrieves a ticket by its ID.
     * 
     * @param ticketId The ID of the ticket to retrieve
     * @return The ticket with the specified ID
     */
    @GetMapping("/{ticketId}")
    public ResponseEntity<Ticket> getTicket(@PathVariable String ticketId) {
        logger.info("Retrieving ticket: {}", ticketId);
        return ticketService.getTicket(ticketId)
                .map(ticket -> new ResponseEntity<>(ticket, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
    
    /**
     * Retrieves all tickets.
     * 
     * @return A list of all tickets
     */
    @GetMapping
    public ResponseEntity<List<Ticket>> getAllTickets() {
        logger.info("Retrieving all tickets");
        List<Ticket> tickets = ticketService.getAllTickets();
        return new ResponseEntity<>(tickets, HttpStatus.OK);
    }
    
    /**
     * Adds a message to a ticket.
     * 
     * @param ticketId The ID of the ticket to add the message to
     * @param message The message to add
     * @return The updated ticket
     */
    @PostMapping("/{ticketId}/messages")
    public ResponseEntity<Ticket> addMessageToTicket(
            @PathVariable String ticketId,
            @RequestBody TicketMessage message) {
        logger.info("Adding message to ticket: {}", ticketId);
        return ticketService.addMessageToTicket(ticketId, message)
                .map(ticket -> new ResponseEntity<>(ticket, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
    
    /**
     * Updates a ticket's status.
     * 
     * @param ticketId The ID of the ticket to update
     * @param status The new status
     * @return The updated ticket
     */
    @PatchMapping("/{ticketId}/status")
    public ResponseEntity<Ticket> updateTicketStatus(
            @PathVariable String ticketId,
            @RequestBody Ticket.TicketStatus status) {
        logger.info("Updating ticket status: {} to {}", ticketId, status);
        return ticketService.updateTicketStatus(ticketId, status)
                .map(ticket -> new ResponseEntity<>(ticket, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
} 