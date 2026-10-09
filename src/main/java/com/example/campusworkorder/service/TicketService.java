package com.example.campusworkorder.service;

import com.example.campusworkorder.domain.enums.TicketStatus;
import com.example.campusworkorder.dto.TicketCreateRequest;
import com.example.campusworkorder.entity.Ticket;
import com.example.campusworkorder.entity.TicketLog;
import com.example.campusworkorder.mapper.TicketLogMapper;
import com.example.campusworkorder.mapper.TicketMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class TicketService {
    private final TicketMapper ticketMapper;
    private final TicketLogMapper ticketLogMapper;
    public TicketService(TicketMapper ticketMapper,TicketLogMapper ticketLogMapper){
        this.ticketLogMapper=ticketLogMapper;
        this.ticketMapper=ticketMapper;
    }

    @Transactional
    public Long createTicket(Long creatorId,TicketCreateRequest ticketCreateRequest){
        Ticket ticket=toTicket(creatorId,ticketCreateRequest);
        int affectRows=ticketMapper.insert(ticket);
        if(affectRows!=1||ticket.getId()==null)
            throw new IllegalStateException("failed to create ticket");
        TicketLog ticketLog=toTicketLog(creatorId,ticket);
        int logAffectedRows = ticketLogMapper.insert(ticketLog);
        if (logAffectedRows != 1) {
            throw new IllegalStateException("failed to create ticket log");
        }
        return ticket.getId();
    }

    public Ticket selectById(Long ticketId){
        return ticketMapper.selectById(ticketId);
    }

    private Ticket toTicket(Long creatorId,TicketCreateRequest ticketCreateRequest){
        Ticket ticket=new Ticket();
        ticket.setCreatorId(creatorId);
        ticket.setAssigneeId(null);
        ticket.setTitle(ticketCreateRequest.getTitle());
        ticket.setDescription(ticketCreateRequest.getDescription());
        ticket.setLocation(ticketCreateRequest.getLocation());
        ticket.setCategory(ticketCreateRequest.getCategory());
        ticket.setPriority(ticketCreateRequest.getPriority());
        ticket.setStatus(TicketStatus.CREATED);
        return ticket;
    }

    private TicketLog toTicketLog(Long creatorId,Ticket ticket){
        TicketLog ticketLog=new TicketLog();
        ticketLog.setTicketId(ticket.getId());
        ticketLog.setFromStatus(null);
        ticketLog.setToStatus(TicketStatus.CREATED);
        ticketLog.setOperatorId(creatorId);
        ticketLog.setNote("报修人创建工单");
        return ticketLog;
    }


}
