package midplay.store;

/** Recent lyrics search terms, most recent first, persisted across sessions. */
public final class LyricsSearchHistoryManager extends SearchHistoryStore {
  private static LyricsSearchHistoryManager instance;

  public static LyricsSearchHistoryManager getInstance() {
    if (instance == null) {
      instance = new LyricsSearchHistoryManager();
    }
    return instance;
  }

  private LyricsSearchHistoryManager() {
    super("lyrics_search_history");
  }
}
