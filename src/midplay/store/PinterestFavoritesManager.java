package midplay.store;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import java.util.Vector;
import midplay.net.PinterestApi;

/**
 * Local Pinterest favorites: saved pins, saved online boards and user-created local folders.
 *
 * <p>Local folders (like Pinterest boards, but kept on the device) hold saved pins. A pin can
 * either live inside a folder or be left loose directly in favorites. Nothing is downloaded here;
 * only the compact metadata returned by the proxy is stored so the items can be reopened later (the
 * pins and images themselves stay online until the user explicitly downloads them).
 */
public final class PinterestFavoritesManager {
  public static final int SUCCESS = 0;
  public static final int ALREADY_EXISTS = 1;
  public static final int FAILED = 2;

  /** Compact local-folder row indexes. */
  public static final int FOLDER_ID = 0;
  public static final int FOLDER_NAME = 1;
  public static final int FOLDER_COUNT = 2;
  public static final int FOLDER_SIZE = 3;

  private static PinterestFavoritesManager instance;
  private static int folderSeq;

  public static synchronized PinterestFavoritesManager getInstance() {
    if (instance == null) {
      instance = new PinterestFavoritesManager();
    }
    return instance;
  }

  private final JsonRecordStore pinsStorage = new JsonRecordStore("pinterest_fav_pins", 1, "[]");
  private final JsonRecordStore boardsStorage =
      new JsonRecordStore("pinterest_fav_boards", 1, "[]");
  private final JsonRecordStore foldersStorage =
      new JsonRecordStore("pinterest_fav_folders", 1, "[]");
  private JSONArray pins;
  private JSONArray boards;
  private JSONArray folders;

  private PinterestFavoritesManager() {}

  // ---------------------------------------------------------------------------
  // Pins
  // ---------------------------------------------------------------------------

  public Vector getPins() {
    Vector result = new Vector();
    JSONArray items = pins();
    for (int i = 0; i < items.size(); i++) {
      String[] row = readPin(items.getObject(i));
      if (row != null) {
        result.addElement(row);
      }
    }
    return result;
  }

  public boolean isPinSaved(String id) {
    return id != null && indexOf(pins(), "id", id) >= 0;
  }

  /** Saves a pin in the loose favorites (no local folder). */
  public int savePin(String[] pin) {
    return savePin(pin, "");
  }

  /** Saves a pin into a local folder; an empty or unknown folder keeps it loose. */
  public int savePin(String[] pin, String folderId) {
    if (pin == null || pin[PinterestApi.PIN_ID].length() == 0) {
      return FAILED;
    }
    String id = pin[PinterestApi.PIN_ID];
    String folder = normalizedFolder(folderId);
    int existing = indexOf(pins(), "id", id);
    if (existing >= 0) {
      // Already saved: move it into the requested folder instead of duplicating it.
      try {
        JSONObject item = pins().getObject(existing);
        if (!folder.equals(item.getString("fo", ""))) {
          item.put("fo", folder);
          savePins(pins());
        }
      } catch (Exception e) {
      }
      return ALREADY_EXISTS;
    }
    JSONArray next = new JSONArray();
    next.add(writePin(pin, folder));
    JSONArray current = pins();
    for (int i = 0; i < current.size(); i++) {
      next.add(current.getObject(i));
    }
    return savePins(next) ? SUCCESS : FAILED;
  }

  /** Saves several pins at once into a folder, returning how many were newly stored. */
  public int savePins(Vector pins, String folderId) {
    if (pins == null) {
      return 0;
    }
    String folder = normalizedFolder(folderId);
    int added = 0;
    for (int i = 0; i < pins.size(); i++) {
      String[] pin = (String[]) pins.elementAt(i);
      if (pin == null || pin[PinterestApi.PIN_ID].length() == 0) {
        continue;
      }
      if (isPinSaved(pin[PinterestApi.PIN_ID])) {
        setPinFolder(pin[PinterestApi.PIN_ID], folder);
        continue;
      }
      savePin(pin, folder);
      added++;
    }
    return added;
  }

  public boolean removePin(String id) {
    if (id == null) {
      return false;
    }
    String[] ids = new String[] {id};
    return removePins(ids) > 0;
  }

  /** Removes several pins at once, returning how many were actually removed. */
  public int removePins(String[] ids) {
    if (ids == null || ids.length == 0) {
      return 0;
    }
    JSONArray current = pins();
    JSONArray next = new JSONArray();
    int removed = 0;
    for (int i = 0; i < current.size(); i++) {
      JSONObject item = current.getObject(i);
      String id = item.getString("id", "");
      if (contains(ids, id)) {
        removed++;
      } else {
        next.add(item);
      }
    }
    if (removed > 0) {
      savePins(next);
    }
    return removed;
  }

  public boolean setPinFolder(String pinId, String folderId) {
    if (pinId == null) {
      return false;
    }
    String folder = normalizedFolder(folderId);
    JSONArray current = pins();
    int index = indexOf(current, "id", pinId);
    if (index < 0) {
      return false;
    }
    try {
      current.getObject(index).put("fo", folder);
      savePins(current);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /** Moves several pins at once to a folder (empty folder id means loose), returning the count. */
  public int setPinsFolder(String[] pinIds, String folderId) {
    if (pinIds == null || pinIds.length == 0) {
      return 0;
    }
    String folder = normalizedFolder(folderId);
    JSONArray current = pins();
    int moved = 0;
    for (int i = 0; i < current.size(); i++) {
      try {
        JSONObject item = current.getObject(i);
        if (contains(pinIds, item.getString("id", ""))) {
          item.put("fo", folder);
          moved++;
        }
      } catch (Exception e) {
      }
    }
    if (moved > 0) {
      savePins(current);
    }
    return moved;
  }

  public void clearPins() {
    savePins(new JSONArray());
  }

  // ---------------------------------------------------------------------------
  // Local folders
  // ---------------------------------------------------------------------------

  /** Local folders as compact rows: {@code {id, name, pin count}} (newest first). */
  public Vector getFolders() {
    Vector result = new Vector();
    JSONArray items = folders();
    for (int i = 0; i < items.size(); i++) {
      JSONObject item = items.getObject(i);
      String id = item.getString("id", "");
      if (id.length() == 0) {
        continue;
      }
      result.addElement(
          new String[] {id, item.getString("n", ""), Integer.toString(countPinsInFolder(id))});
    }
    return result;
  }

  public boolean isFolder(String id) {
    return id != null && id.length() > 0 && indexOf(folders(), "id", id) >= 0;
  }

  public String getFolderName(String id) {
    int index = id == null ? -1 : indexOf(folders(), "id", id);
    if (index < 0) {
      return "";
    }
    try {
      return folders().getObject(index).getString("n", "");
    } catch (Exception e) {
      return "";
    }
  }

  /** Finds a folder by name (case-insensitive), or null when none matches. */
  public String findFolderByName(String name) {
    String wanted = name == null ? "" : name.trim();
    JSONArray items = folders();
    for (int i = 0; i < items.size(); i++) {
      String folderName = items.getObject(i).getString("n", "");
      if (folderName.equalsIgnoreCase(wanted)) {
        return items.getObject(i).getString("id", "");
      }
    }
    return null;
  }

  /** Creates a local folder and returns its id, or null when the name is empty. */
  public String createFolder(String name) {
    String value = name == null ? "" : name.trim();
    if (value.length() == 0) {
      return null;
    }
    String id = newFolderId();
    JSONObject item = new JSONObject();
    item.put("id", id);
    item.put("n", value);
    JSONArray next = new JSONArray();
    next.add(item);
    JSONArray current = folders();
    for (int i = 0; i < current.size(); i++) {
      next.add(current.getObject(i));
    }
    return saveFolders(next) ? id : null;
  }

  public boolean renameFolder(String id, String name) {
    String value = name == null ? "" : name.trim();
    if (id == null || value.length() == 0) {
      return false;
    }
    JSONArray current = folders();
    int index = indexOf(current, "id", id);
    if (index < 0) {
      return false;
    }
    try {
      current.getObject(index).put("n", value);
      return saveFolders(current);
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Removes a local folder and every pin stored inside it. Pins left loose in favorites are
   * untouched. Returns the number of pins removed together with the folder.
   */
  public int deleteFolder(String id) {
    if (id == null || id.length() == 0) {
      return 0;
    }
    JSONArray next = new JSONArray();
    JSONArray current = folders();
    boolean removed = false;
    for (int i = 0; i < current.size(); i++) {
      JSONObject item = current.getObject(i);
      if (id.equals(item.getString("id", ""))) {
        removed = true;
      } else {
        next.add(item);
      }
    }
    if (!removed) {
      return 0;
    }
    saveFolders(next);
    return removePinsInFolder(id);
  }

  public int countPinsInFolder(String folderId) {
    if (folderId == null || folderId.length() == 0) {
      return 0;
    }
    int count = 0;
    JSONArray items = pins();
    for (int i = 0; i < items.size(); i++) {
      if (folderId.equals(items.getObject(i).getString("fo", ""))) {
        count++;
      }
    }
    return count;
  }

  public Vector getPinsInFolder(String folderId) {
    Vector result = new Vector();
    if (folderId == null || folderId.length() == 0) {
      return result;
    }
    JSONArray items = pins();
    for (int i = 0; i < items.size(); i++) {
      JSONObject item = items.getObject(i);
      if (folderId.equals(item.getString("fo", ""))) {
        String[] row = readPin(item);
        if (row != null) {
          result.addElement(row);
        }
      }
    }
    return result;
  }

  /** Pins kept loose in favorites: no folder, or a folder that no longer exists. */
  public Vector getLoosePins() {
    Vector result = new Vector();
    JSONArray items = pins();
    for (int i = 0; i < items.size(); i++) {
      JSONObject item = items.getObject(i);
      String folder = item.getString("fo", "");
      if (folder.length() == 0 || !isFolder(folder)) {
        String[] row = readPin(item);
        if (row != null) {
          result.addElement(row);
        }
      }
    }
    return result;
  }

  private int removePinsInFolder(String folderId) {
    JSONArray current = pins();
    JSONArray next = new JSONArray();
    int removed = 0;
    for (int i = 0; i < current.size(); i++) {
      JSONObject item = current.getObject(i);
      if (folderId.equals(item.getString("fo", ""))) {
        removed++;
      } else {
        next.add(item);
      }
    }
    if (removed > 0) {
      savePins(next);
    }
    return removed;
  }

  // ---------------------------------------------------------------------------
  // Saved online boards
  // ---------------------------------------------------------------------------

  public Vector getBoards() {
    Vector result = new Vector();
    JSONArray items = boards();
    for (int i = 0; i < items.size(); i++) {
      String[] row = readBoard(items.getObject(i));
      if (row != null) {
        result.addElement(row);
      }
    }
    return result;
  }

  public boolean isBoardSaved(String id) {
    return id != null && indexOf(boards(), "id", id) >= 0;
  }

  public int saveBoard(String[] board) {
    if (board == null || board[PinterestApi.BOARD_ID].length() == 0) {
      return FAILED;
    }
    String id = board[PinterestApi.BOARD_ID];
    if (isBoardSaved(id)) {
      return ALREADY_EXISTS;
    }
    JSONArray next = new JSONArray();
    next.add(writeBoard(board));
    JSONArray current = boards();
    for (int i = 0; i < current.size(); i++) {
      next.add(current.getObject(i));
    }
    return saveBoards(next) ? SUCCESS : FAILED;
  }

  public boolean removeBoard(String id) {
    if (id == null) {
      return false;
    }
    JSONArray current = boards();
    JSONArray next = new JSONArray();
    boolean removed = false;
    for (int i = 0; i < current.size(); i++) {
      JSONObject item = current.getObject(i);
      if (id.equals(item.getString("id", ""))) {
        removed = true;
      } else {
        next.add(item);
      }
    }
    if (removed) {
      saveBoards(next);
    }
    return removed;
  }

  /** Removes several saved online boards at once, returning how many were removed. */
  public int removeBoards(String[] ids) {
    if (ids == null || ids.length == 0) {
      return 0;
    }
    JSONArray current = boards();
    JSONArray next = new JSONArray();
    int removed = 0;
    for (int i = 0; i < current.size(); i++) {
      JSONObject item = current.getObject(i);
      if (contains(ids, item.getString("id", ""))) {
        removed++;
      } else {
        next.add(item);
      }
    }
    if (removed > 0) {
      saveBoards(next);
    }
    return removed;
  }

  public void clearBoards() {
    saveBoards(new JSONArray());
  }

  // ---------------------------------------------------------------------------
  // Serialization
  // ---------------------------------------------------------------------------

  private static JSONObject writePin(String[] pin, String folder) {
    JSONObject item = new JSONObject();
    item.put("id", pin[PinterestApi.PIN_ID]);
    item.put("ti", pin[PinterestApi.PIN_TITLE]);
    item.put("de", pin[PinterestApi.PIN_DESCRIPTION]);
    item.put("im", pin[PinterestApi.PIN_IMAGE]);
    item.put("im4", pin[PinterestApi.PIN_IMAGE_LARGE]);
    item.put("un", pin[PinterestApi.PIN_USER_NAME]);
    item.put("us", pin[PinterestApi.PIN_USER]);
    item.put("bd", pin[PinterestApi.PIN_BOARD_NAME]);
    item.put("bus", pin[PinterestApi.PIN_BOARD_USER]);
    item.put("bs", pin[PinterestApi.PIN_BOARD_SLUG]);
    item.put("ct", pin[PinterestApi.PIN_CREATED]);
    item.put("fo", folder == null ? "" : folder);
    return item;
  }

  private static String[] readPin(JSONObject item) {
    if (item == null) {
      return null;
    }
    String id = item.getString("id", "");
    if (id.length() == 0) {
      return null;
    }
    String[] row = new String[PinterestApi.PIN_SIZE];
    row[PinterestApi.PIN_ID] = id;
    row[PinterestApi.PIN_TITLE] = item.getString("ti", "");
    row[PinterestApi.PIN_DESCRIPTION] = item.getString("de", "");
    row[PinterestApi.PIN_IMAGE] = item.getString("im", "");
    row[PinterestApi.PIN_IMAGE_LARGE] = item.getString("im4", "");
    row[PinterestApi.PIN_USER_NAME] = item.getString("un", "");
    row[PinterestApi.PIN_USER] = item.getString("us", "");
    row[PinterestApi.PIN_BOARD_NAME] = item.getString("bd", "");
    row[PinterestApi.PIN_BOARD_USER] = item.getString("bus", "");
    row[PinterestApi.PIN_BOARD_SLUG] = item.getString("bs", "");
    row[PinterestApi.PIN_CREATED] = item.getString("ct", "");
    return row;
  }

  private static JSONObject writeBoard(String[] board) {
    JSONObject item = new JSONObject();
    item.put("id", board[PinterestApi.BOARD_ID]);
    item.put("n", board[PinterestApi.BOARD_NAME]);
    item.put("d", board[PinterestApi.BOARD_DESCRIPTION]);
    item.put("c", board[PinterestApi.BOARD_COVER]);
    item.put("us", board[PinterestApi.BOARD_USER]);
    item.put("sl", board[PinterestApi.BOARD_SLUG]);
    item.put("pc", board[PinterestApi.BOARD_PIN_COUNT]);
    item.put("fc", board[PinterestApi.BOARD_FOLLOWER_COUNT]);
    return item;
  }

  private static String[] readBoard(JSONObject item) {
    if (item == null) {
      return null;
    }
    String id = item.getString("id", "");
    if (id.length() == 0) {
      return null;
    }
    String[] row = new String[PinterestApi.BOARD_SIZE];
    row[PinterestApi.BOARD_ID] = id;
    row[PinterestApi.BOARD_NAME] = item.getString("n", "");
    row[PinterestApi.BOARD_DESCRIPTION] = item.getString("d", "");
    row[PinterestApi.BOARD_COVER] = item.getString("c", "");
    row[PinterestApi.BOARD_USER] = item.getString("us", "");
    row[PinterestApi.BOARD_SLUG] = item.getString("sl", "");
    row[PinterestApi.BOARD_PIN_COUNT] = item.getString("pc", "0");
    row[PinterestApi.BOARD_FOLLOWER_COUNT] = item.getString("fc", "0");
    return row;
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private String normalizedFolder(String folderId) {
    return isFolder(folderId) ? folderId : "";
  }

  private static boolean contains(String[] values, String value) {
    for (int i = 0; i < values.length; i++) {
      if (values[i] != null && values[i].equals(value)) {
        return true;
      }
    }
    return false;
  }

  private static String newFolderId() {
    folderSeq++;
    return "f" + Long.toString(System.currentTimeMillis()) + "_" + folderSeq;
  }

  private static int indexOf(JSONArray items, String key, String value) {
    for (int i = 0; i < items.size(); i++) {
      try {
        if (value.equals(items.getObject(i).getString(key, ""))) {
          return i;
        }
      } catch (Exception e) {
      }
    }
    return -1;
  }

  private JSONArray pins() {
    if (pins == null) {
      pins = loadArray(pinsStorage);
    }
    return pins;
  }

  private JSONArray boards() {
    if (boards == null) {
      boards = loadArray(boardsStorage);
    }
    return boards;
  }

  private JSONArray folders() {
    if (folders == null) {
      folders = loadArray(foldersStorage);
    }
    return folders;
  }

  private static JSONArray loadArray(JsonRecordStore storage) {
    try {
      return JSON.getArray(storage.load());
    } catch (Exception e) {
      return new JSONArray();
    }
  }

  // The in-memory array is the source of truth for the session; the whole JSON document is written
  // by the background writer so a large list does not stall the UI thread.
  private boolean savePins(JSONArray next) {
    pins = next;
    pinsStorage.saveAsync(next.toString());
    return true;
  }

  private boolean saveBoards(JSONArray next) {
    boards = next;
    boardsStorage.saveAsync(next.toString());
    return true;
  }

  private boolean saveFolders(JSONArray next) {
    folders = next;
    foldersStorage.saveAsync(next.toString());
    return true;
  }
}
