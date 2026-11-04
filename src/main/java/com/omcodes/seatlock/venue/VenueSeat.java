package com.omcodes.seatlock.venue;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "venue_seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VenueSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(name = "section", nullable = false, length = 20)
    private String section;

    @Column(name = "row_label", nullable = false, length = 10)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Column(name = "tier", nullable = false, length = 20)
    private String tier;

    public VenueSeat(Venue venue, String section, String rowLabel, int seatNumber, String tier) {
        this.venue = venue;
        this.section = section;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.tier = tier;
    }
}
