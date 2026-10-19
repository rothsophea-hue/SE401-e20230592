
package edu.itc.se.lab01.task3;

import java.util.concurrent.atomic.AtomicInteger;

public class Main2 {
    private static final int PRODUCER_COUNT = 2;
    private static final int CONSUMER_COUNT = 3;
    private static final int ORDERS_PER_PRODUCER = 50;
    private static final int BUFFER_CAPACITY = 5;

    public static void main(String[] args) throws InterruptedException {
        long startTime = System.currentTimeMillis();

        // Replace the handmade buffer with Java's built-in queue
        OrderQueue queue = new BlockingOrderQueue(BUFFER_CAPACITY);
        AtomicInteger processedCount = new AtomicInteger(0);

        Thread[] consumers = new Thread[CONSUMER_COUNT];

        for (int i = 0; i < CONSUMER_COUNT; i++) {
            consumers[i] = new Thread(
                new Consumer(queue, i + 1, processedCount)
            );
            consumers[i].start();
        }

        Thread[] producers = new Thread[PRODUCER_COUNT];

        for (int i = 0; i < PRODUCER_COUNT; i++) {
            producers[i] = new Thread(
                new Producer(queue, i + 1, ORDERS_PER_PRODUCER)
            );
            producers[i].start();
        }

        for (Thread producer : producers) {
            producer.join();
        }

        for (int i = 0; i < CONSUMER_COUNT; i++) {
            queue.put(new Order(-1, "POISON", 0));
        }

        for (Thread consumer : consumers) {
            consumer.join();
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        int total = processedCount.get();

        System.out.println(
            "Total processed: " + total
            + " (expected 100) in " + elapsedTime + " ms"
        );

        if (total != 100) {
            throw new AssertionError(
                "Expected 100 processed orders, but got " + total
            );
        }
    }
}

