package com.example.campusworkorder.service;

import com.example.campusworkorder.domain.enums.TicketStatus;
import com.example.campusworkorder.domain.enums.UserRole;
import com.example.campusworkorder.dto.*;
import com.example.campusworkorder.entity.Ticket;
import com.example.campusworkorder.entity.TicketLog;
import com.example.campusworkorder.exception.BusinessException;
import com.example.campusworkorder.mapper.AppUserMapper;
import com.example.campusworkorder.mapper.TicketLogMapper;
import com.example.campusworkorder.mapper.TicketMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;


@Service
public class TicketService {
    private static final String LOG_CACHE_KEY_PREFIX = "ticket:logs:";
    private final TicketMapper ticketMapper;
    private final TicketLogMapper ticketLogMapper;
    private final AppUserMapper appUserMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration logCacheTtl;
    public TicketService(
            TicketMapper ticketMapper,
            TicketLogMapper ticketLogMapper,
            AppUserMapper appUserMapper,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${app.log-cache.ttl}") Duration logCacheTtl
    ){
        this.ticketLogMapper=ticketLogMapper;
        this.ticketMapper=ticketMapper;
        this.appUserMapper=appUserMapper;
        this.redis=redis;
        this.objectMapper=objectMapper;
        this.logCacheTtl=logCacheTtl;
    }

    @Transactional
    public Long createTicket(Long creatorId,TicketCreateRequest ticketCreateRequest){
        UserRole role=appUserMapper.selectRoleById(creatorId);
        if (role!=UserRole.REQUESTER)
            throw new BusinessException(HttpStatus.FORBIDDEN,"You are not Requester");
        Ticket ticket=toTicket(creatorId,ticketCreateRequest);
        int affectRows=ticketMapper.insert(ticket);
        if(affectRows!=1||ticket.getId()==null)
            throw new IllegalStateException("failed to create ticket");
        TicketLog ticketLog=toTicketLog(creatorId,ticket);
        int logAffectedRows = ticketLogMapper.insert(ticketLog);
        if (logAffectedRows != 1) {
            throw new IllegalStateException("failed to create ticket log");
        }
        redis.delete(LOG_CACHE_KEY_PREFIX + ticket.getId());
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
        redis.delete(LOG_CACHE_KEY_PREFIX + ticketId);
        return ticket;
    }
    @Transactional
    public Ticket startProcessing(Long ticketId,Long userId){
        UserRole role=appUserMapper.selectRoleById(userId);
        Ticket ticket=ticketMapper.selectById(ticketId);
        if (role != UserRole.WORKER)
            throw new BusinessException(HttpStatus.FORBIDDEN, "You don't have the right to process this ticket.");
        if(ticket==null)
            return null;
        if (!userId.equals(ticket.getAssigneeId()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "You don't have the right to process this ticket.");
        if (ticket.getStatus() != TicketStatus.ASSIGNED)
            throw new BusinessException(HttpStatus.CONFLICT, "The status of ticket isn't assigned");
        int rows=ticketMapper.updateProcessIfAssigned(ticketId,userId);
        if(rows!=1)
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Ticket status has changed"
            );
        ticket=ticketMapper.selectById(ticketId);
        TicketLog ticketLog=toTicketLogProcessed(userId,ticket);
        int logRows=ticketLogMapper.insert(ticketLog);
        if(logRows!=1)
            throw new IllegalStateException("failed to insert ticket log");
        redis.delete(LOG_CACHE_KEY_PREFIX + ticketId);
        return ticket;
    }
    @Transactional
    public Ticket resolveTicket(Long ticketId, Long userId, TicketResolveRequest ticketResolveRequest){
        Ticket ticket=ticketMapper.selectById(ticketId);
        UserRole role=appUserMapper.selectRoleById(userId);
        if(role!=UserRole.WORKER)
            throw new BusinessException(HttpStatus.FORBIDDEN,"You don't have the right to resolve this ticket.");
        if(ticket==null)
            return null;
        if(!userId.equals(ticket.getAssigneeId()))
            throw new BusinessException(HttpStatus.FORBIDDEN,"You don't have the right to resolve this ticket.");
        if(ticket.getStatus()!=TicketStatus.PROCESSING)
            throw new BusinessException(HttpStatus.CONFLICT,"The ticket didn't start processing or has been resolved");
        int rows=ticketMapper.updateResolveIfProcessing(ticketId,userId);
        if(rows!=1)
            throw new BusinessException(HttpStatus.CONFLICT,"The ticket has been resolved");
        ticket=ticketMapper.selectById(ticketId);
        TicketLog ticketLog=toTicketLogResolved(userId,ticket,ticketResolveRequest.getResolutionNote());
        int logRows=ticketLogMapper.insert(ticketLog);
        if(logRows!=1)
            throw new IllegalStateException("failed to insert ticket log");
        redis.delete(LOG_CACHE_KEY_PREFIX + ticketId);
        return ticket;
    }
    @Transactional
    public Ticket closeTicket(Long ticketId,Long userId){
        Ticket ticket=ticketMapper.selectById(ticketId);
        UserRole role=appUserMapper.selectRoleById(userId);
        if(role!=UserRole.REQUESTER)
            throw new BusinessException(HttpStatus.FORBIDDEN,"You don't have the right to close this ticket.");
        if(ticket==null)
            return null;
        if(!userId.equals(ticket.getCreatorId()))
            throw new BusinessException(HttpStatus.FORBIDDEN,"You don't have the right to close this ticket.");
        if(ticket.getStatus()!=TicketStatus.RESOLVED)
            throw new BusinessException(HttpStatus.CONFLICT,"The ticket is not resolved or has already been closed.");
        int rows=ticketMapper.updateCloseIfResolved(ticketId,userId);
        if(rows!=1)
            throw new BusinessException(HttpStatus.CONFLICT,"Ticket status has changed.");
        ticket=ticketMapper.selectById(ticketId);
        TicketLog ticketLog=toTicketLogClosed(userId,ticket);
        int logRows=ticketLogMapper.insert(ticketLog);
        if(logRows!=1)
            throw new IllegalStateException("failed to insert ticket log");
        redis.delete(LOG_CACHE_KEY_PREFIX + ticketId);
        return ticket;
    }

    public PageResult<Ticket> selectAdminTickets(Long userId, TicketPageQuery ticketPageQuery){
        int page=ticketPageQuery.getPage();
        int pageSize=ticketPageQuery.getPageSize();
        UserRole role = appUserMapper.selectRoleById(userId);
        if(role!=UserRole.ADMIN)
            throw new BusinessException(HttpStatus.FORBIDDEN,"You are not ADMIN");
        long offset = (page - 1L) * pageSize;
        List<Ticket> tickets=ticketMapper.selectPage(ticketPageQuery,offset,pageSize);
        long count=ticketMapper.countByQuery(ticketPageQuery);
        PageResult<Ticket> pageResult=new PageResult<>();
        pageResult.setItems(tickets);
        pageResult.setPage(page);
        pageResult.setPageSize(pageSize);
        pageResult.setTotal(count);
        return pageResult;
    }

    //仅供内部查询
    public Ticket selectById(Long ticketId){
        return ticketMapper.selectById(ticketId);
    }

    public Ticket selectByIdWithRole(Long ticketId,Long currentUserId){
        UserRole role=appUserMapper.selectRoleById(currentUserId);
        if(role==null)
            throw new BusinessException(HttpStatus.FORBIDDEN, "You don't have right to get the ticket");
        Ticket ticket=ticketMapper.selectById(ticketId);
        if(ticket==null)
            return null;
        ensureCanView(ticket,currentUserId,role,"You don't have right to get the ticket");
        return ticket;
    }

    public List<Ticket> selectByCreatorId(Long creatorId){
        return ticketMapper.selectByCreatorId(creatorId);
    }

    public List<Ticket> selectMyAssignedTickets(Long currentUserId) {
        UserRole role = appUserMapper.selectRoleById(currentUserId);

        if (role != UserRole.WORKER) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "UserRole should be 'WORKER'"
            );
        }

        return ticketMapper.selectByAssigneeId(currentUserId);
    }

    public List<TicketLog> selectLogsByTicketId(Long ticketId,Long userId){
        UserRole role=appUserMapper.selectRoleById(userId);
        if(role==null)
            throw new BusinessException(HttpStatus.FORBIDDEN, "You don't have right to get the ticketLog");
        Ticket ticket=ticketMapper.selectById(ticketId);
        if(ticket==null)
            return null;
        ensureCanView(ticket,userId,role,"You don't have right to get the ticketLog");
        String cacheKey=LOG_CACHE_KEY_PREFIX+ticketId;
        String cached=redis.opsForValue().get(cacheKey);
        if(cached!=null)
            return objectMapper.readValue(cached, new TypeReference<List<TicketLog>>() {});
        List<TicketLog> logs=ticketLogMapper.selectByTicketId(ticketId);
        redis.opsForValue().set(cacheKey,objectMapper.writeValueAsString(logs),logCacheTtl);
        return logs;
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
    private TicketLog toTicketLogProcessed(Long operatorId,Ticket ticket){
        TicketLog ticketLog=new TicketLog();
        ticketLog.setTicketId(ticket.getId());
        ticketLog.setFromStatus(TicketStatus.ASSIGNED);
        ticketLog.setToStatus(TicketStatus.PROCESSING);
        ticketLog.setOperatorId(operatorId);
        ticketLog.setNote("正在处理工单");
        return ticketLog;
    }
    private TicketLog toTicketLogResolved(Long operatorId,Ticket ticket,String note){
        TicketLog ticketLog=new TicketLog();
        ticketLog.setTicketId(ticket.getId());
        ticketLog.setFromStatus(TicketStatus.PROCESSING);
        ticketLog.setToStatus(TicketStatus.RESOLVED);
        ticketLog.setOperatorId(operatorId);
        ticketLog.setNote(note);
        return ticketLog;
    }
    private TicketLog toTicketLogClosed(Long operatorId,Ticket ticket){
        TicketLog ticketLog=new TicketLog();
        ticketLog.setTicketId(ticket.getId());
        ticketLog.setFromStatus(TicketStatus.RESOLVED);
        ticketLog.setToStatus(TicketStatus.CLOSED);
        ticketLog.setOperatorId(operatorId);
        ticketLog.setNote("报修人确认问题已解决并关闭工单");
        return ticketLog;
    }
    private void ensureCanView(Ticket ticket, Long userId, UserRole role,String message) {
        boolean allowed = switch (role) {
            case ADMIN -> true;
            case REQUESTER -> userId.equals(ticket.getCreatorId());
            case WORKER -> userId.equals(ticket.getAssigneeId());
        };
        if (!allowed) throw new BusinessException(HttpStatus.FORBIDDEN, message);
    }
}
