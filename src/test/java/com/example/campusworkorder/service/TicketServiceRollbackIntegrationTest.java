package com.example.campusworkorder.service;

import com.example.campusworkorder.domain.enums.TicketCategory;
import com.example.campusworkorder.domain.enums.TicketPriority;
import com.example.campusworkorder.dto.TicketCreateRequest;
import com.example.campusworkorder.entity.TicketLog;
import com.example.campusworkorder.mapper.TicketLogMapper;
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
                    "SELECT COUNT(*) FROM ticket WHERE title = ?",
                    Long.class,
                    request.getTitle()
            );

            assertEquals(0L, ticketCount);
        }finally {
            if(creatorId!=null) {
                jdbcTemplate.update(
                        "delete from app_user where id=?",
                        creatorId
                );
            }
        }
    }
}
