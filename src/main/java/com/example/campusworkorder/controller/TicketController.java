package com.example.campusworkorder.controller;

import com.example.campusworkorder.dto.*;
import com.example.campusworkorder.entity.Ticket;
import com.example.campusworkorder.security.AppUserPrincipal;
import com.example.campusworkorder.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public ResponseEntity<TicketResponse> selectById(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal principal
            ){
        Ticket ticket=ticketService.selectByIdWithRole(id,principal.getId());
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
    public ResponseEntity<List<TicketResponse>> selectMyAssignedTickets(@AuthenticationPrincipal AppUserPrincipal principal){

        List<Ticket> tickets=ticketService.selectMyAssignedTickets(principal.getId());
        List<TicketResponse> ticketResponse=new ArrayList<>();
        for(Ticket ticket:tickets)
        {
            ticketResponse.add(toTicketResponse(ticket));
        }
        return ResponseEntity.ok(ticketResponse);
    }

    @GetMapping("/mine")
    public ResponseEntity<List<TicketResponse>> selectMyTickets(@AuthenticationPrincipal AppUserPrincipal principal){
        List<Ticket> tickets=ticketService.selectByCreatorId(principal.getId());
        List<TicketResponse> ticketResponse=new ArrayList<>();
        for(Ticket ticket:tickets)
        {
            ticketResponse.add(toTicketResponse(ticket));
        }
        return ResponseEntity.ok(ticketResponse);
    }

    @GetMapping("")
    public PageResult<TicketResponse> selectAllByPage(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @ModelAttribute TicketPageQuery ticketPageQuery
            ){
        PageResult<Ticket> pageResults=ticketService.selectAdminTickets(principal.getId(),ticketPageQuery);
        List<TicketResponse> ticketResponse=new ArrayList<>();
        PageResult<TicketResponse> pageResult=new PageResult<>();
        for(Ticket ticket:pageResults.getItems())
        {
            ticketResponse.add(toTicketResponse(ticket));
        }
        pageResult.setItems(ticketResponse);
        pageResult.setPage(pageResults.getPage());
        pageResult.setPageSize(pageResults.getPageSize());
        pageResult.setTotal(pageResults.getTotal());
        return pageResult;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody TicketCreateRequest ticketCreateRequest
    ){
        Long ticketId=ticketService.createTicket(principal.getId(),ticketCreateRequest);
        Ticket ticket=ticketService.selectById(ticketId);
        TicketResponse ticketResponse=toTicketResponse(ticket);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ticketResponse);
    }

    @PatchMapping("/{id}/assignment")
    public ResponseEntity<TicketResponse> assignTicket(
            @Valid @RequestBody TicketAssignRequest ticketAssignRequest,
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id
            ){
        Ticket ticket=ticketService.assignTicket(id,principal.getId(),ticketAssignRequest);
        if(ticket==null)
            return ResponseEntity.notFound()
                    .build();
        TicketResponse ticketResponse=toTicketResponse(ticket);
        return ResponseEntity.ok(ticketResponse);
    }

    @PatchMapping("/{id}/processing")
    public ResponseEntity<TicketResponse> processTicket(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id
    ){
        Ticket ticket=ticketService.startProcessing(id,principal.getId());
        if(ticket==null)
            return  ResponseEntity.notFound()
                    .build();
        TicketResponse ticketResponse=toTicketResponse(ticket);
        return  ResponseEntity.ok(ticketResponse);
    }

    @PatchMapping("/{id}/resolution")
    public ResponseEntity<TicketResponse> resolveTicket(
            @Valid @RequestBody TicketResolveRequest ticketResolveRequest,
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id
    ){
        Ticket ticket=ticketService.resolveTicket(id,principal.getId(),ticketResolveRequest);
        if(ticket==null)
            return ResponseEntity.notFound()
                    .build();
        TicketResponse ticketResponse=toTicketResponse(ticket);
        return  ResponseEntity.ok(ticketResponse);
    }

    @PatchMapping("/{id}/closure")
    public ResponseEntity<TicketResponse> closeTicket(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id
    ){
        Ticket ticket=ticketService.closeTicket(id,principal.getId());
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
