package com.example.campusworkorder.dto;

import jakarta.validation.constraints.NotNull;

public class TicketAssignRequest {
    @NotNull(message = "assigneeId 不能为Null")
    private Long assigneeId;

    public Long getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
    }
}
