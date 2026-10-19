package edu.itc.se.lab01.task1;

class Countdown extends Thread {
    public Countdown(String name) {
        super(name);
    }

    @Override
    public void run() {
        for (int i = 3; i >= 1; i--) {
            System.out.println(getName() + ": " + i);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        System.out.println(getName() + ": liftoff!");
    }
}

class CountdownTask implements Runnable {
    @Override
    public void run() {
        for (int i = 3; i >= 1; i--) {
            System.out.println(Thread.currentThread().getName() + ": " + i);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        System.out.println(Thread.currentThread().getName() + ": liftoff!");
    }
}

public class Main {
    public static void main(String[] args) throws InterruptedException {
        boolean sequential = args.length > 0 && args[0].equals("--sequential");

        Thread apollo  = new Countdown("apollo");
        Thread gemini  = new Thread(new CountdownTask(), "gemini");
        Thread mercury = new Thread(new CountdownTask(), "mercury");
        Thread artemis = Thread.ofVirtual().name("artemis").unstarted(new CountdownTask());

        if (sequential) {
            apollo.run();
            gemini.run();
            mercury.run();
            artemis.run();
            System.out.println("Mission control: all launched");
        } else {
            apollo.start();
            gemini.start();
            mercury.start();
            artemis.start();
            System.out.println("Mission control: all launched");

            apollo.join();
            gemini.join();
            mercury.join();
            artemis.join();
            System.out.println("Mission control: all landed");

            System.out.printf("%-8s virtual=%-5s state=%s%n", apollo.getName(), apollo.isVirtual(), apollo.getState());
            System.out.printf("%-8s virtual=%-5s state=%s%n", gemini.getName(), gemini.isVirtual(), gemini.getState());
            System.out.printf("%-8s virtual=%-5s state=%s%n", mercury.getName(), mercury.isVirtual(), mercury.getState());
            System.out.printf("%-8s virtual=%-5s state=%s%n", artemis.getName(), artemis.isVirtual(), artemis.getState());
        }
    }
}
