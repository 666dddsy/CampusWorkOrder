package com.example.campusworkorder.mapper;

import com.example.campusworkorder.entity.TicketLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TicketLogMapper {

    int insert(TicketLog ticketLog);
    List<TicketLog> selectByTicketId(@Param("ticketId") Long ticketId);
}