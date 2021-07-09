// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.corretto.benchmark.hyperalloc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class PlainObjectTest {

    ObjectFactory objects = new PlainObjectFactory();

    @Test
    void ObjectSizeTest() {
        final AllocObject o1 = objects.create(40, 40);
        final AllocObject o2 = objects.create(41, 41);
        final AllocObject o3 = objects.create(42, 42);
        final AllocObject o4 = objects.create(48, 48);
        final AllocObject o5 = objects.create(49, 49);
        final AllocObject o6 = objects.create(1024, 1024);

        assertThat(o1.getRealSize(), is(40));
        assertThat(o2.getRealSize(), is(48));
        assertThat(o3.getRealSize(), is(48));
        assertThat(o4.getRealSize(), is(48));
        assertThat(o5.getRealSize(), is(56));
        assertThat(o6.getRealSize(), is(1024));
    }

    @Test
    void NullReferenceTest() {
        assertNull(objects.create(56, 100).getNext());
    }

    @Test
    void NonNullReferenceTest() {
        final AllocObject obj = objects.create(100, 200);
        final AllocObject reference = objects.create(100, 200);
        reference.setNext(obj);
        assertThat(reference.getNext(), Matchers.is(obj));
    }

    @Test
    void SetReferenceTest() {
        final AllocObject ref = objects.create(100, 200);
        final AllocObject obj = objects.create(100, 200);
        assertNull(obj.getNext());

        obj.setNext(ref);

        assertNotNull(obj.getNext());
        assertThat(obj.getNext(), Matchers.is(ref));
    }

    @Test
    void ObjectCreateRangeTest() {
        for (int i = 56; i < 100000; i++) {
            final int size = objects.create(56, i).getRealSize();
            assertThat(size, greaterThanOrEqualTo(56));
            assertThat(size, lessThan(i + 8));
        }
    }

    @Test
    void TouchTest() {
        final AllocObject obj = objects.create(100, 100);
        for (int i = 0; i < 10000; i++) {
            assertDoesNotThrow(obj::touch);
        }
    }

    @Test
    void ObjectCreateFixedTest() {
        for (int i = 56; i < 100000; i++) {
            final int size = objects.create(i, i).getRealSize();
            assertThat(size, greaterThanOrEqualTo(i));
            assertThat(size, lessThan(i + 8));
        }
    }

    @Test
    void OverheadTest() {
        DefaultObjectFactory.setOverhead(DefaultObjectFactory.ObjectOverhead.CompressedOops);
        assertDoesNotThrow(() -> objects.create(40, 40));
        DefaultObjectFactory.setOverhead(DefaultObjectFactory.ObjectOverhead.NonCompressedOops);
        assertThrows(AssertionError.class, () -> objects.create(40, 40));
        assertDoesNotThrow(() -> objects.create(56, 56));

        DefaultObjectFactory.setOverhead(DefaultObjectFactory.ObjectOverhead.CompressedOops);
    }

    @Test
    @Disabled("Only run when compressed oops disabled.")
    void ObjectSizeNoCompressedOopsTest() {
        DefaultObjectFactory.setOverhead(DefaultObjectFactory.ObjectOverhead.NonCompressedOops);

        final AllocObject o1 = objects.create(56, 56);
        final AllocObject o2 = objects.create(57, 57);
        final AllocObject o3 = objects.create(58, 58);
        final AllocObject o4 = objects.create(59, 59);
        final AllocObject o5 = objects.create(65, 65);
        final AllocObject o6 = objects.create(1024, 1024);

        assertThat(o1.getRealSize(), is(56));
        assertThat(o2.getRealSize(), is(64));
        assertThat(o3.getRealSize(), is(64));
        assertThat(o4.getRealSize(), is(64));
        assertThat(o5.getRealSize(), is(72));
        assertThat(o6.getRealSize(), is(1024));
    }
}
