package midplay.store;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import javax.microedition.rms.RecordStoreException;

/**
 * Recent search terms persisted in RMS, most recent first. Subclasses only provide the record-store
 * name, so the music, Wattpad and Pinterest searches share the same storage logic.
 */
public abstract class SearchHistoryStore {
  private static final int MAX_ITEMS = 10;

  private final JsonRecordStore storage;
  private JSONArray entries;

  protected SearchHistoryStore(String storageName) {
    storage = new JsonRecordStore(storageName, 1, "[]");
  }

  public String[] getItems() {
    JSONArray items = items();
    String[] result = new String[items.size()];
    int count = 0;
    for (int i = 0; i < items.size(); i++) {
      try {
        String query = items.getObject(i).getString("query", "");
        if (query.length() > 0) {
          result[count++] = query;
        }
      } catch (Exception e) {
      }
    }
    if (count == result.length) {
      return result;
    }
    String[] trimmed = new String[count];
    System.arraycopy(result, 0, trimmed, 0, count);
    return trimmed;
  }

  public void record(String query) {
    if (query == null || query.length() == 0) {
      return;
    }
    JSONArray next = new JSONArray();
    add(next, query);
    JSONArray current = items();
    for (int i = 0; i < current.size() && next.size() < MAX_ITEMS; i++) {
      try {
        String existing = current.getObject(i).getString("query", "");
        if (!query.equals(existing) && existing.length() > 0) {
          add(next, existing);
        }
      } catch (Exception e) {
      }
    }
    save(next);
  }

  public void clear() {
    save(new JSONArray());
  }

  private JSONArray items() {
    if (entries == null) {
      try {
        entries = JSON.getArray(storage.load());
      } catch (Exception e) {
        entries = new JSONArray();
      }
    }
    return entries;
  }

  private static void add(JSONArray entries, String query) {
    JSONObject entry = new JSONObject();
    entry.put("query", query);
    entries.add(entry);
  }

  private void save(JSONArray next) {
    entries = next;
    try {
      storage.save(next.toString());
    } catch (RecordStoreException e) {
    }
  }
}
