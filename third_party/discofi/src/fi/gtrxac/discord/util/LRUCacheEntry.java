package fi.gtrxac.discord.util;

public class LRUCacheEntry {
    private static long usageCounter;

    private Object object;
    private long lastUsed;

    public LRUCacheEntry(Object o) {
        object = o;
    }

    public LRUCacheEntry(Object o, long lastUsed) {
        object = o;
        this.lastUsed = lastUsed;
    }

    public Object getObject() {
        usageCounter++;
        lastUsed = usageCounter;
        return object;
    }

    public Object getObjectWithoutUpdate() {
        return object;
    }

    public long getLastUsed() {
        return lastUsed;
    }

    public static long getUsageCounter() {
        return usageCounter;
    }
}