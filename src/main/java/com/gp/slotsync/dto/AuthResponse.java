package com.gp.slotsync.dto;

import com.gp.slotsync.enums.Role;

public record AuthResponse(
    String token,
    String tokenType,
    Long userId,
    String name,
    String email,
    Role role
) {
    public AuthResponse(String token, Long userId, String name, String email, Role role) {
        this(token, "Bearer", userId, name, email, role);
    }
}
