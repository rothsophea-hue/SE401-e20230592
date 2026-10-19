package edu.itc.se.lab01.task3;

public class BoundedBuffer implements OrderQueue {
    private final java.util.ArrayDeque<Order> items = new java.util.ArrayDeque<>(); // as storage
    private final int capacity;
    public BoundedBuffer(int capacity) { this.capacity = capacity; }

    @Override public synchronized void put(Order order) throws InterruptedException {
    
        
        while (items.size() == capacity) { 
            wait();
        }
        items.addLast(order);
        notifyAll();
    }
    @Override public synchronized Order take() throws InterruptedException {
       
        while (items.isEmpty()){
            wait();
        }
        Order order = items.removeFirst();

        notifyAll();

        return order;
    }
}