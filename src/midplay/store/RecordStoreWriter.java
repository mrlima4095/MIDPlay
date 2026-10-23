package midplay.store;

import java.util.Vector;

/**
 * Background writer for RMS-backed JSON documents.
 *
 * <p>RMS has no partial update: every save rewrites the whole record, so persisting a large
 * favorites or history list on the command thread can freeze the UI on slow devices. Instead of
 * writing inline, {@link #enqueue} keeps only the newest snapshot per record store, so a burst of
 * changes (bulk save, several reader page turns) collapses into one or two writes, and the actual
 * I/O runs on a low-priority worker thread.
 *
 * <p>Writes are still flushed before the MIDlet pauses or exits through {@link #flush}, so normal
 * usage does not lose the last change. If the process is killed without warning the newest snapshot
 * may be lost, which is the same guarantee the previous synchronous code offered on devices where the
 * write itself was interrupted.
 */
final class RecordStoreWriter {
  /** Upper bound for {@link #flush} so a stuck write cannot block the exit forever. */
  private static final int FLUSH_TIMEOUT = 4000;

  private static final Vector queue = new Vector();
  private static boolean running;

  private RecordStoreWriter() {}

  /** Queues a snapshot, replacing any older pending snapshot for the same store. */
  static synchronized void enqueue(JsonRecordStore store, String json) {
    if (store == null || json == null) {
      return;
    }
    PendingWrite pending = find(store);
    if (pending == null) {
      pending = new PendingWrite(store);
      queue.addElement(pending);
    }
    pending.data = json;
    startWorker();
  }

  /** Waits (bounded) until all queued snapshots have been persisted. */
  static void flush() {
    long deadline = System.currentTimeMillis() + FLUSH_TIMEOUT;
    while (true) {
      synchronized (RecordStoreWriter.class) {
        if (!running && queue.size() == 0) {
          return;
        }
      }
      if (System.currentTimeMillis() >= deadline) {
        return;
      }
      try {
        Thread.sleep(15);
      } catch (InterruptedException e) {
        return;
      }
    }
  }

  private static PendingWrite find(JsonRecordStore store) {
    for (int i = 0; i < queue.size(); i++) {
      PendingWrite pending = (PendingWrite) queue.elementAt(i);
      if (pending.store == store) {
        return pending;
      }
    }
    return null;
  }

  private static synchronized void startWorker() {
    if (running) {
      return;
    }
    running = true;
    Thread worker =
        new Thread(
            new Runnable() {
              public void run() {
                drain();
              }
            });
    // Below normal priority so repainting and key handling always win over storage I/O.
    worker.setPriority(Thread.NORM_PRIORITY - 1);
    worker.start();
  }

  private static void drain() {
    try {
      while (true) {
        PendingWrite pending;
        synchronized (RecordStoreWriter.class) {
          if (queue.size() == 0) {
            return;
          }
          pending = (PendingWrite) queue.elementAt(0);
          queue.removeElementAt(0);
        }
        try {
          pending.store.saveNow(pending.data);
        } catch (Throwable t) {
          // Keep the in-memory copy in use; a failed background write must not kill the UI.
        }
      }
    } finally {
      boolean restart;
      synchronized (RecordStoreWriter.class) {
        running = false;
        restart = queue.size() > 0;
      }
      if (restart) {
        startWorker();
      }
    }
  }

  private static final class PendingWrite {
    final JsonRecordStore store;
    String data;

    PendingWrite(JsonRecordStore store) {
      this.store = store;
    }
  }
}
