
package edu.itc.se.lab01.task3;

public interface OrderQueue {
    void put(Order order) throws InterruptedException;

    Order take() throws InterruptedException;
}

// Adapter for Java's built-in blocking queue
final class BlockingOrderQueue implements OrderQueue {
    private final java.util.concurrent.BlockingQueue<Order> q;

    BlockingOrderQueue(int capacity) {
        this.q = new java.util.concurrent.ArrayBlockingQueue<>(capacity);
    }

    @Override
    public void put(Order order) throws InterruptedException {
        q.put(order);
    }

    @Override
    public Order take() throws InterruptedException {
        return q.take();
    }
}
