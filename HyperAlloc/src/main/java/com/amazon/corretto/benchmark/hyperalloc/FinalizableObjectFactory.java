package com.amazon.corretto.benchmark.hyperalloc;

public class FinalizableObjectFactory extends DefaultObjectFactory implements ObjectFactory {
    @Override
    public AllocObject create(int size) {
        return new FinalizableObject(size);
    }
}
