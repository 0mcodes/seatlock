package com.omcodes.seatlock.event;

import com.omcodes.seatlock.venue.VenueSeat;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "event_seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_seat_id", nullable = false)
    private VenueSeat venueSeat;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SeatStatus status;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public EventSeat(Event event, VenueSeat venueSeat, BigDecimal price) {
        this.event = event;
        this.venueSeat = venueSeat;
        this.price = price;
        this.status = SeatStatus.AVAILABLE;
    }

    public boolean isAvailable() {
        return status == SeatStatus.AVAILABLE;
    }

    public void hold() {
        if (status != SeatStatus.AVAILABLE) {
            throw new IllegalStateException("Seat is not available");
        }
        status = SeatStatus.HELD;
    }

    public void book() {
        if (status != SeatStatus.HELD) {
            throw new IllegalStateException("Seat must be held before booking");
        }
        status = SeatStatus.BOOKED;
    }

    public void release() {
        if (status == SeatStatus.AVAILABLE) {
            throw new IllegalStateException("Seat is already available");
        }
        status = SeatStatus.AVAILABLE;
    }
}
