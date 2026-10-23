package midplay.store;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import java.util.Vector;

/**
 * Local Wattpad reading list. Remembers, for every story opened, the last chapter read plus enough
 * metadata (title, chapter count and chapter name) to list and resume it without a network request.
 */
public final class WattpadProgressManager {
  /** Indexes into the rows returned by {@link #getEntries()}. */
  public static final int ENTRY_ID = 0;

  public static final int ENTRY_TITLE = 1;
  public static final int ENTRY_INDEX = 2;
  public static final int ENTRY_TOTAL = 3;
  public static final int ENTRY_CHAPTER = 4;
  public static final int ENTRY_PAGE = 5;
  public static final int ENTRY_ARCHIVED = 6;
  public static final int ENTRY_SIZE = 7;

  private static final int MAX_ITEMS = 60;
  private static final String STORAGE_NAME = "wattpad_progress";
  private static WattpadProgressManager instance;

  public static WattpadProgressManager getInstance() {
    if (instance == null) {
      instance = new WattpadProgressManager();
    }
    return instance;
  }

  private final JsonRecordStore storage = new JsonRecordStore(STORAGE_NAME, 1, "[]");
  private JSONArray entries;

  private WattpadProgressManager() {}

  /** Index of the chapter last opened for the story, or -1 when there is none. */
  public int getIndex(String storyId) {
    if (storyId == null) {
      return -1;
    }
    JSONArray items = items();
    for (int i = 0; i < items.size(); i++) {
      try {
        JSONObject entry = items.getObject(i);
        if (storyId.equals(entry.getString("story", ""))) {
          return entry.getInt("index", -1);
        }
      } catch (Exception e) {
      }
    }
    return -1;
  }

  /**
   * Saves the chapter and page where the reader stopped and moves the story to the top of the list.
   * Older entries keep their metadata untouched.
   */
  public void record(
      String storyId,
      String storyTitle,
      int index,
      int total,
      String chapterTitle,
      int page) {
    if (storyId == null || storyId.length() == 0 || index < 0) {
      return;
    }
    boolean archived = isArchived(storyId);
    JSONArray next = new JSONArray();
    add(next, storyId, clean(storyTitle), index, total, clean(chapterTitle), page, archived);
    JSONArray current = items();
    for (int i = 0; i < current.size() && next.size() < MAX_ITEMS; i++) {
      try {
        JSONObject entry = current.getObject(i);
        String story = entry.getString("story", "");
        if (story.length() > 0 && !storyId.equals(story)) {
          add(
              next,
              story,
              entry.getString("title", ""),
              entry.getInt("index", 0),
              entry.getInt("parts", 0),
              entry.getString("chapter", ""),
              entry.getInt("page", 0),
              entry.getBoolean("archived", false));
        }
      } catch (Exception e) {
      }
    }
    save(next);
  }

  /** Page where the reader stopped in the last opened chapter, or 0 when there is none. */
  public int getPage(String storyId) {
    if (storyId == null) {
      return 0;
    }
    JSONArray items = items();
    for (int i = 0; i < items.size(); i++) {
      try {
        JSONObject entry = items.getObject(i);
        if (storyId.equals(entry.getString("story", ""))) {
          return entry.getInt("page", 0);
        }
      } catch (Exception e) {
      }
    }
    return 0;
  }

  /** Saved stories, most recently read first, as rows laid out with the {@code ENTRY_*} indexes. */
  public Vector getEntries() {
    return getEntries(false);
  }

  /** Saved stories filtered by archive state, most recently read first. */
  public Vector getEntries(boolean archived) {
    Vector result = new Vector();
    JSONArray items = items();
    for (int i = 0; i < items.size(); i++) {
      try {
        JSONObject entry = items.getObject(i);
        String story = entry.getString("story", "");
        if (story.length() == 0
            || entry.getBoolean("archived", false) != archived) {
          continue;
        }
        result.addElement(row(entry));
      } catch (Exception e) {
      }
    }
    return result;
  }

  /**
   * Most recently read story, regardless of whether it is archived, or {@code null} when the
   * reading list is empty.
   */
  public String[] getLatestEntry() {
    JSONArray items = items();
    for (int i = 0; i < items.size(); i++) {
      try {
        JSONObject entry = items.getObject(i);
        if (entry.getString("story", "").length() == 0) {
          continue;
        }
        return row(entry);
      } catch (Exception e) {
      }
    }
    return null;
  }

  /** Drops one story from the list. */
  public void remove(String storyId) {
    if (storyId == null) {
      return;
    }
    JSONArray next = new JSONArray();
    JSONArray current = items();
    for (int i = 0; i < current.size(); i++) {
      try {
        JSONObject entry = current.getObject(i);
        String story = entry.getString("story", "");
        if (story.length() == 0 || storyId.equals(story)) {
          continue;
        }
        add(
            next,
            story,
            entry.getString("title", ""),
            entry.getInt("index", 0),
            entry.getInt("parts", 0),
            entry.getString("chapter", ""),
            entry.getInt("page", 0),
            entry.getBoolean("archived", false));
      } catch (Exception e) {
      }
    }
    save(next);
  }

  /** Clears only the normal reading list or only the archived stories. */
  public void clear(boolean archived) {
    JSONArray next = new JSONArray();
    JSONArray current = items();
    for (int i = 0; i < current.size(); i++) {
      try {
        JSONObject entry = current.getObject(i);
        String story = entry.getString("story", "");
        if (story.length() == 0
            || entry.getBoolean("archived", false) == archived) {
          continue;
        }
        add(
            next,
            story,
            entry.getString("title", ""),
            entry.getInt("index", 0),
            entry.getInt("parts", 0),
            entry.getString("chapter", ""),
            entry.getInt("page", 0),
            entry.getBoolean("archived", false));
      } catch (Exception e) {
      }
    }
    save(next);
  }

  /** Moves a story to the archive, whether or not it was finished. */
  public void archive(String storyId) {
    setArchived(storyId, true);
  }

  /** Moves an archived story back to the normal reading list. */
  public void unarchive(String storyId) {
    setArchived(storyId, false);
  }

  public boolean isArchived(String storyId) {
    if (storyId == null) {
      return false;
    }
    JSONArray items = items();
    for (int i = 0; i < items.size(); i++) {
      try {
        JSONObject entry = items.getObject(i);
        if (storyId.equals(entry.getString("story", ""))) {
          return entry.getBoolean("archived", false);
        }
      } catch (Exception e) {
      }
    }
    return false;
  }

  private void setArchived(String storyId, boolean archived) {
    if (storyId == null) {
      return;
    }
    JSONArray next = new JSONArray();
    JSONArray current = items();
    for (int i = 0; i < current.size(); i++) {
      try {
        JSONObject entry = current.getObject(i);
        String story = entry.getString("story", "");
        if (story.length() == 0) {
          continue;
        }
        boolean flag =
            storyId.equals(story)
                ? archived
                : entry.getBoolean("archived", false);
        add(
            next,
            story,
            entry.getString("title", ""),
            entry.getInt("index", 0),
            entry.getInt("parts", 0),
            entry.getString("chapter", ""),
            entry.getInt("page", 0),
            flag);
      } catch (Exception e) {
      }
    }
    save(next);
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

  private static String[] row(JSONObject entry) throws Exception {
    String[] row = new String[ENTRY_SIZE];
    row[ENTRY_ID] = entry.getString("story", "");
    row[ENTRY_TITLE] = entry.getString("title", "");
    row[ENTRY_INDEX] = String.valueOf(entry.getInt("index", 0));
    row[ENTRY_TOTAL] = String.valueOf(entry.getInt("parts", 0));
    row[ENTRY_CHAPTER] = entry.getString("chapter", "");
    row[ENTRY_PAGE] = String.valueOf(entry.getInt("page", 0));
    row[ENTRY_ARCHIVED] = entry.getBoolean("archived", false) ? "1" : "0";
    return row;
  }

  private static void add(
      JSONArray entries,
      String story,
      String title,
      int index,
      int total,
      String chapter,
      int page,
      boolean archived) {
    JSONObject entry = new JSONObject();
    entry.put("story", story);
    entry.put("title", title);
    entry.put("index", index);
    entry.put("parts", total);
    entry.put("chapter", chapter);
    entry.put("page", page < 0 ? 0 : page);
    entry.put("archived", archived);
    entries.add(entry);
  }

  /** Keeps list labels on one line: item text uses a newline of its own. */
  private static String clean(String text) {
    if (text == null) {
      return "";
    }
    StringBuffer sb = new StringBuffer(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      sb.append(c == '\n' || c == '\r' || c == '\t' ? ' ' : c);
    }
    return sb.toString().trim();
  }

  private void save(JSONArray next) {
    entries = next;
    storage.saveAsync(next.toString());
  }
}
