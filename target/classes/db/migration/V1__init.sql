-- SeatLock schema (PostgreSQL)
-- Later this becomes the first Flyway migration: src/main/resources/db/migration/V1__init.sql

CREATE TABLE users (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    role           VARCHAR(20)  NOT NULL CHECK (role IN ('ATTENDEE', 'ADMIN')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE venues (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name     VARCHAR(200) NOT NULL,
    city     VARCHAR(100) NOT NULL,
    address  VARCHAR(300)
);
CREATE INDEX idx_venues_city ON venues (city);

CREATE TABLE events (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venue_id   BIGINT       NOT NULL REFERENCES venues (id),
    title      VARCHAR(200) NOT NULL,
    category   VARCHAR(50),
    starts_at  TIMESTAMPTZ  NOT NULL,
    status     VARCHAR(20)  NOT NULL
               CHECK (status IN ('DRAFT', 'ON_SALE', 'SOLD_OUT', 'CANCELLED'))
);
CREATE INDEX idx_events_status_starts ON events (status, starts_at);
CREATE INDEX idx_events_venue ON events (venue_id);

CREATE TABLE venue_seats (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venue_id     BIGINT      NOT NULL REFERENCES venues (id),
    section      VARCHAR(20) NOT NULL,
    row_label    VARCHAR(10) NOT NULL,
    seat_number  INT         NOT NULL,
    tier         VARCHAR(20) NOT NULL,
    CONSTRAINT uq_venue_seat_position UNIQUE (venue_id, section, row_label, seat_number)
);

CREATE TABLE event_seats (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id       BIGINT        NOT NULL REFERENCES events (id),
    venue_seat_id  BIGINT        NOT NULL REFERENCES venue_seats (id),
    status         VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE'
                   CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED')),
    price          NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    version        BIGINT        NOT NULL DEFAULT 0,   -- optimistic lock
    CONSTRAINT uq_event_seat UNIQUE (event_id, venue_seat_id)
);
CREATE INDEX idx_event_seats_event_status ON event_seats (event_id, status);

CREATE TABLE seat_holds (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_seat_id  BIGINT      NOT NULL UNIQUE REFERENCES event_seats (id),  -- one active hold per seat
    user_id        BIGINT      NOT NULL REFERENCES users (id),
    expires_at     TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_seat_holds_expires ON seat_holds (expires_at);
CREATE INDEX idx_seat_holds_user ON seat_holds (user_id);

CREATE TABLE bookings (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT        NOT NULL REFERENCES users (id),
    event_id         BIGINT        NOT NULL REFERENCES events (id),
    status           VARCHAR(20)   NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    total_amount     NUMERIC(10,2) NOT NULL CHECK (total_amount >= 0),
    idempotency_key  VARCHAR(64)   NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_booking_idempotency UNIQUE (user_id, idempotency_key)
);
CREATE INDEX idx_bookings_user_created ON bookings (user_id, created_at DESC);

CREATE TABLE booking_items (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id     BIGINT        NOT NULL REFERENCES bookings (id),
    event_seat_id  BIGINT        NOT NULL REFERENCES event_seats (id),
    price          NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RELEASED'))
);
CREATE INDEX idx_booking_items_booking ON booking_items (booking_id);
-- Partial unique index: a seat has at most one ACTIVE booking item.
-- Cancelled (RELEASED) items stay as history and do not block resale.
CREATE UNIQUE INDEX uq_booking_items_active_seat
    ON booking_items (event_seat_id) WHERE status = 'ACTIVE';

CREATE TABLE waitlist_entries (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id    BIGINT      NOT NULL REFERENCES events (id),
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    status      VARCHAR(20) NOT NULL DEFAULT 'WAITING'
                CHECK (status IN ('WAITING', 'NOTIFIED', 'EXPIRED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_waitlist_event_user UNIQUE (event_id, user_id)
);
CREATE INDEX idx_waitlist_next ON waitlist_entries (event_id, status, created_at);
