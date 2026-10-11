package com.example.campusworkorder.service;

import com.example.campusworkorder.domain.enums.TicketCategory;
import com.example.campusworkorder.domain.enums.TicketPriority;
import com.example.campusworkorder.domain.enums.TicketStatus;
import com.example.campusworkorder.dto.TicketAssignRequest;
import com.example.campusworkorder.dto.TicketCreateRequest;
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
    void assignTicketShouldRollbackWhenLogInsertFails(){
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
            // TODO 1：分别向 app_user 插入 REQUESTER、ADMIN、WORKER
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
            // TODO 2：根据三个 username 查询三个 id
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
            // TODO 3：直接插入一张 CREATED、assignee_id 为 null 的工单
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
            // TODO 4：根据 creator_id 和 title 查询 ticketId
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

}
