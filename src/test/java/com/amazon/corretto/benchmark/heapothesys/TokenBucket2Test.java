package com.amazon.corretto.benchmark.heapothesys;

import org.junit.Test;

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class TokenBucket2Test {
    private Supplier<Long> getClock(long... ticks) {
        LinkedList<Long> list = new LinkedList<>();
        for (long tick: ticks) {
            list.add(tick);
        }
        return list::removeFirst;
    }

    @Test
    public void deductLessThanAvailable() {
        Supplier<Long> clock = getClock(1, 1);
        TokenBucket2 bucket = new TokenBucket2(1000, 1000, TimeUnit.NANOSECONDS, clock);
        long deducted = bucket.take(500);
        assertThat("Should return number of tokens deducted.", deducted, is(500L));
    }

    @Test
    public void deductAndReplenish() {
        Supplier<Long> clock = getClock(1, 1, 1, 3);
        TokenBucket2 bucket = new TokenBucket2(1000, 100, TimeUnit.NANOSECONDS, clock);
        assertThat(bucket.take(1000), is(1000L)); // 1ns.
        assertThat(bucket.take(1000), is(0L));    // 1ns.
        assertThat(bucket.take(1000), is(200L));  // 3ns.
    }

    @Test
    public void doesNotExceedCapacity() {
        Supplier<Long> clock = getClock(1, 1, 1, 100);
        TokenBucket2 bucket = new TokenBucket2(1000, 100, TimeUnit.NANOSECONDS, clock);
        assertThat(bucket.take(1000), is(1000L)); // 1ns.
        assertThat(bucket.take(1000), is(0L));    // 1ns.
        assertThat(bucket.take(1000), is(1000L)); // 100ns.
    }

    @Test
    public void actualRateLimiting() {
        long rate = 512 * 1024 * 1024;
        long max = 1024 * 1024 * 1024;
        System.out.println((double)rate / TimeUnit.SECONDS.toNanos(1));
        TokenBucket2 bucket = new TokenBucket2(rate, rate / 1000, TimeUnit.MILLISECONDS, System::nanoTime);
        long start = System.nanoTime();
        long end = start + TimeUnit.SECONDS.toNanos(30);
        long taken = 0;
        while (System.nanoTime() < end) {
            taken += bucket.take(max);
        }
        System.out.println("Rate: " + (taken / 1024 / 1024 / 30) + " tokens/sec");
    }
}
