package com.omcodes.seatlock.event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query(value = """
            select e from Event e
            join fetch e.venue v
            where e.status = :status
              and e.startsAt >= :after
              and (:city is null or v.city = :city)
              and (:category is null or e.category = :category)
            order by e.startsAt asc
            """,
            countQuery = """
            select count(e) from Event e
            join e.venue v
            where e.status = :status
              and e.startsAt >= :after
              and (:city is null or v.city = :city)
              and (:category is null or e.category = :category)
            """)
    Page<Event> browse(@Param("status") EventStatus status,
                       @Param("after") Instant after,
                       @Param("city") String city,
                       @Param("category") String category,
                       Pageable pageable);
}