package com.omcodes.seatlock.venue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VenueSeatRepository extends JpaRepository<VenueSeat, Long> {

    List<VenueSeat> findByVenueId(Long venueId);
}