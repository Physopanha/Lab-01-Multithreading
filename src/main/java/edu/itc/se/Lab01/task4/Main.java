package edu.itc.se.Lab01.task4;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class PrimeCounter implements Callable<Long> {
    private final long from, to;

    public PrimeCounter(long from, long to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public Long call() {
        return Main.countPrimes(from, to);
    }
}

class FakeDownload implements Callable<String> {
    private final String url;
    private final int index;

    public FakeDownload(String url, int index) {
        this.url = url;
        this.index = index;
    }

    @Override
    public String call() throws Exception {
        if (index == 42) { 
            Thread.sleep(5000);
        } else if (index % 50 == 0) { 
            throw new IOException("boom");
        } else {
            Thread.sleep(200); 
        }
        return url + " -> " + url.length(); 
    }
}

public class Main {
    static long countPrimes(long from, long to) {
        long count = 0;
        for (long n = Math.max(from, 2); n < to; n++) {
            boolean prime = true;
            for (long d = 2; d * d <= n; d++) {
                if (n % d == 0) { prime = false; break; }
            }
            if (prime) count++;
        }
        return count;
    }

    public static void main(String[] args) throws Exception {
        final long LIMIT = 4_000_000, CHUNKS = 16;
        long t0 = System.nanoTime();
        long single = countPrimes(0, LIMIT);
        System.out.printf("single-threaded: %d primes in %d ms%n", single, (System.nanoTime() - t0) / 1_000_000);
        t0 = System.nanoTime();
        try (ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            List<Future<Long>> futures = new ArrayList<>();
            long chunkSize = LIMIT / CHUNKS;
            for (int i = 0; i < CHUNKS; i++) {
                long start = i * chunkSize;
                long end = (i == CHUNKS - 1) ? LIMIT : start + chunkSize;
                futures.add(pool.submit(new PrimeCounter(start, end)));
            }

            long total = 0;
            for (Future<Long> f : futures) {
                total += f.get();
            }
            System.out.printf("pool: %d primes in %d ms%n", total, (System.nanoTime() - t0) / 1_000_000);
        }
        try (ExecutorService fixedPool = Executors.newFixedThreadPool(20)) {
            runDownloads(fixedPool, "downloads on fixed(20) platform threads");
        }
        try (ExecutorService virtualPool = Executors.newVirtualThreadPerTaskExecutor()) {
            runDownloads(virtualPool, "downloads on virtual threads");
        }
        runDownloadsCompletableFuture();
    }

    static void runDownloads(ExecutorService executor, String label) {
        long t0 = System.nanoTime();
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            futures.add(executor.submit(new FakeDownload("http://example.com/file" + i, i)));
        }

        int ok = 0, failed = 0, timedOut = 0;

        for (Future<String> future : futures) {
            try {
                future.get(1, TimeUnit.SECONDS);
                ok++;
            } catch (TimeoutException e) {
                future.cancel(true);
                timedOut++;
            } catch (ExecutionException e) {
                failed++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("%s : ok=%d failed=%d timedOut=%d in %d ms%n", label, ok, failed, timedOut, ms);
    }
    static void runDownloadsCompletableFuture() {
        long t0 = System.nanoTime();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<String>> cfs = new ArrayList<>();
            for (int i = 1; i <= 500; i++) {
                final int idx = i;
                CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> {
                    try {
                        return new FakeDownload("http://example.com/file" + idx, idx).call();
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }, executor)
                .completeOnTimeout("timeout fallback string", 1, TimeUnit.SECONDS)
                .handle((res, ex) -> {
                    if (ex != null) return "failure fallback string";
                    return res;
                });
                cfs.add(cf);
            }
            CompletableFuture.allOf(cfs.toArray(new CompletableFuture[0])).join();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            System.out.printf("CompletableFuture bonus finished in %d ms%n", ms);
        }
    }
}
