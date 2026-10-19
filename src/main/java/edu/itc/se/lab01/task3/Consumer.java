
package edu.itc.se.lab01.task3;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class Consumer implements Runnable {
    private final OrderQueue queue;
    private final int consumerId;
    private final AtomicInteger processedCount;

    public Consumer(
            OrderQueue queue,
            int consumerId,
            AtomicInteger processedCount) {
        this.queue = queue;
        this.consumerId = consumerId;
        this.processedCount = processedCount;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Order order = queue.take();

                // Stop when this consumer receives a poison pill
                if (order.id() == -1) {
                    System.out.println(
                        "consumer-" + consumerId
                        + " received poison pill, stopping"
                    );
                    break;
                }

                // Simulate processing the order
                Thread.sleep(
                    ThreadLocalRandom.current().nextInt(5, 31)
                );

                processedCount.incrementAndGet();

                System.out.println(
                    "consumer-" + consumerId
                    + " processed " + order
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("consumer-" + consumerId + " interrupted");
        }
    }
}
