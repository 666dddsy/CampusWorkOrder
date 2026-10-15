package com.example.campusworkorder.controller;

import com.example.campusworkorder.dto.TicketAssignRequest;
import com.example.campusworkorder.dto.TicketCreateRequest;
import com.example.campusworkorder.dto.TicketResolveRequest;
import com.example.campusworkorder.dto.TicketResponse;
import com.example.campusworkorder.entity.Ticket;
import com.example.campusworkorder.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

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

    @GetMapping("/assigned-to-me")
    public ResponseEntity<List<TicketResponse>> selectMyAssignedTickets(@RequestHeader("X-User-Id") Long currentUserId){

        List<Ticket> tickets=ticketService.selectMyAssignedTickets(currentUserId);
        List<TicketResponse> ticketResponse=new ArrayList<>();
        for(Ticket ticket:tickets)
        {
            ticketResponse.add(toTicketResponse(ticket));
        }
        return ResponseEntity.ok(ticketResponse);
    }

    @GetMapping("/mine")
    public ResponseEntity<List<TicketResponse>> selectMyTickets(@RequestHeader("X-User-Id") Long creatorId){
        List<Ticket> tickets=ticketService.selectByCreatorId(creatorId);
        List<TicketResponse> ticketResponse=new ArrayList<>();
        for(Ticket ticket:tickets)
        {
            ticketResponse.add(toTicketResponse(ticket));
        }
        return ResponseEntity.ok(ticketResponse);
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

    @PatchMapping("/{id}/assignment")
    public ResponseEntity<TicketResponse> assignTicket(
            @Valid @RequestBody TicketAssignRequest ticketAssignRequest,
            @RequestHeader("X-User-Id") Long operatorId,
            @PathVariable Long id
            ){
        Ticket ticket=ticketService.assignTicket(id,operatorId,ticketAssignRequest);
        if(ticket==null)
            return ResponseEntity.notFound()
                    .build();
        TicketResponse ticketResponse=toTicketResponse(ticket);
        return ResponseEntity.ok(ticketResponse);
    }

    @PatchMapping("/{id}/processing")
    public ResponseEntity<TicketResponse> processTicket(
            @RequestHeader("X-User-Id") Long assigneeId,
            @PathVariable Long id
    ){
        Ticket ticket=ticketService.startProcessing(id,assigneeId);
        if(ticket==null)
            return  ResponseEntity.notFound()
                    .build();
        TicketResponse ticketResponse=toTicketResponse(ticket);
        return  ResponseEntity.ok(ticketResponse);
    }

    @PatchMapping("/{id}/resolution")
    public ResponseEntity<TicketResponse> resolveTicket(
            @Valid @RequestBody TicketResolveRequest ticketResolveRequest,
            @RequestHeader("X-User-Id") Long assigneeId,
            @PathVariable Long id
    ){
        Ticket ticket=ticketService.resolveTicket(id,assigneeId,ticketResolveRequest);
        if(ticket==null)
            return ResponseEntity.notFound()
                    .build();
        TicketResponse ticketResponse=toTicketResponse(ticket);
        return  ResponseEntity.ok(ticketResponse);
    }

    @PatchMapping("/{id}/closure")
    public ResponseEntity<TicketResponse> closeTicket(
            @RequestHeader("X-User-Id") Long creatorId,
            @PathVariable Long id
    ){
        Ticket ticket=ticketService.closeTicket(id,creatorId);
        if(ticket==null)
            return ResponseEntity.notFound()
                    .build();
        TicketResponse ticketResponse=toTicketResponse(ticket);
        return  ResponseEntity.ok(ticketResponse);
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
