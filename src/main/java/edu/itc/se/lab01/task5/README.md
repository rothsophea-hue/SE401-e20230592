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

