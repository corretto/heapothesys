package com.amazon.corretto.benchmark.hyperalloc;

public interface ObjectFactory {
    AllocObject create(final int min, final int max);
    AllocObject create(final int size);
}
