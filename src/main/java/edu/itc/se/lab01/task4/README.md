
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
