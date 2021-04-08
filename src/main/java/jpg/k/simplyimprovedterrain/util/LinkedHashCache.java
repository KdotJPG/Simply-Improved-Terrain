package jpg.k.simplyimprovedterrain.util;

import java.util.function.Function;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class LinkedHashCache<K, V> {

	private final int nActivemostNodes;
	private final int maxCacheSize;
	
	private final ConcurrentHashMap<K, LinkedCacheEntry<K, V>> cacheMap;
	private final ReentrantLock linkedCacheLock;
	private LinkedCacheEntry<K, V> lowestCacheEntry;
	private LinkedCacheEntry<K, V> highestCacheEntry;
	private int cacheSize;
	private int frontNodeNumber;
	
	public LinkedHashCache(int nActivemostNodes, int maxCacheSize) {
		this.nActivemostNodes = nActivemostNodes;
		this.maxCacheSize = maxCacheSize;
		this.cacheMap = new ConcurrentHashMap<>();
		this.linkedCacheLock = new ReentrantLock();
		this.cacheSize = 0;
	}
	
	public V get(K key, Function<K, V> generateValue) {
		LinkedCacheEntry<K, V> entry = cacheMap.computeIfAbsent(key, (k) -> {
			LinkedCacheEntry<K, V> newEntry = new LinkedCacheEntry<K, V>();
			
			linkedCacheLock.lock();
			try {
				cacheSize++;
				frontNodeNumber++;
				newEntry.nodeNumber = frontNodeNumber;
				
				// Add it to the end of the list.
				if (highestCacheEntry != null) {
					highestCacheEntry.next = newEntry;
					newEntry.prev = highestCacheEntry;
				} else {
					lowestCacheEntry = newEntry;
				}
				highestCacheEntry = newEntry;
			} finally {
				linkedCacheLock.unlock();
			}

			newEntry.key = k;
			newEntry.value = generateValue.apply(k);
			return newEntry;
		});
		
		// Update cache size and list order. Slight race condition on numbers won't break anything.
		// This doesn't need to run every time its conditions apply, and it doesn't hurt to run when they don't.
		// The important checks are redone inside the atomic code block.
		if (cacheSize >= maxCacheSize || frontNodeNumber - entry.nodeNumber >= nActivemostNodes) {
			if (linkedCacheLock.tryLock()) {
				try {
					
					if (cacheSize >= maxCacheSize) {
						cacheMap.remove(lowestCacheEntry.key);
						lowestCacheEntry = lowestCacheEntry.next;
						lowestCacheEntry.prev = null;
						cacheSize--;
					}
					
					if (entry.next != null && frontNodeNumber - entry.nodeNumber >= nActivemostNodes) {
						LinkedCacheEntry<K, V> a = entry.prev;
						LinkedCacheEntry<K, V> b = entry;
						LinkedCacheEntry<K, V> c = entry.next;
						LinkedCacheEntry<K, V> d = c.next;
		
						int bNodeNumber = b.nodeNumber;
						int cNodeNumber = c.nodeNumber;
						
						if (a != null) a.next = c;
						else lowestCacheEntry = c;
						
						c.prev = a;
						c.next = b;
						c.nodeNumber = bNodeNumber;
						
						b.prev = c;
						b.next = d;
						b.nodeNumber = cNodeNumber;
						
						if (d != null) d.prev = b;
						else highestCacheEntry = b;
					}
					
				} finally {
					linkedCacheLock.unlock();
				}
			}
		}
		
		return entry.value;
	}

	private static class LinkedCacheEntry<K, V> {
		K key;
		V value;
		int nodeNumber;
		LinkedCacheEntry<K, V> prev, next;
	}
}
