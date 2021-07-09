package com.amazon.corretto.benchmark.hyperalloc;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;

public abstract class DefaultObjectFactory implements ObjectFactory {
    public static ObjectOverhead objectOverhead = ObjectOverhead.CompressedOops;
    private final LongAdder bytesAllocated = new LongAdder();

    /**
     * Set the object overhead. By default it assumes that compressedOops is enabled.
     * @param overhead Object size overhead in heap.
     */
    static void setOverhead(final ObjectOverhead overhead) {
        objectOverhead = overhead;
    }

    static void setUseCompressedOops(boolean compressed) {
        objectOverhead = compressed ? ObjectOverhead.CompressedOops : ObjectOverhead.NonCompressedOops;
    }
    static int getRandomSize(int min, int max) {
        return min == max ? min : ThreadLocalRandom.current().nextInt(max - min) + min;
    }

    void addBytesAllocated(long byteCount) {
        bytesAllocated.add(byteCount);
    }

    long getBytesAllocated() {
        return bytesAllocated.longValue();
    }

    @Override
    public AllocObject create(int minSize, int maxSize) {
        assert maxSize >= minSize : "The max value must be greater than min";
        assert minSize >= objectOverhead.getOverhead() : "The object size cannot be smaller than the overhead(" + objectOverhead + ").";

        int size = getRandomSize(minSize, maxSize);
        addBytesAllocated(size);
        return create(size - objectOverhead.getOverhead());
    }

    public abstract AllocObject create(int size);

    /**
     * The enumeration to AllocObject overhead in heap.
     */
    enum ObjectOverhead {
        ///  AllocObject: | header (12) | ref to next (4) | ref to array (4) | align (4) |
        ///  Byte array:  | header (12) | length (4) |
        CompressedOops(40),

        ///  AllocObject: | header (16) | ref to next (8) | ref to array (8) |
        ///  Byte array:  | header (16) | length (4) | align (4) |
        NonCompressedOops(56);

        private final int overhead;

        ObjectOverhead(final int overhead) {
            this.overhead = overhead;
        }

        int getOverhead() {
            return overhead;
        }
    }
}
