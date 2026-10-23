package midplay.store;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import javax.microedition.rms.RecordStoreException;

/** Recent Pinterest search terms, most recent first, persisted across sessions. */
public final class PinterestSearchHistoryManager {
  private static final int MAX_ITEMS = 10;
  private static final String STORAGE_NAME = "pinterest_search_history";
  private static PinterestSearchHistoryManager instance;

  public static PinterestSearchHistoryManager getInstance() {
    if (instance == null) {
      instance = new PinterestSearchHistoryManager();
    }
    return instance;
  }

  private final JsonRecordStore storage = new JsonRecordStore(STORAGE_NAME, 1, "[]");
  private JSONArray entries;

  private PinterestSearchHistoryManager() {}

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
