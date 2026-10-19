
package edu.itc.se.lab01.task5;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class Bank {
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

    // Deliberately broken: different transfers may lock in opposite orders.
    static void transfer(Account from, Account to, long amount) {
        synchronized (from) {
            synchronized (to) {
                if (from.balance() >= amount) {
                    from.withdraw(amount);
                    to.deposit(amount);
                }
            }
        }
    }

    static void startWatchdog() {
        Thread watchdog = new Thread(() -> {
            ThreadMXBean bean =
                    ManagementFactory.getThreadMXBean();

            while (true) {
                long[] ids = bean.findDeadlockedThreads();

                if (ids != null) {
                    ThreadInfo[] infos =
                            bean.getThreadInfo(ids, true, true);

                    String names = Arrays.stream(infos)
                            .filter(info -> info != null)
                            .map(ThreadInfo::getThreadName)
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("unknown");

                    System.out.println(
                            "[watchdog] DEADLOCK detected between: "
                                    + names);
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "deadlock-watchdog");

        watchdog.setDaemon(true);
        watchdog.start();
    }

    public static void main(String[] args)
            throws InterruptedException {
        Account[] accounts = new Account[4];

        for (int i = 0; i < accounts.length; i++) {
            accounts[i] = new Account(i, 1000);
        }

        startWatchdog();

        Thread[] workers = new Thread[8];

        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();

                for (int i = 0; i < 100_000; i++) {
                    Account a = accounts[rnd.nextInt(4)];
                    Account b = accounts[rnd.nextInt(4)];

                    if (a != b) {
                        transfer(a, b, rnd.nextInt(1, 50));
                    }
                }
            }, "transfer-" + w);

            workers[w].start();
        }

        // Intentionally waits forever if the deadlock occurs.
        for (Thread worker : workers) {
            worker.join();
        }

        long total = 0;
        for (Account account : accounts) {
            total += account.balance();
        }

        System.out.println("total = " + total);
    }
}
