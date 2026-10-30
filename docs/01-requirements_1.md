# SeatLock: Requirements

## 1. Overview

SeatLock is a ticket-booking platform where attendees pick specific seats for events (concerts, conferences, sports) and organizers manage events and venues.

The central engineering problem is **selling each seat at most once**, even when thousands of users compete for the same seats at the same moment (a flash sale). Everything else in the system (notifications, search, admin tools) is secondary to that guarantee.

## 2. Actors

| Actor | Description |
|---|---|
| Attendee | Registered user who browses events and books seats |
| Admin / Organizer | Creates and manages events, venues, and pricing |
| System | Automated jobs: hold expiry, waitlist promotion, notifications |

## 3. Functional Requirements

### 3.1 Attendee

- FR-1: Register with email and password, log in, and log out.
- FR-2: Browse events with pagination, filtered by date, city, and category.
- FR-3: View an event's seat map showing the status of every seat (available, held, booked).
- FR-4: Select 1 to 4 individual seats and place a **hold** on them. A hold lasts 10 minutes.
- FR-5: Complete payment (simulated) within the hold window to confirm the booking.
- FR-6: View booking history and cancel a booking.
- FR-7: Join a waitlist for a sold-out event and be notified when a seat becomes available.

### 3.2 Admin / Organizer

- FR-8: Create, update, and cancel events.
- FR-9: Define a venue's seat layout (sections, rows, seat numbers) and price tiers.
- FR-10: View booking statistics per event (seats sold, revenue, waitlist size).

### 3.3 System

- FR-11: Release expired holds automatically so seats return to the available pool.
- FR-12: Promote the next waitlisted user when a seat is released.
- FR-13: Send booking confirmation and waitlist emails asynchronously.

## 4. Non-Functional Requirements

| ID | Category | Target | Rationale |
|---|---|---|---|
| NFR-1 | Correctness | A seat is never confirmed for two bookings. Zero oversells under concurrent load | Core promise of the product |
| NFR-2 | Availability | Browsing and booking continue to work if email or the queue is down | Notifications must not block purchases |
| NFR-3 | Latency | Event listing p95 < 200 ms. Hold and booking requests p95 < 500 ms | Slow checkout causes abandonment |
| NFR-4 | Scalability | Survive a flash-sale burst (see capacity estimation) | Ticket traffic is spiky |
| NFR-5 | Durability | A confirmed booking is never lost | Payment and trust are involved |
| NFR-6 | Security | JWT authentication, role-based authorization, rate limiting on hold and booking endpoints | Prevent bots and abuse |
| NFR-7 | Idempotency | Retrying a hold or booking request must not create duplicates | Clients retry after timeouts |
| NFR-8 | Observability | Structured logs with correlation IDs, metrics for booking outcomes, health endpoints | Needed to diagnose production issues |

## 5. Key Trade-offs

- **Consistency over availability for seat inventory.** If the database cannot confirm a seat's state, the request fails rather than risk selling a seat twice.
- **Availability over consistency for notifications and listings.** Emails may be delayed, and cached event listings may be a few seconds stale.
- **Seat map freshness is a soft requirement, the booking write is a hard one.** The seat map may show a seat as available that was just taken. The hold request is the authoritative check and will reject it.

## 6. Out of Scope

- Real payment gateway integration (payment is simulated)
- Refund accounting and invoicing
- Multi-currency and tax handling
- Mobile apps
- Dynamic pricing
- Seat map editor UI (layouts are defined through the API)

## 7. Assumptions

- A single booking holds at most 4 seats.
- Hold duration is fixed at 10 minutes.
- Each event belongs to exactly one venue and has one price per seat tier.
- Users must be logged in to hold or book. Browsing is public.
