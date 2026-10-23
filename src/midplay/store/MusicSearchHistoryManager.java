package midplay.store;

/** Recent music search terms, most recent first, persisted across sessions. */
public final class MusicSearchHistoryManager extends SearchHistoryStore {
  private static MusicSearchHistoryManager instance;

  public static MusicSearchHistoryManager getInstance() {
    if (instance == null) {
      instance = new MusicSearchHistoryManager();
    }
    return instance;
  }

  private MusicSearchHistoryManager() {
    super("music_search_history");
  }
}
