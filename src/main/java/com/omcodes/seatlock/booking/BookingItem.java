package com.omcodes.seatlock.booking;

import com.omcodes.seatlock.event.EventSeat;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "booking_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_seat_id", nullable = false)
    private EventSeat eventSeat;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingItemStatus status;

    // Package-private: only Booking creates its items.
    BookingItem(Booking booking, EventSeat eventSeat, BigDecimal price) {
        this.booking = booking;
        this.eventSeat = eventSeat;
        this.price = price;
        this.status = BookingItemStatus.ACTIVE;
    }

    void release() {
        status = BookingItemStatus.RELEASED;
    }
}
