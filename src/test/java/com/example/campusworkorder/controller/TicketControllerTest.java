package com.example.campusworkorder.controller;

import com.example.campusworkorder.domain.enums.TicketCategory;
import com.example.campusworkorder.domain.enums.TicketPriority;
import com.example.campusworkorder.domain.enums.TicketStatus;
import com.example.campusworkorder.dto.TicketCreateRequest;
import com.example.campusworkorder.entity.Ticket;
import com.example.campusworkorder.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TicketController.class)
public class TicketControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private TicketService ticketService;

    private Ticket exampleTicket() {
        Ticket ticket = new Ticket();
        ticket.setId(101L);
        ticket.setCreatorId(12L);
        ticket.setAssigneeId(null);
        ticket.setTitle("宿舍水管漏水");
        ticket.setDescription("洗手池下方持续漏水");
        ticket.setLocation("滨江宿舍3栋412");
        ticket.setCategory(TicketCategory.PLUMBING);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setStatus(TicketStatus.CREATED);
        ticket.setCreatedAt(LocalDateTime.of(2026, 10, 9, 10, 30));
        ticket.setUpdatedAt(LocalDateTime.of(2026, 10, 9, 10, 30));
        return ticket;
    }

    @Test
    void createTicket_withRightInformation_shouldReturn201()throws Exception{
        Ticket ticket=exampleTicket();
        when(ticketService.createTicket(eq(12L),any(TicketCreateRequest.class)))
                .thenReturn(101L);
        when(ticketService.selectById(eq(101L)))
                .thenReturn(ticket);
        mockMvc.perform(post("/tickets")
                .header("X-User-Id", "12")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "title": "宿舍水管漏水",
                                  "description": "洗手池下方持续漏水",
                                  "location": "滨江宿舍3栋412",
                                  "category": "PLUMBING",
                                  "priority": "MEDIUM"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.creatorId").value(12))
                .andExpect(jsonPath("$.assigneeId").value(nullValue()))
                .andExpect(jsonPath("$.title").value("宿舍水管漏水"))
                .andExpect(jsonPath("$.category").value("PLUMBING"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.createdAt").exists());
        verify(ticketService).createTicket(
                eq(12L),
                any(TicketCreateRequest.class)
        );
        verify(ticketService).selectById(101L);
    }
    @Test
    void createTicket_withBlankTitle_shouldReturn400()throws Exception{
        mockMvc.perform(post("/tickets")
                .header("X-User-Id", "12")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "title": " ",
                                  "description": "洗手池下方持续漏水",
                                  "location": "滨江宿舍3栋412",
                                  "category": "PLUMBING",
                                  "priority": "MEDIUM"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("title must not be blank"));
        verifyNoInteractions(ticketService);
    }
    @Test
    void createTicket_withoutHeader_shouldReturn400()throws Exception{
        mockMvc.perform(post("/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "title": "宿舍水管漏水",
                                  "description": "洗手池下方持续漏水",
                                  "location": "滨江宿舍3栋412",
                                  "category": "PLUMBING",
                                  "priority": "MEDIUM"
                                }
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ticketService);
    }
    @Test
    void createTicket_withWrongCategory_shouldReturn400()throws Exception{
        mockMvc.perform(post("/tickets")
                .header("X-User-Id", "12")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "title": "宿舍水管漏水",
                                  "description": "洗手池下方持续漏水",
                                  "location": "滨江宿舍3栋412",
                                  "category": "TXT",
                                  "priority": "MEDIUM"
                                }
                                """))
                .andExpect(status().isBadRequest()) ;
    verifyNoInteractions(ticketService);
    }
    @Test
    void selectById_withNotNull_shouldReturn200()throws Exception{
        Ticket ticket=exampleTicket();
        when(ticketService.selectById(101L))
                .thenReturn(ticket);
        mockMvc.perform(get("/tickets/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.creatorId").value(12))
                .andExpect(jsonPath("$.assigneeId").value(nullValue()))
                .andExpect(jsonPath("$.title").value("宿舍水管漏水"))
                .andExpect(jsonPath("$.category").value("PLUMBING"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.createdAt").exists());
        verify(ticketService).selectById(101L);
    }
    @Test
    void selectById_withNull_shouldReturn404()throws Exception{
        when(ticketService.selectById(any(Long.class)))
                .thenReturn(null);
        mockMvc.perform(get("/tickets/1"))
                .andExpect(status().isNotFound());
        verify(ticketService).selectById(1L);
    }
    @Test
    void selectMine_withNotNull_shouldReturn200()throws Exception{
        Ticket ticket=exampleTicket();
        List<Ticket> tickets=new ArrayList<>();
        tickets.add(ticket);
        when(ticketService.selectByCreatorId(12L))
                .thenReturn(tickets);
        mockMvc.perform(get("/tickets/mine")
                .header("X-User-Id","12"))
                .andExpect(status().isOk());
        verify(ticketService).selectByCreatorId(12L);
    }
    @Test
    void selectMine_withEmptyList_shouldReturnEmptyArrayList()throws Exception{
        List<Ticket> tickets=new ArrayList<>();
        when(ticketService.selectByCreatorId(any(Long.class)))
                .thenReturn(tickets);
        mockMvc.perform(get("/tickets/mine")
                        .header("X-User-Id","12"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        verify(ticketService).selectByCreatorId(12L);
    }
}
