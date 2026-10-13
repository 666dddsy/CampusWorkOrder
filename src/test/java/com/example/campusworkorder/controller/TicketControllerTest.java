package com.example.campusworkorder.controller;

import com.example.campusworkorder.domain.enums.TicketCategory;
import com.example.campusworkorder.domain.enums.TicketPriority;
import com.example.campusworkorder.domain.enums.TicketStatus;
import com.example.campusworkorder.dto.TicketAssignRequest;
import com.example.campusworkorder.dto.TicketCreateRequest;
import com.example.campusworkorder.entity.Ticket;
import com.example.campusworkorder.exception.BusinessException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
    private Ticket exampleTicket2() {
        Ticket ticket = new Ticket();
        ticket.setId(99L);
        ticket.setCreatorId(12L);
        ticket.setAssigneeId(null);
        ticket.setTitle("漏水");
        ticket.setDescription("厕所持续漏水");
        ticket.setLocation("滨江宿舍3栋412");
        ticket.setCategory(TicketCategory.PLUMBING);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setStatus(TicketStatus.CREATED);
        ticket.setCreatedAt(LocalDateTime.of(2025, 10, 9, 10, 30));
        ticket.setUpdatedAt(LocalDateTime.of(2025, 10, 9, 10, 30));
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
        Ticket ticket2=exampleTicket2();
        List<Ticket> tickets=new ArrayList<>();
        tickets.add(ticket);
        tickets.add(ticket2);
        when(ticketService.selectByCreatorId(12L))
                .thenReturn(tickets);
        mockMvc.perform(get("/tickets/mine")
                .header("X-User-Id","12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].creatorId").value(12))
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[1].creatorId").value(12))
                .andExpect(jsonPath("$[1].id").value(99));
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
    @Test
    void assignTicket_shouldReturn200AndAssignedTicket()throws Exception{
        Ticket ticket=exampleTicket();
        ticket.setStatus(TicketStatus.ASSIGNED);
        ticket.setAssigneeId(3L);
        when(ticketService.assignTicket(eq(101L),eq(4L),any(TicketAssignRequest.class)))
                .thenReturn(ticket);
        mockMvc.perform(patch("/tickets/101/assignment")
                .header("X-User-Id","4")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "assigneeId":3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.creatorId").value(12))
                .andExpect(jsonPath("$.assigneeId").value(3))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }
    @Test
    void assignTicket_withNullAssigneeId_shouldReturn400()throws Exception{
        Ticket ticket=exampleTicket();
        ticket.setStatus(TicketStatus.ASSIGNED);
        ticket.setAssigneeId(3L);
        when(ticketService.assignTicket(eq(101L),eq(4L),any(TicketAssignRequest.class)))
                .thenReturn(ticket);
        mockMvc.perform(patch("/tickets/101/assignment")
                .header("X-User-Id","4")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "assigneeId":null
                                }
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ticketService);
    }
    @Test
    void assignTicket_withNoCreatedTicket_shouldReturn409AndMessage()throws Exception{
        Ticket ticket=exampleTicket();
        ticket.setStatus(TicketStatus.ASSIGNED);
        ticket.setAssigneeId(3L);
        when(ticketService.assignTicket(eq(101L),eq(4L),any(TicketAssignRequest.class)))
                .thenThrow(new BusinessException(HttpStatus.CONFLICT,"The ticket has been operated"));
        mockMvc.perform(patch("/tickets/101/assignment")
                .header("X-User-Id","4")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "assigneeId":3
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The ticket has been operated"));
    }
    @Test
    void assignTicket_withNonAdminOperator_shouldReturn403AndMessage()throws Exception{
        Ticket ticket=exampleTicket();
        ticket.setStatus(TicketStatus.ASSIGNED);
        ticket.setAssigneeId(3L);
        when(ticketService.assignTicket(eq(101L),eq(1L),any(TicketAssignRequest.class)))
                .thenThrow(new BusinessException(HttpStatus.FORBIDDEN,"OperatorRole should be 'ADMIN'"));
        mockMvc.perform(patch("/tickets/101/assignment")
                        .header("X-User-Id","1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assigneeId":3
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("OperatorRole should be 'ADMIN'"));
    }
    @Test
    void selectAssignedTickets_withNotNull_shouldReturn200()throws Exception{
        Ticket ticket1=exampleTicket();
        Ticket ticket2=exampleTicket2();
        ticket1.setAssigneeId(3L);
        ticket1.setStatus(TicketStatus.ASSIGNED);
        ticket2.setAssigneeId(3L);
        ticket2.setStatus(TicketStatus.ASSIGNED);
        List<Ticket> tickets=new ArrayList<>();
        tickets.add(ticket1);
        tickets.add(ticket2);
        when(ticketService.selectMyAssignedTickets(eq(3L)))
                .thenReturn(tickets);
        mockMvc.perform(get("/tickets/assigned-to-me")
                .header("X-User-Id","3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].assigneeId").value(3))
                .andExpect(jsonPath("$[0].title").value(ticket1.getTitle()))
                .andExpect(jsonPath("$[1].id").value(99))
                .andExpect(jsonPath("$[1].assigneeId").value(3))
                .andExpect(jsonPath("$[1].title").value(ticket2.getTitle()));
        verify(ticketService).selectMyAssignedTickets(3L);
    }
    @Test
    void selectAssignedTickets_withEmptyList_shouldReturnEmptyArrayList()throws Exception{
        List<Ticket> tickets=new ArrayList<>();
        when(ticketService.selectMyAssignedTickets(eq(3L)))
                .thenReturn(tickets);
        mockMvc.perform(get("/tickets/assigned-to-me")
                        .header("X-User-Id","3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0))
                .andExpect(content().json("[]"));
        verify(ticketService).selectMyAssignedTickets(3L);
    }
    @Test
    void selectAssignedTickets_withWrongUserRole_shouldReturn403AndMessage()throws Exception{
        when(ticketService.selectMyAssignedTickets(any(Long.class)))
                .thenThrow(new BusinessException(HttpStatus.FORBIDDEN,"UserRole should be 'WORKER'"));
        mockMvc.perform(get("/tickets/assigned-to-me")
                .header("X-User-Id","3"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("UserRole should be 'WORKER'"));
        verify(ticketService).selectMyAssignedTickets(3L);
    }
    @Test
    void processTicket_shouldReturn200()throws Exception{
        Ticket ticket=exampleTicket();
        ticket.setAssigneeId(3L);
        ticket.setStatus(TicketStatus.PROCESSING);
        when(ticketService.startProcessing(eq(ticket.getId()),eq(3L)))
                .thenReturn(ticket);
        mockMvc.perform(patch("/tickets/101/processing")
                .header("X-User-Id","3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.creatorId").value(12))
                .andExpect(jsonPath("$.assigneeId").value(3))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
        verify(ticketService).startProcessing(101L, 3L);
    }
    @Test
    void processTicket_shouldReturn404()throws Exception{
        when(ticketService.startProcessing(eq(101L),eq(3L)))
                .thenReturn(null);
        mockMvc.perform(patch("/tickets/101/processing")
                        .header("X-User-Id","3"))
                .andExpect(status().isNotFound());
        verify(ticketService).startProcessing(101L, 3L);
    }
    @Test
    void processTicket_shouldReturn403()throws Exception{
        when(ticketService.startProcessing(eq(101L),eq(5L)))
                .thenThrow(new BusinessException(HttpStatus.FORBIDDEN,"You don't have the right to process this ticket."));
        mockMvc.perform(patch("/tickets/101/processing")
                        .header("X-User-Id","5"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You don't have the right to process this ticket."));
        verify(ticketService).startProcessing(101L, 5L);
    }
    @Test
    void processTicket_shouldReturn409()throws Exception{
        when(ticketService.startProcessing(eq(101L),eq(3L)))
                .thenThrow(new BusinessException(HttpStatus.CONFLICT,"The status of ticket isn't assigned"));
        mockMvc.perform(patch("/tickets/101/processing")
                        .header("X-User-Id","3"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("The status of ticket isn't assigned"));
        verify(ticketService).startProcessing(101L, 3L);
    }
}
