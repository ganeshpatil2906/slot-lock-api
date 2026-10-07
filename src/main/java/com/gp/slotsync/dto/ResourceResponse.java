package com.gp.slotsync.dto;

import com.gp.slotsync.entity.Resource;

public record ResourceResponse(
    Long id,
    String name,
    String type,
    boolean active
) {
    public static ResourceResponse fromEntity(Resource resource) {
        return new ResourceResponse(
            resource.getId(),
            resource.getName(),
            resource.getType(),
            resource.isActive()
        );
    }
}
