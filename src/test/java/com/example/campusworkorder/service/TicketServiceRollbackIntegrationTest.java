package com.example.campusworkorder.service;

import com.example.campusworkorder.domain.enums.TicketCategory;
import com.example.campusworkorder.domain.enums.TicketPriority;
import com.example.campusworkorder.domain.enums.TicketStatus;
import com.example.campusworkorder.dto.TicketAssignRequest;
import com.example.campusworkorder.dto.TicketCreateRequest;
import com.example.campusworkorder.dto.TicketResolveRequest;
import com.example.campusworkorder.entity.Ticket;
import com.example.campusworkorder.entity.TicketLog;
import com.example.campusworkorder.mapper.TicketLogMapper;
import com.example.campusworkorder.mapper.TicketMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
public class TicketServiceRollbackIntegrationTest {
    @Autowired
    private TicketService ticketService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private TicketLogMapper ticketLogMapper;
    @Autowired
    private TicketMapper ticketMapper;

    @Test
    void TicketServiceIntegrationTest_shouldRollback(){
        String username = "test_requester_" + System.nanoTime();
        Long creatorId=null;
        try{
            jdbcTemplate.update(
                    """
                            insert into app_user (username,password_hash,role)
                            values(?,?,?)
                            """,
                    username, "test_hash", "REQUESTER"
            );
            creatorId = jdbcTemplate.queryForObject(
                    """
                            select id from app_user where username=?
                            """,
                    Long.class,
                    username
            );
            TicketCreateRequest request = new TicketCreateRequest();
            request.setTitle("集成测试工单");
            request.setDescription("验证创建工单和日志是否同时写入");
            request.setLocation("测试地点");
            request.setCategory(TicketCategory.NETWORK);
            request.setPriority(TicketPriority.HIGH);
            when(ticketLogMapper.insert(any(TicketLog.class)))
                    .thenThrow(new RuntimeException("forced log failure"));
            Long finalCreatorId = creatorId;
            assertThrows(RuntimeException.class,
                    () -> ticketService.createTicket(finalCreatorId, request));

            Long ticketCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ticket WHERE creator_id = ? AND title = ?",
                    Long.class,
                    creatorId,
                    request.getTitle()
            );

            assertEquals(0L, ticketCount);
        }finally {
            jdbcTemplate.update(
                    "DELETE FROM ticket WHERE creator_id = ?",
                    creatorId
            );
            if(creatorId!=null) {
                jdbcTemplate.update(
                        "delete from app_user where id=?",
                        creatorId
                );
            }
        }
    }
    @Test
    void assignTicket_shouldRollbackWhenLogInsertFails(){
        String suffix = String.valueOf(System.nanoTime());

        String requesterUsername = "assign_requester_" + suffix;
        String adminUsername = "assign_admin_" + suffix;
        String workerUsername = "assign_worker_" + suffix;
        String ticketTitle = "assign_ticket_" + suffix;

        Long requesterId = null;
        Long adminId = null;
        Long workerId = null;
        Long ticketId = null;

        try {
            jdbcTemplate.update(
                    """
                        insert into app_user (username,password_hash,role)
                        values(?,?,?)
                        """,
                    requesterUsername,"test_hash", "REQUESTER"
            );
            jdbcTemplate.update(
                    """
                        insert into app_user (username,password_hash,role)
                        values(?,?,?)
                        """,
                    adminUsername,"test_hash", "ADMIN"
            );
            jdbcTemplate.update(
                    """
                        insert into app_user (username,password_hash,role)
                        values(?,?,?)
                        """,
                    workerUsername,"test_hash", "WORKER"
            );
            requesterId=jdbcTemplate.queryForObject(
                    """
                            select id from app_user where username=?
                            """,
                    Long.class,
                    requesterUsername
            );
            adminId=jdbcTemplate.queryForObject(
                    """
                            select id from app_user where username=?
                            """,
                    Long.class,
                    adminUsername
            );
            workerId=jdbcTemplate.queryForObject(
                    """
                            select id from app_user where username=?
                            """,
                    Long.class,
                    workerUsername
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO ticket
                    (
                        creator_id,
                        assignee_id,
                        title,
                        description,
                        location,
                        category,
                        priority,
                        status
                    )
                    VALUES (?, NULL, ?, ?, ?, ?, ?, ?)
                    """,
                    requesterId,
                    ticketTitle,
                    "验证管理员分配工单",
                    "测试地点",
                    "NETWORK",
                    "HIGH",
                    "CREATED"
            );
            ticketId=jdbcTemplate.queryForObject(
                    """
                            select id from ticket where creator_id=? and title=?
                            """,
                    Long.class,
                    requesterId,
                    ticketTitle
            );
            TicketAssignRequest request = new TicketAssignRequest();
            request.setAssigneeId(workerId);
            when(ticketLogMapper.insert(any(TicketLog.class)))
                    .thenThrow(new RuntimeException("123456"));

            Long finalTicketId = ticketId;
            Long finalAdminId = adminId;
            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> ticketService.assignTicket(finalTicketId, finalAdminId, request)
            );

            assertEquals("123456", exception.getMessage());
            Ticket ticket=ticketService.selectById(ticketId);
            assertNotNull(ticket);
            assertNull(ticket.getAssigneeId());
            assertEquals(TicketStatus.CREATED,ticket.getStatus());
        } finally {
            // 下一步再写清理
            if (ticketId != null) {
                jdbcTemplate.update(
                        "DELETE FROM ticket_log WHERE ticket_id = ?",
                        ticketId
                );

                jdbcTemplate.update(
                        "DELETE FROM ticket WHERE id = ?",
                        ticketId
                );
            }

            if (adminId != null) {
                jdbcTemplate.update(
                        "DELETE FROM app_user WHERE id = ?",
                        adminId
                );
            }

            if (workerId != null) {
                jdbcTemplate.update(
                        "DELETE FROM app_user WHERE id = ?",
                        workerId
                );
            }

            if (requesterId != null) {
                jdbcTemplate.update(
                        "DELETE FROM app_user WHERE id = ?",
                        requesterId
                );
            }
        }
    }
    @Test
    void processTicket_shouldRollbackWhenLogInsertFails(){
        String suffix = String.valueOf(System.nanoTime());

        String requesterUsername = "assign_requester_" + suffix;
        String workerUsername = "assign_worker_" + suffix;
        String ticketTitle = "assign_ticket_" + suffix;

        Long requesterId = null;
        Long workerId = null;
        Long ticketId = null;

        try {
            jdbcTemplate.update(
                    """
                        insert into app_user (username,password_hash,role)
                        values(?,?,?)
                        """,
                    requesterUsername,"test_hash", "REQUESTER"
            );
            jdbcTemplate.update(
                    """
                        insert into app_user (username,password_hash,role)
                        values(?,?,?)
                        """,
                    workerUsername,"test_hash", "WORKER"
            );
            requesterId=jdbcTemplate.queryForObject(
                    """
                            select id from app_user where username=?
                            """,
                    Long.class,
                    requesterUsername
            );

            workerId=jdbcTemplate.queryForObject(
                    """
                            select id from app_user where username=?
                            """,
                    Long.class,
                    workerUsername
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO ticket
                    (
                        creator_id,
                        assignee_id,
                        title,
                        description,
                        location,
                        category,
                        priority,
                        status
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    requesterId,
                    workerId,
                    ticketTitle,
                    "验证管理员分配工单",
                    "测试地点",
                    "NETWORK",
                    "HIGH",
                    "ASSIGNED"
            );
            ticketId=jdbcTemplate.queryForObject(
                    """
                            select id from ticket where creator_id=? and title=?
                            """,
                    Long.class,
                    requesterId,
                    ticketTitle
            );
            when(ticketLogMapper.insert(any(TicketLog.class)))
                    .thenThrow(new RuntimeException("123456"));

            Long finalTicketId = ticketId;
            Long finalWorkerId = workerId;
            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> ticketService.startProcessing(finalTicketId, finalWorkerId)
            );

            assertEquals("123456", exception.getMessage());
            Ticket ticket=ticketService.selectById(ticketId);
            assertNotNull(ticket);
            assertEquals(workerId,ticket.getAssigneeId());
            assertEquals(TicketStatus.ASSIGNED, ticket.getStatus());
            Long logCount = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM ticket_log
                    WHERE ticket_id = ?
                      AND from_status = 'ASSIGNED'
                      AND to_status = 'PROCESSING'
                    """,
                    Long.class,
                    ticketId
            );

            assertEquals(0L, logCount);
        }finally {
            if (ticketId != null) {
                jdbcTemplate.update(
                        "DELETE FROM ticket_log WHERE ticket_id = ?",
                        ticketId
                );

                jdbcTemplate.update(
                        "DELETE FROM ticket WHERE id = ?",
                        ticketId
                );
            }

            if (workerId != null) {
                jdbcTemplate.update(
                        "DELETE FROM app_user WHERE id = ?",
                        workerId
                );
            }

            if (requesterId != null) {
                jdbcTemplate.update(
                        "DELETE FROM app_user WHERE id = ?",
                        requesterId
                );
            }
        }
    }
    @Test
    void resolveTicket_shouldRollbackWhenLogInsertFails(){
        String suffix = String.valueOf(System.nanoTime());

        String requesterUsername = "assign_requester_" + suffix;
        String workerUsername = "assign_worker_" + suffix;
        String ticketTitle = "assign_ticket_" + suffix;

        Long requesterId = null;
        Long workerId = null;
        Long ticketId = null;

        try {
            jdbcTemplate.update(
                    """
                        insert into app_user (username,password_hash,role)
                        values(?,?,?)
                        """,
                    requesterUsername,"test_hash", "REQUESTER"
            );
            jdbcTemplate.update(
                    """
                        insert into app_user (username,password_hash,role)
                        values(?,?,?)
                        """,
                    workerUsername,"test_hash", "WORKER"
            );
            requesterId=jdbcTemplate.queryForObject(
                    """
                            select id from app_user where username=?
                            """,
                    Long.class,
                    requesterUsername
            );

            workerId=jdbcTemplate.queryForObject(
                    """
                            select id from app_user where username=?
                            """,
                    Long.class,
                    workerUsername
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO ticket
                    (
                        creator_id,
                        assignee_id,
                        title,
                        description,
                        location,
                        category,
                        priority,
                        status
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    requesterId,
                    workerId,
                    ticketTitle,
                    "验证管理员分配工单",
                    "测试地点",
                    "NETWORK",
                    "HIGH",
                    "PROCESSING"
            );
            ticketId=jdbcTemplate.queryForObject(
                    """
                            select id from ticket where creator_id=? and title=?
                            """,
                    Long.class,
                    requesterId,
                    ticketTitle
            );
            when(ticketLogMapper.insert(any(TicketLog.class)))
                    .thenThrow(new RuntimeException("123456"));

            Long finalTicketId = ticketId;
            Long finalWorkerId = workerId;
            TicketResolveRequest ticketResolveRequest=new TicketResolveRequest();
            ticketResolveRequest.setResolutionNote("测试，因回滚消失");
            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> ticketService.resolveTicket(finalTicketId, finalWorkerId,ticketResolveRequest)
            );

            assertEquals("123456", exception.getMessage());
            Ticket ticket=ticketService.selectById(ticketId);
            assertNotNull(ticket);
            assertEquals(workerId,ticket.getAssigneeId());
            assertEquals(TicketStatus.PROCESSING, ticket.getStatus());
            Long logCount = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM ticket_log
                    WHERE ticket_id = ?
                      AND from_status = 'PROCESSING'
                      AND to_status = 'RESOLVED'
                    """,
                    Long.class,
                    ticketId
            );

            assertEquals(0L, logCount);
        }finally {
            if (ticketId != null) {
                jdbcTemplate.update(
                        "DELETE FROM ticket_log WHERE ticket_id = ?",
                        ticketId
                );

                jdbcTemplate.update(
                        "DELETE FROM ticket WHERE id = ?",
                        ticketId
                );
            }

            if (workerId != null) {
                jdbcTemplate.update(
                        "DELETE FROM app_user WHERE id = ?",
                        workerId
                );
            }

            if (requesterId != null) {
                jdbcTemplate.update(
                        "DELETE FROM app_user WHERE id = ?",
                        requesterId
                );
            }
        }
    }
    @Test
    void closeTicket_shouldRollbackWhenLogInsertFails(){
        String suffix = String.valueOf(System.nanoTime());

        String requesterUsername = "close_requester_" + suffix;
        String workerUsername = "close_worker_" + suffix;
        String ticketTitle = "close_ticket_" + suffix;

        Long requesterId = null;
        Long workerId = null;
        Long ticketId = null;

        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO app_user(username,password_hash,role)
                    VALUES(?,?,?)
                    """,
                    requesterUsername,
                    "test_hash",
                    "REQUESTER"
            );

            jdbcTemplate.update(
                    """
                    INSERT INTO app_user(username,password_hash,role)
                    VALUES(?,?,?)
                    """,
                    workerUsername,
                    "test_hash",
                    "WORKER"
            );

            requesterId = jdbcTemplate.queryForObject(
                    """
                    SELECT id
                    FROM app_user
                    WHERE username=?
                    """,
                    Long.class,
                    requesterUsername
            );

            workerId = jdbcTemplate.queryForObject(
                    """
                    SELECT id
                    FROM app_user
                    WHERE username=?
                    """,
                    Long.class,
                    workerUsername
            );

            jdbcTemplate.update(
                    """
                    INSERT INTO ticket(
                        creator_id,
                        assignee_id,
                        title,
                        description,
                        location,
                        category,
                        priority,
                        status
                    )
                    VALUES(?,?,?,?,?,?,?,?)
                    """,
                    requesterId,
                    workerId,
                    ticketTitle,
                    "验证关闭工单写日志失败时事务回滚",
                    "测试地点",
                    "NETWORK",
                    "HIGH",
                    "RESOLVED"
            );

            ticketId = jdbcTemplate.queryForObject(
                    """
                    SELECT id
                    FROM ticket
                    WHERE creator_id=?
                      AND title=?
                    """,
                    Long.class,
                    requesterId,
                    ticketTitle
            );

            when(ticketLogMapper.insert(any(TicketLog.class)))
                    .thenThrow(new RuntimeException("123456"));

            Long finalTicketId = ticketId;
            Long finalRequesterId = requesterId;

            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> ticketService.closeTicket(
                            finalTicketId,
                            finalRequesterId
                    )
            );

            assertEquals("123456",exception.getMessage());

            Ticket savedTicket = ticketMapper.selectById(ticketId);

            assertNotNull(savedTicket);
            assertEquals(TicketStatus.RESOLVED,savedTicket.getStatus());
            assertEquals(requesterId,savedTicket.getCreatorId());
            assertEquals(workerId,savedTicket.getAssigneeId());

            Long logCount = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM ticket_log
                    WHERE ticket_id=?
                      AND from_status='RESOLVED'
                      AND to_status='CLOSED'
                    """,
                    Long.class,
                    ticketId
            );

            assertEquals(0L,logCount);

        } finally {
            if(ticketId != null){
                jdbcTemplate.update(
                        "DELETE FROM ticket_log WHERE ticket_id=?",
                        ticketId
                );

                jdbcTemplate.update(
                        "DELETE FROM ticket WHERE id=?",
                        ticketId
                );
            }

            if(workerId != null){
                jdbcTemplate.update(
                        "DELETE FROM app_user WHERE id=?",
                        workerId
                );
            }

            if(requesterId != null){
                jdbcTemplate.update(
                        "DELETE FROM app_user WHERE id=?",
                        requesterId
                );
            }
        }
    }

}
