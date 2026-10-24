package midplay.ui.screen;

import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import midplay.MIDPlay;
import midplay.net.HttpTextOperation;
import midplay.net.RedditApi;
import midplay.store.RedditFavoritesManager;
import midplay.ui.BaseList;
import midplay.ui.Navigator;
import midplay.util.Lang;

/**
 * Subreddits and users the user follows. Choosing an entry browses its posts; removing it stops
 * following. Follow data is only stored locally, like the saved posts list.
 */
public final class RedditFollowingScreen extends BaseList {
  private static final int TYPE_SUBREDDIT = 0;
  private static final int TYPE_USER = 1;

  private final Command openCommand;
  private final Command removeCommand;
  private final Vector items = new Vector();
  private boolean loading;

  public RedditFollowingScreen(Navigator navigator) {
    super(Lang.tr("reddit.following"), navigator);
    openCommand = new Command(Lang.tr("reddit.open"), Command.SCREEN, 1);
    removeCommand = new Command(Lang.tr("reddit.unfollow"), Command.SCREEN, 2);
    reload();
  }

  protected void showNotify() {
    super.showNotify();
    reload();
  }

  private void reload() {
    items.removeAllElements();
    Vector subs = RedditFavoritesManager.getInstance().getSubreddits();
    for (int i = 0; i < subs.size(); i++) {
      items.addElement(new String[] {String.valueOf(TYPE_SUBREDDIT), (String) subs.elementAt(i)});
    }
    Vector users = RedditFavoritesManager.getInstance().getUsers();
    for (int i = 0; i < users.size(); i++) {
      items.addElement(new String[] {String.valueOf(TYPE_USER), (String) users.elementAt(i)});
    }
    refresh();
    updateCommands();
  }

  private void updateCommands() {
    boolean hasItems = items.size() > 0;
    setCommandVisible(openCommand, hasItems);
    setCommandVisible(removeCommand, hasItems);
  }

  private void setCommandVisible(Command command, boolean visible) {
    if (visible) {
      addCommand(command);
    } else {
      removeCommand(command);
    }
  }

  protected void populateItems() {
    if (items.size() == 0) {
      append(Lang.tr("reddit.following_empty"), null);
      return;
    }
    for (int i = 0; i < items.size(); i++) {
      append(label((String[]) items.elementAt(i)), null);
    }
  }

  private String label(String[] item) {
    boolean user = String.valueOf(TYPE_USER).equals(item[0]);
    return (user ? "u/" : "r/") + item[1];
  }

  protected void handleSelection() {
    openSelected();
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == openCommand) {
      openSelected();
    } else if (c == removeCommand) {
      removeSelected();
    }
  }

  private String[] selectedItem() {
    int index = getSelectedIndex();
    if (index < 0 || index >= items.size()) {
      return null;
    }
    return (String[]) items.elementAt(index);
  }

  private void openSelected() {
    String[] item = selectedItem();
    if (item == null || loading) {
      return;
    }
    if (String.valueOf(TYPE_USER).equals(item[0])) {
      openUser(item[1]);
    } else {
      openSubreddit(item[1]);
    }
  }

  private void removeSelected() {
    final String[] item = selectedItem();
    if (item == null) {
      return;
    }
    if (String.valueOf(TYPE_USER).equals(item[0])) {
      RedditFavoritesManager.getInstance().removeUser(item[1]);
    } else {
      RedditFavoritesManager.getInstance().removeSubreddit(item[1]);
    }
    reload();
    navigator.showAlert(Lang.tr("reddit.unfollowed"), AlertType.CONFIRMATION);
  }

  private void openSubreddit(final String subreddit) {
    if (subreddit == null || subreddit.length() == 0) {
      return;
    }
    load(
        RedditApi.subPostsUrl(subreddit, "new", ""),
        RedditResultsScreen.SOURCE_SUBPOSTS,
        subreddit,
        "",
        "new",
        Lang.tr("reddit.results_posts", "r/" + subreddit));
  }

  private void openUser(final String username) {
    if (username == null || username.length() == 0) {
      return;
    }
    load(
        RedditApi.userPostsUrl(username, ""),
        RedditResultsScreen.SOURCE_USER_POSTS,
        username,
        "",
        "",
        Lang.tr("reddit.results_user_posts", username));
  }

  private void load(
      final String url,
      final int source,
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
                        navigator.dismissAlert();
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
                                source,
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
                        navigator.dismissAlert();
                        navigator.showAlert(e.toString(), AlertType.ERROR);
                      }
                    });
              }
            }));
  }
}
