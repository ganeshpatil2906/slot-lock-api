package com.gp.slotsync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResourceRequest(
        @NotBlank(message = "Resource name is required") @Size(max = 120, message = "Resource name cannot exceed 120 characters") String name,

        @NotBlank(message = "Resource type is required") @Size(max = 50, message = "Resource type cannot exceed 50 characters") String type,

        Boolean active) {
    public boolean isActiveOrDefault() {
        return active == null || active;
    }
}
