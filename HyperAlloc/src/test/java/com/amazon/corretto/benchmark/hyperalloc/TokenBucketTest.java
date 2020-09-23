// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.corretto.benchmark.hyperalloc;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenBucketTest {

    @Test
    void constructTest() {
        TokenBucket rate1 = new TokenBucket(1234567L);
        assertFalse(rate1.isThrottled(), "Should not be throttled when created.");
    }

    @Test
    void deductLessThanLimitThroughputShouldNotBeThrottled() {
        TokenBucket rate = new TokenBucket(3000L, TimeUnit.SECONDS.toNanos(1));
        rate.deduct(1000L);
        assertFalse(rate.isThrottled(), "Should not be throttled when the throughput usage is less than the limit.");
        assertThat("Should return correct current remaining.", rate.getCurrent(), is(2000L));
    }

    @Test
    void deductMoreThanLimitThroughputShouldBeThrottled() {
        TokenBucket rate = new TokenBucket(3000L, TimeUnit.SECONDS.toNanos(1));
        rate.deduct(2500L);
        assertFalse(rate.isThrottled(), "Should not be throttled when the throughput usage is less than the limit.");

        rate.deduct(501L);
        assertTrue(rate.isThrottled(), "Should be throttled when the throughput usage is more than the limit.");
        assertThat("Should return correct current remaining.", rate.getCurrent(), is(-1L));
    }

    @Test
    void outOfTimeSliceRateShouldNotBeThrottled() throws InterruptedException {
        Supplier<Long> clock = (Supplier<Long>) mock(Supplier.class);
        when(clock.get()).thenReturn(100L).thenReturn(102L).thenReturn(103L).thenReturn(111L);

        TokenBucket rate = new TokenBucket(3000L,
                TimeUnit.NANOSECONDS.toNanos(10), clock);
        assertThat(rate.deduct(3500L), is(200L));
        assertThat(rate.getCurrent(), is(-300L));
        assertTrue(rate.isThrottled(), "Should be throttled when the throughput usage is more than the limit.");

        assertFalse(rate.isThrottled(), "Should not be throttled when out of time slice.");
    }

    @Test
    void deductCallShouldRenewTimeSlice() throws InterruptedException {
        Supplier<Long> clock = (Supplier<Long>) mock(Supplier.class);
        when(clock.get()).thenReturn(100L).thenReturn(102L).thenReturn(131L).thenReturn(134L).thenReturn(135L);

        TokenBucket rate = new TokenBucket(3000L,
                TimeUnit.NANOSECONDS.toNanos(10), clock);

        assertThat(rate.deduct(3500L), is(200L));
        assertThat(rate.getCurrent(), is(-300L));

        assertFalse(rate.isThrottled(), "Should not be throttled when out of time slice.");

        assertThat(rate.deduct(3500L), is(0L));
        assertThat(rate.getCurrent(), is(5200L));
        assertFalse(rate.isThrottled(), "Should not be throttled when time slice get renewed.");
    }

}
