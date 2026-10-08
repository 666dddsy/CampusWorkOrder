package com.example.campusworkorder.mapper;

import com.example.campusworkorder.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TicketMapper {

    int insert(Ticket ticket);
    Ticket selectById(@Param("id") Long id);
}