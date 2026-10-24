package midplay.store;

/** Recent Reddit search terms, most recent first, persisted across sessions. */
public final class RedditSearchHistoryManager extends SearchHistoryStore {
  private static RedditSearchHistoryManager instance;

  public static RedditSearchHistoryManager getInstance() {
    if (instance == null) {
      instance = new RedditSearchHistoryManager();
    }
    return instance;
  }

  private RedditSearchHistoryManager() {
    super("reddit_search_history");
  }
}
