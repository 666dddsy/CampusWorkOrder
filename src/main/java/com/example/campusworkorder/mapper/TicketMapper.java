package com.example.campusworkorder.mapper;

import com.example.campusworkorder.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TicketMapper {

    int insert(Ticket ticket);
}