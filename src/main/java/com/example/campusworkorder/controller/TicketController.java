package com.example.campusworkorder.controller;

import com.example.campusworkorder.dto.TicketCreateRequest;
import com.example.campusworkorder.dto.TicketResponse;
import com.example.campusworkorder.entity.Ticket;
import com.example.campusworkorder.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tickets")
public class TicketController {
    private final TicketService ticketService;

    public TicketController(TicketService ticketService){
        this.ticketService=ticketService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> selectById(@PathVariable Long id){
        Ticket ticket=ticketService.selectById(id);
        if (ticket==null)
            return ResponseEntity.notFound()
                    .build();
        else
        {
            TicketResponse ticketResponse = toTicketResponse(ticket);
            return ResponseEntity.ok(ticketResponse);
        }
    }
    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(
            @RequestHeader("X-User-Id") Long creatorId,@Valid @RequestBody TicketCreateRequest ticketCreateRequest
    ){
        Long ticketId=ticketService.createTicket(creatorId,ticketCreateRequest);
        Ticket ticket=ticketService.selectById(ticketId);
        TicketResponse ticketResponse=toTicketResponse(ticket);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ticketResponse);
    }

    private TicketResponse toTicketResponse(Ticket ticket){
        TicketResponse ticketResponse=new TicketResponse();
        ticketResponse.setAssigneeId(ticket.getAssigneeId());
        ticketResponse.setCreatedAt(ticket.getCreatedAt());
        ticketResponse.setCategory(ticket.getCategory());
        ticketResponse.setDescription(ticket.getDescription());
        ticketResponse.setId(ticket.getId());
        ticketResponse.setLocation(ticket.getLocation());
        ticketResponse.setPriority(ticket.getPriority());
        ticketResponse.setStatus(ticket.getStatus());
        ticketResponse.setCreatorId(ticket.getCreatorId());
        ticketResponse.setTitle(ticket.getTitle());
        return ticketResponse;
    }
}
