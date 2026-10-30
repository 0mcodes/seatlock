# SeatLock: Capacity Estimation

These are back-of-the-envelope numbers. The goal is to find the order of magnitude and the real bottleneck, not exact figures.

## 1. Assumptions

| Item | Value |
|---|---|
| Registered users | 500,000 |
| Daily active users (DAU) | 50,000 |
| Live events at any time | 5,000 |
| Average seats per event | 2,000 |
| Page views per active user per day | 10 |
| Bookings per active user per day | 0.1 |
| Max seats per booking | 4 |
| Flash-sale event | 5,000 seats, 50,000 users arrive within 60 seconds |

## 2. Normal Load

**Reads**
- 50,000 users x 10 views = 500,000 requests/day
- 500,000 / 86,400 s = about **6 RPS** average

**Bookings**
- 50,000 x 0.1 = 5,000 bookings/day
- 5,000 / 86,400 s = about **0.06 bookings/s**

Normal load is tiny. A single small instance handles it. The design is driven by peak load, not by this number.

## 3. Peak Load (Flash Sale)

- 50,000 users arriving over 60 s = about **830 new users/s**
- Each user makes roughly 5 calls (open event, load seat map, place hold, pay, confirm)
- Peak = 830 x 5 = about **4,000 RPS**
- Peak / average = 4,000 / 6 = roughly **700x**

**Contention.** All of these requests target one event, and 5,000 seats. Popular seats (front rows, center sections) will be picked by many users at once. If 20% of users pick from the best 500 seats, about 10,000 users compete for 500 rows. The same rows receive many simultaneous write attempts.

**Expected outcomes at peak**
- Most hold attempts fail because the seat is already taken. This is correct behavior, not an error.
- The system must fail those requests fast and cleanly, with a clear response telling the user to pick another seat.

## 4. Seat Map Payload

The seat map is the heaviest read during a sale because every arriving user loads it.

- Naive format: 5,000 seats x ~30 bytes of JSON = about 150 KB per response
- At 830 loads/s that is about 125 MB/s, which is not workable
- Compact format: one status per seat as a short code plus a static layout fetched once. About 5 to 10 KB with compression
- At 830 loads/s: about 4 to 8 MB/s, which is manageable

**Consequence:** separate the static layout (cacheable for a long time) from the dynamic seat statuses (cached for 1 to 2 seconds). Return statuses in a compact format.

## 5. Storage

| Data | Size per row | Rows | Total |
|---|---|---|---|
| Seats | ~100 B | 5,000 events x 2,000 = 10M | ~1 GB |
| Bookings | ~200 B | 5,000/day x 365 = ~1.8M/year | ~365 MB/year |
| Booking items (seat links) | ~50 B | ~3M/year (avg 1.7 seats per booking) | ~150 MB/year |
| Users | ~300 B | 500,000 | ~150 MB |
| Events and venues | ~1 KB | 5,000 | ~5 MB |

Total is under **2 GB**. One small PostgreSQL instance is enough for storage. The bottleneck is **concurrency, not data volume**.

Indexes needed because of the 10M seat rows:
- `seats(event_id, status)` for listing available seats
- `bookings(user_id)` for booking history
- `seat_holds(expires_at)` for the expiry job

## 6. Hold Volume

- Max simultaneous holds is bounded by seats: at most 5,000 for the flash-sale event
- Hold TTL is 10 minutes, and each hold row is small
- The expiry job scans `seat_holds(expires_at)` and runs every 30 to 60 seconds, so it touches a small, indexed range

## 7. Notification Volume

- Normal: 5,000 confirmation emails/day, which is trivial
- Flash sale: up to 5,000 confirmations within minutes, plus waitlist notifications
- This is why emails go through a queue (SQS). The booking request returns immediately and the consumer sends emails at its own pace

## 8. Design Consequences

| Finding | Design decision |
|---|---|
| Peak is about 700x average | Design and load-test for the peak, not the average |
| Many writers hit the same seat rows | Concurrency control on seats (optimistic locking, conditional updates) |
| Most hold attempts at peak will fail | Fail fast with clear conflict responses, and rate limit per user |
| Seat map is the heaviest read | Split static layout from dynamic status, compact format, short-TTL cache |
| Reads far exceed writes overall | Cache event listings |
| Email bursts at sale time | Queue notifications asynchronously |
| 10M seat rows | Composite indexes on seat queries |
| Storage is small | No sharding or partitioning needed. Do not over-engineer |

## 9. Design Targets vs Test Targets

The design above targets about 4,000 RPS at peak. A free-tier instance (single small EC2 and RDS) cannot serve that, and it is not the goal of this project.

| | Value |
|---|---|
| Design target (documented, reasoned about) | ~4,000 RPS peak |
| Load test target (actually run) | 500 concurrent users competing for the same seats |
| Pass criteria | Zero oversells, no 5xx errors, p95 hold latency under 500 ms |
| Scaling path (documented only) | Multiple app instances behind a load balancer, managed Redis cache, read replica |

The correctness result (zero oversells under contention) is what the load test proves. Throughput numbers are reported honestly for the hardware used.
