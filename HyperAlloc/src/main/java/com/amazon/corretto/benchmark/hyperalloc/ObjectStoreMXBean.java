package com.amazon.corretto.benchmark.hyperalloc;

public interface ObjectStoreMXBean {
    long getCurrentSize();
    long getSizeLimit();
    long getDroppedCount();
}
