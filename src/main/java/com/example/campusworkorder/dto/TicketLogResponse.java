package com.example.campusworkorder.dto;

import com.example.campusworkorder.domain.enums.TicketStatus;

import java.time.LocalDateTime;

public class TicketLogResponse {
    private Long id;
    private TicketStatus fromStatus;
    private TicketStatus toStatus;
    private Long operatorId;
    private String note;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TicketStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(TicketStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public TicketStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(TicketStatus toStatus) {
        this.toStatus = toStatus;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
