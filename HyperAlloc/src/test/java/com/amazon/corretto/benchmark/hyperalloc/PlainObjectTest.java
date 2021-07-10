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
        }
    }
}
