package midplay.store;

import javax.microedition.rms.InvalidRecordIDException;
import javax.microedition.rms.RecordStoreException;

public class JsonRecordStore {
  private final RecordStoreManager storage;
  private int recordId;
  private final String defaultValue;

  public JsonRecordStore(String storeName, int recordId, String defaultValue) {
    this.storage = new RecordStoreManager(storeName);
    this.recordId = recordId;
    this.defaultValue = defaultValue;
  }

  public String load() {
    try {
      return storage.getRecordAsString(recordId);
    } catch (RecordStoreException e) {
    }
    return defaultValue;
  }

  /** Writes the document immediately; use only where the caller must report a failed save. */
  public synchronized void save(String json) throws RecordStoreException {
    saveNow(json);
  }

  /**
   * Queues the document for the background writer. Bursts of calls for the same store collapse into
   * the newest snapshot, so lists that change often do not block the command thread.
   */
  public void saveAsync(String json) {
    RecordStoreWriter.enqueue(this, json);
  }

  /** Persists a queued snapshot; called by the background writer and by {@link #save}. */
  synchronized void saveNow(String json) throws RecordStoreException {
    try {
      storage.setRecord(recordId, json);
    } catch (InvalidRecordIDException e) {
      recordId = storage.addRecord(json);
    }
  }

  /** Flushes every pending background write; call before pausing or destroying the MIDlet. */
  public static void flushAll() {
    RecordStoreWriter.flush();
  }

  public void close() {
    storage.closeRecordStore();
  }
}
