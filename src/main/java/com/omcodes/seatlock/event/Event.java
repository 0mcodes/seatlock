package com.omcodes.seatlock.event;

import com.omcodes.seatlock.venue.Venue;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EventStatus status;

    public Event(Venue venue, String title, String category, Instant startsAt) {
        this.venue = venue;
        this.title = title;
        this.category = category;
        this.startsAt = startsAt;
        this.status = EventStatus.DRAFT;
    }

    public void updateDetails(String title, String category, Instant startsAt) {
        if (status == EventStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled events cannot be edited");
        }
        this.title = title;
        this.category = category;
        this.startsAt = startsAt;
    }

    public void putOnSale() {
        if (status != EventStatus.DRAFT) {
            throw new IllegalStateException("Only a draft event can be put on sale");
        }
        status = EventStatus.ON_SALE;
    }

    public void markSoldOut() {
        if (status != EventStatus.ON_SALE) {
            throw new IllegalStateException("Only an on-sale event can sell out");
        }
        status = EventStatus.SOLD_OUT;
    }

    public void reopenSales() {
        if (status != EventStatus.SOLD_OUT) {
            throw new IllegalStateException("Only a sold-out event can reopen");
        }
        status = EventStatus.ON_SALE;
    }

    public void cancel() {
        if (status == EventStatus.CANCELLED) {
            throw new IllegalStateException("Event is already cancelled");
        }
        status = EventStatus.CANCELLED;
    }
}