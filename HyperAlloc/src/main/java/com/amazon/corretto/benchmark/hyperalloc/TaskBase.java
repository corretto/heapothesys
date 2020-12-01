// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.corretto.benchmark.hyperalloc;

import java.util.ArrayDeque;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

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

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    
    /**
     * Start all task runners.
     */
    abstract void start();

    /**
     * Create a single task runner to generate allocation.
     * @param store Long-lived object store.
     * @param rateInMb Allocation rate in Mb.
     * @param durationInMs The duration of run in millisecond.
     * @return A runnable that allocates objects and drives retention.
     */
    static Callable<Long> createSingle(final ObjectStore store, final long rateInMb, final long durationInMs) {
        return createSingle(store, rateInMb, durationInMs,
                DEFAULT_MIN_OBJECT_SIZE, DEFAULT_MAX_OBJECT_SIZE, DEFAULT_SURVIVOR_QUEUE_LENGTH, 0.0);
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
    static Callable<Long> createSingle(final ObjectStore store, final long rateInMb,
                                       final long durationInMs, final int minObjectSize, final int maxObjectSize,
                                       final int queueLength, double rampUpSeconds) {
        return () -> {
            final long rate = rateInMb * 1024 * 1024;
            final ArrayDeque<AllocObject> survivorQueue = new ArrayDeque<>();

            final long start = System.nanoTime();
            final long end = start + durationInMs * 1000000;
            Function<Double, Double> rampUp = null;
            if (rampUpSeconds > 0) {
                rampUp = sinusoidalRampUp(rate, rampUpSeconds);
            }

            final TokenBucket throughput = new TokenBucket(rate);
            int longLivedRate = MAX_LONG_LIVED_RATIO;
            int longLivedCounter = longLivedRate;

            while (System.nanoTime() < end) {
                long wave = 0L;
                while (wave < rate / 10) {
                    if (rampUp != null) {
                        final double elapsedSeconds = (System.nanoTime() - start) / NANOS_PER_SECOND;
                        if (elapsedSeconds < rampUpSeconds) {
                            double currentRate = rampUp.apply(elapsedSeconds);
                            throughput.adjustThrottle((long)currentRate);
                        } else {
                            throughput.adjustThrottle(rate);
                            rampUp = null;
                        }
                    }

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


    static Callable<Long> createBurstyAllocator(final ObjectStore store, final long rateInMb, final long durationInMs,
                                                final double allocSmoothnessFactor, final int minObjectSize,
                                                final int maxObjectSize, final int queueLength) {
        return () -> {
            final long rate = rateInMb * 1024 * 1024;
            final ArrayDeque<AllocObject> survivorQueue = new ArrayDeque<>();

            final long end = System.nanoTime() + durationInMs * 1000000;
            final BurstyTokenBucket throughput = new BurstyTokenBucket(rate, TimeUnit.SECONDS);
            int longLivedRate = MAX_LONG_LIVED_RATIO;
            int longLivedCounter = longLivedRate;

            // This arguably belongs inside the rate limiter. This code is meant
            // to smooth out the extremely spiky allocation patterns. Even with
            // the rate limiter and the maximum 'burst' size this code will burn
            // through its tokens very quickly and then recover them slowly. What
            // we do here is compute how long the allocation operation _should_
            // take to achieve a smooth, constant rate. Every time we complete an
            // allocation we compute the difference between this target operation
            // time and the actual operation time. We then add this difference to
            // a 'sleep debt'. Once the debt is over a millisecond (the resolution
            // of our sleep timer), we sleep away the time to track closer to the
            // target. The alloc smoothness factor controls how fast the sleep
            // debt accumulates, 0 = no sleep debt, most spiky alloc rate.
            // 1 = normal sleep debt rate, least spiky alloc rate.
            double expectedAverageSize = (maxObjectSize - minObjectSize) / 2.0;
            double allocationTargetTimeForRate = ((expectedAverageSize / rate) * TimeUnit.SECONDS.toNanos(1)) * allocSmoothnessFactor;
            long sleepDebtNs = 0;
            long nanosPerMilli = TimeUnit.MILLISECONDS.toNanos(1);

            while (System.nanoTime() < end) {
                long size = AllocObject.getRandomSize(minObjectSize, maxObjectSize);
                long allowed = throughput.take(size, minObjectSize);
                if (allowed >= minObjectSize) {
                    long start = System.nanoTime();
                    final AllocObject obj = AllocObject.create((int)allowed);
                    long elapsed = start - System.nanoTime();
                    sleepDebtNs += (allocationTargetTimeForRate - elapsed);

                    if (sleepDebtNs > nanosPerMilli) {
                        sleepDebtNs = 0;
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            Thread.interrupted();
                            return 0L;
                        }
                    }

                    survivorQueue.push(obj);

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
                }
            }
            return 0L;
        };
    }

    private static Function<Double, Double> linearRampUp(long maxRate, double rampUpSeconds) {
        final double rampUpRate = maxRate / rampUpSeconds;
        return (Double elapsedSeconds) -> elapsedSeconds * rampUpRate;
    }

    private static Function<Double, Double> sinusoidalRampUp(final long maxRate, final double rampUpSeconds) {
        return (Double elapsedSeconds) -> {
            // First, map the elapsed time to the domain over which our function (cosine)
            // takes the minimum and maximum values: cos(pi) -> -1, cos(2pi) -> +1. Then,
            // map the range of our function to a rate between 0 and the maximum.
            double radians = Math.PI * ((elapsedSeconds / rampUpSeconds) + 1);
            return ((Math.cos(radians) + 1) / 2) * maxRate;
        };
    }
}
