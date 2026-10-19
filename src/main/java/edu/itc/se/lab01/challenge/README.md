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
