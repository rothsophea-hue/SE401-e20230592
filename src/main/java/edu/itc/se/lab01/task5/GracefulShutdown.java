
package edu.itc.se.lab01.task5;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class GracefulShutdown {
    static final class Account {
        final int id;
        long balance;
        final ReentrantLock lock = new ReentrantLock();

        Account(int id, long balance) {
            this.id = id;
            this.balance = balance;
        }
    }

    static boolean transfer(
            Account from, Account to, long amount)
            throws InterruptedException {

        while (!Thread.currentThread().isInterrupted()) {
            boolean firstLocked = false;
            boolean secondLocked = false;

            try {
                firstLocked = from.lock.tryLock(
                        10, TimeUnit.MILLISECONDS);

                if (firstLocked) {
                    secondLocked = to.lock.tryLock(
                            10, TimeUnit.MILLISECONDS);
                }

                if (firstLocked && secondLocked) {
                    if (from.balance < amount) {
                        return false;
                    }

                    from.balance -= amount;
                    to.balance += amount;
                    return true;
                }
            } finally {
                if (secondLocked) {
                    to.lock.unlock();
                }
                if (firstLocked) {
                    from.lock.unlock();
                }
            }

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

        int workerCount = 8;
        Thread[] workers = new Thread[workerCount];
        AtomicLong completed = new AtomicLong();

        for (int w = 0; w < workerCount; w++) {
            final int workerId = w;

            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                long myTransfers = 0;

                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        int x = rnd.nextInt(4);
                        int y = rnd.nextInt(3);

                        if (y >= x) {
                            y++;
                        }

                        if (transfer(accounts[x], accounts[y], 1)) {
                            completed.incrementAndGet();
                            myTransfers++;
                        }
                    }
                } catch (InterruptedException e) {
                    // Restore the interrupt flag before exiting.
                    Thread.currentThread().interrupt();
                } finally {
                    System.out.printf(
                            "transfer-%d stopped after %d transfers%n",
                            workerId, myTransfers);
                }
            }, "transfer-" + w);

            workers[w].start();
        }

        Thread.sleep(2000);

        System.out.println(
                "[main] interrupting " + workerCount
                        + " workers after 2000 ms");

        for (Thread worker : workers) {
            worker.interrupt();
        }

        // Give each worker at most one second to finish.
        for (Thread worker : workers) {
            worker.join(1000);
        }

        boolean allStopped = true;

        for (Thread worker : workers) {
            if (worker.isAlive()) {
                allStopped = false;
                System.out.println(
                        "[main] WARNING: " + worker.getName()
                                + " has not stopped");
            }
        }

        // No worker should still be modifying balances here.
        long total = 0;

        for (Account account : accounts) {
            account.lock.lock();
            try {
                total += account.balance;
            } finally {
                account.lock.unlock();
            }
        }

        System.out.println("transfers completed = " + completed.get());
        System.out.println("all workers stopped = " + allStopped);
        System.out.println(
                "total = " + total + " (expected 4000)");
    }
}

