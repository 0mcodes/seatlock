# SeatLock: Data Model

Database: PostgreSQL. The ERD source is in `seatlock.dbml` (paste into dbdiagram.io to render) and the schema is in `schema.sql`.

## 1. Design Principles

- **The database is the final referee for double booking.** Application locking can have bugs, but constraints cannot be bypassed.
- **Every query in the requirements implies an index.** Indexes are listed in section 5 with the query each one serves.
- **Status belongs to the pair (event, seat), not to the seat alone.** A physical seat can be booked for one event and free for another.
- **Store snapshots of money values.** Prices are copied into `event_seats` and `booking_items` so later price changes do not rewrite history.

## 2. Entities

### users
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | Identity column |
| email | VARCHAR(255) UNIQUE | Login identity. Unique constraint stops duplicate accounts when two registrations race |
| password_hash | VARCHAR(255) | BCrypt hash |
| role | VARCHAR(20) | `ATTENDEE` or `ADMIN` |
| created_at | TIMESTAMPTZ | |

### venues
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| name | VARCHAR(200) | |
| city | VARCHAR(100) | Used for browsing by city (FR-2) |
| address | VARCHAR(300) | |

### events
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| venue_id | BIGINT FK to venues | One venue per event |
| title | VARCHAR(200) | |
| category | VARCHAR(50) | |
| starts_at | TIMESTAMPTZ | |
| status | VARCHAR(20) | `DRAFT`, `ON_SALE`, `SOLD_OUT`, `CANCELLED` |

### venue_seats (physical layout, defined once per venue)
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| venue_id | BIGINT FK to venues | |
| section | VARCHAR(20) | |
| row_label | VARCHAR(10) | |
| seat_number | INT | |
| tier | VARCHAR(20) | Price tier, e.g. `VIP`, `STANDARD` |

Unique on `(venue_id, section, row_label, seat_number)`. A seat position cannot be defined twice.

### event_seats (sale state of each seat for one event)
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| event_id | BIGINT FK to events | |
| venue_seat_id | BIGINT FK to venue_seats | |
| status | VARCHAR(20) | `AVAILABLE`, `HELD`, `BOOKED` |
| price | NUMERIC(10,2) | Snapshot for this event |
| version | BIGINT | Optimistic locking column (`@Version` in JPA) |

Unique on `(event_id, venue_seat_id)`. This is the hot table: about 10M rows and the center of contention during a sale.

### seat_holds (temporary reservations)
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| event_seat_id | BIGINT FK, UNIQUE | At most one active hold per seat |
| user_id | BIGINT FK to users | |
| expires_at | TIMESTAMPTZ | Hold expiry job scans this (FR-11) |
| created_at | TIMESTAMPTZ | |

A row is deleted when the hold expires, is cancelled, or converts into a booking.

### bookings
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| user_id | BIGINT FK to users | |
| event_id | BIGINT FK to events | |
| status | VARCHAR(20) | `CONFIRMED`, `CANCELLED` |
| total_amount | NUMERIC(10,2) | |
| idempotency_key | VARCHAR(64) | Client-generated UUID. Implements NFR-7 |
| created_at | TIMESTAMPTZ | |

Unique on `(user_id, idempotency_key)`. A retried request with the same key hits the constraint, and the API returns the original booking instead of creating a duplicate.

### booking_items (seats inside a booking)
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| booking_id | BIGINT FK to bookings | |
| event_seat_id | BIGINT FK to event_seats | |
| price | NUMERIC(10,2) | Price paid |
| status | VARCHAR(20) | `ACTIVE` or `RELEASED` |

A **partial unique index** on `event_seat_id WHERE status = 'ACTIVE'` guarantees a seat has at most one active booking item. Cancelling a booking marks its items `RELEASED`, which frees the seat for resale while keeping the history.

### waitlist_entries
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| event_id | BIGINT FK to events | |
| user_id | BIGINT FK to users | |
| status | VARCHAR(20) | `WAITING`, `NOTIFIED`, `EXPIRED` |
| created_at | TIMESTAMPTZ | Defines queue order |

Unique on `(event_id, user_id)`. A user cannot join the same waitlist twice.

## 3. Relationships

```
users 1---* bookings 1---* booking_items *---1 event_seats
users 1---* seat_holds *---1 event_seats
venues 1---* events 1---* event_seats *---1 venue_seats *---1 venues
events 1---* bookings
events 1---* waitlist_entries *---1 users
```

## 4. Double-Booking Defenses

| Layer | Mechanism | Failure it catches |
|---|---|---|
| 1 | `version` column on `event_seats` (optimistic lock) | Two requests race to change the same seat. The loser updates zero rows and gets a conflict response |
| 2 | Unique constraint on `seat_holds.event_seat_id` | A second hold on a seat that already has one, even if layer 1 had a bug |
| 3 | Partial unique index on `booking_items.event_seat_id` where status is `ACTIVE` | A second active booking for the same seat. The database cannot record it |

The `status` column on `event_seats` and the rows in `seat_holds` and `booking_items` are always changed in one transaction, so they cannot drift apart.

## 5. Indexes

| Index | Query it serves |
|---|---|
| `venues(city)` | Browse events by city (FR-2) |
| `events(status, starts_at)` | List on-sale events by date (FR-2) |
| `events(venue_id)` | Join events to venues |
| `event_seats(event_id, status)` | Load the seat map and count available seats (FR-3) |
| `seat_holds(expires_at)` | Expiry job finds expired holds (FR-11) |
| `seat_holds(user_id)` | Show a user's active holds |
| `bookings(user_id, created_at DESC)` | Booking history (FR-6) |
| `waitlist_entries(event_id, status, created_at)` | Find the next person to promote (FR-12) |

Unique constraints (listed above) also create indexes, which cover lookups on those columns.

## 6. Decisions

- **Why two seat tables?** Physical layout does not change per event, while sale state does. Merging them would duplicate layout data and make status ambiguous.
- **Why a separate booking_items table?** A booking has 1 to 4 seats. A list inside one column cannot carry a per-seat constraint.
- **Why a partial unique index instead of a plain unique constraint?** With a plain constraint, a cancelled booking would block the seat from ever being sold again. The partial index applies only to active items.
- **Why TIMESTAMPTZ?** Event times and expiry checks must be correct across time zones.
- **Why NUMERIC for money?** Floating-point types introduce rounding errors.
- **Why not UUID primary keys?** Identity columns are smaller and faster to index for a 10M-row table. Public identifiers can be added later if needed.
