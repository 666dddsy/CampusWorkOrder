package com.example.campusworkorder.mapper;

import com.example.campusworkorder.entity.TicketLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TicketLogMapper {

    int insert(TicketLog ticketLog);
}