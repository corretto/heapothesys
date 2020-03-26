package com.amazon.corretto.benchmark.heapothesys;

import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TokenBucketTest {

    @Test
    public void constructTest() {
        TokenBucket rate1 = new TokenBucket(1234567L);
        assertFalse("Should not be throttled when created.", rate1.isThrottled());
    }

    @Test
    public void deductLessThanLimitThroughputShouldNotBeThrottled() {
        TokenBucket rate = new TokenBucket(3000L);
        rate.deduct(1000L);
        assertFalse("Should not be throttled when the throughput usage is less than the limit.", rate.isThrottled());
        assertThat("Should return correct current remaining.", rate.getCurrent(), is(2000L));
    }

    @Test
    public void deductMoreThanLimitThroughputShouldBeThrottled() {
        TokenBucket rate = new TokenBucket(3000L);
        rate.deduct(2500L);
        assertFalse("Should not be throttled when the throughput usage is less than the limit.", rate.isThrottled());

        rate.deduct(501L);
        assertTrue("Should be throttled when the throughput usage is more than the limit.", rate.isThrottled());
        assertThat("Should return correct current remaining.", rate.getCurrent(), is(-1L));
    }

    @Test
    public void outOfTimeSliceRateShouldNotBeThrottled() throws InterruptedException {
        Supplier<Long> clock = (Supplier<Long>) mock(Supplier.class);
        when(clock.get()).thenReturn(100L).thenReturn(102L).thenReturn(103L).thenReturn(111L);

        TokenBucket rate = new TokenBucket(3000L,
                TimeUnit.NANOSECONDS.toNanos(10), clock);
        assertThat(rate.deduct(3500L), is(200L));
        assertThat(rate.getCurrent(), is(-300L));
        assertTrue("Should be throttled when the throughput usage is more than the limit.", rate.isThrottled());

        assertFalse("Should not be throttled when out of time slice.", rate.isThrottled());
    }

    @Test
    public void deductCallShouldRenewTimeSlice() throws InterruptedException {
        Supplier<Long> clock = (Supplier<Long>) mock(Supplier.class);
        when(clock.get()).thenReturn(100L).thenReturn(102L).thenReturn(131L).thenReturn(134L).thenReturn(135L);

        TokenBucket rate = new TokenBucket(3000L,
                TimeUnit.NANOSECONDS.toNanos(10), clock);

        assertThat(rate.deduct(3500L), is(200L));
        assertThat(rate.getCurrent(), is(-300L));

        assertFalse("Should not be throttled when out of time slice.", rate.isThrottled());

        assertThat(rate.deduct(3500L), is(0L));
        assertThat(rate.getCurrent(), is(5200L));
        assertFalse("Should not be throttled when time slice get renewed.", rate.isThrottled());
    }

}