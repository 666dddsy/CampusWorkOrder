package com.example.campusworkorder.service;

import com.example.campusworkorder.domain.enums.TicketCategory;
import com.example.campusworkorder.domain.enums.TicketPriority;
import com.example.campusworkorder.domain.enums.TicketStatus;
import com.example.campusworkorder.dto.TicketCreateRequest;
import com.example.campusworkorder.entity.Ticket;
import com.example.campusworkorder.mapper.TicketMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

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
}
