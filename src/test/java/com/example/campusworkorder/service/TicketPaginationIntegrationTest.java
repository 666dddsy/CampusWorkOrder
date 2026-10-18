package com.example.campusworkorder.service;

import com.example.campusworkorder.domain.enums.TicketCategory;
import com.example.campusworkorder.domain.enums.TicketPriority;
import com.example.campusworkorder.domain.enums.TicketStatus;
import com.example.campusworkorder.domain.enums.UserRole;
import com.example.campusworkorder.dto.PageResult;
import com.example.campusworkorder.dto.TicketPageQuery;
import com.example.campusworkorder.entity.Ticket;
import com.example.campusworkorder.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TicketPaginationIntegrationTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void admin_shouldGetCorrectPagesAndStableOrder() {
        Fixture fixture = prepareFixture();

        try {
            TicketPageQuery firstQuery = new TicketPageQuery();
            firstQuery.setPage(1);
            firstQuery.setPageSize(2);

            TicketPageQuery secondQuery = new TicketPageQuery();
            secondQuery.setPage(2);
            secondQuery.setPageSize(2);

            PageResult<Ticket> firstPage =
                    ticketService.selectAdminTickets(
                            fixture.adminId,
                            firstQuery
                    );

            PageResult<Ticket> secondPage =
                    ticketService.selectAdminTickets(
                            fixture.adminId,
                            secondQuery
                    );

            long expectedTotal = fixture.baselineTotal + 5;

            assertEquals(2, firstPage.getItems().size());
            assertEquals(2, secondPage.getItems().size());
            assertEquals(expectedTotal, firstPage.getTotal());
            assertEquals(expectedTotal, secondPage.getTotal());

            assertEquals(1, firstPage.getPage());
            assertEquals(2, secondPage.getPage());
            assertEquals(2, firstPage.getPageSize());
            assertEquals(2, secondPage.getPageSize());

            // 独立查询数据库排序结果，作为测试期望值
            List<Long> expectedIds = jdbcTemplate.query(
                    """
                    SELECT id
                    FROM ticket
                    ORDER BY created_at DESC, id DESC
                    """,
                    (rs, rowNum) -> rs.getLong("id")
            );

            List<Long> firstIds = firstPage.getItems()
                    .stream()
                    .map(Ticket::getId)
                    .toList();

            List<Long> secondIds = secondPage.getItems()
                    .stream()
                    .map(Ticket::getId)
                    .toList();

            assertEquals(expectedIds.subList(0, 2), firstIds);
            assertEquals(expectedIds.subList(2, 4), secondIds);

            // 单独确认本轮相同 created_at 时按 id 降序
            List<Long> fixtureExpectedIds =
                    new ArrayList<>(fixture.ticketIds);

            fixtureExpectedIds.sort(Comparator.reverseOrder());

            List<Long> fixtureActualIds = jdbcTemplate.query(
                    """
                    SELECT id
                    FROM ticket
                    WHERE creator_id = ?
                    ORDER BY created_at DESC, id DESC
                    """,
                    (rs, rowNum) -> rs.getLong("id"),
                    fixture.requesterId
            );

            assertEquals(fixtureExpectedIds, fixtureActualIds);
        } finally {
            cleanup(fixture);
        }
    }

    @Test
    void hugePage_shouldReturnEmptyItemsAndKeepTotal() {
        Fixture fixture = prepareFixture();

        try {
            TicketPageQuery query = new TicketPageQuery();
            query.setPage(Integer.MAX_VALUE);
            query.setPageSize(100);

            PageResult<Ticket> result =
                    ticketService.selectAdminTickets(
                            fixture.adminId,
                            query
                    );

            assertTrue(result.getItems().isEmpty());
            assertEquals(fixture.baselineTotal + 5, result.getTotal());
            assertEquals(Integer.MAX_VALUE, result.getPage());
            assertEquals(100, result.getPageSize());
        } finally {
            cleanup(fixture);
        }
    }

    @Test
    void requesterWorkerAndMissingUser_shouldReturn403() {
        Fixture fixture = prepareFixture();

        try {
            TicketPageQuery query = new TicketPageQuery();

            BusinessException requesterException =
                    assertThrows(
                            BusinessException.class,
                            () -> ticketService.selectAdminTickets(
                                    fixture.requesterId,
                                    query
                            )
                    );

            BusinessException workerException =
                    assertThrows(
                            BusinessException.class,
                            () -> ticketService.selectAdminTickets(
                                    fixture.workerId,
                                    query
                            )
                    );

            BusinessException missingUserException =
                    assertThrows(
                            BusinessException.class,
                            () -> ticketService.selectAdminTickets(
                                    Long.MAX_VALUE,
                                    query
                            )
                    );

            assertEquals(
                    HttpStatus.FORBIDDEN,
                    requesterException.getStatus()
            );

            assertEquals(
                    HttpStatus.FORBIDDEN,
                    workerException.getStatus()
            );

            assertEquals(
                    HttpStatus.FORBIDDEN,
                    missingUserException.getStatus()
            );
        } finally {
            cleanup(fixture);
        }
    }

    @Test
    void admin_withSingleParameter_shouldReturn200(){
        Fixture fixture=prepareFixture();
        try {
            TicketPageQuery query1=new TicketPageQuery();
            TicketPageQuery query2=new TicketPageQuery();
            TicketPageQuery query3=new TicketPageQuery();
            query1.setStatus(TicketStatus.ASSIGNED);
            query2.setCreatorId(fixture.requesterId);
            query3.setAssigneeId(fixture.workerId);
            PageResult<Ticket> page1=ticketService.selectAdminTickets(fixture.adminId,query1);
            PageResult<Ticket> page2=ticketService.selectAdminTickets(fixture.adminId,query2);
            PageResult<Ticket> page3=ticketService.selectAdminTickets(fixture.adminId,query3);
            assertNotNull(page1);
            assertEquals(5, page2.getItems().size());
            assertEquals(5, page3.getItems().size());
            assertEquals(5, page2.getTotal());
            assertEquals(5, page3.getTotal());

            List<Long> expectIds2=jdbcTemplate.query(
                    """
                            SELECT id from ticket
                            WHERE creator_id=?
                            ORDER BY created_at DESC, id DESC
                            """,
                    (rs, rowNum) -> rs.getLong("id"),
                    fixture.requesterId
            );
            List<Long> expectIds3=jdbcTemplate.query(
                    """
                            SELECT id from ticket
                            WHERE assignee_id=?
                            ORDER BY created_at DESC, id DESC
                            """,
                    (rs, rowNum) -> rs.getLong("id"),
                    fixture.workerId
            );
            List<Long> expectIds1=jdbcTemplate.query(
                    """
                            SELECT id from ticket
                            Where status=?
                            ORDER BY created_at DESC, id DESC
                            """,
                    (rs,rowNum)->rs.getLong("id"),
                    "ASSIGNED"
            );
            List<Long> ids1=page1.getItems()
                    .stream()
                    .map(Ticket::getId)
                    .toList();
            List<Long> ids2=page2.getItems()
                            .stream()
                                    .map(Ticket::getId)
                                            .toList();
            List<Long> ids3=page3.getItems()
                            .stream()
                                    .map(Ticket::getId)
                                            .toList();
            int expectedSize = Math.min(query1.getPageSize(), expectIds1.size());

            assertEquals(
                    expectIds1.subList(0, expectedSize),
                    ids1
            );
            assertEquals(expectIds1.size(), page1.getTotal());
            assertEquals(expectIds2,ids2);
            assertEquals(expectIds2.size(), page2.getTotal());
            assertEquals(expectIds3,ids3);
            assertEquals(expectIds3.size(), page3.getTotal());
        }finally{
            cleanup(fixture);
        }
    }

    @Test
    void admin_withCombinedFilters_shouldOnlyReturnTicketsMatchingAllConditions(){
        Fixture fixture=prepareFixture();
        try {
            TicketPageQuery query=new TicketPageQuery();
            query.setStatus(TicketStatus.ASSIGNED);
            query.setCreatorId(fixture.requesterId);
            query.setAssigneeId(fixture.workerId);
            // B. 期望值来自数据库，三个条件都写上（跟目标三一个套路）
            List<Long> expectedIds=jdbcTemplate.query(
                            """
                            SELECT id FROM ticket
                            WHERE creator_id=? AND assignee_id=? AND status=?
                            ORDER BY created_at DESC, id DESC
                            """,
                            (rs,rowNum)->rs.getLong("id"),
                    fixture.requesterId,fixture.workerId,"ASSIGNED"
            );// 先存 5 条夹具
            String suffix=String.valueOf(System.nanoTime());
            fixture.ticketIds.add(insertTicket(fixture.requesterId,fixture.workerId,
                    TicketStatus.CREATED,"combo_status_"+suffix));
            fixture.ticketIds.add(insertTicket(fixture.adminId,fixture.workerId,
                    TicketStatus.ASSIGNED,"combo_creator_"+suffix));
            fixture.ticketIds.add(insertTicket(fixture.requesterId,fixture.adminId,
                    TicketStatus.ASSIGNED,"combo_assignee_"+suffix));
            PageResult<Ticket> result=ticketService.selectAdminTickets(fixture.adminId,query);

            assertEquals(5,result.getItems().size());
            assertEquals(5,result.getTotal());
            assertEquals(expectedIds,
                    result.getItems().stream().map(Ticket::getId).toList());
        } finally {
            cleanup(fixture);
        }
    }

    @Test
    void admin_withCombinedFilters_shouldReturnEmptyItemsAndZeroTotal(){
        Fixture fixture=prepareFixture();
        try {
            // 先确认单个条件有数据，证明空结果是"组合"造成的
            TicketPageQuery byCreator=new TicketPageQuery();
            byCreator.setCreatorId(fixture.requesterId);
            assertEquals(5,ticketService.selectAdminTickets(fixture.adminId,byCreator).getTotal());

            TicketPageQuery query=new TicketPageQuery();
            query.setStatus(TicketStatus.CLOSED);   // 夹具都是 ASSIGNED
            query.setCreatorId(fixture.requesterId);
            query.setAssigneeId(fixture.workerId);
            query.setPage(1);
            query.setPageSize(2);

            PageResult<Ticket> result=ticketService.selectAdminTickets(fixture.adminId,query);

            assertNotNull(result.getItems());
            assertTrue(result.getItems().isEmpty());
            assertEquals(0,result.getTotal());
            assertEquals(1,result.getPage());
            assertEquals(2,result.getPageSize());
        } finally {
            cleanup(fixture);
        }
    }

    @Test
    void admin_filterResultAcrossTwoPages_shouldKeepSameTotalAndNoDuplicate(){
        Fixture fixture=prepareFixture();
        try {
            List<Long> expectedIds=jdbcTemplate.query("""
                SELECT id FROM ticket WHERE creator_id=?
                ORDER BY created_at DESC, id DESC
                """,(rs,rowNum)->rs.getLong("id"),fixture.requesterId);
            assertEquals(5,expectedIds.size());

            TicketPageQuery first=new TicketPageQuery();
            first.setCreatorId(fixture.requesterId);
            first.setPage(1);
            first.setPageSize(2);

            TicketPageQuery second=new TicketPageQuery();
            second.setCreatorId(fixture.requesterId);
            second.setPage(2);
            second.setPageSize(2);

            PageResult<Ticket> page1=ticketService.selectAdminTickets(fixture.adminId,first);
            PageResult<Ticket> page2=ticketService.selectAdminTickets(fixture.adminId,second);

            assertEquals(5,page1.getTotal());
            assertEquals(5,page2.getTotal());
            assertEquals(2,page1.getItems().size());
            assertEquals(2,page2.getItems().size());

            List<Long> ids1=page1.getItems().stream().map(Ticket::getId).toList();
            List<Long> ids2=page2.getItems().stream().map(Ticket::getId).toList();

            assertEquals(expectedIds.subList(0,2),ids1);
            assertEquals(expectedIds.subList(2,4),ids2);

            Set<Long> union=new HashSet<>(ids1);
            union.addAll(ids2);
            assertEquals(4,union.size());   // 两页 4 条互不重复
        } finally {
            cleanup(fixture);
        }
    }

    private Fixture prepareFixture() {
        Fixture fixture = new Fixture();

        fixture.baselineTotal = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket",
                Long.class
        );

        String suffix = String.valueOf(System.nanoTime());

        fixture.requesterId = insertUser(
                "page_requester_" + suffix,
                UserRole.REQUESTER
        );

        fixture.workerId = insertUser(
                "page_worker_" + suffix,
                UserRole.WORKER
        );

        fixture.adminId = insertUser(
                "page_admin_" + suffix,
                UserRole.ADMIN
        );

        LocalDateTime sameCreatedAt =
                LocalDateTime.of(2099, 1, 1, 0, 0);

        for (int i = 1; i <= 5; i++) {
            String title = "pagination_fixture_"
                    + suffix
                    + "_"
                    + i;

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
                        status,
                        created_at,
                        updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    fixture.requesterId,
                    fixture.workerId,
                    title,
                    "分页集成测试工单",
                    "测试地点",
                    TicketCategory.NETWORK.name(),
                    TicketPriority.HIGH.name(),
                    TicketStatus.ASSIGNED.name(),
                    sameCreatedAt,
                    sameCreatedAt
            );

            Long ticketId = jdbcTemplate.queryForObject(
                    """
                    SELECT id
                    FROM ticket
                    WHERE creator_id = ?
                      AND title = ?
                    """,
                    Long.class,
                    fixture.requesterId,
                    title
            );

            fixture.ticketIds.add(ticketId);
        }

        return fixture;
    }

    private Long insertUser(String username, UserRole role) {
        jdbcTemplate.update(
                """
                INSERT INTO app_user(
                    username,
                    password_hash,
                    role
                )
                VALUES (?, ?, ?)
                """,
                username,
                "test_hash",
                role.name()
        );

        return jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM app_user
                WHERE username = ?
                """,
                Long.class,
                username
        );
    }

    private Long insertTicket(Long creatorId,Long assigneeId,TicketStatus status,String title){
        LocalDateTime createdAt=LocalDateTime.of(2099,1,1,0,0);
        jdbcTemplate.update("""
            INSERT INTO ticket(creator_id,assignee_id,title,description,location,
                               category,priority,status,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """,
                creatorId,assigneeId,title,"组合筛选测试工单","测试地点",
                TicketCategory.ELECTRICAL.name(),TicketPriority.LOW.name(),
                status.name(),createdAt,createdAt);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM ticket WHERE title=?",Long.class,title);
    }

    private void cleanup(Fixture fixture) {
        if (fixture == null) {
            return;
        }

        for (Long ticketId : fixture.ticketIds) {
            jdbcTemplate.update(
                    "DELETE FROM ticket_log WHERE ticket_id = ?",
                    ticketId
            );

            jdbcTemplate.update(
                    "DELETE FROM ticket WHERE id = ?",
                    ticketId
            );
        }

        if (fixture.workerId != null) {
            jdbcTemplate.update(
                    "DELETE FROM app_user WHERE id = ?",
                    fixture.workerId
            );
        }

        if (fixture.adminId != null) {
            jdbcTemplate.update(
                    "DELETE FROM app_user WHERE id = ?",
                    fixture.adminId
            );
        }

        if (fixture.requesterId != null) {
            jdbcTemplate.update(
                    "DELETE FROM app_user WHERE id = ?",
                    fixture.requesterId
            );
        }
    }

    private static class Fixture {
        private Long requesterId;
        private Long workerId;
        private Long adminId;
        private long baselineTotal;
        private final List<Long> ticketIds = new ArrayList<>();
    }
}