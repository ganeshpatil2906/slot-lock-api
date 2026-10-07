package com.gp.slotsync.entity;

import com.gp.slotsync.enums.SlotStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "slots",
       uniqueConstraints = @UniqueConstraint(columnNames = {"resource_id", "start_time"}))
public class Slot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id")
    private Resource resource;
    @Column(name = "start_time", nullable = false) private LocalDateTime startTime;
    @Column(name = "end_time", nullable = false) private LocalDateTime endTime;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SlotStatus status = SlotStatus.AVAILABLE;

    // optimistic locking: two concurrent updates on the same slot -> one fails
    @Version private Long version;

    public Long getId() { return id; }
    public Resource getResource() { return resource; }
    public void setResource(Resource resource) { this.resource = resource; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public SlotStatus getStatus() { return status; }
    public void setStatus(SlotStatus status) { this.status = status; }
    public Long getVersion() { return version; }
}
