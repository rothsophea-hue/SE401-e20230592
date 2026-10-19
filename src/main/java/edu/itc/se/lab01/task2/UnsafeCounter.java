package edu.itc.se.lab01.task2;

public class UnsafeCounter implements Counter {
    private long count = 0;

    @Override
    public void increment() {
        count++; 
    }

    @Override
    public long get() {
        return count;
    }
}