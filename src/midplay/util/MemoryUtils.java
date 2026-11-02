package midplay.util;

/**
 * Heap helpers for the hidden htop screen. J2ME only treats {@code gc()} as a hint and some
 * devices run it asynchronously, so a single call right after killing a subapp often reports the
 * old numbers. This helper asks for collection a few times and yields before the caller measures
 * the heap again.
 */
public final class MemoryUtils {
  private MemoryUtils() {}

  /** Requests a full collection and gives the runtime a moment to finish it. */
  public static void collect() {
    for (int i = 0; i < 2; i++) {
      System.gc();
      Runtime.getRuntime().gc();
      Thread.yield();
    }
    try {
      Thread.sleep(120);
    } catch (InterruptedException ignored) {
    }
    System.gc();
    Runtime.getRuntime().gc();
  }

  /** Heap currently used by live objects, in bytes. */
  public static long usedBytes() {
    Runtime runtime = Runtime.getRuntime();
    return runtime.totalMemory() - runtime.freeMemory();
  }
}
