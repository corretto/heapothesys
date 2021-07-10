package com.amazon.corretto.benchmark.hyperalloc;

import java.util.concurrent.ThreadLocalRandom;

public abstract class DataObject implements AllocObject {
    private final byte[] data;
    private int realSize;

    public DataObject(final int size) {
        this.data = new byte[size];
    }

    /**
     * This method is used to exercise barriers.
     * TODO: Are we sure access to array of primitives exercises barriers?
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
        return realSize;
    }

    public void setRealSize(int size) {
        realSize = size;
    }
}
