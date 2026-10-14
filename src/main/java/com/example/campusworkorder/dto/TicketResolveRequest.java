package com.example.campusworkorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class TicketResolveRequest {
    @NotBlank(message = "resolutionNote should be not blank")
    @Size(max=500,message = "The length of note can't exceed 500")
    private String resolutionNote;

    public String getResolutionNote() {
        return resolutionNote;
    }

    public void setResolutionNote(String resolutionNote) {
        this.resolutionNote = resolutionNote;
    }
}
