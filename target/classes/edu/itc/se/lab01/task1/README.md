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