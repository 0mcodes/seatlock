package com.omcodes.seatlock.waitlist;

import com.omcodes.seatlock.event.Event;
import com.omcodes.seatlock.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "waitlist_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WaitlistStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public WaitlistEntry(Event event, User user, Instant now) {
        this.event = event;
        this.user = user;
        this.createdAt = now;
        this.status = WaitlistStatus.WAITING;
    }

    public void markNotified() {
        if (status != WaitlistStatus.WAITING) {
            throw new IllegalStateException("Only a waiting entry can be notified");
        }
        status = WaitlistStatus.NOTIFIED;
    }

    public void expire() {
        if (status != WaitlistStatus.NOTIFIED) {
            throw new IllegalStateException("Only a notified entry can expire");
        }
        status = WaitlistStatus.EXPIRED;
    }
}
