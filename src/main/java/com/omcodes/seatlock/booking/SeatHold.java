package com.omcodes.seatlock.booking;

import com.omcodes.seatlock.event.EventSeat;
import com.omcodes.seatlock.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name="seat_hold")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class SeatHold {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long Id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_seat_id",nullable = false,unique = true)
    private EventSeat eventSeat;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "user_id",nullable = false)
    private User user;

    @Column(name="expires_at",nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at",nullable = false,updatable = false)
    private Instant createdAt;

    public SeatHold(EventSeat eventSeat,User user,Instant now,Duration ttl){
        this.eventSeat = eventSeat;
        this.user = user;
        this.createdAt = now;
        this.expiresAt = now.plus(ttl);
    }

    public Boolean isExpired(Instant now){
        return !expiresAt.isAfter(now);
    }
}
