package com.amazon.corretto.benchmark.heapothesys;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * The object store for long lived object. The objects are stored in a list of object lists. Objects from one list may
 * reference objects from the list with lower index number (i to i+1). In this way, it creates a complex reference graph
 * for GC to work on. It maintains a maximum size of all objects, and accept new objects from a queue. It would randomly
 * replace objects from the store based on the <i>pruneRatioPerMinute</i> argument. It would also reshuffle references
 * and object values based on the <i>reshuffleRatio</i> argument. It makes sure the barriers are exercised during run
 * time.
 */
public class ObjectStore implements Runnable {
    // The default value of number of object in a store list group.
    private static final int DEFAULT_MAX_ITEM_IN_GROUP = 512;
    // The default value of the prune ratio per minute. 1/50 of objects in the store would be replaced within a minute.
    public static final int DEFAULT_PRUNE_RATIO = 50;
    // The default value of the reshuffle ratio. 1/100 object groups will be reshuffled during prune period.
    public static final int DEFAULT_RESHUFFLE_RATIO = 100;
    // The interval between pulls on the objects input queue.
    private static final int INTERVAL_IN_MS = 2;
    // The portion of objects to leave in the incoming queue to avoid starvation. 1/100 by default.
    private static final int IN_QUEUE_RATIO = 100;

    private ArrayList<List<AllocObject>> store;
    final ArrayBlockingQueue<AllocObject> queue;
    final long sizeLimit;
    final TokenBucket pruneRate;
    final int reshuffleRatio;
    final int maxItemInGroup;

    private AtomicLong currentSize;
    private boolean running;

    public ObjectStore(final int sizeLimitInMb) {
        this(sizeLimitInMb, DEFAULT_PRUNE_RATIO, DEFAULT_RESHUFFLE_RATIO);
    }

    public ObjectStore(final int sizeLimitInMb, final int pruneRatioPerMinute, final int reshuffleRatio) {
        this(sizeLimitInMb, pruneRatioPerMinute, reshuffleRatio, DEFAULT_MAX_ITEM_IN_GROUP);
    }

    public ObjectStore(final int sizeLimitInMb, final int pruneRatioPerMinute,
                       final int reshuffleRatio, final int maxItemInGroup) {
        this(sizeLimitInMb, pruneRatioPerMinute, reshuffleRatio, maxItemInGroup, System::nanoTime);
    }

    ObjectStore(final int sizeLimitInMb, final int pruneRatioPerMinute, final int reshuffleRatio,
                final int maxItemInGroup, Supplier<Long> timeSupplier) {
        assert pruneRatioPerMinute >= 0;
        assert reshuffleRatio >= 0;
        assert sizeLimitInMb >= 0;

        store = new ArrayList<>();
        store.add(new ArrayList<>());

        this.sizeLimit = sizeLimitInMb * 1024L * 1024L;
        this.pruneRate = new TokenBucket(pruneRatioPerMinute == 0 ? 0
                : sizeLimit / pruneRatioPerMinute, TimeUnit.MINUTES.toNanos(1), timeSupplier);
        this.reshuffleRatio = reshuffleRatio;
        this.maxItemInGroup = maxItemInGroup;

        currentSize = new AtomicLong(0L);
        queue = new ArrayBlockingQueue<>(maxItemInGroup);
        running = true;
    }

    public boolean tryAdd(final AllocObject obj) {
        try {
            if (currentSize.get() >= sizeLimit) {
                return false;
            }

            if (queue.offer(obj, 5, TimeUnit.MICROSECONDS)) {
                currentSize.addAndGet(obj.getRealSize());
                return true;
            }

            return false;
        } catch (InterruptedException e) {
            return false;
        }
    }

    public long stopAndReturnSize() {
        running = false;

        return currentSize.get();
    }

    ArrayList<List<AllocObject>> getStore() {
        return store;
    }

    @Override
    public void run() {
        while (running) {
            try {
                if (currentSize.get() < sizeLimit * (1D - 1D / IN_QUEUE_RATIO)) {
                    final AllocObject obj = queue.poll(1, TimeUnit.MICROSECONDS);
                    if (obj != null) {
                        addToStore(obj);
                        continue;
                    }
                } else if (!pruneRate.isThrottled()) {
                    final AllocObject obj = queue.poll(1, TimeUnit.MICROSECONDS);
                    if (obj != null) {
                        replaceInStore(obj);
                        pruneRate.deduct(obj.getRealSize());
                        if (ThreadLocalRandom.current().nextBoolean()) {
                            reshuffle(reshuffleRatio);
                        }
                        continue;
                    }
                }

                Thread.sleep(INTERVAL_IN_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    private void addToStore(final AllocObject obj) {
        List<AllocObject> currentEnd = store.get(store.size() - 1);
        if (currentEnd.size() == maxItemInGroup) {
            store.add(new ArrayList<>());
            currentEnd = store.get(store.size() - 1);
        }

        currentEnd.add(obj);

        tryRefMe(obj, store.size() - 1);
    }

    private void replaceInStore(final AllocObject obj) {
        final int groupIndex = randomRemove();

        store.get(groupIndex).add(obj);
        tryRefMe(obj, groupIndex);
        tryRef(obj, groupIndex);
    }

    private int randomRemove() {
        final int groupIndex = ThreadLocalRandom.current().nextInt(store.size());
        final List<AllocObject> group = store.get(groupIndex);
        if (group.size() > 0) {
            final AllocObject toRemove = group.get(ThreadLocalRandom.current().nextInt(group.size()));
            group.remove(toRemove);

            if (groupIndex != 0) {
                for (AllocObject obj : group) {
                    if (obj.getNext() != null && obj.getNext() == toRemove) {
                        obj.setNext(null);
                    }
                }
            }
            currentSize.addAndGet(-toRemove.getRealSize());
        }

        return groupIndex;
    }

    private void tryRefMe(final AllocObject obj, final int groupIndex) {
        if (groupIndex == 0) {
            return;
        }

        if (ThreadLocalRandom.current().nextBoolean()) {
            store.get(groupIndex - 1).get(ThreadLocalRandom.current().nextInt(maxItemInGroup)).setNext(obj);
        }
    }

    private void tryRef(final AllocObject obj, final int groupIndex) {
        if (groupIndex >= store.size() - 1) {
            return;
        }

        if (ThreadLocalRandom.current().nextBoolean()) {
            final List<AllocObject> nextLayer = store.get(groupIndex + 1);
            obj.setNext(nextLayer.get(ThreadLocalRandom.current().nextInt(nextLayer.size())));
        } else {
            obj.setNext(null);
        }
    }

    private void reshuffle(final int shuffleRatio) {
        if (reshuffleRatio == 0) {
            return;
        }

        for (int i = 0; i < ((double) store.size() / shuffleRatio) && store.size() > 1; i++) {
            final int currentIndex = ThreadLocalRandom.current().nextInt(store.size() - 1);
            final List<AllocObject> current = store.get(currentIndex);
            current.forEach(obj -> tryRef(obj, currentIndex));
            current.forEach(AllocObject::touch);
        }
    }
}
