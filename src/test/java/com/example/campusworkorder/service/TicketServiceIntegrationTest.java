package com.example.campusworkorder.service;

import com.example.campusworkorder.domain.enums.TicketCategory;
import com.example.campusworkorder.domain.enums.TicketPriority;
import com.example.campusworkorder.domain.enums.TicketStatus;
import com.example.campusworkorder.dto.TicketAssignRequest;
import com.example.campusworkorder.dto.TicketCreateRequest;
import com.example.campusworkorder.dto.TicketResolveRequest;
import com.example.campusworkorder.entity.Ticket;
import com.example.campusworkorder.entity.TicketLog;
import com.example.campusworkorder.exception.BusinessException;
import com.example.campusworkorder.mapper.TicketLogMapper;
import com.example.campusworkorder.mapper.TicketMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
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
    @Autowired
    private TicketLogMapper ticketLogMapper;

    @Test
    void createTicket_shouldInsertTicketAndLog(){
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
    void assignTicket_shouldUpdateStatusAndAssignee(){
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
    void assignTicket_shouldRejectRepeatedAssignment(){
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
    void selectMyAssignedTickets_shouldOnlyReturnCurrentWorkersTickets() {
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
    @Test
    void processTicket_shouldUpdateStatus(){
        String suffix = String.valueOf(System.nanoTime());

        String requesterUsername = "assign_requester_" + suffix;
        String workerUsername = "assign_worker_" + suffix;
        String ticketTitle = "assign_ticket_" + suffix;

        Long requesterId = null;
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
                    workerUsername,"test_hash", "WORKER"
            );
            // TODO 2：根据两个 username 查询三个 id
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
            // TODO 3：直接插入一张工单
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
            // TODO 4：根据 creator_id 和 title 查询 ticketId
            ticketId=jdbcTemplate.queryForObject(
                    """
                            select id from ticket where creator_id=? and title=?
                            """,
                    Long.class,
                    requesterId,
                    ticketTitle
            );
            Ticket result=ticketService.startProcessing(ticketId,workerId);
            Ticket savedTicket = ticketMapper.selectById(ticketId);

            assertNotNull(result);
            assertEquals(TicketStatus.PROCESSING,result.getStatus());
            assertEquals(workerId, result.getAssigneeId());

            assertNotNull(savedTicket);
            assertEquals(workerId, savedTicket.getAssigneeId());
            assertEquals(TicketStatus.PROCESSING, savedTicket.getStatus());
            Long processedLogCount = jdbcTemplate.queryForObject(
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
            String fromStatus = jdbcTemplate.queryForObject(
                    "SELECT from_status FROM ticket_log WHERE ticket_id=?",
                    String.class, ticketId);

            String toStatus = jdbcTemplate.queryForObject(
                    "SELECT to_status FROM ticket_log WHERE ticket_id=?",
                    String.class, ticketId);

            Long operatorId = jdbcTemplate.queryForObject(
                    "SELECT operator_id FROM ticket_log WHERE ticket_id=?",
                    Long.class, ticketId);

            assertEquals("ASSIGNED", fromStatus);
            assertEquals("PROCESSING", toStatus);
            assertEquals(workerId, operatorId);
            assertEquals(1L,processedLogCount);
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
    void processTicket_withOtherWorker_shouldReturn403(){
        String suffix = String.valueOf(System.nanoTime());

        String requesterUsername = "assign_requester_" + suffix;
        String workerUsername = "assign_worker_" + suffix;
        String otherWorkerUsername = "assign_other_worker_" + suffix;
        String ticketTitle = "assign_ticket_" + suffix;

        Long requesterId = null;
        Long workerId = null;
        Long otherWorkerId=null;
        Long ticketId = null;

        try {
            // TODO 1：分别向 app_user 插入 REQUESTER、WORKER、OTHER WORKER
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
            jdbcTemplate.update(
                    """
                        insert into app_user (username,password_hash,role)
                        values(?,?,?)
                        """,
                    otherWorkerUsername,"test_hash", "WORKER"
            );
            // TODO 2：根据三个 username 查询三个 id
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
            otherWorkerId=jdbcTemplate.queryForObject(
                    """
                            select id from app_user where username=?
                            """,
                    Long.class,
                    otherWorkerUsername
            );
            // TODO 3：直接插入一张工单
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
            // TODO 4：根据 creator_id 和 title 查询 ticketId
            ticketId=jdbcTemplate.queryForObject(
                    """
                            select id from ticket where creator_id=? and title=?
                            """,
                    Long.class,
                    requesterId,
                    ticketTitle
            );
            Long finalOtherWorkerId = otherWorkerId;
            Long finalTicketId = ticketId;
            BusinessException exception=assertThrows(BusinessException.class,()->ticketService.startProcessing(finalTicketId, finalOtherWorkerId));
            assertEquals("You don't have the right to process this ticket.", exception.getMessage());
            assertEquals(HttpStatus.FORBIDDEN,exception.getStatus());
            Ticket ticket=ticketService.selectById(ticketId);
            assertNotNull(ticket);
            assertEquals(workerId,ticket.getAssigneeId());
            assertEquals(TicketStatus.ASSIGNED,ticket.getStatus());
            Long processedLogCount = jdbcTemplate.queryForObject(
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

            assertEquals(0L, processedLogCount);

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

            if (otherWorkerId != null) {
                jdbcTemplate.update(
                        "DELETE FROM app_user WHERE id = ?",
                        otherWorkerId
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
    void processTicket_withNonAssignedTicket_shouldReturn403And409(){
        String suffix = String.valueOf(System.nanoTime());

        String requesterUsername = "assign_requester_" + suffix;
        String workerUsername = "assign_worker_" + suffix;
        String ticketTitle1 = "assign_ticket1_" + suffix;
        String ticketTitle2 = "assign_ticket2_" + suffix;

        Long requesterId = null;
        Long workerId = null;
        Long ticketId1 = null;
        Long ticketId2 = null;

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
                    workerUsername,"test_hash", "WORKER"
            );
            // TODO 2：根据两个 username 查询三个 id
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
            // TODO 3：直接插入一张工单
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
                    VALUES (?, null, ?, ?, ?, ?, ?, ?)
                    """,
                    requesterId,
                    ticketTitle1,
                    "验证管理员分配工单",
                    "测试地点",
                    "NETWORK",
                    "HIGH",
                    "CREATED"
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
                    ticketTitle2,
                    "验证管理员分配工单2",
                    "测试地点2",
                    "NETWORK",
                    "HIGH",
                    "PROCESSING"
            );
            // TODO 4：根据 creator_id 和 title 查询 ticketId
            ticketId1=jdbcTemplate.queryForObject(
                    """
                            select id from ticket where creator_id=? and title=?
                            """,
                    Long.class,
                    requesterId,
                    ticketTitle1
            );
            ticketId2=jdbcTemplate.queryForObject(
                    """
                            select id from ticket where creator_id=? and title=?
                            """,
                    Long.class,
                    requesterId,
                    ticketTitle2
            );
            Long finalTicketId1 = ticketId1;
            Long finalTicketId2 = ticketId2;
            Long finalWorkerId = workerId;
            BusinessException exception1=assertThrows(
                    BusinessException.class,()->ticketService.startProcessing(finalTicketId1, finalWorkerId));
            BusinessException exception2=assertThrows(
                    BusinessException.class,()->ticketService.startProcessing(finalTicketId2, finalWorkerId));
            Ticket savedTicket1 = ticketMapper.selectById(ticketId1);
            Ticket savedTicket2 = ticketMapper.selectById(ticketId2);

            assertEquals("You don't have the right to process this ticket.",exception1.getMessage());
            assertEquals(HttpStatus.FORBIDDEN,exception1.getStatus());
            assertEquals("The status of ticket isn't assigned",exception2.getMessage());
            assertEquals(HttpStatus.CONFLICT,exception2.getStatus());

            assertNotNull(savedTicket1);
            assertNotNull(savedTicket2);
            assertNull(savedTicket1.getAssigneeId());
            assertEquals(workerId, savedTicket2.getAssigneeId());

            assertEquals(TicketStatus.CREATED, savedTicket1.getStatus());
            assertEquals(TicketStatus.PROCESSING, savedTicket2.getStatus());
            Long processedLogCount1 = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM ticket_log
                    WHERE ticket_id = ?
                      AND from_status = 'ASSIGNED'
                      AND to_status = 'PROCESSING'
                    """,
                    Long.class,
                    ticketId1
            );
            Long processedLogCount2 = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM ticket_log
                    WHERE ticket_id = ?
                      AND from_status = 'ASSIGNED'
                      AND to_status = 'PROCESSING'
                    """,
                    Long.class,
                    ticketId2
            );
            assertEquals(0L,processedLogCount1);
            assertEquals(0L,processedLogCount2);
        }finally {
            if (ticketId1 != null) {
                jdbcTemplate.update("DELETE FROM ticket_log WHERE ticket_id=?", ticketId1);
                jdbcTemplate.update("DELETE FROM ticket WHERE id=?", ticketId1);
            }

            if (ticketId2 != null) {
                jdbcTemplate.update("DELETE FROM ticket_log WHERE ticket_id=?", ticketId2);
                jdbcTemplate.update("DELETE FROM ticket WHERE id=?", ticketId2);
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
    void resolveTicket_shouldUpdateStatus(){
        String suffix = String.valueOf(System.nanoTime());

        String requesterUsername = "assign_requester_" + suffix;
        String workerUsername = "assign_worker_" + suffix;
        String ticketTitle = "assign_ticket_" + suffix;

        Long requesterId = null;
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
                    workerUsername,"test_hash", "WORKER"
            );
            // TODO 2：根据两个 username 查询三个 id
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
            // TODO 3：直接插入一张工单
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
            // TODO 4：根据 creator_id 和 title 查询 ticketId
            ticketId=jdbcTemplate.queryForObject(
                    """
                            select id from ticket where creator_id=? and title=?
                            """,
                    Long.class,
                    requesterId,
                    ticketTitle
            );
            TicketResolveRequest ticketResolveRequest=new TicketResolveRequest();
            ticketResolveRequest.setResolutionNote("测试，出现在数据库中,但不应该留下");
            Ticket result=ticketService.resolveTicket(ticketId,workerId,ticketResolveRequest);
            Ticket savedTicket = ticketMapper.selectById(ticketId);

            assertNotNull(result);
            assertEquals(TicketStatus.RESOLVED,result.getStatus());
            assertEquals(workerId, result.getAssigneeId());

            assertNotNull(savedTicket);
            assertEquals(workerId, savedTicket.getAssigneeId());
            assertEquals(TicketStatus.RESOLVED, savedTicket.getStatus());
            Long resolvedLogCount = jdbcTemplate.queryForObject(
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
            String fromStatus = jdbcTemplate.queryForObject(
                    "SELECT from_status FROM ticket_log WHERE ticket_id=?",
                    String.class, ticketId);

            String toStatus = jdbcTemplate.queryForObject(
                    "SELECT to_status FROM ticket_log WHERE ticket_id=?",
                    String.class, ticketId);

            Long operatorId = jdbcTemplate.queryForObject(
                    "SELECT operator_id FROM ticket_log WHERE ticket_id=?",
                    Long.class, ticketId);

            String note = jdbcTemplate.queryForObject(
                    "SELECT note FROM ticket_log WHERE ticket_id=?",
                    String.class, ticketId);
            assertEquals("PROCESSING", fromStatus);
            assertEquals("RESOLVED", toStatus);
            assertEquals(ticketResolveRequest.getResolutionNote(),note);
            assertEquals(workerId, operatorId);
            assertEquals(1L,resolvedLogCount);
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
    void resolveTicket_withOtherWorker_shouldReturn403(){
        String suffix = String.valueOf(System.nanoTime());

        String requesterUsername = "assign_requester_" + suffix;
        String workerUsername = "assign_worker_" + suffix;
        String otherWorkerUsername = "assign_other_worker_" + suffix;
        String ticketTitle = "assign_ticket_" + suffix;

        Long requesterId = null;
        Long workerId = null;
        Long otherWorkerId=null;
        Long ticketId = null;

        try {
            // TODO 1：分别向 app_user 插入 REQUESTER、WORKER、OTHER WORKER
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
            jdbcTemplate.update(
                    """
                        insert into app_user (username,password_hash,role)
                        values(?,?,?)
                        """,
                    otherWorkerUsername,"test_hash", "WORKER"
            );
            // TODO 2：根据三个 username 查询三个 id
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
            otherWorkerId=jdbcTemplate.queryForObject(
                    """
                            select id from app_user where username=?
                            """,
                    Long.class,
                    otherWorkerUsername
            );
            // TODO 3：直接插入一张工单
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
            // TODO 4：根据 creator_id 和 title 查询 ticketId
            ticketId=jdbcTemplate.queryForObject(
                    """
                            select id from ticket where creator_id=? and title=?
                            """,
                    Long.class,
                    requesterId,
                    ticketTitle
            );
            Long finalOtherWorkerId = otherWorkerId;
            Long finalTicketId = ticketId;
            TicketResolveRequest ticketResolveRequest=new TicketResolveRequest();
            ticketResolveRequest.setResolutionNote("测试，不应该出现在数据库中");
            BusinessException exception=assertThrows(
                    BusinessException.class,()->ticketService.resolveTicket(finalTicketId,finalOtherWorkerId,ticketResolveRequest)
            );
            assertEquals("You don't have the right to resolve this ticket.", exception.getMessage());
            assertEquals(HttpStatus.FORBIDDEN,exception.getStatus());
            Ticket ticket=ticketService.selectById(ticketId);
            assertNotNull(ticket);
            assertEquals(workerId,ticket.getAssigneeId());
            assertEquals(TicketStatus.PROCESSING,ticket.getStatus());
            Long resolvedLogCount = jdbcTemplate.queryForObject(
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

            assertEquals(0L, resolvedLogCount);

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

            if (otherWorkerId != null) {
                jdbcTemplate.update(
                        "DELETE FROM app_user WHERE id = ?",
                        otherWorkerId
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
    void resolveTicket_withNonProcessingTicket_shouldReturn409(){
        String suffix = String.valueOf(System.nanoTime());

        String requesterUsername = "assign_requester_" + suffix;
        String workerUsername = "assign_worker_" + suffix;
        String ticketTitle1 = "assign_ticket1_" + suffix;
        String ticketTitle2 = "assign_ticket2_" + suffix;

        Long requesterId = null;
        Long workerId = null;
        Long ticketId1 = null;
        Long ticketId2 = null;

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
                    workerUsername,"test_hash", "WORKER"
            );
            // TODO 2：根据两个 username 查询三个 id
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
            // TODO 3：直接插入一张工单
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
                    ticketTitle1,
                    "验证管理员分配工单",
                    "测试地点",
                    "NETWORK",
                    "HIGH",
                    "ASSIGNED"
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
                    ticketTitle2,
                    "验证管理员分配工单2",
                    "测试地点2",
                    "NETWORK",
                    "HIGH",
                    "RESOLVED"
            );
            // TODO 4：根据 creator_id 和 title 查询 ticketId
            ticketId1=jdbcTemplate.queryForObject(
                    """
                            select id from ticket where creator_id=? and title=?
                            """,
                    Long.class,
                    requesterId,
                    ticketTitle1
            );
            ticketId2=jdbcTemplate.queryForObject(
                    """
                            select id from ticket where creator_id=? and title=?
                            """,
                    Long.class,
                    requesterId,
                    ticketTitle2
            );
            Long finalTicketId1 = ticketId1;
            Long finalTicketId2 = ticketId2;
            Long finalWorkerId = workerId;
            TicketResolveRequest ticketResolveRequest=new TicketResolveRequest();
            ticketResolveRequest.setResolutionNote("测试，不应该出现在数据库中");
            BusinessException exception1=assertThrows(
                    BusinessException.class,()->ticketService.resolveTicket(finalTicketId1, finalWorkerId,ticketResolveRequest));
            BusinessException exception2=assertThrows(
                    BusinessException.class,()->ticketService.resolveTicket(finalTicketId2, finalWorkerId,ticketResolveRequest));
            Ticket savedTicket1 = ticketMapper.selectById(ticketId1);
            Ticket savedTicket2 = ticketMapper.selectById(ticketId2);

            assertEquals("The ticket didn't start processing or has been resolved",exception1.getMessage());
            assertEquals(HttpStatus.CONFLICT,exception1.getStatus());
            assertEquals("The ticket didn't start processing or has been resolved",exception2.getMessage());
            assertEquals(HttpStatus.CONFLICT,exception2.getStatus());

            assertNotNull(savedTicket1);
            assertNotNull(savedTicket2);
            assertEquals(workerId, savedTicket1.getAssigneeId());
            assertEquals(workerId, savedTicket2.getAssigneeId());

            assertEquals(TicketStatus.ASSIGNED, savedTicket1.getStatus());
            assertEquals(TicketStatus.RESOLVED, savedTicket2.getStatus());
            Long resolvedLogCount1 = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM ticket_log
                    WHERE ticket_id = ?
                      AND from_status = 'PROCESSING'
                      AND to_status = 'RESOLVED'
                    """,
                    Long.class,
                    ticketId1
            );
            Long resolvedLogCount2 = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM ticket_log
                    WHERE ticket_id = ?
                      AND from_status = 'PROCESSING'
                      AND to_status = 'RESOLVED'
                    """,
                    Long.class,
                    ticketId2
            );
            assertEquals(0L,resolvedLogCount1);
            assertEquals(0L,resolvedLogCount2);
        }finally {
            if (ticketId1 != null) {
                jdbcTemplate.update("DELETE FROM ticket_log WHERE ticket_id=?", ticketId1);
                jdbcTemplate.update("DELETE FROM ticket WHERE id=?", ticketId1);
            }

            if (ticketId2 != null) {
                jdbcTemplate.update("DELETE FROM ticket_log WHERE ticket_id=?", ticketId2);
                jdbcTemplate.update("DELETE FROM ticket WHERE id=?", ticketId2);
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
