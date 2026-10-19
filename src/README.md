## Task 1: Creating and Running Threads
### Implementation Details
Four different thread creation techniques were implemented:
1. **Subclassing `Thread`**: `Countdown` class overriding `run()`.
2. **Implementing `Runnable`**: `CountdownTask` class.
3. **Lambda Expression**: Inline `Runnable` passed to `Thread`.
4. **Virtual Thread Builder**: `Thread.ofVirtual().name("artemis")`.

### `--sequential` vs. Concurrent Mode Explanation
* **Concurrent Mode (`start()`)**: Each thread is scheduled independently by the JVM/OS. Consequently, `"Mission control: all launched"` prints immediately after thread creation, while the countdown outputs interleave concurrently before `"Mission control: all landed"` prints at the end.
* **Sequential Mode (`--sequential`, calling `run()` directly)**: Calling `run()` executes the task synchronously on the main thread rather than spawning a new execution context. Every countdown finishes sequentially before the next one starts, and `"Mission control: all launched"` appears *after* all countdowns complete.

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

##Task 3: Producer-Consumer Pattern

## 1. Objective

Implement a bounded buffer using Java synchronized methods,
wait(), and notifyAll(). Then replace the custom buffer with
ArrayBlockingQueue and compare the implementations.

## 2. Project Configuration

- Producers: 2
- Consumers: 3
- Orders per producer: 50
- Total expected orders: 100
- Buffer capacity: 5
- Poison pills: 3

## 3. Custom Bounded Buffer

The BoundedBuffer class uses an ArrayDeque to store orders.

The put() method waits while the buffer is full.
The take() method waits while the buffer is empty.

Both methods use synchronized to protect shared data.
After changing the buffer, notifyAll() wakes waiting threads
so they can check whether they can continue.

## 4. BlockingQueue Implementation

Main2 uses ArrayBlockingQueue instead of BoundedBuffer.

ArrayBlockingQueue already implements thread-safe blocking
operations. Its put() method waits when the queue is full,
and take() waits when it is empty.

This removes the need to implement wait(), notifyAll(),
and synchronized buffer operations manually.

## 5. Experiment: notifyAll() vs notify()

Changing notifyAll() to notify() can cause a thread to wake
that cannot proceed, while another thread that could proceed
remains waiting.

For example, if the buffer is full, a producer must wait.
When a consumer removes an order, a producer can proceed.
However, notify() may wake another consumer waiting for an
order instead. That consumer checks the empty buffer and
waits again, while the producer remains asleep.

This can cause a deadlock in certain thread schedules.

## 6. Experiment: while vs if

Changing while to if is unsafe because a thread can wake up
without the required condition being true.

For example, multiple consumers may be waiting on an empty
buffer. After one order is added, several threads may wake.
One consumer removes the order first. Another consumer may
then continue without rechecking whether the buffer is empty.

This can cause an exception or incorrect behavior.

The while loop checks the condition again after every wake-up,
making the implementation safer.

## 7. Conclusion

The custom BoundedBuffer demonstrates how synchronization,
wait(), and notifyAll() coordinate multiple threads.

ArrayBlockingQueue provides the same basic producer-consumer
functionality with much less code and fewer opportunities
for synchronization mistakes.

Both implementations should process exactly 100 orders
when the program completes successfully.



# Task 4: Thread Pools, Future, and Virtual Threads

## 1. Objective

This project demonstrates how to use Java concurrency tools:
- ExecutorService and thread pools
- Callable and Future
- Exception handling with ExecutionException
- Timeouts and task cancellation
- Platform threads versus virtual threads
- CompletableFuture for asynchronous tasks

## 2. CPU-Bound Work: Prime Counting

The program counts prime numbers below 4,000,000.

First, it runs the calculation using a single thread. Next, it divides the range into 16 chunks and submits the chunks to a fixed thread pool. The pool size is based on the number of available processors.

Each task returns its result through a Future<Long>. The main thread collects all 16 results and adds them together.

The two totals should match.

Parallel processing may be faster because multiple CPU cores can process separate chunks simultaneously. However, the speedup depends on the computer's CPU, available processors, JVM optimization, and system load.

## 3. I/O-Bound Work: Simulated Downloads

The program submits 500 simulated downloads. Each normal download sleeps for 200 milliseconds to represent waiting for a network response.

It compares:

1. A fixed pool of 20 platform threads.
2. A virtual-thread-per-task executor.

### Platform threads

A platform thread is an operating-system-backed thread. The fixed pool limits the number of concurrently running tasks to 20.

When tasks wait for simulated network responses, their platform threads remain occupied. Tasks waiting in the executor queue cannot start until a worker becomes available.

### Virtual threads

Virtual threads are lightweight Java threads managed by the JVM. They are designed to support large numbers of tasks that spend much of their time waiting.

While a virtual thread waits during Thread.sleep(), the JVM can schedule other virtual threads to run.

Therefore, virtual threads can complete many I/O-bound tasks faster than a small fixed pool of platform threads.

The measured times are not guaranteed. They depend on the machine, JVM version, and system load.

## 4. Failure Handling

Every 50th numbered download throws IOException("boom").

For 500 downloads, 10 tasks fail and 490 succeed.

The program uses Future.get() to retrieve results. When a task fails, get() throws ExecutionException. The program catches this exception, counts the failure, and continues processing the remaining tasks.

Expected summary:

ok=490 failed=10

## 5. Timeout and Cancellation

The timeout test starts one simulated download that sleeps for five seconds.

The program waits for at most one second using:

future.get(1, TimeUnit.SECONDS)

If the timeout expires, it catches TimeoutException and calls future.cancel(true).

The true argument requests interruption of the running task. Since the task uses Thread.sleep(), it responds to interruption. Cancellation is not a guarantee that every possible task stops immediately.

## 6. CompletableFuture Bonus

The bonus implementation uses supplyAsync() to submit asynchronous tasks.

CompletableFuture.allOf() waits for all tasks to finish. handle() processes either a successful result or a failure, returning a fallback string when a download fails.

This demonstrates an alternative to collecting each Future individually.

## 7. Conclusion

ExecutorService simplifies task management compared with manually creating threads.

Future allows the program to retrieve results and detect failures. Timeouts prevent waiting indefinitely, while cancellation requests that unnecessary work stop.

For CPU-bound work, a platform-thread pool can use multiple CPU cores. For I/O-bound work, virtual threads can handle many concurrent waiting tasks with less thread-management overhead.

The best choice depends on the workload and should be evaluated through measurement.


## Task 5: Diagnosing a Deadlock and Shutting Down Gracefully

### Deadlock Reproduction & `jstack`
Running the uncoordinated `Bank` class results in a circular wait condition where transfer threads acquire locks on accounts in opposing orders (`Account A -> Account B` vs `Account B -> Account A`).
* **Watchdog Thread**: Detects deadlocked threads using `ManagementFactory.getThreadMXBean().findDeadlockedThreads()`.
* **`deadlock.txt`**: Contains the full `jstack` excerpt capturing the Java-level deadlock between transfer threads.

### Fixes Verified
1. **`OrderedBank` (Lock Ordering)**: Acquires locks in ascending order of account IDs (`id`), eliminating circular wait. Successfully completes 800,000 transfers with total balance invariant maintained (`4,000`).
2. **`TryLockBank`**: Uses `ReentrantLock` and `tryLock(10, MILLISECONDS)` with back-off retry logic.

### Graceful Shutdown
Transfer worker loops check `while (!Thread.currentThread().isInterrupted())`. After 2 seconds, `main` interrupts workers, joins them with a 1-second timeout, ensuring all threads exit cleanly and restore the interrupt flag upon catching `InterruptedException`.


# Parallel word-frequency analyser (`WordCount`)

Command-line tool that scans a directory of text files in parallel, shows live progress,
and stops cleanly on Ctrl+C or `--timeout`. JDK only (Java 21); JUnit 6 is used for tests.

## Build and run

```bash
mvn -q compile                                   # classes go to target/classes
java -cp target/classes edu.itc.se.lab01.challenge.DataGenerator data 200 50000
java -cp target/classes edu.itc.se.lab01.challenge.WordCount data --top 5 --timeout 30
java -cp target/classes edu.itc.se.lab01.challenge.WordCount data --top 3 --virtual
mvn test                                         # JUnit 6 tests
java -cp target/classes edu.itc.se.lab01.challenge.Bench data 5   # regenerate the table below
```

Without Maven: `javac -d target/classes src/main/java/edu/itc/se/lab01/challenge/*.java`.

Options: `<dir> [--top N] [--timeout SECONDS] [--virtual] [--workers N]`.
`--workers` is an addition to the specified CLI (needed for the 1/2/4/8 measurements);
the default is the number of cores. Exit code: `0` complete, `1` partial, `2` bad usage.

## Files

| File | Purpose |
|---|---|
| `WordCount.java` | The tool: scanner, bounded queue, workers, reporter, shutdown, result/CSV |
| `DataGenerator.java` | Writes N files of M random words (fixed 2 000-word vocabulary, skewed distribution) |
| `ReferenceCount.java` | Single-threaded reference (`HashMap`, no regex, no threads) |
| `Bench.java` | Runs 1/2/4/8 platform workers and virtual threads, prints the table below |
| `WordCountTest.java` | JUnit 6 tests (equality, timeout, Ctrl+C simulation, thread leaks, CSV) |

## Design

```
scanner (1 thread) --put--> ArrayBlockingQueue<Path>(16) --take--> N workers --> ConcurrentHashMap<String, LongAdder>
                                                                       reporter (every 500 ms, read-only) ----^
```

* **Back-pressure.** `queue.put` blocks once 16 paths are waiting, so the scanner cannot run ahead and memory stays bounded.
* **Completion.** The scanner puts one poison pill per worker in a `finally` block, so workers terminate even if the directory walk fails. A `CountDownLatch` tells the main thread when all workers have finished.
* **Progress.** The reporter reads `AtomicInteger`/`LongAdder` counters and iterates the map with its weakly consistent, lock-free iterator. It never locks the map and never blocks a worker.
* **Cancellation.** `--timeout` (main thread) and the JVM shutdown hook (Ctrl+C / SIGTERM) both call the same `shutdown()`. It claims a single `AtomicBoolean`, interrupts the scanner and all workers (`shutdownNow`), waits at most 2 s in total, then prints the `[PARTIAL]` result. Whoever claims the flag owns the final printout; every other path only waits for it. This also resolves the race "workers finish *because* they were interrupted" (no false `DONE`).
* **Interrupts.** Workers test the interrupt flag between files and between lines, and restore it when `take()` throws `InterruptedException`. A file abandoned mid-way is not counted in `files`, but the words already counted from it stay in the map, which is acceptable for a result labelled `[PARTIAL]`.
* **Tokenizer.** Split on `[^\p{L}]+`, skip empty tokens, `toLowerCase(Locale.ROOT)`; input is read as UTF-8 (invalid bytes are replaced, not fatal).
* **Virtual mode.** `--virtual` runs 64 worker tasks on `newVirtualThreadPerTaskExecutor()`; the work per task is identical.

## Verification

* `parallelResultEqualsSingleThreadedReference` compares `WordCount`'s word-to-count map with `ReferenceCount` on a 20-file fixture for 1, 2, 4, 8 platform workers and virtual threads.
* `timeoutOneSecondStopsWithinThreeSecondsOn5000Files` runs with `--timeout 1` semantics on 5 000 files and asserts `[PARTIAL]` and elapsed < 3 s. It sets the package-private `fileDelayMs` test hook (5 ms per file) so that the run is slow on any machine; without the hook a fast machine could finish 5 000 tiny files before the deadline.
* `shutdownCalledFromAnotherThreadActsLikeCtrlC` calls `shutdown()` from a separate thread exactly as the hook does.
* All run/timeout tests also assert that no `scanner` / `wc-*` thread survives `run()`.

Manual checks on the 200 x 50 000-word data set: `--timeout 1` printed `[PARTIAL]` at 1.03 s and the process exited at about 1.2 s wall-clock. A real `SIGINT` sent at 1.2 s printed `[PARTIAL]` and the JVM exited 0.09 s later (exit code 130, no stray threads). The full result matched the reference: 10 000 000 words, identical top-5 counts.

## Scaling results

Data set: 200 files x 50 000 words (10 000 000 words, 62 MB). Median of 5 in-process runs after one warm-up run (`Bench`).
**Machine: a 2-vCPU Linux sandbox, OpenJDK 21.0.12.** Because there are only two hardware threads, the curve flattens at 2 workers. Re-run `Bench` on your own computer and replace this table with its output before submitting.

| Mode | Median elapsed (s) | Best (s) | Speed-up vs 1 worker |
|---|---:|---:|---:|
| 1 platform worker | 2.36 | 2.29 | 1.00x |
| 2 platform workers | 1.68 | 1.45 | 1.41x |
| 4 platform workers | 1.64 | 1.43 | 1.44x |
| 8 platform workers | 1.75 | 1.51 | 1.35x |
| virtual threads (64 tasks) | 1.69 | 1.50 | 1.40x |

The single-threaded reference needs about 2.2 s on the same data (one run). Going from 1 to 2 workers helps because the work is CPU-bound; beyond the core count extra threads only add scheduling overhead. Virtual threads perform like a platform pool of similar size here, since the files are read from the OS cache and there is almost no blocking for them to hide.

## Why `LongAdder` / `merge` beats `synchronized` on the map

A `synchronized` map (or `Collections.synchronizedMap`) uses one monitor for every operation. Every increment of every word, even for different words, must take that single lock, so workers queue behind each other and the parallel part of the program shrinks to almost nothing (Amdahl's law); the read-then-write of an increment also has to sit inside the lock to stay atomic. `ConcurrentHashMap` removes the global lock: reads of existing keys take no lock, and inserting a new key locks only one bin. Using `LongAdder` values makes the hot path lock-free: `increment()` is a CAS on a base cell and, only under contention (typically on very frequent words such as "the"), spreads updates over several cells, which are added up by `sum()` when a total is read. An `AtomicLong` would make all threads retry on the same cell for such a word, and `merge(w, 1L, Long::sum)` takes the bin lock and boxes a `Long` on every call, although it is still far better than a global lock. The reporter benefits too: `sum()` and map iteration need no lock, so progress reporting cannot slow the workers. (The code additionally calls `get()` before `computeIfAbsent()`, so the common case of an already known word never touches a lock.) This reasoning is standard; I did not benchmark a `synchronized` variant, and on a 2-core machine the difference would be small.

## Coffman conditions

A deadlock needs all four of mutual exclusion, hold-and-wait, no preemption and circular wait; this design rules out three of them. **Hold-and-wait** is excluded because no thread ever holds a lock or resource while waiting for another: workers call `take()` holding nothing, the counting code uses only lock-free counters and the map's own short internal bin locks (never nested with anything else), and the scanner blocks on `put()` while holding nothing but its directory stream. **Circular wait** is excluded because there is no lock ordering to violate: the only dependency is a one-way pipeline (the scanner waits for free space only while workers are draining the queue; workers wait for data only while the scanner is producing or poison pills are guaranteed to arrive from its `finally` block), and no thread ever waits on a resource owned by a thread that is waiting for it. **No preemption** is broken for the only blocking operations that exist: `take()`, `put()`, `sleep` and channel reads are interruptible, so `shutdown()` can forcibly take every waiting thread out of its wait, which also makes shutdown independent of what the other threads are doing. **Mutual exclusion** cannot be removed entirely (the queue's internal lock and the map's bin locks are exclusive), but it is limited to very short critical sections that never block on anything else, which is why it cannot contribute to a deadlock.
