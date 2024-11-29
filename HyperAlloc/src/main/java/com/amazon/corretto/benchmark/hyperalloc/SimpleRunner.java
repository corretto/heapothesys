// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.corretto.benchmark.hyperalloc;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Simple runner class to evenly divide the load and sent to multiple runners.
 */
public class SimpleRunner extends TaskBase {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    private final SimpleRunConfig config;
    private long realAllocRate;

    public SimpleRunner(SimpleRunConfig config) {
        this.config = config;
    }

    static Logger logger = Logger.getGlobal();

    @Override
    public void start() {
        System.out.println("Starting a SimpleRunner");
        try {
            AllocObject.setOverhead(config.isUseCompressedOops() ? AllocObject.ObjectOverhead.CompressedOops
                    : AllocObject.ObjectOverhead.NonCompressedOops);
            final ObjectStore store = new ObjectStore(config.getLongLivedInMb(), config.getPruneRatio(),
                    config.getReshuffleRatio());
            final Thread storeThread = new Thread(store);
            storeThread.setDaemon(true);
            storeThread.setName("HyperAlloc-Store");
            storeThread.start();

            AllocationRateLogger allocationLogger = new AllocationRateLogger(config.getAllocationLogFile());
            allocationLogger.start();

            final ExecutorService executor = Executors.newFixedThreadPool(config.getNumOfThreads(), runnable -> {
                Thread thread = new Thread(runnable);
                thread.setDaemon(true);
                thread.setName("HyperAlloc-" + THREAD_COUNTER.incrementAndGet());
                return thread;
            });

            final List<Future<Long>> results = executor.invokeAll(createTasks(store));

            try {
                for (Future<Long> r : results) {
                    r.get();
                }
            } catch (ExecutionException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                System.exit(1);
            }

            allocationLogger.stop();

            executor.shutdown();
            try {
                // All tasks should already be idle, but we'll still
                // allow 60 seconds for termination.
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow(); // Recommended protocol is overkill
                    if (!executor.awaitTermination(60, TimeUnit.SECONDS))
                        System.err.println("Executor pool did not terminate!\n");
                }
            } catch (InterruptedException ie) {
                System.err.println("Unexpected exception shutting down Executor pool\n");
                // Recancel just to be sure
                executor.shutdownNow();
                // Preserve interrupt status
                Thread.currentThread().interrupt();
            }
            store.stopAndReturnSize();
            realAllocRate = AllocObject.getBytesAllocated() / 1024 / 1024 / config.getDurationInSecond();
            try (RunReport report = new RunReport(config.getLogFile())) {
                this.writeOn(report);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private List<Callable<Long>> createTasks(final ObjectStore store) {
        final int queueSize = (int) (config.getMidAgedInMb() * 1024L * 1024L * 2L
                / (config.getMaxObjectSize() + config.getMinObjectSize())
                / config.getNumOfThreads());

        long allocRateMbPerThread = config.getAllocRateInMbPerSecond() / config.getNumOfThreads();
        long durationInMs = config.getDurationInSecond() * 1000L;
        IntFunction<Callable<Long>> factory;
        if (config.getAllocationSmoothnessFactor() == null) {
            factory = (ignored) -> createSingle(store, allocRateMbPerThread,
                    durationInMs, config.getMinObjectSize(),
                    config.getMaxObjectSize(), queueSize,
                    config.getRampUpSeconds());
        } else {
            factory = (ignored) -> createBurstyAllocator(store, allocRateMbPerThread,
                    durationInMs, config.getAllocationSmoothnessFactor(),
                    config.getMinObjectSize(), config.getMaxObjectSize(),
                    queueSize);
        }

        return IntStream.range(0, config.getNumOfThreads())
                .mapToObj(factory)
                .collect(Collectors.toList());
    }

    private static class AllocationRateLogger implements Runnable {

        volatile boolean shouldRun = true;
        private final Thread allocationLoggerThread;
        private final String allocationLogFile;

        public AllocationRateLogger(String allocationLogFile) {
            this.allocationLogFile = allocationLogFile;
            allocationLoggerThread = new Thread(this);
            allocationLoggerThread.setName("HyperAlloc-Allocations");
            allocationLoggerThread.setDaemon(true);
        }

        public void start() {
            if (allocationLogFile != null) {
                allocationLoggerThread.start();
            }
        }

        public void stop() {
            if (allocationLogFile != null) {
                try {
                    shouldRun = false;
                    allocationLoggerThread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public void run() {
            final long MB = 1024 * 1024;
            final long NANOS = TimeUnit.SECONDS.toNanos(1);

            long lastValue = AllocObject.getBytesAllocated();
            long lastTime = System.nanoTime();
            long startTime = lastTime;

            try (PrintWriter writer = new PrintWriter(allocationLogFile)) {
                while (shouldRun) {
                    long now = System.nanoTime();
                    long timeDeltaNs = now - lastTime;
                    long bytesAllocated = AllocObject.getBytesAllocated();

                    if (timeDeltaNs > 0) {

                        double allocationDelta = bytesAllocated - lastValue;
                        double megaBytesPerSecond = (allocationDelta * NANOS / timeDeltaNs) / MB;

                        double elapsedSecondsSinceBoot = (double) (now - startTime) / NANOS;
                        writer.printf("%.2f, %.2f\n", elapsedSecondsSinceBoot, megaBytesPerSecond);
                        writer.flush();

                        lastTime = now;
                        lastValue = bytesAllocated;
                    }

                    //noinspection BusyWait
                    Thread.sleep(100);
                }
            } catch (IOException ioe) {
                System.err.println("Cannot write to allocation log: " + allocationLogFile);
                logger.log(Level.SEVERE, ioe.getMessage(), ioe);
            } catch (InterruptedException iee) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void writeOn(RunReport report) throws IOException {
        report.write(config.getHeapSizeInMb());
        report.write(config.getAllocRateInMbPerSecond());
        report.write(realAllocRate);
        report.write((config.getLongLivedInMb() + config.getMidAgedInMb()) / config.getHeapSizeInMb());
        report.write(config.isUseCompressedOops());
        report.write(config.getNumOfThreads());
        report.write(config.getMinObjectSize());
        report.write(config.getMaxObjectSize());
        report.write(config.getPruneRatio());
        report.write(config.getReshuffleRatio());
        report.eol();
    }
}
