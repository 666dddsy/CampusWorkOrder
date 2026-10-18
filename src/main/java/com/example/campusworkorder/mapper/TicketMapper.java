package com.example.campusworkorder.mapper;

import com.example.campusworkorder.dto.TicketPageQuery;
import com.example.campusworkorder.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TicketMapper {

    int insert(Ticket ticket);
    long countByQuery(@Param("query") TicketPageQuery query);
    Ticket selectById(@Param("id") Long id);
    List<Ticket> selectByCreatorId(@Param("creatorId") Long creatorId);
    List<Ticket> selectByAssigneeId(@Param("assigneeId") Long assigneeId);
    List<Ticket> selectPage(@Param("query")TicketPageQuery query, @Param("offset") long offset, @Param("pageSize") int pageSize);
    int updateAssignmentIfCreated(@Param("ticketId") Long ticketId,@Param("assigneeId")Long assigneeId);
    int updateProcessIfAssigned(@Param("ticketId")Long ticketId,@Param("assigneeId") Long assigneeId);
    int updateResolveIfProcessing(@Param("ticketId")Long ticketId,@Param("assigneeId") Long assigneeId);
    int updateCloseIfResolved(@Param("ticketId")Long ticketId,@Param("creatorId")Long creatorId);
}