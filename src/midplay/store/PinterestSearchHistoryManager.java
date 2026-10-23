package midplay.store;

/** Recent Pinterest search terms, most recent first, persisted across sessions. */
public final class PinterestSearchHistoryManager extends SearchHistoryStore {
  private static PinterestSearchHistoryManager instance;

  public static PinterestSearchHistoryManager getInstance() {
    if (instance == null) {
      instance = new PinterestSearchHistoryManager();
    }
    return instance;
  }

  private PinterestSearchHistoryManager() {
    super("pinterest_search_history");
  }
}
