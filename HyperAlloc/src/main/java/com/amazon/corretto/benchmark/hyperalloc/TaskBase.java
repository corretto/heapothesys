// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.corretto.benchmark.hyperalloc;

import java.util.ArrayDeque;
import java.util.concurrent.Callable;

public abstract class TaskBase {
    // The default maximum object size.
    protected static final int DEFAULT_MAX_OBJECT_SIZE = 1024;
    // The default minimum object size.
    protected static final int DEFAULT_MIN_OBJECT_SIZE = 64;
    // The default length of object queue.
    // All created objects would be put into the queue before being popped out later.
    protected static final int DEFAULT_SURVIVOR_QUEUE_LENGTH = 512 * 1024;
    // The interval between creating new objects if throttled.
    protected static final int DEFAULT_SLEEP_TIME_IN_MILLISECONDS = 1;
    // The minimum ratio to try make objects long lived. The algorithm has an simple back-off.
    // When the input queue of the long lived objects store refuse to take new objects, it would
    // decrease the rate. When it successful added an object to the queue, it would increase the rate.
    // Basically, task threads are producers, and the ObjectStore is a consumer.
    protected static final int MIN_LONG_LIVED_RATIO = 1024 * 1024;
    // The maximum ratio to try make objects long lived.
    protected static final int MAX_LONG_LIVED_RATIO = 2;

    /**
     * Start all task runners.
     */
    abstract void start();

    /**
     * Create a single task runner to generate allocation.
     * @param store Long-lived object store.
     * @param rateInMb Allocation rate in Mb.
     * @param durationInMs The duration of run in millisecond.
     * @return
     */
    static Callable<Long> createSingle(final ObjectStore store, final long rateInMb, final long durationInMs) {
        return createSingle(store, rateInMb, durationInMs,
                DEFAULT_MIN_OBJECT_SIZE, DEFAULT_MAX_OBJECT_SIZE, DEFAULT_SURVIVOR_QUEUE_LENGTH);
    }

    /**
     * Create a single task runner to generate allocation.
     * @param store Long-lived object store.
     * @param rateInMb Allocation rate in Mb.
     * @param durationInMs The duration of run in millisecond.
     * @param minObjectSize The minimum object size.
     * @param maxObjectSize The maximum object size.
     * @param queueLength The queue length of mid-aged objects.
     * @return The unused allocation allowance during the run.
     */
    static Callable<Long> createSingle(final ObjectStore store, final long rateInMb, final long durationInMs,
                                       final int minObjectSize, final int maxObjectSize, final int queueLength) {
        return () -> {
            final long rate = rateInMb * 1024 * 1024;
            final ArrayDeque<AllocObject> survivorQueue = new ArrayDeque<>();

            final long end = System.nanoTime() + durationInMs * 1000000;
            final TokenBucket throughput = new TokenBucket(rate);
            int longLivedRate = MAX_LONG_LIVED_RATIO;
            int longLivedCounter = longLivedRate;

            while (System.nanoTime() < end) {
                long wave = 0L;
                while (wave < rate / 10) {
                    if (!throughput.isThrottled()) {
                        final AllocObject obj = AllocObject.create(minObjectSize, maxObjectSize, null);
                        throughput.deduct(obj.getRealSize());
                        wave += obj.getRealSize();
                        survivorQueue.addLast(obj);

                        if (survivorQueue.size() > queueLength) {
                            final AllocObject removed = survivorQueue.poll();
                            if (--longLivedCounter == 0) {
                                if (store.tryAdd(removed)) {
                                    if (longLivedRate > MAX_LONG_LIVED_RATIO) {
                                        longLivedRate /= 2;
                                    }
                                } else {
                                    if (longLivedRate < MIN_LONG_LIVED_RATIO) {
                                        longLivedRate *= 2;
                                    }
                                }
                                longLivedCounter = longLivedRate;
                            }
                        }
                    } else {
                        Thread.sleep(DEFAULT_SLEEP_TIME_IN_MILLISECONDS);
                        break;
                    }
                }
            }

            return throughput.getCurrent();
        };
    }
}
