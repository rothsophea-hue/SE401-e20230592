package edu.itc.se.lab01.task2;

import java.util.concurrent.atomic.AtomicLong;

public class AtomicCounter implements Counter {
    private final AtomicLong count = new AtomicLong(0);

    @Override
    public void increment() {
        count.incrementAndGet();
    }

    @Override
    public long get() {
        return count.get();
    }
}