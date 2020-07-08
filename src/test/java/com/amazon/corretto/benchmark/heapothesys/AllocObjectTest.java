package com.amazon.corretto.benchmark.heapothesys;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static com.amazon.corretto.benchmark.heapothesys.AssertionUtils.*;

import org.hamcrest.Matchers;
import org.junit.Ignore;
import org.junit.Test;

public class AllocObjectTest {
    @Test
    public void ObjectSizeTest() {
        final AllocObject o1 = new AllocObject(40, null);
        final AllocObject o2 = new AllocObject(41, null);
        final AllocObject o3 = new AllocObject(42, null);
        final AllocObject o4 = new AllocObject(48, null);
        final AllocObject o5 = new AllocObject(49, null);

        final AllocObject o6 = new AllocObject(1024, null);

        assertThat(o1.getRealSize(), is(40));
        assertThat(o2.getRealSize(), is(48));
        assertThat(o3.getRealSize(), is(48));
        assertThat(o4.getRealSize(), is(48));
        assertThat(o5.getRealSize(), is(56));
        assertThat(o6.getRealSize(), is(1024));
    }

    @Test
    public void NullReferenceTest() {
        assertNull(AllocObject.create(56, 100, null).getNext());
    }

    @Test
    public void NonNullReferenceTest() {
        final AllocObject obj = AllocObject.create(100, 200, null);

        assertThat(AllocObject.create(100, 200, obj).getNext(), Matchers.is(obj));
    }

    @Test
    public void SetReferenceTest() {
        final AllocObject ref = AllocObject.create(100, 200, null);
        final AllocObject obj = AllocObject.create(100, 200, null);
        assertNull(obj.getNext());

        obj.setNext(ref);

        assertNotNull(obj.getNext());
        assertThat(obj.getNext(), Matchers.is(ref));
    }

    @Test
    public void ObjectCreateRangeTest() {
        for (int i = 56; i < 100000; i++) {
            final int size = AllocObject.create(56, i, null).getRealSize();
            assertThat(size, greaterThanOrEqualTo(56));
            assertThat(size, lessThan(i + 8));
        }
    }

    @Test
    public void TouchTest() {
        final AllocObject obj = AllocObject.create(100, 100, null);
        for (int i = 0; i < 10000; i++) {
            assertNoThrow(() -> obj.touch());
        }
    }

    @Test
    public void ObjectCreateFixedTest() {
        for (int i = 56; i < 100000; i++) {
            final int size = AllocObject.create(i, i, null).getRealSize();
            assertThat(size, greaterThanOrEqualTo(i));
            assertThat(size, lessThan(i + 8));
        }
    }

    @Test
    public void OverheadTest() {
        AllocObject.setOverhead(AllocObject.ObjectOverhead.CompressedOops);
        assertNoThrow(() -> AllocObject.create(40, 40, null), AssertionError.class);
        AllocObject.setOverhead(AllocObject.ObjectOverhead.NonCompressedOops);
        assertThrow(() -> AllocObject.create(40, 40, null), AssertionError.class);
        assertNoThrow(() -> AllocObject.create(56, 56, null), AssertionError.class);

        AllocObject.setOverhead(AllocObject.ObjectOverhead.CompressedOops);
    }

    @Test
    @Ignore("Only run when compressed oops disabled.")
    public void ObjectSizeNoCompressedOopsTest() {
        AllocObject.setOverhead(AllocObject.ObjectOverhead.NonCompressedOops);

        final AllocObject o1 = new AllocObject(56, null);
        final AllocObject o2 = new AllocObject(57, null);
        final AllocObject o3 = new AllocObject(58, null);
        final AllocObject o4 = new AllocObject(59, null);
        final AllocObject o5 = new AllocObject(65, null);

        final AllocObject o6 = new AllocObject(1024, null);

        assertThat(o1.getRealSize(), is(56));
        assertThat(o2.getRealSize(), is(64));
        assertThat(o3.getRealSize(), is(64));
        assertThat(o4.getRealSize(), is(64));
        assertThat(o5.getRealSize(), is(72));
        assertThat(o6.getRealSize(), is(1024));
    }
}
