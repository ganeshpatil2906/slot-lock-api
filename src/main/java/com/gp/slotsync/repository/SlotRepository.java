package com.gp.slotsync.repository;

import com.gp.slotsync.entity.Slot;
import com.gp.slotsync.enums.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface SlotRepository extends JpaRepository<Slot, Long> {
    List<Slot> findByResourceId(Long resourceId);
    List<Slot> findByResourceIdAndStatus(Long resourceId, SlotStatus status);
    List<Slot> findByResourceIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(
            Long resourceId, LocalDateTime startTime, LocalDateTime endTime);
    List<Slot> findByStatus(SlotStatus status);
    boolean existsByResourceIdAndStartTime(Long resourceId, LocalDateTime startTime);
}

