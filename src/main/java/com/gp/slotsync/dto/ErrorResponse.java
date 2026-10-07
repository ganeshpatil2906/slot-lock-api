package com.gp.slotsync.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
    int status,
    String message,
    LocalDateTime timestamp,
    List<String> errors
) {
    public ErrorResponse(int status, String message, List<String> errors) {
        this(status, message, LocalDateTime.now(), errors);
    }

    public ErrorResponse(int status, String message) {
        this(status, message, LocalDateTime.now(), List.of());
    }
}
