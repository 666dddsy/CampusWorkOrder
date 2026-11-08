package com.example.campusworkorder.service;

import com.example.campusworkorder.domain.enums.TicketCategory;
import com.example.campusworkorder.domain.enums.TicketPriority;
import com.example.campusworkorder.domain.enums.TicketStatus;
import com.example.campusworkorder.domain.enums.UserRole;
import com.example.campusworkorder.dto.TicketAssignRequest;
import com.example.campusworkorder.dto.TicketCreateRequest;
import com.example.campusworkorder.dto.TicketResolveRequest;
import com.example.campusworkorder.entity.Ticket;
import com.example.campusworkorder.entity.TicketLog;
import com.example.campusworkorder.exception.BusinessException;
import com.example.campusworkorder.mapper.TicketLogMapper;
import com.example.campusworkorder.mapper.TicketMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    private final List<Long> fixtureUserIds = new ArrayList<>();
    private final List<Long> fixtureTicketIds = new ArrayList<>();
    private final String fixtureSuffix = Long.toUnsignedString(System.nanoTime());
    private int fixtureSeq = 0;

    private Long insertUser(UserRole role) {
        String username = "fx_" + role.name().toLowerCase(Locale.ROOT) + "_" + fixtureSuffix + "_" + (fixtureSeq++);
        jdbcTemplate.update(
                "insert into app_user (username, password_hash, role) values (?, ?, ?)",
                username, "test_hash", role.name());
        Long id = jdbcTemplate.queryForObject(
                "select id from app_user where username = ?", Long.class, username);
        fixtureUserIds.add(id);
        return id;
    }

    private Long insertTicket(Long creatorId, Long assigneeId, TicketStatus status) {
        return insertTicket(creatorId, assigneeId, status,
                "fx_ticket_" + fixtureSuffix + "_" + (fixtureSeq++));
    }

    private Long insertTicket(Long creatorId, Long assigneeId, TicketStatus status, String title) {
        jdbcTemplate.update("""
                insert into ticket
                    (creator_id, assignee_id, title, description, location,
                     category, priority, status)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                creatorId, assigneeId, title, "测试描述", "测试地点",
                "NETWORK", "HIGH", status.name());
        Long id = jdbcTemplate.queryForObject(
                "select id from ticket where creator_id = ? and title = ?",
                Long.class, creatorId, title);
        fixtureTicketIds.add(id);
        return id;
    }

    private Long insertTicket(Long creatorId, Long assigneeId, TicketStatus status,
                              String title, LocalDateTime createdAt) {
        jdbcTemplate.update("""
                insert into ticket
                    (creator_id, assignee_id, title, description, location,
                     category, priority, status, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                creatorId, assigneeId, title, "测试描述", "测试地点",
                "NETWORK", "HIGH", status.name(), Timestamp.valueOf(createdAt));
        Long id = jdbcTemplate.queryForObject(
                "select id from ticket where creator_id = ? and title = ?",
                Long.class, creatorId, title);
        fixtureTicketIds.add(id);
        return id;
    }

    private void insertLog(Long ticketId, Long operatorId,
                           TicketStatus from, TicketStatus to, String note) {
        jdbcTemplate.update("""
                insert into ticket_log (ticket_id, operator_id, from_status, to_status, note)
                values (?, ?, ?, ?, ?)
                """,
                ticketId, operatorId,
                from == null ? null : from.name(),
                to.name(),
                note);
    }
    private void insertLog(Long ticketId, Long operatorId,
                           TicketStatus from, TicketStatus to, String note,
                           LocalDateTime createdAt) {
        jdbcTemplate.update("""
            insert into ticket_log (ticket_id, operator_id, from_status, to_status, note, created_at)
            values (?, ?, ?, ?, ?, ?)
            """,
                ticketId, operatorId,
                from == null ? null : from.name(),
                to.name(),
                note,
                Timestamp.valueOf(createdAt));
    }
    @AfterEach
    void cleanFixtures() {
        for (Long id : fixtureTicketIds) {
            jdbcTemplate.update("delete from ticket_log where ticket_id = ?", id);
        }
        for (Long id : fixtureTicketIds) {
            jdbcTemplate.update("delete from ticket where id = ?", id);
        }
        for (Long id : fixtureUserIds) {
            jdbcTemplate.update("delete from app_user where id = ?", id);
        }
        fixtureTicketIds.clear();
        fixtureUserIds.clear();
    }

    @Test
    void selectTicketByIdWithAllRole_shouldSucceed(){
        Long requesterId = insertUser(UserRole.REQUESTER);
        Long adminId = insertUser(UserRole.ADMIN);
        Long workerId = insertUser(UserRole.WORKER);

        String ticketTitle = "assign_ticket_" + fixtureSuffix;
        Long ticketId = insertTicket(requesterId, workerId, TicketStatus.ASSIGNED, ticketTitle);

        Ticket requesterResult=ticketService.selectByIdWithRole(ticketId,requesterId);
        Ticket adminResult=ticketService.selectByIdWithRole(ticketId,adminId);
        Ticket workerResult=ticketService.selectByIdWithRole(ticketId,workerId);
        assertNotNull(requesterResult);
        assertNotNull(adminResult);
        assertNotNull(workerResult);
        assertEquals(ticketTitle,requesterResult.getTitle());
        assertEquals(ticketTitle,adminResult.getTitle());
        assertEquals(ticketTitle,workerResult.getTitle());
    }
    @Test
    void selectTicketByIdWithAllRole_shouldFail(){
        Long requesterId = insertUser(UserRole.REQUESTER);
        Long otherRequesterId = insertUser(UserRole.REQUESTER);
        Long workerId = insertUser(UserRole.WORKER);
        Long otherWorkerId = insertUser(UserRole.WORKER);

        Long ticketId = insertTicket(requesterId, workerId, TicketStatus.ASSIGNED);

        BusinessException exception1=assertThrows(
                BusinessException.class,()->ticketService.selectByIdWithRole(ticketId, otherRequesterId)
        );
        BusinessException exception2=assertThrows(
                BusinessException.class,()->ticketService.selectByIdWithRole(ticketId, otherWorkerId)
        );
        BusinessException exception3=assertThrows(
                BusinessException.class,()->ticketService.selectByIdWithRole(ticketId, 0L)
        );
        BusinessException exception4=assertThrows(
                BusinessException.class,()->ticketService.selectByIdWithRole(0L, 0L)
        );
        assertEquals(HttpStatus.FORBIDDEN,exception1.getStatus());
        assertEquals(HttpStatus.FORBIDDEN,exception2.getStatus());
        assertEquals(HttpStatus.FORBIDDEN,exception3.getStatus());
        assertEquals(HttpStatus.FORBIDDEN,exception4.getStatus());
        assertEquals("You don't have right to get the ticket",exception1.getMessage());
        assertEquals("You don't have right to get the ticket",exception2.getMessage());
        assertEquals("You don't have right to get the ticket",exception3.getMessage());
        assertEquals("You don't have right to get the ticket",exception4.getMessage());
        assertNull(ticketService.selectByIdWithRole(Long.MAX_VALUE, requesterId));
    }

    @Test
    void selectMyAssignedTickets_shouldOnlyReturnCurrentWorkersTickets() {
        Long requesterId = insertUser(UserRole.REQUESTER);
        Long workerAId = insertUser(UserRole.WORKER);
        Long workerBId = insertUser(UserRole.WORKER);

        LocalDateTime baseTime = LocalDateTime.of(2026, 10, 12, 10, 0);
        String aOldTitle = "a_old_" + fixtureSuffix;
        String aNewTitle = "a_new_" + fixtureSuffix;
        String bTitle = "b_ticket_" + fixtureSuffix;

        Long aOldTicketId = insertTicket(requesterId, workerAId, TicketStatus.ASSIGNED, aOldTitle, baseTime);
        Long aNewTicketId = insertTicket(requesterId, workerAId, TicketStatus.ASSIGNED, aNewTitle, baseTime.plusHours(1));
        Long workerBTicketId = insertTicket(requesterId, workerBId, TicketStatus.ASSIGNED, bTitle, baseTime.plusHours(2));

        List<Ticket> result =
                ticketService.selectMyAssignedTickets(workerAId);

        assertEquals(2, result.size());

        assertEquals(workerAId, result.get(0).getAssigneeId());
        assertEquals(workerAId, result.get(1).getAssigneeId());

        assertEquals(aNewTicketId, result.get(0).getId());
        assertEquals(aOldTicketId, result.get(1).getId());

        assertNotEquals(workerBTicketId, result.get(0).getId());
        assertNotEquals(workerBTicketId, result.get(1).getId());
    }

    @Test
    void selectTicketLogsByTicketId_withAllAllowedRoles_shouldReturnLogs(){
        Long requesterId = insertUser(UserRole.REQUESTER);
        Long adminId = insertUser(UserRole.ADMIN);
        Long workerId = insertUser(UserRole.WORKER);

        Long ticketId = insertTicket(requesterId, workerId, TicketStatus.ASSIGNED);
        insertLog(ticketId, requesterId, null, TicketStatus.CREATED, "测试");
        insertLog(ticketId, adminId, TicketStatus.CREATED, TicketStatus.ASSIGNED, "测试");

        List<TicketLog> adminView=ticketService.selectLogsByTicketId(ticketId,adminId);
        List<TicketLog> currentRequesterView=ticketService.selectLogsByTicketId(ticketId,requesterId);
        List<TicketLog> currentWorkerView=ticketService.selectLogsByTicketId(ticketId,workerId);

        for (List<TicketLog> view : List.of(adminView, currentRequesterView, currentWorkerView)) {
            assertEquals(2, view.size());
            assertEquals(TicketStatus.ASSIGNED, view.get(0).getToStatus());
            assertEquals(TicketStatus.CREATED, view.get(1).getToStatus());
            assertNull(view.get(1).getFromStatus());
        }

        List<Long> adminIds = adminView.stream().map(TicketLog::getId).toList();
        assertEquals(adminIds, currentRequesterView.stream().map(TicketLog::getId).toList());
        assertEquals(adminIds, currentWorkerView.stream().map(TicketLog::getId).toList());
    }
    @Test
    void selectTicketLogsByTicketId_withNotAllowedRoles_shouldReturn403(){
        Long requesterId = insertUser(UserRole.REQUESTER);
        Long otherRequesterId = insertUser(UserRole.REQUESTER);
        Long adminId = insertUser(UserRole.ADMIN);
        Long workerId = insertUser(UserRole.WORKER);
        Long otherWorkerId = insertUser(UserRole.WORKER);

        Long ticketId = insertTicket(requesterId, workerId, TicketStatus.ASSIGNED);
        insertLog(ticketId, requesterId, null, TicketStatus.CREATED, "测试");
        insertLog(ticketId, adminId, TicketStatus.CREATED, TicketStatus.ASSIGNED, "测试");

        BusinessException wrongRequesterEx=assertThrows(
                BusinessException.class,()->ticketService.selectLogsByTicketId(ticketId, otherRequesterId));
        BusinessException wrongWorkerEx=assertThrows(
                BusinessException.class,()->ticketService.selectLogsByTicketId(ticketId, otherWorkerId));
        BusinessException missingUserEx=assertThrows(
                BusinessException.class,()->ticketService.selectLogsByTicketId(ticketId, 0L));
        List<TicketLog> missingTicketView= ticketService.selectLogsByTicketId(0L, adminId);

        assertEquals("You don't have right to get the ticketLog",wrongRequesterEx.getMessage());
        assertEquals("You don't have right to get the ticketLog",wrongWorkerEx.getMessage());
        assertEquals("You don't have right to get the ticketLog",missingUserEx.getMessage());
        assertNull(missingTicketView);
        assertEquals(HttpStatus.FORBIDDEN, wrongRequesterEx.getStatus());
        assertEquals(HttpStatus.FORBIDDEN, wrongWorkerEx.getStatus());
        assertEquals(HttpStatus.FORBIDDEN, missingUserEx.getStatus());
    }
    @Test
    void selectTicketLogsByTicketId_withEmptyLogs(){
        Long requesterId=insertUser(UserRole.REQUESTER);
        Long adminId=insertUser(UserRole.ADMIN);
        Long ticketId=insertTicket(requesterId,null,TicketStatus.CREATED);
        List<TicketLog> logs = ticketService.selectLogsByTicketId(ticketId, adminId);
        assertNotNull(logs);
        assertTrue(logs.isEmpty());
    }
    @Test
    void selectTicketLogsByTicketId_sameSecond_shouldOrderByIdDesc(){
        Long requesterId = insertUser(UserRole.REQUESTER);
        Long adminId = insertUser(UserRole.ADMIN);

        Long ticketId = insertTicket(requesterId, null, TicketStatus.CREATED);

        LocalDateTime sameSecond = LocalDateTime.of(2026, 11, 8, 10, 0, 0);
        insertLog(ticketId, requesterId, null, TicketStatus.CREATED, "older", sameSecond);
        insertLog(ticketId, adminId, TicketStatus.CREATED, TicketStatus.ASSIGNED, "newer", sameSecond);

        List<TicketLog> logs = ticketService.selectLogsByTicketId(ticketId, adminId);

        assertEquals(2, logs.size());
        assertEquals("newer", logs.get(0).getNote());
        assertEquals("older", logs.get(1).getNote());
    }

    @Test
    void createTicket_shouldInsertTicketAndLog(){
        Long creatorId = insertUser(UserRole.REQUESTER);

        TicketCreateRequest request=new TicketCreateRequest();
        request.setTitle("集成测试工单");
        request.setDescription("验证创建工单和日志是否同时写入");
        request.setLocation("测试地点");
        request.setCategory(TicketCategory.NETWORK);
        request.setPriority(TicketPriority.HIGH);
        Long ticketId= ticketService.createTicket(creatorId,request);
        fixtureTicketIds.add(ticketId);
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
    }
    @Test
    void createTicket_withNonRequester_shouldReturn403AndInsertNothing(){
        Long adminId = insertUser(UserRole.ADMIN);
        Long workerId = insertUser(UserRole.WORKER);

        int ticketBeforeCount=jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(created_at) from ticket
                        """,
                int.class
        );
        int ticketLogBeforeCount=jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(created_at) from ticket_log
                        """,
                int.class
        );
        TicketCreateRequest request=new TicketCreateRequest();
        request.setTitle("集成测试工单");
        request.setDescription("验证创建工单和日志是否同时写入");
        request.setLocation("测试地点");
        request.setCategory(TicketCategory.NETWORK);
        request.setPriority(TicketPriority.HIGH);
        BusinessException exception1=assertThrows(BusinessException.class,()->ticketService.createTicket(adminId,request));
        BusinessException exception2=assertThrows(BusinessException.class,()->ticketService.createTicket(workerId,request));
        BusinessException exception3=assertThrows(BusinessException.class,()->ticketService.createTicket(0L,request));
        int ticketAfterCount=jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) from ticket
                        """,
                int.class
        );
        int ticketLogAfterCount=jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) from ticket_log
                        """,
                int.class
        );
        assertEquals(HttpStatus.FORBIDDEN,exception1.getStatus());
        assertEquals(HttpStatus.FORBIDDEN,exception2.getStatus());
        assertEquals(HttpStatus.FORBIDDEN,exception3.getStatus());
        assertEquals("You are not Requester",exception1.getMessage());
        assertEquals("You are not Requester",exception2.getMessage());
        assertEquals("You are not Requester",exception3.getMessage());
        assertEquals(ticketBeforeCount,ticketAfterCount);
        assertEquals(ticketLogBeforeCount,ticketLogAfterCount);
    }
    @Test
    void assignTicket_shouldUpdateStatusAndAssignee(){
        Long requesterId = insertUser(UserRole.REQUESTER);
        Long adminId = insertUser(UserRole.ADMIN);
        Long workerId = insertUser(UserRole.WORKER);

        Long ticketId = insertTicket(requesterId, null, TicketStatus.CREATED);

        TicketAssignRequest request = new TicketAssignRequest();
        request.setAssigneeId(workerId);

        Ticket result = ticketService.assignTicket(ticketId, adminId, request);
        Ticket savedTicket = ticketMapper.selectById(ticketId);
        assertNotNull(result);
        assertEquals(TicketStatus.ASSIGNED,result.getStatus());
        assertEquals(workerId,result.getAssigneeId());

        assertNotNull(savedTicket);
        assertEquals(TicketStatus.ASSIGNED, savedTicket.getStatus());
        assertEquals(workerId, savedTicket.getAssigneeId());
    }
    @Test
    void assignTicket_shouldRejectRepeatedAssignment(){
        Long requesterId = insertUser(UserRole.REQUESTER);
        Long adminId = insertUser(UserRole.ADMIN);
        Long workerId = insertUser(UserRole.WORKER);

        Long ticketId = insertTicket(requesterId, null, TicketStatus.CREATED);

        TicketAssignRequest request = new TicketAssignRequest();
        request.setAssigneeId(workerId);
        ticketService.assignTicket(ticketId, adminId, request);
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> ticketService.assignTicket(ticketId, adminId, request)
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
    }
    @Test
    void processTicket_shouldUpdateStatus(){
        Long requesterId = insertUser(UserRole.REQUESTER);
        Long workerId = insertUser(UserRole.WORKER);

        Long ticketId = insertTicket(requesterId, workerId, TicketStatus.ASSIGNED);

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
    }
    @Test
    void processTicket_withOtherWorker_shouldReturn403(){
        Long requesterId = insertUser(UserRole.REQUESTER);
        Long workerId = insertUser(UserRole.WORKER);
        Long otherWorkerId = insertUser(UserRole.WORKER);

        Long ticketId = insertTicket(requesterId, workerId, TicketStatus.ASSIGNED);

        BusinessException exception=assertThrows(BusinessException.class,()->ticketService.startProcessing(ticketId, otherWorkerId));
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
    }
    @Test
    void processTicket_withNonAssignedTicket_shouldReturn403And409(){
        Long requesterId = insertUser(UserRole.REQUESTER);
        Long workerId = insertUser(UserRole.WORKER);

        Long ticketId1 = insertTicket(requesterId, null, TicketStatus.CREATED);
        Long ticketId2 = insertTicket(requesterId, workerId, TicketStatus.PROCESSING);

        BusinessException exception1=assertThrows(
                BusinessException.class,()->ticketService.startProcessing(ticketId1, workerId));
        BusinessException exception2=assertThrows(
                BusinessException.class,()->ticketService.startProcessing(ticketId2, workerId));
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
    }
    @Test
    void resolveTicket_shouldUpdateStatus(){
        Long requesterId = insertUser(UserRole.REQUESTER);
        Long workerId = insertUser(UserRole.WORKER);

        Long ticketId = insertTicket(requesterId, workerId, TicketStatus.PROCESSING);

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
    }
    @Test
    void resolveTicket_withOtherWorker_shouldReturn403(){
        Long requesterId = insertUser(UserRole.REQUESTER);
        Long workerId = insertUser(UserRole.WORKER);
        Long otherWorkerId = insertUser(UserRole.WORKER);

        Long ticketId = insertTicket(requesterId, workerId, TicketStatus.PROCESSING);

        TicketResolveRequest ticketResolveRequest=new TicketResolveRequest();
        ticketResolveRequest.setResolutionNote("测试，不应该出现在数据库中");
        BusinessException exception=assertThrows(
                BusinessException.class,()->ticketService.resolveTicket(ticketId,otherWorkerId,ticketResolveRequest)
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
    }
    @Test
    void resolveTicket_withNonProcessingTicket_shouldReturn409(){
        Long requesterId = insertUser(UserRole.REQUESTER);
        Long workerId = insertUser(UserRole.WORKER);

        Long ticketId1 = insertTicket(requesterId, workerId, TicketStatus.ASSIGNED);
        Long ticketId2 = insertTicket(requesterId, workerId, TicketStatus.RESOLVED);

        TicketResolveRequest ticketResolveRequest=new TicketResolveRequest();
        ticketResolveRequest.setResolutionNote("测试，不应该出现在数据库中");
        BusinessException exception1=assertThrows(
                BusinessException.class,()->ticketService.resolveTicket(ticketId1, workerId,ticketResolveRequest));
        BusinessException exception2=assertThrows(
                BusinessException.class,()->ticketService.resolveTicket(ticketId2, workerId,ticketResolveRequest));
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
    }
    @Test
    void closeTicket_shouldUpdateStatusAndInsertLog(){
        Long requesterId = insertUser(UserRole.REQUESTER);
        Long workerId = insertUser(UserRole.WORKER);

        Long ticketId = insertTicket(requesterId, workerId, TicketStatus.RESOLVED);

        Ticket result = ticketService.closeTicket(ticketId,requesterId);
        Ticket savedTicket = ticketMapper.selectById(ticketId);

        assertNotNull(result);
        assertEquals(TicketStatus.CLOSED,result.getStatus());
        assertEquals(requesterId,result.getCreatorId());
        assertEquals(workerId,result.getAssigneeId());

        assertNotNull(savedTicket);
        assertEquals(TicketStatus.CLOSED,savedTicket.getStatus());
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

        Long operatorId = jdbcTemplate.queryForObject(
                """
                SELECT operator_id
                FROM ticket_log
                WHERE ticket_id=?
                  AND to_status='CLOSED'
                """,
                Long.class,
                ticketId
        );

        String note = jdbcTemplate.queryForObject(
                """
                SELECT note
                FROM ticket_log
                WHERE ticket_id=?
                  AND to_status='CLOSED'
                """,
                String.class,
                ticketId
        );

        assertEquals(1L,logCount);
        assertEquals(requesterId,operatorId);
        assertEquals("报修人确认问题已解决并关闭工单",note);
    }
    @Test
    void closeTicket_withWrongRole_shouldReturn403(){
        Long requesterId = insertUser(UserRole.REQUESTER);
        Long workerId = insertUser(UserRole.WORKER);
        Long adminId = insertUser(UserRole.ADMIN);

        Long ticketId = insertTicket(requesterId, workerId, TicketStatus.RESOLVED);

        BusinessException workerException = assertThrows(
                BusinessException.class,
                () -> ticketService.closeTicket(
                        ticketId,
                        workerId
                )
        );

        BusinessException adminException = assertThrows(
                BusinessException.class,
                () -> ticketService.closeTicket(
                        ticketId,
                        adminId
                )
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                workerException.getStatus()
        );
        assertEquals(
                "You don't have the right to close this ticket.",
                workerException.getMessage()
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                adminException.getStatus()
        );
        assertEquals(
                "You don't have the right to close this ticket.",
                adminException.getMessage()
        );

        Ticket savedTicket = ticketService.selectById(ticketId);

        assertNotNull(savedTicket);
        assertEquals(
                TicketStatus.RESOLVED,
                savedTicket.getStatus()
        );
        assertEquals(
                requesterId,
                savedTicket.getCreatorId()
        );
        assertEquals(
                workerId,
                savedTicket.getAssigneeId()
        );

        Long closedLogCount = jdbcTemplate.queryForObject(
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

        assertEquals(0L, closedLogCount);
    }
    @Test
    void closeTicket_withOtherRequester_shouldReturn403(){
        Long requesterId = insertUser(UserRole.REQUESTER);
        Long otherRequesterId = insertUser(UserRole.REQUESTER);
        Long workerId = insertUser(UserRole.WORKER);

        Long ticketId = insertTicket(requesterId, workerId, TicketStatus.RESOLVED);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.closeTicket(
                        ticketId,
                        otherRequesterId
                )
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatus()
        );
        assertEquals(
                "You don't have the right to close this ticket.",
                exception.getMessage()
        );

        Ticket savedTicket = ticketService.selectById(ticketId);

        assertNotNull(savedTicket);
        assertEquals(
                TicketStatus.RESOLVED,
                savedTicket.getStatus()
        );
        assertEquals(
                requesterId,
                savedTicket.getCreatorId()
        );
        assertEquals(
                workerId,
                savedTicket.getAssigneeId()
        );

        Long closedLogCount = jdbcTemplate.queryForObject(
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

        assertEquals(0L, closedLogCount);
    }
    @Test
    void closeTicket_withNonResolvedTicket_shouldReturn409(){
        Long requesterId = insertUser(UserRole.REQUESTER);
        Long workerId = insertUser(UserRole.WORKER);

        Long processingTicketId = insertTicket(requesterId, workerId, TicketStatus.PROCESSING);
        Long closedTicketId = insertTicket(requesterId, workerId, TicketStatus.CLOSED);

        BusinessException processingException = assertThrows(
                BusinessException.class,
                () -> ticketService.closeTicket(
                        processingTicketId,
                        requesterId
                )
        );

        BusinessException closedException = assertThrows(
                BusinessException.class,
                () -> ticketService.closeTicket(
                        closedTicketId,
                        requesterId
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                processingException.getStatus()
        );
        assertEquals(
                "The ticket is not resolved or has already been closed.",
                processingException.getMessage()
        );

        assertEquals(
                HttpStatus.CONFLICT,
                closedException.getStatus()
        );
        assertEquals(
                "The ticket is not resolved or has already been closed.",
                closedException.getMessage()
        );

        Ticket processingTicket =
                ticketService.selectById(processingTicketId);
        Ticket closedTicket =
                ticketService.selectById(closedTicketId);

        assertNotNull(processingTicket);
        assertNotNull(closedTicket);

        assertEquals(
                TicketStatus.PROCESSING,
                processingTicket.getStatus()
        );
        assertEquals(
                TicketStatus.CLOSED,
                closedTicket.getStatus()
        );

        assertEquals(
                requesterId,
                processingTicket.getCreatorId()
        );
        assertEquals(
                requesterId,
                closedTicket.getCreatorId()
        );
        assertEquals(
                workerId,
                processingTicket.getAssigneeId()
        );
        assertEquals(
                workerId,
                closedTicket.getAssigneeId()
        );

        Long closedLogCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ticket_log
                WHERE ticket_id IN (?,?)
                  AND from_status='RESOLVED'
                  AND to_status='CLOSED'
                """,
                Long.class,
                processingTicketId,
                closedTicketId
        );

        assertEquals(0L, closedLogCount);
    }
}
