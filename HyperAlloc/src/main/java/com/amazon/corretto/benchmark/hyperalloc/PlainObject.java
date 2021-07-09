// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.corretto.benchmark.hyperalloc;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The representation of an allocated object with fixed size in heap. It can also have a reference
 * to another AllocObject to allow us creating a complex reachability graph among them.
 */
class PlainObject implements AllocObject {

    private AllocObject next;
    private final byte[] data;

    PlainObject(final int size, final AllocObject ref) {
        this.data = new byte[size];
        this.next = ref;
    }

    /**
     * Set the next object referenced by the current one.
     *
     * @param next The object referenced by the current one.
     */
    @Override
    public void setNext(final AllocObject next) {
        this.next = next;
    }

    /**
     * Get the object referenced by the current one.
     *
     * @return The referenced object.
     */
    public AllocObject getNext() {
        return next;
    }

    /**
     * This method is used to exercise barriers.
     */
    public void touch() {
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
    public int getRealSize() {
        return DefaultObjectFactory.objectOverhead.getOverhead() + (data.length % 8 == 0 ? data.length : (data.length / 8 + 1) * 8);
    }
}
