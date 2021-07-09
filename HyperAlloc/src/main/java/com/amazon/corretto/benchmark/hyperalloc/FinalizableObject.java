package com.amazon.corretto.benchmark.hyperalloc;

import java.util.concurrent.atomic.LongAdder;

public class FinalizableObject extends PlainObject {
    private static final LongAdder FINALIZED = new LongAdder();

    FinalizableObject(int size) {
        super(size, null);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void finalize() throws Throwable {
        FINALIZED.increment();
    }
}
