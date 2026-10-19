
package edu.itc.se.lab01.task5;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class OrderedBank {
    static final class Account {
        final int id;
        private long balance;

        Account(int id, long balance) {
            this.id = id;
            this.balance = balance;
        }

        long balance() {
            return balance;
        }

        void withdraw(long amount) {
            balance -= amount;
        }

        void deposit(long amount) {
            balance += amount;
        }
    }

    static boolean transfer(Account from, Account to, long amount) {
        Account first = from.id < to.id ? from : to;
        Account second = from.id < to.id ? to : from;

        synchronized (first) {
            synchronized (second) {
                if (from.balance() < amount) {
                    return false;
                }

                from.withdraw(amount);
                to.deposit(amount);
                return true;
            }
        }
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
                    while (true) {
                        int x = rnd.nextInt(4);
                        int y = rnd.nextInt(3);

                        // Choose a distinct destination.
                        if (y >= x) {
                            y++;
                        }

                        if (transfer(accounts[x], accounts[y], 1)) {
                            completed.incrementAndGet();
                            break;
                        }
                    }
                }
            }, "ordered-" + w);

            workers[w].start();
        }

        for (Thread worker : workers) {
            worker.join();
        }

        long total = 0;
        for (Account account : accounts) {
            total += account.balance();
        }

        long elapsedMs =
                (System.nanoTime() - start) / 1_000_000;

        System.out.printf(
                "transfers=%d total=%d (expected 4000) in %d ms%n",
                completed.get(), total, elapsedMs);
    }
}
