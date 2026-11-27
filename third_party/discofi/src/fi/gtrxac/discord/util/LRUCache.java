package fi.gtrxac.discord.util;

import java.util.*;

/**
 * Generic functionality for least-recently-used caches.
 * Usage times are handled by LRUCacheEntry.
 */
public class LRUCache {
    protected final Hashtable cache;
    protected final int size;

    protected LRUCache(int size) {
        this.size = size;
        this.cache = new Hashtable(size);
    }

    public Object get(Object key) {
        LRUCacheEntry entry = (LRUCacheEntry) cache.get(key);
        if (entry == null) return null;
        return entry.getObject();
    }

    public void set(Object key, Object value) {
        LRUCacheEntry entry = new LRUCacheEntry(value);
        set(key, entry);
    }

    public void set(Object key, LRUCacheEntry entry) {
        deleteOldestItem();
        cache.put(key, entry);
    }

    public void clear() {
        cache.clear();
    }

    protected void deleteOldestItem() {
        if (cache.size() < size) return;

        long oldestLastUsed = Integer.MAX_VALUE;
        Object oldestKey = null;

        for (Enumeration e = cache.keys(); e.hasMoreElements(); ) {
            Object key = e.nextElement();
            LRUCacheEntry entry = (LRUCacheEntry) cache.get(key);

            long lastUsed = entry.getLastUsed();

            if (lastUsed < oldestLastUsed) {
                oldestLastUsed = lastUsed;
                oldestKey = key;
            }
        }
        
        if (oldestKey != null) cache.remove(oldestKey);
    }
}