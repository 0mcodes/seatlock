package com.omcodes.seatlock.booking;

import com.omcodes.seatlock.event.Event;
import com.omcodes.seatlock.event.EventSeat;
import com.omcodes.seatlock.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    private List<BookingItem> items = new ArrayList<>();

    public Booking(User user, Event event, String idempotencyKey, Instant now) {
        this.user = user;
        this.event = event;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = now;
        this.status = BookingStatus.CONFIRMED;
        this.totalAmount = BigDecimal.ZERO;
    }

    public List<BookingItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void addItem(EventSeat seat, BigDecimal price) {
        items.add(new BookingItem(this, seat, price));
        totalAmount = totalAmount.add(price);
    }

    public void cancel() {
        if (status != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only a confirmed booking can be cancelled");
        }
        status = BookingStatus.CANCELLED;
        items.forEach(BookingItem::release);
    }
}
