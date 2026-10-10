package com.example.campusworkorder.mapper;

import com.example.campusworkorder.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TicketMapper {

    int insert(Ticket ticket);
    Ticket selectById(@Param("id") Long id);
    List<Ticket> selectByCreatorId(@Param("creatorId") Long creatorId);
}