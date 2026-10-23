package midplay.store;

/** Recent Wattpad search terms, most recent first, persisted across sessions. */
public final class WattpadSearchHistoryManager extends SearchHistoryStore {
  private static WattpadSearchHistoryManager instance;

  public static WattpadSearchHistoryManager getInstance() {
    if (instance == null) {
      instance = new WattpadSearchHistoryManager();
    }
    return instance;
  }

  private WattpadSearchHistoryManager() {
    super("wattpad_search_history");
  }
}
