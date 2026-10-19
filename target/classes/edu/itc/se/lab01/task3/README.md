# Lab 01 - Task 3: Producer-Consumer Pattern

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
