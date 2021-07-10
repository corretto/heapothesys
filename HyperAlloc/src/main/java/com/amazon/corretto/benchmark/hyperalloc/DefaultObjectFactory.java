package com.amazon.corretto.benchmark.hyperalloc;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;

public abstract class DefaultObjectFactory implements ObjectFactory {
    private final LongAdder objectsAllocated = new LongAdder();
    private final LongAdder threadBytesAllocated = new LongAdder();
    private final com.sun.management.ThreadMXBean threadBean;

    static int getRandomSize(int min, int max) {
        return min == max ? min : ThreadLocalRandom.current().nextInt(max - min) + min;
    }

    long getBytesAllocated() {
        return threadBytesAllocated.longValue();
    }

    DefaultObjectFactory() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        if (threadMXBean instanceof com.sun.management.ThreadMXBean) {
            threadBean = (com.sun.management.ThreadMXBean) threadMXBean;
        } else {
            threadBean = null;
        }
    }

    @Override
    public AllocObject create(int minSize, int maxSize) {
        assert maxSize >= minSize : "The max value must be greater than min";
        int size = getRandomSize(minSize, maxSize);
        objectsAllocated.increment();

        long before = getThreadBytesAllocated();
        AllocObject object = create(size);
        long after = getThreadBytesAllocated();

        if (after > before) {
            long diff = after - before;
            object.setRealSize((int)diff);
            threadBytesAllocated.add(diff);
        }
        return object;
    }

    private long getThreadBytesAllocated() {
        return threadBean == null ? 0 : threadBean.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    public abstract AllocObject create(int size);
}
