package com.omcodes.seatlock.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SeatHoldRepository extends JpaRepository<SeatHold, Long> {

    @Query("""
            select h from SeatHold h
            where h.user.id = :userId
              and h.eventSeat.event.id = :eventId
            """)
    List<SeatHold> findByUserAndEvent(@Param("userId") Long userId,
                                      @Param("eventId") Long eventId);

    List<SeatHold> findTop100ByExpiresAtLessThanEqualOrderByExpiresAtAsc(Instant now);
}