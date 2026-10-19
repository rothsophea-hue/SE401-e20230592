## Task 2: Fixing a Race Condition

### UnsafeCounter Results
Running `UnsafeCounter` with 8 threads performing 1,000,000 increments each consistently produces incorrect results due to lost updates caused by non-atomic read-modify-write operations (`count++`).
* **Observed Unsafe Results**: `value=3,412,907` (or similar wrong totals) `correct=false`[cite: 2].

### Correct Implementations & Performance Benchmarks
* `SynchronizedCounter`: Uses intrinsic locks (`synchronized`). Correct value reached (`8,000,000`), slower due to monitor contention overhead.
* `LockCounter`: Uses `ReentrantLock` with explicit unlocking inside a `finally` block. Correct value reached.
* `AtomicCounter`: Uses `AtomicLong` CAS operations. Correct value reached with improved performance.
* `LongAdderCounter`: Uses `LongAdder` to strip contention by dynamically striping cells. Reaches correct value with the lowest execution time under heavy contention.

### Why making only `increment()` synchronized but not `get()` leaves a bug
Making only `increment()` synchronized guarantees mutual exclusion during updates, but leaving `get()` unsynchronized violates **memory visibility** (Java Memory Model). Without proper synchronization or volatile/atomic semantics on `get()`, threads may read stale cached values from CPU registers rather than the flushed main memory value.

