
package edu.itc.se.lab01.task5;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class TryLockBank {
    static final class Account {
        final int id;
        private long balance;
        final ReentrantLock lock = new ReentrantLock();

        Account(int id, long balance) {
            this.id = id;
            this.balance = balance;
        }
    }

    static final AtomicLong retries = new AtomicLong();

    static boolean transfer(
            Account from, Account to, long amount)
            throws InterruptedException {

        while (!Thread.currentThread().isInterrupted()) {
            boolean gotFrom = false;
            boolean gotTo = false;

            try {
                gotFrom = from.lock.tryLock(
                        10, TimeUnit.MILLISECONDS);

                if (gotFrom) {
                    gotTo = to.lock.tryLock(
                            10, TimeUnit.MILLISECONDS);
                }

                if (gotFrom && gotTo) {
                    if (from.balance < amount) {
                        return false;
                    }

                    from.balance -= amount;
                    to.balance += amount;
                    return true;
                }
            } finally {
                if (gotTo) {
                    to.lock.unlock();
                }
                if (gotFrom) {
                    from.lock.unlock();
                }
            }

            retries.incrementAndGet();

            // Random back-off, interruptible.
            Thread.sleep(ThreadLocalRandom.current()
                    .nextLong(1, 4));
        }

        return false;
    }

    public static void main(String[] args)
            throws InterruptedException {
        Account[] accounts = new Account[4];

        for (int i = 0; i < accounts.length; i++) {
            accounts[i] = new Account(i, 1000);
        }

        int workersCount = 8;
        int transfersPerWorker = 100_000;
        AtomicLong completed = new AtomicLong();
        Thread[] workers = new Thread[workersCount];

        long start = System.nanoTime();

        for (int w = 0; w < workersCount; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();

                for (int i = 0; i < transfersPerWorker; i++) {
                    boolean done = false;

                    while (!done &&
                            !Thread.currentThread().isInterrupted()) {
                        int x = rnd.nextInt(4);
                        int y = rnd.nextInt(3);

                        if (y >= x) {
                            y++;
                        }

                        try {
                            done = transfer(
                                    accounts[x], accounts[y], 1);

                            if (done) {
                                completed.incrementAndGet();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }

                    if (!done) {
                        return;
                    }
                }
            }, "trylock-" + w);

            workers[w].start();
        }

        for (Thread worker : workers) {
            worker.join();
        }

        long total = 0;

        for (Account account : accounts) {
            account.lock.lock();
            try {
                total += account.balance;
            } finally {
                account.lock.unlock();
            }
        }

        long elapsedMs =
                (System.nanoTime() - start) / 1_000_000;

        System.out.printf(
                "transfers=%d retries=%d total=%d "
                        + "(expected 4000) in %d ms%n",
                completed.get(), retries.get(), total, elapsedMs);
    }
}


