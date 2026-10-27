package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.UserBlock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    Optional<UserBlock> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    List<UserBlock> findByBlockerIdOrderByCreatedAtDesc(Long blockerId);

    /** Every block, newest first - the admin's moderation view. */
    List<UserBlock> findTop500ByOrderByCreatedAtDesc();
}
