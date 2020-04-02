package com.amazon.corretto.benchmark.heapothesys;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * An implementation of the <a href="https://en.wikipedia.org/wiki/Token_bucket">Token Bucket</a> algorithm.
 * To used it, first define the <i>limit</i> of tokens for a given <i>timeSlice</i> (1 second by default).
 * Then you can keep on deducting from it by calling <i>deduct</i>. When the allowed tokens has been
 * used up within the current time slice, <i>isThrottled</i> would return <i>true</i>.
 */
public class TokenBucket {
    private static final long DEFAULT_TIME_SLICE = TimeUnit.MILLISECONDS.toNanos(10);
    private static final int DEFAULT_OVERDRAFT_RATIO = 10;
    private final long timeSlice;
    private final long limit;
    private final long overdraftLimit;
    private final Supplier<Long> clock;
    private long current;
    private long resetAtNanoSecond;

    /**
     * This constructor should only be used for testing purpose since we can control the timing with it.
     *
     * @param limit The token limit within a time slice.
     * @param timeSlice The duration of a time slice in nanoseconds.
     * @param overdraftRatio The ratio of overdraft per time slice. By default is 10% (1/10).
     * @param clock The supplier which returns the current timestamp in milliseconds.
     */
    TokenBucket(final long limit, final long timeSlice, final int overdraftRatio, final Supplier<Long> clock) {
        this.clock = clock;
        this.limit = limit;
        this.current = limit;
        this.resetAtNanoSecond = clock.get() + timeSlice;
        this.timeSlice = timeSlice;
        this.overdraftLimit = limit / overdraftRatio;
    }

    TokenBucket(final long limit, final long timeSlice, final Supplier<Long> clock) {
        this(limit, timeSlice, DEFAULT_OVERDRAFT_RATIO, clock);
    }

    /**
     * @param limit The token limit within a time slice.
     * @param timeSlice The duration of a time slice in nanoseconds.
     */
    public TokenBucket(final long limit, final long timeSlice) {
        this(limit, timeSlice, System::nanoTime);
    }

    /**
     * @param limit The token limit within a second.
     */
    public TokenBucket(final long limit) {
        this(limit/100, DEFAULT_TIME_SLICE);
    }

    /**
     * Deduct the used tokens.
     *
     * @param usedTokens The tokens to deduct.
     * @return The actual deducted tokens. If it is less than the
     */
    public long deduct(final long usedTokens) {
        final long now = clock.get();
        if (now >= resetAtNanoSecond) {
            current = current + ((now - resetAtNanoSecond) / timeSlice + 1) * limit;
            resetAtNanoSecond = this.resetAtNanoSecond + ((now - this.resetAtNanoSecond) / timeSlice + 1) * timeSlice;
        }

        if (current + overdraftLimit < usedTokens) {
            long remain = usedTokens - current - overdraftLimit;
            current = -overdraftLimit;
            return remain;
        } else {
            current -= usedTokens;
            return 0;
        }
    }

    /**
     * Get the remaining tokens of the current time slice.
     *
     * @return The remaining tokens can be used.
     */
    public long getCurrent() {
        return current;
    }

    /**
     * Whether tokens has been used up within the current time slice.
     *
     * @return Whether tokens has been used up within the current time slice.
     */
    public boolean isThrottled() {
        return clock.get() < this.resetAtNanoSecond && current <= 0;
    }
}
