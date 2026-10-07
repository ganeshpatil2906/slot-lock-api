package com.gp.slotsync.repository;

import com.gp.slotsync.entity.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ResourceRepository extends JpaRepository<Resource, Long> {
    List<Resource> findByActiveTrue();
    List<Resource> findByTypeIgnoreCase(String type);
}

