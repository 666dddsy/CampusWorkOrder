package com.example.campusworkorder.dto;

import com.example.campusworkorder.domain.enums.TicketStatus;
import com.example.campusworkorder.entity.TicketLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// @JsonTest 是"切片"测试：只启动 JSON 相关的自动配置，
// 不连 MySQL、不连 Redis，所以跑得很快
@JsonTest
class TicketLogJsonTest {

    @Autowired
    ObjectMapper mapper;

    @Test
    void logsSurviveJsonRoundTrip() throws Exception {
        TicketLog log = new TicketLog();
        log.setId(42L);
        log.setTicketId(7L);
        log.setOperatorId(9L);
        log.setFromStatus(TicketStatus.CREATED);
        log.setToStatus(TicketStatus.ASSIGNED);
        log.setNote("管理员分配工单");
        log.setCreatedAt(LocalDateTime.of(2026, 11, 26, 15, 0, 0));

        List<TicketLog> origin = List.of(log);

        String json = mapper.writeValueAsString(origin);
        System.out.println("[JSON] " + json);

        // 关键：带泛型的反序列化必须用 TypeReference，否则拿到的是 List<LinkedHashMap>
        List<TicketLog> back = mapper.readValue(json, new TypeReference<List<TicketLog>>() {});

        TicketLog r = back.get(0);
        assertEquals(42L, r.getId());
        assertEquals(7L, r.getTicketId());
        assertEquals(9L, r.getOperatorId());
        assertEquals(TicketStatus.CREATED, r.getFromStatus());
        assertEquals(TicketStatus.ASSIGNED, r.getToStatus());
        assertEquals("管理员分配工单", r.getNote());
        assertEquals(
                LocalDateTime.of(2026, 11, 26, 15, 0, 0), r.getCreatedAt());
    }
}