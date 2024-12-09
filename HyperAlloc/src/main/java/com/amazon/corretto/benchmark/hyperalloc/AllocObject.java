// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.corretto.benchmark.hyperalloc;

import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;

/**
 * The representation of an allocated object with fixed size in heap. It can also have a reference
 * to another AllocObject to allow us creating a complex reachability graph among them.
 */
class AllocObject {
    private static final ObjectOverhead objectOverhead = new ObjectOverhead();
    private static final LongAdder BytesAllocated = new LongAdder();


    private AllocObject next;
    private final byte[] data;

    AllocObject(final int size) {
        this.data = new byte[size];
        this.next = null;
    }

    AllocObject(final int size, final AllocObject ref) {
        this.data = new byte[size - objectOverhead.getOverhead()];
        this.next = ref;
    }

    /**
     * Set the next object referenced by the current one.
     *
     * @param next The object referenced by the current one.
     */
    void setNext(final AllocObject next) {
        this.next = next;
    }

    /**
     * Get the object referenced by the current one.
     *
     * @return The referenced object.
     */
    AllocObject getNext() {
        return next;
    }

    /**
     * This method is used to exercise barriers.
     */
    void touch() {
        data[ThreadLocalRandom.current().nextInt(data.length)] += 1;
    }

    long getSum() {
        long sum = 0;
        for (byte b : data) {
            sum += b;
        }
        return sum;
    }

    /**
     * Get the size of the object in heap.
     * @return The size of the object in heap.
     */
    int getRealSize() {
        return objectOverhead.getOverhead() + (data.length % 8 == 0 ? data.length : (data.length / 8 + 1) * 8);
    }

    /**
     * Create an AllocObject instance with a random size
     *
     * @param min The minimum object size, inclusive.
     * @param max The maximum object size, exclusive.
     * @param ref The referenced object from the created one.
     * @return The constructed AllocObject instance.
     */
    static AllocObject create(final int min, final int max, final AllocObject ref) {
        assert max >= min : "The max value must be greater than min";
        int size = getRandomSize(min, max);
        return create(size, ref);
    }

    static int getRandomSize(int min, int max) {
        return min == max ? min : ThreadLocalRandom.current().nextInt(max - min) + min;
    }

    static AllocObject create(final int size) {
        return create(size, null);
    }

    static AllocObject create(final int size, final AllocObject ref) {
        assert size >= objectOverhead.getOverhead()
                : "The object size cannot be smaller than the overhead(" + objectOverhead + ").";
        BytesAllocated.add(size);
        return new AllocObject(size, ref);
    }

    static long getBytesAllocated() {
        return BytesAllocated.longValue();
    }

    static int getObjectOverhead() {
        return objectOverhead.overhead;
    }

    /**
     * The enumeration to AllocObject overhead in heap.
     */
    static class ObjectOverhead {
        private final int overhead = (int) GraphLayout.parseInstance(new AllocObject(0)).totalSize();
        int getOverhead() {
            return overhead;
        }
    }
}
