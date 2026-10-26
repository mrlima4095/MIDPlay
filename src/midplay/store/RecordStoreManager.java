package midplay.store;

import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import midplay.util.Utils;

public class RecordStoreManager {
  private final String recordStoreName;
  private RecordStore recordStore;

  public RecordStoreManager(String recordStoreName) {
    if (recordStoreName == null || recordStoreName.length() == 0) {
      throw new IllegalArgumentException("Record store name cannot be null or empty");
    }
    this.recordStoreName = recordStoreName;
  }

  private synchronized void openRecordStore() throws RecordStoreException {
    if (recordStore == null) {
      recordStore = RecordStore.openRecordStore(recordStoreName, true);
    }
  }

  public synchronized void closeRecordStore() {
    if (recordStore != null) {
      try {
        recordStore.closeRecordStore();
      } catch (RecordStoreException e) {
      } finally {
        recordStore = null;
      }
    }
  }

  public synchronized int addRecord(String data) throws RecordStoreException {
    if (data == null) {
      throw new IllegalArgumentException("Data cannot be null");
    }
    byte[] bytes = Utils.utf8ToBytes(data);
    openRecordStore();
    return recordStore.addRecord(bytes, 0, bytes.length);
  }

  public synchronized int addRecordBytes(byte[] data) throws RecordStoreException {
    if (data == null) {
      throw new IllegalArgumentException("Data cannot be null");
    }
    openRecordStore();
    return recordStore.addRecord(data, 0, data.length);
  }

  public synchronized String getRecordAsString(int recordId) throws RecordStoreException {
    if (recordId <= 0) {
      throw new IllegalArgumentException("Record ID must be positive");
    }
    openRecordStore();
    return Utils.bytesToUtf8(recordStore.getRecord(recordId));
  }

  public synchronized byte[] getRecordBytes(int recordId) throws RecordStoreException {
    if (recordId <= 0) {
      throw new IllegalArgumentException("Record ID must be positive");
    }
    openRecordStore();
    return recordStore.getRecord(recordId);
  }

  public synchronized void setRecord(int recordId, String data) throws RecordStoreException {
    if (data == null) {
      throw new IllegalArgumentException("Data cannot be null");
    }
    if (recordId <= 0) {
      throw new IllegalArgumentException("Record ID must be positive");
    }
    byte[] bytes = Utils.utf8ToBytes(data);
    openRecordStore();
    recordStore.setRecord(recordId, bytes, 0, bytes.length);
  }

  public synchronized void setRecordBytes(int recordId, byte[] data) throws RecordStoreException {
    if (data == null) {
      throw new IllegalArgumentException("Data cannot be null");
    }
    if (recordId <= 0) {
      throw new IllegalArgumentException("Record ID must be positive");
    }
    openRecordStore();
    recordStore.setRecord(recordId, data, 0, data.length);
  }

  public synchronized void deleteRecord(int recordId) throws RecordStoreException {
    if (recordId <= 0) {
      throw new IllegalArgumentException("Record ID must be positive");
    }
    openRecordStore();
    recordStore.deleteRecord(recordId);
  }

  private synchronized RecordEnumeration enumerateRecords() throws RecordStoreException {
    openRecordStore();
    return recordStore.enumerateRecords(null, null, false);
  }

  /**
   * Visits every record. Returns false when the store itself could not be read, so callers never
   * mistake an unreadable store for an empty one and overwrite good data with a partial list.
   */
  public synchronized boolean forEachRecord(RecordHandler handler) {
    try {
      RecordEnumeration enumeration = enumerateRecords();
      try {
        while (enumeration.hasNextElement()) {
          int recordId = enumeration.nextRecordId();
          String data = getRecordAsString(recordId);
          try {
            if (!handler.handle(recordId, data)) {
              break;
            }
          } catch (Exception e) {
          }
        }
      } finally {
        enumeration.destroy();
      }
      return true;
    } catch (Exception e) {
      // Some device implementations leave the RecordStore unusable after an error (including
      // runtime exceptions such as ArrayIndexOutOfBoundsException). Close it so the next call
      // opens a fresh instance instead of keeping the whole app broken until a restart.
      closeRecordStore();
      return false;
    }
  }

  public interface RecordHandler {
    boolean handle(int recordId, String data);
  }
}
