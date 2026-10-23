package edu.itc.se.Lab01.task5;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("--- Running Broken Bank with Watchdog ---");
        // Bank.runBrokenBank(); 

        System.out.println("\n--- Running OrderedBank (Fix A) ---");
        OrderedBank.run();

        System.out.println("\n--- Running TryLockBank (Fix B) ---");
        TryLockBank.run();

        System.out.println("\n--- Running Graceful Shutdown ---");
        GracefulBank.run();
    }
}

class Account {
    final int id; private long balance;
    Account(int id, long balance) { this.id = id; this.balance = balance; }
    long balance() { return balance; }
    void withdraw(long a) { balance -= a; }
    void deposit(long a) { balance += a; }
}

class Bank {
    static void transfer(Account from, Account to, long amount) {
        synchronized (from) {
            synchronized (to) {
                if (from.balance() >= amount) { from.withdraw(amount); to.deposit(amount); }
            }
        }
    }

    public static void runBrokenBank() throws InterruptedException {
        Thread watchdog = new Thread(() -> {
            ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            while (true) {
                long[] threadIds = bean.findDeadlockedThreads(); 
                if (threadIds != null) {
                    ThreadInfo[] infos = bean.getThreadInfo(threadIds);
                    System.out.print("[watchdog] DEADLOCK detected between: ");
                    for (int i = 0; i < infos.length; i++) {
                        System.out.print(infos[i].getThreadName() + (i < infos.length - 1 ? ", " : ""));
                    }
                    System.out.println();
                    break;
                }
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
            }
        });
        watchdog.setDaemon(true);
        watchdog.start();

        Account[] accounts = new Account[4];
        for (int i = 0; i < accounts.length; i++) accounts[i] = new Account(i, 1_000);
        
        Thread[] workers = new Thread[8];
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                for (int i = 0; i < 100_000; i++) {
                    Account a = accounts[rnd.nextInt(accounts.length)];
                    Account b = accounts[rnd.nextInt(accounts.length)];
                    if (a != b) transfer(a, b, rnd.nextInt(1, 50));
                }
            }, "transfer-" + w);
            workers[w].start();
        }
        for (Thread t : workers) t.join();
    }
}

class OrderedBank {
    static void transfer(Account from, Account to, long amount) {
        Account first = from.id < to.id ? from : to;
        Account second = from.id < to.id ? to : from;

        synchronized (first) {
            synchronized (second) {
                if (from.balance() >= amount) { from.withdraw(amount); to.deposit(amount); }
            }
        }
    }

    public static void run() throws InterruptedException {
        long t0 = System.currentTimeMillis();
        Account[] accounts = new Account[4];
        for (int i = 0; i < accounts.length; i++) accounts[i] = new Account(i, 1_000);

        Thread[] workers = new Thread[8];
        AtomicInteger transfers = new AtomicInteger(0);
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                for (int i = 0; i < 100_000; i++) {
                    Account a = accounts[rnd.nextInt(accounts.length)];
                    Account b = accounts[rnd.nextInt(accounts.length)];
                    if (a != b) {
                        transfer(a, b, rnd.nextInt(1, 50));
                        transfers.incrementAndGet();
                    }
                }
            }, "transfer-" + w);
            workers[w].start();
        }
        for (Thread t : workers) t.join();
        
        long total = 0; for (Account a : accounts) total += a.balance();
        System.out.printf("transfers=%d total=%d (expected 4000) in %d ms%n", transfers.get(), total, (System.currentTimeMillis() - t0));
    }
}

class LockAccount {
    final int id; private long balance;
    final ReentrantLock lock = new ReentrantLock(); // gives each account a ReentrantLock[cite: 18]
    
    LockAccount(int id, long balance) { this.id = id; this.balance = balance; }
    long balance() { return balance; }
    void withdraw(long a) { balance -= a; }
    void deposit(long a) { balance += a; }
}

class TryLockBank {
    static void transfer(LockAccount from, LockAccount to, long amount, AtomicInteger retries) {
        while (true) {
            try {
                if (from.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                    try {
                        if (to.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                            try {
                                if (from.balance() >= amount) { from.withdraw(amount); to.deposit(amount); }
                                return;
                            } finally {
                                to.lock.unlock();
                            }
                        }
                    } finally {
                        from.lock.unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            retries.incrementAndGet();
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(1, 10)); // random back-off on failure[cite: 18]
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public static void run() throws InterruptedException {
        long t0 = System.currentTimeMillis();
        LockAccount[] accounts = new LockAccount[4];
        for (int i = 0; i < accounts.length; i++) accounts[i] = new LockAccount(i, 1_000);

        Thread[] workers = new Thread[8];
        AtomicInteger transfers = new AtomicInteger(0);
        AtomicInteger retries = new AtomicInteger(0);
        
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                for (int i = 0; i < 100_000; i++) {
                    LockAccount a = accounts[rnd.nextInt(accounts.length)];
                    LockAccount b = accounts[rnd.nextInt(accounts.length)];
                    if (a != b) {
                        transfer(a, b, rnd.nextInt(1, 50), retries);
                        transfers.incrementAndGet();
                    }
                }
            }, "transfer-" + w);
            workers[w].start();
        }
        for (Thread t : workers) t.join();
        long total = 0; for (LockAccount a : accounts) total += a.balance();
        System.out.printf("transfers=%d retries=%d total=%d (expected 4000) in %d ms%n", transfers.get(), retries.get(), total, (System.currentTimeMillis() - t0));
    }
}

class GracefulBank {
    public static void run() throws InterruptedException {
        LockAccount[] accounts = new LockAccount[4];
        for (int i = 0; i < accounts.length; i++) accounts[i] = new LockAccount(i, 1_000);

        Thread[] workers = new Thread[8];
        AtomicInteger transfers = new AtomicInteger(0);
        
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                int localTransfers = 0;
                
                while (!Thread.currentThread().isInterrupted()) {
                    LockAccount a = accounts[rnd.nextInt(accounts.length)];
                    LockAccount b = accounts[rnd.nextInt(accounts.length)];
                    
                    if (a != b) {
                        TryLockBank.transfer(a, b, rnd.nextInt(1, 50), new AtomicInteger(0));
                        if (Thread.currentThread().isInterrupted()) break; // Catch interrupt flag set by transfer
                        localTransfers++;
                        transfers.incrementAndGet();
                    }
                }
                System.out.printf("%s stopped after %d transfers%n", Thread.currentThread().getName(), localTransfers);
            }, "transfer-" + w);
            workers[w].start();
        }
        Thread.sleep(2000);
        System.out.println("[main] interrupting 8 workers after 2000 ms");
        for (Thread t : workers) t.interrupt();
        for (Thread t : workers) t.join(1000);
        long total = 0; for (LockAccount a : accounts) total += a.balance();
        System.out.printf("all workers stopped=true total=%d (expected 4000)%n", total);
    }
}
