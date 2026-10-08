package com.example.campusworkorder.dto;

import com.example.campusworkorder.domain.enums.TicketCategory;
import com.example.campusworkorder.domain.enums.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class TicketCreateRequest {

    @NotBlank(message = "title must not be blank")
    @Size(max = 100, message = "title length must not exceed 100")
    private String title;

    @NotBlank(message = "description must not be blank")
    @Size(max = 1000, message = "description length must not exceed 1000")
    private String description;

    @NotBlank(message = "location must not be blank")
    @Size(max = 100, message = "location length must not exceed 100")
    private String location;

    @NotNull(message = "category must not be null")
    private TicketCategory category;

    @NotNull(message = "priority must not be null")
    private TicketPriority priority;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TicketCategory getCategory() {
        return category;
    }

    public void setCategory(TicketCategory category) {
        this.category = category;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public void setPriority(TicketPriority priority) {
        this.priority = priority;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}