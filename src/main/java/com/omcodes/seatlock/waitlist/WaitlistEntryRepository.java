package com.omcodes.seatlock.waitlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    boolean existsByEventIdAndUserId(Long eventId, Long userId);

    Optional<WaitlistEntry> findByEventIdAndUserId(Long eventId, Long userId);

    long countByEventIdAndStatus(Long eventId, WaitlistStatus status);

    @Query(value = """
            select * from waitlist_entries
            where event_id = :eventId
              and status = 'WAITING'
            order by created_at, id
            limit 1
            for update skip locked
            """, nativeQuery = true)
    Optional<WaitlistEntry> lockNextWaiting(@Param("eventId") Long eventId);
}