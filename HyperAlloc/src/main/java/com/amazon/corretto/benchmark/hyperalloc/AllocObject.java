package com.amazon.corretto.benchmark.hyperalloc;

public interface AllocObject {
    void setNext(final AllocObject next);
    AllocObject getNext();

    void touch();

    int getRealSize();
    void setRealSize(int size);
}
