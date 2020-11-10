package com.amazon.corretto.benchmark.hyperalloc;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * An implementation of the <a href="https://en.wikipedia.org/wiki/Token_bucket">Token Bucket</a> algorithm.
 * The bucket has a capacity (i.e., maximum burst) and a refill rate. Tokens may
 * be taken from the bucket until it is empty. Tokens will be replaced as time
 * elapses according to the refill rate.
 *
 * This class is not thread safe.
 */
public class BurstyTokenBucket {
    private final long capacity;
    private final Supplier<Long> clock;
    private final TimeUnit refillTimeUnit;
    private final long refillRate;
    private long lastFilled;
    private long available;

    /**
     * Create a token bucket with the given refill rate. This bucket will
     * use System.nanoTime as the clock and the capacity will be equal to
     * the refill rate.
     *
     * @param refillRate The number of tokens to generate for the given time unit.
     * @param timeUnit   The unit of time for the refill rate.
     */
    public BurstyTokenBucket(long refillRate, TimeUnit timeUnit) {
        this(refillRate, refillRate, timeUnit, System::nanoTime);
    }

    /**
     * Create a token bucket. This constructor is exposed mostly to allow
     * unit tests to replace the clock for deterministic testing.
     *
     * @param capacity   The maximum number of tokens the bucket will hold.
     * @param refillRate How many tokens to generate per unit of time.
     * @param timeUnit   The unit of time for the refill rate.
     * @param clock      Used to measure elapsed time.
     */
    public BurstyTokenBucket(long capacity, long refillRate, TimeUnit timeUnit, Supplier<Long> clock) {
        this.available = capacity;
        this.capacity = capacity;
        this.clock = clock;
        this.refillTimeUnit = timeUnit;
        this.refillRate = refillRate;
        this.lastFilled = clock.get();
    }

    public synchronized long take(long requested) {
        if (requested <= 0) {
            return 0;
        }

        replenish();

        return takeWithoutReplenish(requested);
    }

    /**
     * Attempt to take the requested number of tokens from the bucket. If the
     * minimum number of tokens is not available, no tokens are taken from the
     * bucket. This returns the number of tokens taken from the bucket which
     * will be between 0 and requested.
     *
     * @param requested The number of tokens to try to take from the bucket.
     * @param minimum The minimum number to take. If there aren't at least this
     *                many tokens available, no tokens will be taken from the
     *                bucket.
     * @return The number of tokens actually taken from the bucket.
     */
    public synchronized long take(long requested, long minimum) {
        if (requested < minimum) {
            throw new IllegalArgumentException("Requested should be higher than minimum.");
        }

        if (requested <= 0) {
            return 0;
        }

        replenish();

        if (requested > available) {
            if (minimum > available) {
                return 0;
            }
            requested = minimum;
        }

        return takeWithoutReplenish(requested);
    }

    private void replenish() {
        long now = clock.get();
        long elapsed = refillTimeUnit.convert(now - lastFilled, TimeUnit.NANOSECONDS);
        long tokens = refillRate * elapsed;
        long newValue = Math.min(capacity, available + tokens);
        if (newValue > available) {
             available = newValue;
             lastFilled = now;
         }
    }

    private long takeWithoutReplenish(long requested) {

        if (requested > available) {
            long taken = available;
            available = 0;
            return taken;
        }

        available -= requested;
        return requested;
    }
}
