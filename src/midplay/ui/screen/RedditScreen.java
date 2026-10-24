package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextField;
import midplay.MIDPlay;
import midplay.net.HttpTextOperation;
import midplay.net.RedditApi;
import midplay.store.RedditSearchHistoryManager;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;

/**
 * Reddit start screen: search subreddits, users, or posts, and reach favorites/settings. Posts are
 * searched inside one subreddit, typed as {@code subreddit keywords} (an optional {@code r/} prefix
 * is accepted). With no keywords, the subreddit's newest posts are listed.
 */
public final class RedditScreen extends BaseForm {
  private static final int TYPE_SUBREDDITS = 0;
  private static final int TYPE_USERS = 1;
  private static final int TYPE_POSTS = 2;

  private final TextField queryField;
  private final ChoiceGroup searchTypeGroup;
  private final ChoiceGroup history;
  private final Command favoritesCommand;
  private final Command followingCommand;
  private final Command settingsCommand;
  private final Command useHistory;
  private final Command clearHistory;
  private boolean loading;

  public RedditScreen(Navigator navigator) {
    super(Lang.tr("reddit.title"), navigator);
    queryField = new TextField(Lang.tr("reddit.search_field"), "", 120, TextField.ANY);
    append(queryField);

    searchTypeGroup = new ChoiceGroup(Lang.tr("search.type"), ChoiceGroup.EXCLUSIVE);
    searchTypeGroup.append(Lang.tr("reddit.search_subreddits"), null);
    searchTypeGroup.append(Lang.tr("reddit.search_user"), null);
    searchTypeGroup.append(Lang.tr("reddit.search_posts"), null);
    searchTypeGroup.setSelectedIndex(TYPE_SUBREDDITS, true);
    append(searchTypeGroup);

    history =
        new ChoiceGroup(
            Lang.tr("open_uri.history"),
            Choice.POPUP,
            RedditSearchHistoryManager.getInstance().getItems(),
            null);
    append(history);

    favoritesCommand = new Command(Lang.tr("reddit.favorites"), Command.SCREEN, 4);
    followingCommand = new Command(Lang.tr("reddit.following"), Command.SCREEN, 4);
    settingsCommand = new Command(Lang.tr("reddit.settings_title"), Command.SCREEN, 5);
    useHistory = new Command(Lang.tr("open_uri.use"), Command.SCREEN, 6);
    clearHistory = new Command(Lang.tr("open_uri.clear"), Command.SCREEN, 7);
    addCommand(Commands.ok());
    addCommand(favoritesCommand);
    addCommand(followingCommand);
    addCommand(settingsCommand);
    addCommand(useHistory);
    addCommand(clearHistory);
    addCommand(Commands.playerNowPlaying());
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == Commands.playerNowPlaying()) {
      PlayerNavHelper.showNowPlaying(navigator);
    } else if (c == Commands.ok()) {
      search();
    } else if (c == favoritesCommand) {
      navigator.forward(new RedditFavoritesScreen(navigator));
    } else if (c == followingCommand) {
      navigator.forward(new RedditFollowingScreen(navigator));
    } else if (c == settingsCommand) {
      navigator.forward(new RedditSettingsScreen(navigator));
    } else if (c == useHistory) {
      useSelectedHistory();
    } else if (c == clearHistory) {
      RedditSearchHistoryManager.getInstance().clear();
      refreshHistory();
    }
  }

  private void useSelectedHistory() {
    int selected = history.getSelectedIndex();
    if (selected >= 0 && selected < history.size()) {
      queryField.setString(history.getString(selected));
    }
  }

  private void refreshHistory() {
    history.deleteAll();
    String[] items = RedditSearchHistoryManager.getInstance().getItems();
    for (int i = 0; i < items.length; i++) {
      history.append(items[i], null);
    }
  }

  private void search() {
    String query = queryField.getString().trim();
    if (query.length() == 0) {
      navigator.showAlert(Lang.tr("search.error.empty_keyword"), AlertType.ERROR);
      return;
    }
    int type = searchTypeGroup.getSelectedIndex();
    if (type == TYPE_POSTS) {
      searchPosts(query);
    } else if (type == TYPE_USERS) {
      searchUsers(query);
    } else {
      searchSubreddits(query);
    }
  }

  private void searchPosts(String query) {
    if (loading) {
      return;
    }
    String rest = query;
    if (rest.regionMatches(true, 0, "r/", 0, 2)) {
      rest = rest.substring(2);
    } else if (rest.startsWith("/r/")) {
      rest = rest.substring(3);
    }
    rest = rest.trim();
    String subreddit;
    String keywords;
    int space = indexOfWhitespace(rest);
    if (space < 0) {
      subreddit = rest;
      keywords = "";
    } else {
      subreddit = rest.substring(0, space);
      keywords = rest.substring(space).trim();
    }
    if (subreddit.length() == 0) {
      navigator.showAlert(Lang.tr("reddit.subreddit_required"), AlertType.ERROR);
      return;
    }
    final String sub = subreddit;
    final String text = keywords;
    loading = true;
    navigator.showLoadingAlert(
        Lang.tr("reddit.loading"),
        new Runnable() {
          public void run() {
            loading = false;
          }
        });
    final String url =
        text.length() == 0
            ? RedditApi.subPostsUrl(sub, "new", "")
            : RedditApi.searchPostsUrl(sub, text, "");
    final String title =
        text.length() == 0
            ? Lang.tr("reddit.results_posts", "r/" + sub)
            : Lang.tr("reddit.results_search", text);
    final int source =
        text.length() == 0
            ? RedditResultsScreen.SOURCE_SUBPOSTS
            : RedditResultsScreen.SOURCE_SEARCH_POSTS;
    MIDPlay.startOperation(
        new HttpTextOperation(
            url,
            new HttpTextOperation.Listener() {
              public void onText(final String response) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        RedditSearchHistoryManager.getInstance().record(queryField.getString().trim());
                        refreshHistory();
                        String error = RedditApi.parseError(response);
                        if (error.length() > 0) {
                          navigator.dismissAlert();
                          navigator.showAlert(error, AlertType.ERROR);
                          return;
                        }
                        java.util.Vector items = RedditApi.parsePosts(response);
                        if (items.size() == 0) {
                          navigator.dismissAlert();
                          navigator.showAlert(Lang.tr("reddit.empty"), AlertType.INFO);
                          return;
                        }
                        navigator.forward(
                            new RedditResultsScreen(
                                navigator,
                                source,
                                sub,
                                text,
                                "new",
                                title,
                                items,
                                RedditApi.parseBookmark(response)));
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        navigator.showAlert(e.toString(), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private void searchUsers(final String query) {
    if (loading) {
      return;
    }
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
            RedditApi.searchUsersUrl(query),
            new HttpTextOperation.Listener() {
              public void onText(final String response) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        RedditSearchHistoryManager.getInstance().record(queryField.getString().trim());
                        refreshHistory();
                        String error = RedditApi.parseError(response);
                        if (error.length() > 0) {
                          navigator.dismissAlert();
                          navigator.showAlert(error, AlertType.ERROR);
                          return;
                        }
                        java.util.Vector items = RedditApi.parseUsers(response);
                        if (items.size() == 0) {
                          navigator.dismissAlert();
                          navigator.showAlert(Lang.tr("reddit.empty"), AlertType.INFO);
                          return;
                        }
                        navigator.forward(
                            new RedditResultsScreen(
                                navigator,
                                RedditResultsScreen.SOURCE_SEARCH_USERS,
                                query,
                                "",
                                "",
                                Lang.tr("reddit.results_users", query),
                                items,
                                RedditApi.parseBookmark(response)));
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        navigator.showAlert(e.toString(), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private void searchSubreddits(final String query) {
    if (loading) {
      return;
    }
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
            RedditApi.searchSubredditsUrl(query),
            new HttpTextOperation.Listener() {
              public void onText(final String response) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        RedditSearchHistoryManager.getInstance().record(queryField.getString().trim());
                        refreshHistory();
                        String error = RedditApi.parseError(response);
                        if (error.length() > 0) {
                          navigator.dismissAlert();
                          navigator.showAlert(error, AlertType.ERROR);
                          return;
                        }
                        java.util.Vector items = RedditApi.parseSubreddits(response);
                        if (items.size() == 0) {
                          navigator.dismissAlert();
                          navigator.showAlert(Lang.tr("reddit.empty"), AlertType.INFO);
                          return;
                        }
                        navigator.forward(
                            new RedditResultsScreen(
                                navigator,
                                RedditResultsScreen.SOURCE_SEARCH_SUBREDDITS,
                                query,
                                "",
                                "",
                                Lang.tr("reddit.results_subreddits", query),
                                items,
                                RedditApi.parseBookmark(response)));
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        navigator.showAlert(e.toString(), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private static int indexOfWhitespace(String value) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
        return i;
      }
    }
    return -1;
  }
}
