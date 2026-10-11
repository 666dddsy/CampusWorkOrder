package com.example.campusworkorder.service;

import com.example.campusworkorder.domain.enums.TicketStatus;
import com.example.campusworkorder.domain.enums.UserRole;
import com.example.campusworkorder.dto.TicketAssignRequest;
import com.example.campusworkorder.dto.TicketCreateRequest;
import com.example.campusworkorder.entity.Ticket;
import com.example.campusworkorder.entity.TicketLog;
import com.example.campusworkorder.exception.BusinessException;
import com.example.campusworkorder.mapper.AppUserMapper;
import com.example.campusworkorder.mapper.TicketLogMapper;
import com.example.campusworkorder.mapper.TicketMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
public class TicketService {
    private final TicketMapper ticketMapper;
    private final TicketLogMapper ticketLogMapper;
    private final AppUserMapper appUserMapper;
    public TicketService(
            TicketMapper ticketMapper,TicketLogMapper ticketLogMapper,AppUserMapper appUserMapper
    ){
        this.ticketLogMapper=ticketLogMapper;
        this.ticketMapper=ticketMapper;
        this.appUserMapper=appUserMapper;
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
    @Transactional
    public Ticket assignTicket(Long ticketId,Long userId,TicketAssignRequest ticketAssignRequest){
        UserRole adminRole=appUserMapper.selectRoleById(userId);
        UserRole assigneeRole=appUserMapper.selectRoleById(ticketAssignRequest.getAssigneeId());
        if(adminRole!= UserRole.ADMIN){
            throw new BusinessException(HttpStatus.FORBIDDEN,"OperatorRole should be 'ADMIN'");
        }
        if(assigneeRole!= UserRole.WORKER){
            throw new BusinessException(HttpStatus.BAD_REQUEST,"AssigneeRole should be 'WORKER'");
        }
        Ticket ticket=ticketMapper.selectById(ticketId);
        if(ticket==null)
            return null;
        if(ticket.getStatus()!=TicketStatus.CREATED)
            throw new BusinessException(HttpStatus.CONFLICT,"The ticket has been operated");
        int rows=ticketMapper.updateAssignmentIfCreated(ticketId,ticketAssignRequest.getAssigneeId());
        if(rows!=1)
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Ticket status has changed"
            );
        ticket=ticketMapper.selectById(ticketId);
        TicketLog ticketLog=toTicketLogAssigned(userId,ticket);
        int logRows=ticketLogMapper.insert(ticketLog);
        if(logRows!=1)
            throw new IllegalStateException("failed to insert ticket log");
        return ticket;
    }

    public Ticket selectById(Long ticketId){
        return ticketMapper.selectById(ticketId);
    }
    public List<Ticket> selectByCreatorId(Long creatorId){
        return ticketMapper.selectByCreatorId(creatorId);
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
    private TicketLog toTicketLogAssigned(Long operatorId,Ticket ticket){
        TicketLog ticketLog=new TicketLog();
        ticketLog.setTicketId(ticket.getId());
        ticketLog.setFromStatus(TicketStatus.CREATED);
        ticketLog.setToStatus(TicketStatus.ASSIGNED);
        ticketLog.setOperatorId(operatorId);
        ticketLog.setNote("管理员分配工单");
        return ticketLog;
    }

}
