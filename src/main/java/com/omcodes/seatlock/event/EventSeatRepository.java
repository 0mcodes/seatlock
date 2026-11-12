package com.omcodes.seatlock.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface EventSeatRepository extends JpaRepository<EventSeat, Long> {

    @Query("""
            select es from EventSeat es
            join fetch es.venueSeat
            where es.event.id = :eventId
              and es.id in :seatIds
            order by es.id
            """)
    List<EventSeat> findForEvent(@Param("eventId") Long eventId,
                                 @Param("seatIds") Collection<Long> seatIds);

    long countByEventIdAndStatus(Long eventId, SeatStatus status);

    interface SeatStatusView {
        Long getId();
        SeatStatus getStatus();
    }

    @Query("""
            select es.id as id, es.status as status
            from EventSeat es
            where es.event.id = :eventId
            order by es.id
            """)
    List<SeatStatusView> findStatusesByEventId(@Param("eventId") Long eventId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into event_seats (event_id, venue_seat_id, status, price, version)
            select cast(:eventId as bigint), vs.id, 'AVAILABLE', cast(:price as numeric), 0
            from venue_seats vs
            where vs.venue_id = :venueId
            """, nativeQuery = true)
    int createForEvent(@Param("eventId") Long eventId,
                       @Param("venueId") Long venueId,
                       @Param("price") BigDecimal price);
}