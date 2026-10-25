package midplay.ui.screen;

import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import midplay.MIDPlay;
import midplay.net.HttpTextOperation;
import midplay.net.RedditApi;
import midplay.net.RedditThumbnailOperation;
import midplay.store.Configuration;
import midplay.store.RedditFavoritesManager;
import midplay.store.SettingsManager;
import midplay.ui.BaseList;
import midplay.ui.Navigator;
import midplay.ui.Sharing;
import midplay.util.Lang;

/**
 * Paginated list of Reddit posts, subreddits or users. A subreddit opens its newest posts, a user
 * opens their posts, and a post opens its detail screen.
 */
public final class RedditResultsScreen extends BaseList {
  public static final int SOURCE_SEARCH_SUBREDDITS = 0;
  public static final int SOURCE_SEARCH_USERS = 1;
  public static final int SOURCE_SEARCH_POSTS = 2;
  public static final int SOURCE_SUBPOSTS = 3;
  public static final int SOURCE_USER_POSTS = 4;

  private final int source;
  private final String sourceId;
  private final String sourceQuery;
  private final String sourceSort;
  private final Vector items;
  private final Hashtable ids = new Hashtable();
  private final Hashtable thumbCache = new Hashtable();
  private final Command moreCommand;
  private final Command saveCommand;
  private final Command shareCommand;
  private final Command newCommand;
  private final Command topCommand;
  private String bookmark;
  private boolean hasMore;
  private boolean loading;
  private boolean thumbsInFlight;
  private int thumbToken;
  private RedditThumbnailOperation thumbOperation;
  private final Command translateCommand;
  private final Command originalCommand;
  private final Command followCommand;
  private final Command unfollowCommand;
  private final Hashtable translatedTitles = new Hashtable();
  private boolean translating;
  private boolean titlesTranslated;
  private boolean showOriginal;

  public RedditResultsScreen(
      Navigator navigator,
      int source,
      String sourceId,
      String sourceQuery,
      String sourceSort,
      String title,
      Vector items,
      String bookmark) {
    super(title, navigator);
    this.source = source;
    this.sourceId = sourceId == null ? "" : sourceId;
    this.sourceQuery = sourceQuery == null ? "" : sourceQuery;
    this.sourceSort = sourceSort == null ? "" : sourceSort;
    this.items = items == null ? new Vector() : items;
    this.bookmark = bookmark == null ? "" : bookmark;
    hasMore = this.bookmark.length() > 0;
    moreCommand = new Command(Lang.tr("reddit.more"), Command.SCREEN, 1);
    saveCommand = new Command(Lang.tr("reddit.save"), Command.SCREEN, 2);
    shareCommand = new Command(Lang.tr("reddit.share"), Command.SCREEN, 3);
    newCommand = new Command(Lang.tr("reddit.sort_new"), Command.SCREEN, 5);
    topCommand = new Command(Lang.tr("reddit.sort_top"), Command.SCREEN, 6);
    translateCommand = new Command(Lang.tr("reddit.translate"), Command.SCREEN, 8);
    originalCommand = new Command(Lang.tr("reddit.show_original"), Command.SCREEN, 8);
    followCommand = new Command(Lang.tr("reddit.follow"), Command.SCREEN, 9);
    unfollowCommand = new Command(Lang.tr("reddit.unfollow"), Command.SCREEN, 9);
    for (int i = 0; i < this.items.size(); i++) {
      String id = idOf((String[]) this.items.elementAt(i));
      ids.put(id, id);
    }
    if (this.items.size() > 0) {
      addCommand(shareCommand);
    }
    if (isPosts() && this.items.size() > 0) {
      addCommand(saveCommand);
    }
    if (isSubPosts()) {
      addCommand(newCommand);
      addCommand(topCommand);
    }
    if (hasMore) {
      addCommand(moreCommand);
    }
    if (isPosts() && this.items.size() > 0) {
      updateTranslateCommands();
    }
    if (isSubreddits() || isUsers()) {
      addCommand(followCommand);
      addCommand(unfollowCommand);
    }
    refresh();
    loadThumbnails();
    if (isPosts()
        && SettingsManager.getInstance().getRedditAutoTranslate()
            == Configuration.REDDIT_TRANSLATE_ON) {
      translateTitles(false);
    }
  }

  protected void populateItems() {
    for (int i = 0; i < items.size(); i++) {
      String[] item = (String[]) items.elementAt(i);
      String thumbUrl = thumbnailUrl(item);
      Image image =
          thumbUrl == null || thumbUrl.length() == 0
              ? null
              : (Image) thumbCache.get(thumbUrl);
      append(label(item), image);
    }
  }

  private String label(String[] item) {
    if (isPosts()) {
      if (!showOriginal) {
        String translated = (String) translatedTitles.get(item[RedditApi.POST_ID]);
        if (translated != null) {
          return RedditApi.postLabel(item, translated);
        }
      }
      return RedditApi.postLabel(item);
    }
    if (isUsers()) {
      return RedditApi.userLabel(item);
    }
    return RedditApi.subredditLabel(item);
  }

  protected void handleSelection() {
    int index = getSelectedIndex();
    if (index < 0 || index >= items.size() || loading) {
      return;
    }
    String[] item = (String[]) items.elementAt(index);
    if (isPosts()) {
      navigator.forward(new RedditPostScreen(navigator, item));
    } else if (isUsers()) {
      RedditProfileScreen.open(navigator, true, item[RedditApi.USER_NAME]);
    } else {
      RedditProfileScreen.open(navigator, false, item[RedditApi.SUB_NAME]);
    }
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == moreCommand) {
      loadMore();
    } else if (c == saveCommand) {
      saveSelected();
    } else if (c == shareCommand) {
      shareSelected();
    } else if (c == newCommand) {
      reloadSub("new");
    } else if (c == topCommand) {
      reloadSub("top");
    } else if (c == translateCommand) {
      if (titlesTranslated) {
        showOriginal = false;
        refresh();
        updateTranslateCommands();
      } else {
        translateTitles(true);
      }
    } else if (c == originalCommand) {
      showOriginal = true;
      refresh();
      updateTranslateCommands();
    } else if (c == followCommand || c == unfollowCommand) {
      toggleFollow(c == followCommand);
    }
  }

  private boolean isSubreddits() {
    return source == SOURCE_SEARCH_SUBREDDITS;
  }

  private void updateTranslateCommands() {
    if (!isPosts() || items.size() == 0) {
      return;
    }
    if (titlesTranslated && !showOriginal) {
      removeCommand(translateCommand);
      addCommand(originalCommand);
    } else {
      removeCommand(originalCommand);
      addCommand(translateCommand);
    }
  }

  private void toggleFollow(boolean follow) {
    String[] item = selectedItem();
    if (item == null) {
      return;
    }
    if (isUsers()) {
      String name = item[RedditApi.USER_NAME];
      if (follow) {
        RedditFavoritesManager.getInstance().saveUser(name);
      } else {
        RedditFavoritesManager.getInstance().removeUser(name);
      }
    } else if (isSubreddits()) {
      String name = item[RedditApi.SUB_NAME];
      if (follow) {
        RedditFavoritesManager.getInstance().saveSubreddit(name);
      } else {
        RedditFavoritesManager.getInstance().removeSubreddit(name);
      }
    } else {
      return;
    }
    navigator.showAlert(
        Lang.tr(follow ? "reddit.followed" : "reddit.unfollowed"), AlertType.CONFIRMATION);
  }

  /** Translates every visible post title in one proxy call (line breaks keep the order). */
  private void translateTitles(final boolean reportErrors) {
    if (translating || !isPosts() || items.size() == 0) {
      return;
    }
    StringBuffer combined = new StringBuffer();
    for (int i = 0; i < items.size(); i++) {
      String[] item = (String[]) items.elementAt(i);
      if (i > 0) {
        combined.append('\n');
      }
      String title = item[RedditApi.POST_TITLE];
      combined.append(title == null ? "" : title);
    }
    translating = true;
    if (reportErrors) {
      navigator.showLoadingAlert(
          Lang.tr("reddit.translating"),
          new Runnable() {
            public void run() {
              translating = false;
            }
          });
    }
    MIDPlay.startOperation(
        new HttpTextOperation(
            RedditApi.translateUrl(combined.toString(), RedditApi.translateTarget()),
            new HttpTextOperation.Listener() {
              public void onText(final String response) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        translating = false;
                        String result = RedditApi.parseTranslation(response);
                        if (result.length() == 0) {
                          if (reportErrors) {
                            navigator.showAlert(
                                Lang.tr("reddit.translate_error"), AlertType.ERROR);
                          }
                          return;
                        }
                        String[] lines = splitLines(result);
                        if (lines.length != items.size()) {
                          // Line alignment was lost; never risk showing a title on the
                          // wrong row.
                          if (reportErrors) {
                            navigator.showAlert(
                                Lang.tr("reddit.translate_error"), AlertType.ERROR);
                          }
                          return;
                        }
                        for (int i = 0; i < lines.length; i++) {
                          String[] item = (String[]) items.elementAt(i);
                          translatedTitles.put(item[RedditApi.POST_ID], lines[i]);
                        }
                        titlesTranslated = true;
                        showOriginal = false;
                        if (reportErrors) {
                          navigator.dismissAlert();
                        }
                        refresh();
                        updateTranslateCommands();
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        translating = false;
                        if (reportErrors) {
                          navigator.showAlert(
                              Lang.tr("reddit.translate_error"), AlertType.ERROR);
                        }
                      }
                    });
              }
            }));
  }

  private static String[] splitLines(String text) {
    String value = text;
    while (value.endsWith("\n")) {
      value = value.substring(0, value.length() - 1);
    }
    Vector lines = new Vector();
    int start = 0;
    for (int i = 0; i < value.length(); i++) {
      if (value.charAt(i) == '\n') {
        String line = value.substring(start, i);
        if (line.endsWith("\r")) {
          line = line.substring(0, line.length() - 1);
        }
        lines.addElement(line);
        start = i + 1;
      }
    }
    String last = value.substring(start);
    if (last.endsWith("\r")) {
      last = last.substring(0, last.length() - 1);
    }
    lines.addElement(last);
    String[] result = new String[lines.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = (String) lines.elementAt(i);
    }
    return result;
  }

  private boolean isPosts() {
    return source == SOURCE_SEARCH_POSTS
        || source == SOURCE_SUBPOSTS
        || source == SOURCE_USER_POSTS;
  }

  private boolean isSubPosts() {
    return source == SOURCE_SUBPOSTS && sourceId.length() > 0;
  }

  private boolean isUsers() {
    return source == SOURCE_SEARCH_USERS;
  }

  private String[] selectedItem() {
    int index = getSelectedIndex();
    if (index < 0 || index >= items.size()) {
      return null;
    }
    return (String[]) items.elementAt(index);
  }

  private void reloadSub(String sort) {
    if (loading || sourceId.length() == 0) {
      return;
    }
    loadPosts(RedditApi.subPostsUrl(sourceId, sort, ""), SOURCE_SUBPOSTS, sourceId, "", sort,
        Lang.tr("reddit.results_posts", "r/" + sourceId));
  }

  private void loadPosts(
      final String url,
      final int newSource,
      final String id,
      final String query,
      final String sort,
      final String title) {
    loading = true;
    navigator.showLoadingAlert(
        Lang.tr("reddit.loading"),
        new Runnable() {
          public void run() {
            loading = false;
          }
        });
    MIDPlay.startOperation(
        new HttpTextOperation(
            url,
            new HttpTextOperation.Listener() {
              public void onText(final String response) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        String error = RedditApi.parseError(response);
                        if (error.length() > 0) {
                          navigator.showAlert(error, AlertType.ERROR);
                          return;
                        }
                        Vector posts = RedditApi.parsePosts(response);
                        if (posts.size() == 0) {
                          navigator.showAlert(Lang.tr("reddit.empty"), AlertType.INFO);
                          return;
                        }
                        navigator.forward(
                            new RedditResultsScreen(
                                navigator,
                                newSource,
                                id,
                                query,
                                sort,
                                title,
                                posts,
                                RedditApi.parseBookmark(response)));
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.showAlert(e.toString(), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private void saveSelected() {
    String[] item = selectedItem();
    if (item == null || !isPosts()) {
      return;
    }
    int result = RedditFavoritesManager.getInstance().savePost(item);
    if (result == RedditFavoritesManager.ALREADY_EXISTS) {
      navigator.showAlert(Lang.tr("reddit.already_saved"), AlertType.INFO);
    } else if (result == RedditFavoritesManager.SUCCESS) {
      navigator.showAlert(Lang.tr("reddit.saved_post"), AlertType.CONFIRMATION);
    } else {
      navigator.showAlert(Lang.tr("reddit.save_error"), AlertType.ERROR);
    }
  }

  private void shareSelected() {
    String[] item = selectedItem();
    if (item == null) {
      return;
    }
    String text;
    if (isPosts()) {
      text = RedditApi.postLabel(item) + "\n" + RedditApi.postWebUrl(item);
    } else if (isUsers()) {
      text =
          RedditApi.userLabel(item)
              + "\n"
              + RedditApi.userWebUrl(item[RedditApi.USER_NAME]);
    } else {
      text =
          RedditApi.subredditLabel(item)
              + "\n"
              + RedditApi.subredditWebUrl(item[RedditApi.SUB_NAME]);
    }
    Sharing.toMPGram(this, text);
  }

  private void loadMore() {
    if (loading || !hasMore) {
      return;
    }
    final String url = nextPageUrl();
    if (url.length() == 0) {
      return;
    }
    loading = true;
    navigator.showLoadingAlert(
        Lang.tr("status.loading"),
        new Runnable() {
          public void run() {
            loading = false;
          }
        });
    MIDPlay.startOperation(
        new HttpTextOperation(
            url,
            new HttpTextOperation.Listener() {
              public void onText(final String response) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        appendMore(response);
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.showAlert(e.toString(), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private String nextPageUrl() {
    if (source == SOURCE_SEARCH_POSTS) {
      return RedditApi.searchPostsUrl(sourceId, sourceQuery, bookmark);
    }
    if (source == SOURCE_SUBPOSTS) {
      return RedditApi.subPostsUrl(sourceId, sourceSort, bookmark);
    }
    if (source == SOURCE_USER_POSTS) {
      return RedditApi.userPostsUrl(sourceId, bookmark);
    }
    return "";
  }

  private void appendMore(String response) {
    String error = RedditApi.parseError(response);
    if (error.length() > 0) {
      navigator.showAlert(error, AlertType.ERROR);
      return;
    }
    Vector more = RedditApi.parsePosts(response);
    String newBookmark = RedditApi.parseBookmark(response);
    boolean end = newBookmark.length() == 0 || newBookmark.equals(bookmark);
    int added = 0;
    for (int i = 0; i < more.size(); i++) {
      String[] item = (String[]) more.elementAt(i);
      String id = idOf(item);
      if (ids.containsKey(id)) {
        continue;
      }
      ids.put(id, id);
      items.addElement(item);
      added++;
    }
    bookmark = newBookmark;
    if (end) {
      hasMore = false;
      removeCommand(moreCommand);
    }
    if (added > 0) {
      refresh();
      loadThumbnails();
      if (isPosts()
          && (titlesTranslated
              || SettingsManager.getInstance().getRedditAutoTranslate()
                  == Configuration.REDDIT_TRANSLATE_ON)) {
        translatedTitles.clear();
        translateTitles(false);
      }
    }
    if (end) {
      navigator.showAlert(Lang.tr("reddit.end"), AlertType.INFO);
    }
  }

  private static String idOf(String[] item) {
    return item[0];
  }

  private String thumbnailUrl(String[] item) {
    if (isPosts()) {
      return item[RedditApi.POST_THUMB];
    }
    if (isUsers()) {
      return null;
    }
    return item[RedditApi.SUB_ICON];
  }

  protected void showNotify() {
    super.showNotify();
    loadThumbnails();
  }

  protected void hideNotify() {
    cancelThumbnails();
    super.hideNotify();
  }

  private void loadThumbnails() {
    if (thumbsInFlight
        || SettingsManager.getInstance().getCurrentThumbnails() == Configuration.THUMBNAILS_OFF) {
      return;
    }
    String[] urls = new String[items.size()];
    int count = 0;
    for (int i = 0; i < items.size() && count < RedditApi.MAX_THUMBNAILS; i++) {
      String[] item = (String[]) items.elementAt(i);
      String url = thumbnailUrl(item);
      if (url == null || url.length() == 0 || thumbCache.containsKey(url)) {
        continue;
      }
      urls[i] = url;
      count++;
    }
    if (count == 0) {
      return;
    }
    cancelThumbnails();
    final int token = ++thumbToken;
    thumbsInFlight = true;
    thumbOperation =
        new RedditThumbnailOperation(
            urls,
            36,
            new RedditThumbnailOperation.Listener() {
              public void onImage(final int index, final Image image) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        if (token != thumbToken || index < 0 || index >= items.size()) {
                          return;
                        }
                        String[] item = (String[]) items.elementAt(index);
                        String url = thumbnailUrl(item);
                        if (url != null && url.length() > 0) {
                          thumbCache.put(url, image);
                        }
                        if (index < size()) {
                          set(index, getString(index), image);
                        }
                      }
                    });
              }

              public void onDone() {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        if (token == thumbToken) {
                          thumbsInFlight = false;
                        }
                      }
                    });
              }

              public void onError(Exception e) {
                // A single failed thumbnail is ignored; the operation still continues with the
                // remaining images and reports completion through onDone().
              }
            });
    thumbOperation.start();
  }

  private void cancelThumbnails() {
    if (thumbOperation != null) {
      thumbOperation.stop();
      thumbOperation = null;
    }
    thumbsInFlight = false;
    thumbToken++;
  }
}
