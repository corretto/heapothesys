// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.corretto.benchmark.hyperalloc;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.*;

class ObjectStoreTest {
    @Test
    void CreationTest() {
        final ObjectStore store = new ObjectStore(1);
        assertThat(store.queue.size(), is(0));
        assertThat(store.getStore().size(), is(1));
        assertThat(store.getStore().get(0).size(), is(0));
        assertThat(store.stopAndReturnSize(), is(0L));
    }

    @Test
    void ShouldAddWhenNotFullTest() {
        final ObjectStore store = new ObjectStore(1);
        final PlainObject obj = new PlainObject(1024, null);

        assertTrue(store.tryAdd(obj));

        assertThat(store.queue.size(), is(1));
        assertThat(store.getStore().size(), is(1));
        assertThat(store.getStore().get(0).size(), is(0));
    }

    @Test
    void ShouldFailToAddWhenFullTest() {
        final ObjectStore store = new ObjectStore(1);
        final PlainObject objA = new PlainObject(512 * 1024, null);
        final PlainObject objB = new PlainObject(512 * 1024, null);

        assertTrue(store.tryAdd(objA));
        assertTrue(store.tryAdd(objB));

        assertFalse(store.tryAdd(new PlainObject(128, null)));
    }

    @Test
    void ShouldFailToAddWhenSizeIsZeroTest() {
        final ObjectStore store = new ObjectStore(0);

        assertFalse(store.tryAdd(new PlainObject(64, null)));
    }

    @Test
    void StopShouldReturnCurrentSizeTest() {
        final ObjectStore store = new ObjectStore(1);
        final PlainObject obj = new PlainObject(1024 - DefaultObjectFactory.objectOverhead.getOverhead(), null);

        store.tryAdd(obj);

        assertThat(store.stopAndReturnSize(), is(1024L));
    }

    @Test
    void FromQueueToStoreTest() throws InterruptedException {
        final ObjectStore store = new ObjectStore(1, 0, 0);

        final PlainObject obj = new PlainObject(1024, null);

        store.tryAdd(obj);

        assertThat(store.queue.size(), is(1));
        assertThat(store.getStore().get(0).size(), is(0));

        new Thread(store).start();
        Thread.sleep(1000);

        assertThat(store.queue.size(), is(0));
        assertThat(store.getStore().get(0).size(), is(1));

        store.stopAndReturnSize();
    }

    @Test
    void PruneShouldReplaceObjectTest() throws InterruptedException {
        final ObjectStore store = new ObjectStore(1, 2, 0, 10);
        final List<AllocObject> initial = new ArrayList<>();

        for (int i = 0; i < 24; i++) {
            final AllocObject obj = new PlainObject(64 * 1024, null);
            initial.add(obj);
            store.tryAdd(obj);
        }

        new Thread(store).start();

        for (int i = 0; i < 24; i++) {
            final AllocObject obj = new PlainObject(64 * 1024, null);
            store.tryAdd(obj);
            Thread.sleep(5);
        }

        final List<AllocObject> objs = store.getStore().get(0);
        assertThat(objs.stream().filter(initial::contains).count(), lessThan(16L));

        store.stopAndReturnSize();
    }

    private long storedDataSum(ObjectStore store) {
        long sum = 0;
        for (List<AllocObject> list : store.getStore()) {
            for (AllocObject object : list) {
                sum += ((PlainObject)object).getSum();
            }
        }
        return sum;
    }

    @Test
    void ReshuffleTest() throws InterruptedException {
        final ObjectStore store = new ObjectStore(1, 2, 2);
        final List<AllocObject> initial = new ArrayList<>();

        for (int i = 0; i < 1024; i++) {
            final AllocObject obj = new PlainObject(1024, null);
            initial.add(obj);
            store.tryAdd(obj);
        }

        new Thread(store).start();

        assertThat(storedDataSum(store), is(0L));

        for (int i = 0; i < 1024; i++) {
            final AllocObject obj = new PlainObject(1024, null);
            store.tryAdd(obj);
            Thread.sleep(1);
        }

        assertThat(storedDataSum(store), greaterThan(512L));

        store.stopAndReturnSize();
    }

    @Test
    @Disabled("For debugging only.")
    void DebugTest() throws InterruptedException {
        final ObjectStore store = new ObjectStore(1);
        final ObjectFactory factory = new PlainObjectFactory();
        new Thread(store).start();
        int rejected = 0;
        for (int i = 0; i < 2048; i++) {
            if (!store.tryAdd(factory.create(1024, 1024))) {
                rejected++;
            }
            Thread.sleep(2);
        }
        Thread.sleep(2000);
        System.out.println("rejected: " + rejected);
        System.out.println(store.stopAndReturnSize());
    }
}
