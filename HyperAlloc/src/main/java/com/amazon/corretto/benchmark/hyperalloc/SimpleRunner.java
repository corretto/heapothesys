package com.amazon.corretto.benchmark.hyperalloc;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Simple runner class to evenly divide the load and sent to multiple runners.
 */
public class SimpleRunner extends TaskBase {

    private final SimpleRunConfig config;

    public SimpleRunner(SimpleRunConfig config) {
        this.config = config;
    }

    @Override
    public void start() {
        try {
            AllocObject.setOverhead(config.isUseCompressedOops() ? AllocObject.ObjectOverhead.CompressedOops
                    : AllocObject.ObjectOverhead.NonCompressedOops);
            final ObjectStore store = new ObjectStore(config.getLongLivedInMb(), config.getPruneRatio(),
                    config.getReshuffleRatio());
            new Thread(store).start();
            final ExecutorService executor = Executors.newFixedThreadPool(config.getNumOfThreads());
            final List<Future<Long>> results = executor.invokeAll(createTasks(store));

            long sum = 0;
            try {
                for (Future<Long> r : results) {
                    final long t = r.get();
                    sum += t;
                }
            } catch (ExecutionException ex) {
                ex.printStackTrace();
                printResult(-1);
                System.exit(1);
            }

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
            printResult((config.getAllocRateInMbPerSecond() * 1024L * 1024L * config.getDurationInSecond() - sum)
                    / config.getDurationInSecond() / 1024 / 1024);
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private void printResult(final long realAllocRate) throws IOException {
        try (FileWriter fw = new FileWriter(config.getLogFile(), true)) {
            fw.write(config.getHeapSizeInMb() + ","
                    + config.getAllocRateInMbPerSecond() + ","
                    + realAllocRate + ","
                    + ((double) (config.getLongLivedInMb() + config.getMidAgedInMb()) / config.getHeapSizeInMb()) + ","
                    + config.isUseCompressedOops() + ","
                    + config.getNumOfThreads() + ","
                    + config.getMinObjectSize() + ","
                    + config.getMaxObjectSize() + ","
                    + config.getPruneRatio() + ","
                    + config.getReshuffleRatio() + ",\n"
            );
        }
    }

    private List<Callable<Long>> createTasks(final ObjectStore store) {
        final int queueSize = (int) (config.getMidAgedInMb() * 1024L * 1024L * 2L
                / (config.getMaxObjectSize() + config.getMinObjectSize())
                / config.getNumOfThreads());

        return IntStream.range(0, config.getNumOfThreads())
                .mapToObj(i -> createSingle(store, config.getAllocRateInMbPerSecond() / config.getNumOfThreads(),
                        config.getDurationInSecond() * 1000, config.getMinObjectSize(),
                        config.getMaxObjectSize(), queueSize))
                .collect(Collectors.toList());
    }
}
