# SeatLock: API Design

Base path: `/api/v1`. JSON over HTTPS. Authentication uses a JWT in the `Authorization: Bearer <token>` header.

## 1. Conventions

- **Resources are plural nouns** (`/events`, `/bookings`). Actions that don't map to a field update use a sub-resource or verb path (`/bookings/{id}/cancel`).
- **Versioning in the path** (`/v1`) so breaking changes can ship as `/v2`.
- **DTOs only.** Entities are never exposed. Request and response shapes are separate classes.
- **Pagination** uses `page` and `size` query parameters. Responses include `content`, `page`, `size`, `totalElements`.
- **Timestamps** are ISO-8601 in UTC (`2026-11-05T18:30:00Z`).
- **Money** is a decimal string or number with two decimals, with a separate `currency` field if multi-currency is ever added.

## 2. Endpoints

### Auth (public)
| Method | Path | Purpose |
|---|---|---|
| POST | `/auth/register` | Create an attendee account |
| POST | `/auth/login` | Returns access token and refresh token |
| POST | `/auth/refresh` | Exchange a refresh token for a new access token |

### Events and seats (public reads)
| Method | Path | Purpose |
|---|---|---|
| GET | `/events?city=&category=&from=&to=&page=&size=` | Browse events (FR-2) |
| GET | `/events/{eventId}` | Event details |
| GET | `/events/{eventId}/seat-layout` | Static layout: sections, rows, seat positions, tiers. Cacheable for a long time |
| GET | `/events/{eventId}/seat-status` | Dynamic seat statuses in a compact format. Cached 1 to 2 seconds |

### Holds (attendee)
| Method | Path | Purpose |
|---|---|---|
| POST | `/events/{eventId}/holds` | Hold 1 to 4 seats for 10 minutes (FR-4) |
| DELETE | `/events/{eventId}/holds` | Release all of the caller's holds for this event |

### Bookings (attendee)
| Method | Path | Purpose |
|---|---|---|
| POST | `/bookings` | Confirm a booking for currently held seats. Requires `Idempotency-Key` header (FR-5) |
| GET | `/bookings?page=&size=` | The caller's booking history (FR-6) |
| GET | `/bookings/{bookingId}` | One booking. Only the owner or an admin |
| POST | `/bookings/{bookingId}/cancel` | Cancel a booking (FR-6) |

### Waitlist (attendee)
| Method | Path | Purpose |
|---|---|---|
| POST | `/events/{eventId}/waitlist` | Join the waitlist (FR-7) |
| DELETE | `/events/{eventId}/waitlist` | Leave the waitlist |

### Admin (role `ADMIN`)
| Method | Path | Purpose |
|---|---|---|
| POST | `/admin/venues` | Create a venue |
| POST | `/admin/venues/{venueId}/seats` | Bulk-define the seat layout (FR-9) |
| POST | `/admin/events` | Create an event. Generates its `event_seats` rows |
| PUT | `/admin/events/{eventId}` | Update an event |
| POST | `/admin/events/{eventId}/cancel` | Cancel an event (FR-8) |
| GET | `/admin/events/{eventId}/stats` | Seats sold, revenue, waitlist size (FR-10) |

## 3. Key Request and Response Shapes

### Place a hold
`POST /events/42/holds`
```json
{ "seatIds": [1001, 1002] }
```
Response `201 Created`:
```json
{
  "eventId": 42,
  "seats": [
    { "seatId": 1001, "section": "A", "row": "3", "number": 12, "price": 80.00 },
    { "seatId": 1002, "section": "A", "row": "3", "number": 13, "price": 80.00 }
  ],
  "expiresAt": "2026-11-05T18:40:00Z"
}
```
Holds are **all or nothing**. If any requested seat is unavailable, no seat is held and the request fails with `409`.

### Confirm a booking
`POST /bookings` with header `Idempotency-Key: 3f6c2b9e-...` (a client-generated UUID)
```json
{ "eventId": 42, "seatIds": [1001, 1002] }
```
Response `201 Created`:
```json
{
  "bookingId": 9001,
  "eventId": 42,
  "status": "CONFIRMED",
  "totalAmount": 160.00,
  "seats": [1001, 1002],
  "createdAt": "2026-11-05T18:33:10Z"
}
```

### Seat status (compact)
`GET /events/42/seat-status`
```json
{
  "eventId": 42,
  "asOf": "2026-11-05T18:30:01Z",
  "statuses": "AAAHBBAAHA..."
}
```
One character per seat, in the same order as the seat layout: `A` available, `H` held, `B` booked. This keeps the payload to a few KB for thousands of seats (see capacity estimation, section 4).

## 4. Idempotency

`POST /bookings` requires an `Idempotency-Key` header.

| Situation | Behavior |
|---|---|
| New key | Process normally, store the key on the booking |
| Same key, same user, same body (a retry) | Return the original booking with `200 OK`. No second booking, no second charge |
| Same key, different body | `422 Unprocessable Entity`, because the key was reused for a different request |
| Missing header | `400 Bad Request` |

The database enforces this with a unique constraint on `(user_id, idempotency_key)`.

## 5. Error Format

All errors use RFC 7807 Problem Details (`application/problem+json`), which Spring supports through `ProblemDetail`.

Seat conflict, `409 Conflict`:
```json
{
  "type": "https://seatlock.dev/problems/seat-unavailable",
  "title": "Seat unavailable",
  "status": 409,
  "detail": "One or more requested seats were just taken.",
  "unavailableSeatIds": [1002],
  "traceId": "c0a8-1f2e"
}
```

| Status | When |
|---|---|
| 400 | Validation failed, malformed body, missing idempotency key |
| 401 | Missing or invalid token |
| 403 | Authenticated but not allowed (for example, a non-admin calling `/admin/**`) |
| 404 | Resource not found. Also used when a user asks for another user's booking, to avoid leaking existence |
| 409 | Seat already held or booked, hold expired before booking |
| 422 | Idempotency key reused with a different body |
| 429 | Rate limit exceeded. Includes a `Retry-After` header |
| 500 | Unexpected error. Body contains only a `traceId`, never internals |

`traceId` is the correlation ID that also appears in the server logs (NFR-8).

## 6. Rate Limiting

| Endpoint group | Limit (initial) |
|---|---|
| `POST /events/*/holds`, `POST /bookings` | 10 requests per minute per user, 30 per minute per IP |
| `POST /auth/login` | 5 requests per minute per IP |
| Public reads | 120 requests per minute per IP |

Exceeding a limit returns `429` with `Retry-After`.

## 7. Authorization Summary

| Area | Access |
|---|---|
| `/auth/**`, event and seat reads | Public |
| Holds, bookings, waitlist | Authenticated attendee |
| `/admin/**` | Role `ADMIN` |
| `GET /bookings/{id}` and cancel | Owner of the booking, or admin |

## 8. Decisions

- **Why separate layout and status endpoints?** Layout is static and cacheable for a long time. Status changes every second during a sale and is cached briefly. Combining them would force the short cache on the large static part.
- **Why are holds all or nothing?** A user who holds 2 of 3 requested seats cannot complete the purchase they wanted. Partial holds also waste inventory.
- **Why does booking take seat IDs when holds already exist?** The server verifies that the caller holds every listed seat and that the holds have not expired. It never trusts the client's claim about what it holds.
- **Why 404 instead of 403 for another user's booking?** A 403 confirms the booking exists.
- **Why Problem Details?** It is a standard format, and Spring provides it, so clients get a consistent error shape.
