package midplay.ui.screen;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.StringItem;
import javax.microedition.rms.RecordStore;
import midplay.net.LyricsApi;
import midplay.store.Configuration;
import midplay.store.JsonRecordStore;
import midplay.store.LyricsSearchHistoryManager;
import midplay.store.MusicSearchHistoryManager;
import midplay.store.PinterestSearchHistoryManager;
import midplay.store.RecentManager;
import midplay.store.RedditSearchHistoryManager;
import midplay.store.SettingsManager;
import midplay.store.UriHistoryManager;
import midplay.store.WattpadSearchHistoryManager;
import midplay.ui.BaseForm;
import midplay.ui.Navigator;
import midplay.util.Lang;
import midplay.util.Utils;

/**
 * Hidden storage panel, reachable only by typing {@code rms} in the Open... screen.
 *
 * <p>Shows how much space the RMS areas and the memory card use, and offers a safe way to clear
 * history caches without touching notes, tasks, playlists or settings.
 */
public final class StorageScreen extends BaseForm {
  private static final int GROUP_PLAYLISTS = 0;
  private static final int GROUP_NOTES = 1;
  private static final int GROUP_TASKS = 2;
  private static final int GROUP_DOWNLOADS = 3;
  private static final int GROUP_SETTINGS = 4;
  private static final int GROUP_HISTORY = 5;
  private static final int GROUP_WATTPAD = 6;
  private static final int GROUP_PINTEREST = 7;
  private static final int GROUP_REDDIT = 8;
  private static final int GROUP_OTHER = 9;
  private static final int GROUP_COUNT = 10;

  private static final String[] GROUP_NAMES = {
    "storage.group_playlists",
    "storage.group_notes",
    "storage.group_tasks",
    "storage.group_downloads",
    "storage.group_settings",
    "storage.group_history",
    "storage.group_wattpad",
    "storage.group_pinterest",
    "storage.group_reddit",
    "storage.group_other"
  };

  /** Used when {@link RecordStore#listRecordStores()} is unavailable on the device. */
  private static final String[] KNOWN_STORES = {
    Configuration.STORAGE_FAVORITES,
    Configuration.STORAGE_TRACKS,
    Configuration.STORAGE_NOTES,
    Configuration.STORAGE_TASKS,
    Configuration.STORAGE_DOWNLOADS,
    Configuration.STORAGE_DOWNLOADS_BACKUP,
    Configuration.STORAGE_SETTINGS,
    Configuration.STORAGE_MENU,
    Configuration.STORAGE_RECENT,
    Configuration.STORAGE_LAST_SESSION,
    "open_uri_history",
    "music_search_history",
    "lyrics_search_history",
    "wattpad_search_history",
    "pinterest_search_history",
    "reddit_search_history",
    "wattpad_progress",
    "pinterest_fav_pins",
    "pinterest_fav_boards",
    "pinterest_fav_folders",
    "reddit_fav_posts",
    "reddit_fav_subs",
    "reddit_fav_users"
  };

  private final Command refreshCommand;
  private final Command clearHistoryCommand;

  public StorageScreen(Navigator navigator) {
    super(Lang.tr("storage.title"), navigator);
    refreshCommand = new Command(Lang.tr("storage.refresh"), Command.SCREEN, 1);
    clearHistoryCommand = new Command(Lang.tr("storage.clear_history"), Command.SCREEN, 2);
    addCommand(refreshCommand);
    addCommand(clearHistoryCommand);
    rebuild();
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == refreshCommand) {
      rebuild();
    } else if (c == clearHistoryCommand) {
      promptClearHistory();
    }
  }

  private void promptClearHistory() {
    navigator.showConfirmationAlert(
        Lang.tr("storage.clear_confirm"),
        new Runnable() {
          public void run() {
            clearHistory();
            rebuild();
            navigator.showAlert(Lang.tr("storage.history_cleared"), AlertType.CONFIRMATION);
          }
        },
        AlertType.WARNING);
  }

  private void clearHistory() {
    try {
      RecentManager.getInstance().clear();
      UriHistoryManager.getInstance().clear();
      MusicSearchHistoryManager.getInstance().clear();
      LyricsSearchHistoryManager.getInstance().clear();
      WattpadSearchHistoryManager.getInstance().clear();
      PinterestSearchHistoryManager.getInstance().clear();
      RedditSearchHistoryManager.getInstance().clear();
      LyricsApi.clearCache();
      // History stores write asynchronously; flush so the sizes below reflect the cleared state.
      JsonRecordStore.flushAll();
    } catch (Throwable t) {
    }
  }

  private void rebuild() {
    deleteAll();
    String[] names = listStoreNames();
    long[] sizes = new long[GROUP_COUNT];
    int[] counts = new int[GROUP_COUNT];
    long totalSize = 0;
    int totalRecords = 0;
    int areas = 0;
    long available = -1;

    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      if (name == null || name.length() == 0) {
        continue;
      }
      RecordStore store = null;
      try {
        store = RecordStore.openRecordStore(name, false);
        int size = store.getSize();
        int count = store.getNumRecords();
        if (available < 0) {
          try {
            available = store.getSizeAvailable();
          } catch (Throwable t) {
          }
        }
        if (size > 0 || count > 0) {
          int group = groupOf(name);
          sizes[group] += size;
          counts[group] += count;
          totalSize += size;
          totalRecords += count;
        }
      } catch (Throwable t) {
        // Missing stores and devices without listRecordStores() are expected; skip them.
      } finally {
        closeStore(store);
      }
    }

    for (int g = 0; g < GROUP_COUNT; g++) {
      if (counts[g] > 0 || sizes[g] > 0) {
        areas++;
      }
    }

    StringBuffer rms = new StringBuffer();
    if (areas == 0) {
      rms.append(Lang.tr("storage.empty"));
    } else {
      String summary = Lang.tr("storage.summary");
      summary = Utils.replace(summary, "{0}", Utils.formatDataSize(totalSize));
      summary = Utils.replace(summary, "{1}", Integer.toString(areas));
      summary = Utils.replace(summary, "{2}", Integer.toString(totalRecords));
      rms.append(summary);
      if (available >= 0) {
        rms.append('\n').append(Lang.tr("storage.available", Utils.formatDataSize(available)));
      }
      rms.append('\n');
      for (int g = 0; g < GROUP_COUNT; g++) {
        if (counts[g] == 0 && sizes[g] == 0) {
          continue;
        }
        rms.append('\n')
            .append(Lang.tr(GROUP_NAMES[g]))
            .append(": ")
            .append(Utils.formatDataSize(sizes[g]))
            .append(" (")
            .append(counts[g])
            .append(')');
      }
    }
    append(new StringItem(Lang.tr("storage.rms"), rms.toString()));
    append(new StringItem(Lang.tr("storage.card"), cardText()));
  }

  private static String[] listStoreNames() {
    try {
      String[] names = RecordStore.listRecordStores();
      if (names != null && names.length > 0) {
        return names;
      }
    } catch (Throwable t) {
    }
    return KNOWN_STORES;
  }

  /** Free/total space of the download folder, shown under the memory card title. */
  private String cardText() {
    String path = SettingsManager.getInstance().getCurrentDownloadPath();
    if (path == null || path.length() == 0) {
      return Lang.tr("storage.card_unavailable");
    }
    FileConnection file = null;
    try {
      file = (FileConnection) Connector.open(path, Connector.READ);
      if (file.exists()) {
        String line = Lang.tr("storage.card_line");
        line = Utils.replace(line, "{0}", Utils.formatDataSize(file.availableSize()));
        line = Utils.replace(line, "{1}", Utils.formatDataSize(file.totalSize()));
        return Lang.tr("settings.download_path") + ": " + line;
      }
    } catch (Throwable t) {
    } finally {
      if (file != null) {
        try {
          file.close();
        } catch (Exception e) {
        }
      }
    }
    return Lang.tr("storage.card_unavailable");
  }

  private static int groupOf(String name) {
    if (name.equals(Configuration.STORAGE_FAVORITES)
        || name.equals(Configuration.STORAGE_TRACKS)) {
      return GROUP_PLAYLISTS;
    }
    if (name.equals(Configuration.STORAGE_NOTES)) {
      return GROUP_NOTES;
    }
    if (name.equals(Configuration.STORAGE_TASKS)) {
      return GROUP_TASKS;
    }
    if (name.equals(Configuration.STORAGE_DOWNLOADS)
        || name.equals(Configuration.STORAGE_DOWNLOADS_BACKUP)) {
      return GROUP_DOWNLOADS;
    }
    if (name.equals(Configuration.STORAGE_SETTINGS) || name.equals(Configuration.STORAGE_MENU)) {
      return GROUP_SETTINGS;
    }
    if (name.equals(Configuration.STORAGE_RECENT)
        || name.equals(Configuration.STORAGE_LAST_SESSION)
        || name.equals("open_uri_history")
        || name.endsWith("search_history")) {
      return GROUP_HISTORY;
    }
    if (name.startsWith("wattpad")) {
      return GROUP_WATTPAD;
    }
    if (name.startsWith("pinterest")) {
      return GROUP_PINTEREST;
    }
    if (name.startsWith("reddit")) {
      return GROUP_REDDIT;
    }
    return GROUP_OTHER;
  }

  private static void closeStore(RecordStore store) {
    if (store != null) {
      try {
        store.closeRecordStore();
      } catch (Exception e) {
      }
    }
  }
}
