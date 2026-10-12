package com.example.campusworkorder.service;

import com.example.campusworkorder.domain.enums.TicketCategory;
import com.example.campusworkorder.domain.enums.TicketPriority;
import com.example.campusworkorder.domain.enums.TicketStatus;
import com.example.campusworkorder.dto.TicketAssignRequest;
import com.example.campusworkorder.dto.TicketCreateRequest;
import com.example.campusworkorder.entity.Ticket;
import com.example.campusworkorder.mapper.TicketMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class TicketServiceIntegrationTest {
    @Autowired
    private TicketService ticketService;
    @Autowired
    private TicketMapper ticketMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createTicketShouldInsertTicketAndLog(){
        String username = "test_requester_" + System.nanoTime();
        Long creatorId = null;
        Long ticketId = null;
        try{
            jdbcTemplate.update(
                    """
                        insert into app_user (username,password_hash,role)
                        values(?,?,?)
                        """,
                    username,"test_hash", "REQUESTER"
            );
            creatorId=jdbcTemplate.queryForObject(
                    """
                            select id from app_user where username=?
                            """,
                    Long.class,
                    username
            );
            TicketCreateRequest request=new TicketCreateRequest();
            request.setTitle("集成测试工单");
            request.setDescription("验证创建工单和日志是否同时写入");
            request.setLocation("测试地点");
            request.setCategory(TicketCategory.NETWORK);
            request.setPriority(TicketPriority.HIGH);
            ticketId= ticketService.createTicket(creatorId,request);
            assertNotNull(ticketId);

            Ticket ticket=ticketMapper.selectById(ticketId);
            assertNotNull(ticket);
            assertEquals(creatorId,ticket.getCreatorId());
            assertNull(ticket.getAssigneeId());
            assertEquals("集成测试工单", ticket.getTitle());
            assertEquals(TicketCategory.NETWORK, ticket.getCategory());
            assertEquals(TicketPriority.HIGH, ticket.getPriority());
            assertEquals(TicketStatus.CREATED, ticket.getStatus());

            Map<String,Object> log=jdbcTemplate.queryForMap(
                    """
                         SELECT operator_id, from_status, to_status, note
                                        FROM ticket_log
                                        WHERE ticket_id = ?
                        """,
                        ticketId
            );
            assertEquals(creatorId.longValue(),((Number)log.get("operator_id")).longValue());
            assertNull(log.get("from_status"));
            assertEquals("CREATED", log.get("to_status"));
            assertEquals("报修人创建工单", log.get("note"));
        }finally{
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

            if (creatorId != null) {
                jdbcTemplate.update(
                        "DELETE FROM app_user WHERE id = ?",
                        creatorId
                );
            }
        }
    }
    @Test
    void assignTicketShouldUpdateStatusAndAssignee(){
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

            Ticket result = ticketService.assignTicket(ticketId, adminId, request);
            Ticket savedTicket = ticketMapper.selectById(ticketId);
            // 下一步再写断言
            assertNotNull(result);
            assertEquals(TicketStatus.ASSIGNED,result.getStatus());
            assertEquals(workerId,result.getAssigneeId());

            assertNotNull(savedTicket);
            assertEquals(TicketStatus.ASSIGNED, savedTicket.getStatus());
            assertEquals(workerId, savedTicket.getAssigneeId());

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
    void assignTicketShouldRejectRepeatedAssignment(){
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
            Long finalTicketId = ticketId;
            Long finalAdminId = adminId;
            ticketService.assignTicket(finalTicketId, finalAdminId, request);
            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> ticketService.assignTicket(finalTicketId, finalAdminId, request)
            );

            assertEquals("The ticket has been operated", exception.getMessage());
            Ticket ticket=ticketService.selectById(ticketId);
            assertNotNull(ticket);
            assertEquals(workerId,ticket.getAssigneeId());
            assertEquals(TicketStatus.ASSIGNED,ticket.getStatus());
            Long assignedLogCount = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM ticket_log
                    WHERE ticket_id = ?
                      AND from_status = 'CREATED'
                      AND to_status = 'ASSIGNED'
                    """,
                    Long.class,
                    ticketId
            );

            assertEquals(1L, assignedLogCount);
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
    void selectMyAssignedTicketsShouldOnlyReturnCurrentWorkersTickets() {
        String suffix = String.valueOf(System.nanoTime());

        String requesterUsername = "list_requester_" + suffix;
        String workerAUsername = "list_worker_a_" + suffix;
        String workerBUsername = "list_worker_b_" + suffix;

        String aOldTitle = "a_old_" + suffix;
        String aNewTitle = "a_new_" + suffix;
        String bTitle = "b_ticket_" + suffix;

        try {

            jdbcTemplate.update(
                    """
                    INSERT INTO app_user (username, password_hash, role)
                    VALUES
                        (?, ?, ?),
                        (?, ?, ?),
                        (?, ?, ?)
                    """,
                    requesterUsername, "test_hash", "REQUESTER",
                    workerAUsername, "test_hash", "WORKER",
                    workerBUsername, "test_hash", "WORKER"
            );

            Long requesterId = jdbcTemplate.queryForObject(
                    "SELECT id FROM app_user WHERE username = ?",
                    Long.class,
                    requesterUsername
            );

            Long workerAId = jdbcTemplate.queryForObject(
                    "SELECT id FROM app_user WHERE username = ?",
                    Long.class,
                    workerAUsername
            );

            Long workerBId = jdbcTemplate.queryForObject(
                    "SELECT id FROM app_user WHERE username = ?",
                    Long.class,
                    workerBUsername
            );

            // 2. 准备明确的创建时间，避免依赖插入速度
            LocalDateTime baseTime =
                    LocalDateTime.of(2026, 10, 12, 10, 0);

            // 3. 创建三张工单：A 两张、B 一张
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
                        status,
                        created_at
                    )
                    VALUES
                        (?, ?, ?, ?, ?, ?, ?, ?, ?),
                        (?, ?, ?, ?, ?, ?, ?, ?, ?),
                        (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,

                    // A 的旧工单
                    requesterId,
                    workerAId,
                    aOldTitle,
                    "worker A 旧工单",
                    "测试地点",
                    "NETWORK",
                    "MEDIUM",
                    "ASSIGNED",
                    Timestamp.valueOf(baseTime),

                    // A 的新工单
                    requesterId,
                    workerAId,
                    aNewTitle,
                    "worker A 新工单",
                    "测试地点",
                    "NETWORK",
                    "MEDIUM",
                    "ASSIGNED",
                    Timestamp.valueOf(baseTime.plusHours(1)),

                    // B 的工单故意设置为最新
                    requesterId,
                    workerBId,
                    bTitle,
                    "worker B 工单",
                    "测试地点",
                    "NETWORK",
                    "MEDIUM",
                    "ASSIGNED",
                    Timestamp.valueOf(baseTime.plusHours(2))
            );

            Long aOldTicketId = jdbcTemplate.queryForObject(
                    "SELECT id FROM ticket WHERE title = ?",
                    Long.class,
                    aOldTitle
            );

            Long aNewTicketId = jdbcTemplate.queryForObject(
                    "SELECT id FROM ticket WHERE title = ?",
                    Long.class,
                    aNewTitle
            );

            Long workerBTicketId = jdbcTemplate.queryForObject(
                    "SELECT id FROM ticket WHERE title = ?",
                    Long.class,
                    bTitle
            );


            List<Ticket> result =
                    ticketService.selectMyAssignedTickets(workerAId);


            assertEquals(2, result.size());

            assertEquals(workerAId, result.get(0).getAssigneeId());
            assertEquals(workerAId, result.get(1).getAssigneeId());


            assertEquals(aNewTicketId, result.get(0).getId());
            assertEquals(aOldTicketId, result.get(1).getId());


            assertNotEquals(workerBTicketId, result.get(0).getId());
            assertNotEquals(workerBTicketId, result.get(1).getId());

        } finally {
            // 先删除可能存在的日志
            jdbcTemplate.update(
                    """
                    DELETE FROM ticket_log
                    WHERE ticket_id IN
                    (
                        SELECT id
                        FROM ticket
                        WHERE title IN (?, ?, ?)
                    )
                    """,
                    aOldTitle,
                    aNewTitle,
                    bTitle
            );

            // 再删除本轮创建的三张工单
            jdbcTemplate.update(
                    "DELETE FROM ticket WHERE title IN (?, ?, ?)",
                    aOldTitle,
                    aNewTitle,
                    bTitle
            );

            // 最后删除本轮创建的三个用户
            jdbcTemplate.update(
                    "DELETE FROM app_user WHERE username IN (?, ?, ?)",
                    requesterUsername,
                    workerAUsername,
                    workerBUsername
            );
        }
    }
}
