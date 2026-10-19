
package edu.itc.se.lab01.task3;

import java.util.concurrent.ThreadLocalRandom;

public class Producer implements Runnable {
    private final OrderQueue queue;
    private final int producerId;
    private final int numberOfOrders;

    public Producer(OrderQueue queue, int producerId, int numberOfOrders) {
        this.queue = queue;
        this.producerId = producerId;
        this.numberOfOrders = numberOfOrders;
    }

    @Override
    public void run() {
        try {
            for (int i = 1; i <= numberOfOrders; i++) {
                int id = (producerId - 1) * numberOfOrders + i;
                int qty = ThreadLocalRandom.current().nextInt(1, 6);

                Order order = new Order(id, "item-" + id, qty);

                queue.put(order);

                // Random pause between 0 and 20 milliseconds
                Thread.sleep(ThreadLocalRandom.current().nextInt(21));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("producer-" + producerId + " interrupted");
        }
    }
}
