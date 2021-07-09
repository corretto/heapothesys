package com.amazon.corretto.benchmark.hyperalloc;

public class PlainObjectFactory extends DefaultObjectFactory implements ObjectFactory {
    @Override
    public AllocObject create(int size) {
        return new PlainObject(size, null);
    }
}
