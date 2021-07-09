package com.amazon.corretto.benchmark.hyperalloc;

import java.lang.ref.WeakReference;

public class WeakObject extends DataObject implements AllocObject {

    private WeakReference<AllocObject> next;

    WeakObject(final int size) {
        super(size);
    }

    @Override
    public void setNext(AllocObject next) {
        this.next = new WeakReference<>(next);
    }

    @Override
    public AllocObject getNext() {
        return this.next == null ? null : this.next.get();
    }
}
