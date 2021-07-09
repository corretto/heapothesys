package com.amazon.corretto.benchmark.hyperalloc;

public class WeakObjectFactory extends DefaultObjectFactory implements ObjectFactory {
    @Override
    public AllocObject create(int size) {
        return new WeakObject(size);
    }
}
