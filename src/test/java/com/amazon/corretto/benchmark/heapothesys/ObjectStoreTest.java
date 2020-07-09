package com.amazon.corretto.benchmark.heapothesys;

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
        final AllocObject obj = new AllocObject(1024, null);

        assertTrue(store.tryAdd(obj));

        assertThat(store.queue.size(), is(1));
        assertThat(store.getStore().size(), is(1));
        assertThat(store.getStore().get(0).size(), is(0));
    }

    @Test
    void ShouldFailToAddWhenFullTest() {
        final ObjectStore store = new ObjectStore(1);
        final AllocObject objA = new AllocObject(512 * 1024, null);
        final AllocObject objB = new AllocObject(512 * 1024, null);

        assertTrue(store.tryAdd(objA));
        assertTrue(store.tryAdd(objB));

        assertFalse(store.tryAdd(new AllocObject(128, null)));
    }

    @Test
    void ShouldFailToAddWhenSizeIsZeroTest() {
        final ObjectStore store = new ObjectStore(0);

        assertFalse(store.tryAdd(new AllocObject(64, null)));
    }

    @Test
    void StopShouldReturnCurrentSizeTest() {
        final ObjectStore store = new ObjectStore(1);
        final AllocObject obj = new AllocObject(1024, null);

        store.tryAdd(obj);

        assertThat(store.stopAndReturnSize(), is(1024L));
    }

    @Test
    void FromQueueToStoreTest() throws InterruptedException {
        final ObjectStore store = new ObjectStore(1, 0, 0);

        final AllocObject obj = new AllocObject(1024, null);

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
            final AllocObject obj = new AllocObject(64 * 1024, null);
            initial.add(obj);
            store.tryAdd(obj);
        }

        new Thread(store).start();

        for (int i = 0; i < 24; i++) {
            final AllocObject obj = new AllocObject(64 * 1024, null);
            store.tryAdd(obj);
            Thread.sleep(5);
        }

        final List<AllocObject> objs = store.getStore().get(0);
        assertThat(objs.stream().filter(o -> initial.contains(o)).count(), lessThan(16L));

        store.stopAndReturnSize();
    }

    @Test
    void ReshuffleTest() throws InterruptedException {
        final ObjectStore store = new ObjectStore(1, 2, 2);
        final List<AllocObject> initial = new ArrayList<>();

        for (int i = 0; i < 1024; i++) {
            final AllocObject obj = new AllocObject(1024, null);
            initial.add(obj);
            store.tryAdd(obj);
        }

        new Thread(store).start();

        assertThat(store.getStore().stream().mapToLong(g -> g.stream().mapToLong(AllocObject::getSum).sum()).sum(),
                is(0L));

        for (int i = 0; i < 1024; i++) {
            final AllocObject obj = new AllocObject(1024, null);
            store.tryAdd(obj);
            Thread.sleep(1);
        }

        assertThat(store.getStore().stream().mapToLong(g -> g.stream().mapToLong(AllocObject::getSum).sum()).sum(),
                greaterThan(512L));

        store.stopAndReturnSize();
    }

    @Test
    @Disabled("For debugging only.")
    void DebugTest() throws InterruptedException {
        final ObjectStore store = new ObjectStore(1);
        new Thread(store).start();
        int rejected = 0;
        for (int i = 0; i < 2048; i++) {
            if (!store.tryAdd(AllocObject.create(1024, 1024, null))) {
                rejected++;
            }
            Thread.sleep(2);
        }
        Thread.sleep(2000);
        System.out.println("rejected: " + rejected);
        System.out.println(store.stopAndReturnSize());
    }
}
