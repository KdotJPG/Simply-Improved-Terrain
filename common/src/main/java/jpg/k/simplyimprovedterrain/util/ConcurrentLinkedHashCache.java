package jpg.k.simplyimprovedterrain.util;

import java.util.concurrent.*;
import java.util.function.Function;

public class ConcurrentLinkedHashCache<K, V> {

    private final ConcurrentHashMap<K, LinkedEntry<K, V>> storage;
    private LinkedEntry<K, V> mostRecentlyUsedEntry, leastRecentlyUsedEntry;
    private final Object sync;
    private final LinkedBlockingQueue<K> removalQueue;
    private int size, minSize, maxSize;
    private int approximateRecentCacheAccessDepth;
    private int recencyPruningFactor;

    ConcurrentLinkedDeque<LinkedEntry<K, V>> test;

    private LinkedEntry<K, V> orderUpdatePassCurrentEntry;
    private int orderUpdatePassCurrentIndexValue;

    public ConcurrentLinkedHashCache(int minSize, int maxSize, int recencyPruningFactor) {
        storage = new ConcurrentHashMap<>();
        removalQueue = new LinkedBlockingQueue<>();
        size = 0;
        sync = new Object();
        this.minSize = Math.max(minSize, 1);
        this.maxSize = Math.max(maxSize, this.minSize);
        this.recencyPruningFactor = recencyPruningFactor;
        orderUpdatePassCurrentIndexValue = 0;
    }

    public V computeIfAbsent(K key, Function<K, V> callback) {

        LinkedEntry<K, V> resultEntry = storage.compute(key, (k, entry) -> {

            // Prevents race condition on entries marked for removal.
            if (entry != null && entry.awaitableValue == null) {
                entry = null;
            }

            boolean isNew = (entry == null);
            if (isNew) {
                entry = new LinkedEntry<>(key, CompletableFuture.supplyAsync(() -> callback.apply(k)));
            }

            synchronized(sync) {
                if (isNew) {
                    size++;
                } else {

                    // Existing entry (pull out of current location in cache order linked list)
                    if (entry.previous != null) entry.previous.next = entry.next;
                    else mostRecentlyUsedEntry = entry.next;
                    if (entry.next != null) entry.next.previous = entry.previous;
                    else leastRecentlyUsedEntry = entry.previous;
                    entry.previous = null;

                }

                // Provides a rough idea on how deep into the cache we've been accessing elements lately.
                // Starts at double the entry's approximate order index and decreases by one every access.
                approximateRecentCacheAccessDepth = Math.max(Math.max(0, approximateRecentCacheAccessDepth - 1), entry.approximateOrderIndex * 2);

                // Place at front of cache order linked list.
                // (except for LRU update, done further down.)
                if (mostRecentlyUsedEntry != null) mostRecentlyUsedEntry.previous = entry;
                entry.next = mostRecentlyUsedEntry;
                entry.approximateOrderIndex = 0;
                mostRecentlyUsedEntry = entry;
                if (leastRecentlyUsedEntry == null) {

                    // The missing LRU update from above.
                    leastRecentlyUsedEntry = entry;

                } else {

                    // If we're above the max size, or we're above the min size and recent access patterns tell us the cache can be smaller,
                    // flag an entry for removal.
                    if (size > maxSize || (size > minSize && leastRecentlyUsedEntry.approximateOrderIndex > approximateRecentCacheAccessDepth * recencyPruningFactor)) {
                        leastRecentlyUsedEntry.approximateOrderIndex = -1; // Mark for removal
                        removalQueue.add(leastRecentlyUsedEntry.key);
                        size--;

                        if (leastRecentlyUsedEntry == orderUpdatePassCurrentEntry) {
                            orderUpdatePassCurrentEntry = mostRecentlyUsedEntry;
                            orderUpdatePassCurrentIndexValue = 0;
                        }

                        // with minSize forced >= 1, this should never be null.
                        LinkedEntry<K, V> newLeastRecentlyUsedEntry = leastRecentlyUsedEntry.previous;
                        newLeastRecentlyUsedEntry.next = null;
                        leastRecentlyUsedEntry = newLeastRecentlyUsedEntry;
                    }

                }

                // Repeatedly scan MRU to LRU in the order list, advancing by one step for each access, updating the order values.
                // This provides an approximation of the absolute order of each entry that we can use for dynamic cache sizing,
                // without breaking O(1) cache access time.
                if (orderUpdatePassCurrentEntry != null) {
                    orderUpdatePassCurrentEntry.approximateOrderIndex = orderUpdatePassCurrentIndexValue;
                    orderUpdatePassCurrentIndexValue++;
                    orderUpdatePassCurrentEntry = orderUpdatePassCurrentEntry.next;
                    if (orderUpdatePassCurrentEntry == null) {
                        orderUpdatePassCurrentEntry = mostRecentlyUsedEntry;
                        orderUpdatePassCurrentIndexValue = 0;
                    }
                } else {
                    orderUpdatePassCurrentIndexValue = 0;
                    orderUpdatePassCurrentEntry = mostRecentlyUsedEntry;
                }

            }

            return entry;
        });

        // Remove a requested entry, if any, accounting for race conditions.
        K keyToRemove = removalQueue.poll();
        if (keyToRemove != null) {
            storage.computeIfPresent(keyToRemove,
                    (keyForRemoval, entryForRemoval) -> leastRecentlyUsedEntry.approximateOrderIndex == -1 ? null : entryForRemoval
            );
        }

        try {
            return resultEntry.awaitableValue.get();
        } catch (InterruptedException | ExecutionException e) {
            return callback.apply(key);
        }
    }

    private static class LinkedEntry<K, V> {
        final K key;
        CompletableFuture<V> awaitableValue;
        int approximateOrderIndex;
        LinkedEntry<K, V> previous, next;

        public LinkedEntry(K key, CompletableFuture<V> awaitableValue) {
            this.key = key;
            this.awaitableValue = awaitableValue;
        }
    }

}
