# Latest Value Price Service

## Overview

The **Latest Value Price Service** is an in-memory Java service that keeps track of the
**latest price per financial instrument**, based on an `asOf` timestamp provided by producers.

The service supports **atomic batch publishing**, ensuring consumers never see partial data.
Only **completed batches** are visible; cancelled or in-progress batches are ignored.

This project is intentionally implemented using **core Java + Spring Boot (optional wiring)**,
with a focus on **clarity, correctness, and concurrency safety**.

---

## Business Requirements Covered

✔ Producers upload prices in batches  
✔ Batch lifecycle: `start → upload (parallel chunks) → complete / cancel`  
✔ Atomic visibility of completed batches  
✔ Cancelled batches are fully discarded  
✔ Latest price determined by `asOf` timestamp (not arrival order)  
✔ Consumers never see partial or inconsistent data  
✔ Defensive handling of incorrect producer calls

---

## Design Principles

### 1. Atomic Visibility
- Uses immutable snapshots for visible prices
- Consumers always read from a **stable, completed state**

### 2. Thread Safety
- `ConcurrentHashMap` for in-flight batch data
- `AtomicReference<Map<...>>` for published state
- No global locks on read path

### 3. Separation of Concerns
- Interface defines the contract
- Implementation contains business logic
- Spring configuration handles wiring (optional)

### 4. Testability
- No static state
- Deterministic unit tests
- No Spring context required for tests

---

## Data Model

### PriceRecord

```java
public record PriceRecord(
        String id,
        Instant asOf,
        Map<String, Object> payload
) {}
