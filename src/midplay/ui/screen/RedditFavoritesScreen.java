package midplay.ui.screen;

import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
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
 * Local saved Reddit posts, kept as a single flat list. Only online references are stored, so
 * nothing is downloaded unless the user opens a post and downloads its photo there.
 */
public final class RedditFavoritesScreen extends BaseList {
  private final Command openCommand;
  private final Command selectCommand;
  private final Command removeCommand;
  private final Command shareCommand;
  private final Command clearCommand;
  private final Hashtable thumbCache = new Hashtable();
  private Vector items = new Vector();
  private boolean thumbsInFlight;
  private int thumbToken;
  private RedditThumbnailOperation thumbOperation;

  public RedditFavoritesScreen(Navigator navigator) {
    super(Lang.tr("reddit.favorites"), navigator);
    openCommand = new Command(Lang.tr("reddit.open"), Command.SCREEN, 1);
    selectCommand = new Command(Lang.tr("reddit.select"), Command.SCREEN, 2);
    removeCommand = new Command(Lang.tr("reddit.remove_saved"), Command.SCREEN, 3);
    shareCommand = new Command(Lang.tr("reddit.share"), Command.SCREEN, 4);
    clearCommand = new Command(Lang.tr("reddit.clear_all"), Command.SCREEN, 5);
    reload();
  }

  protected void showNotify() {
    super.showNotify();
    reload();
  }

  protected void hideNotify() {
    cancelThumbnails();
    super.hideNotify();
  }

  private void reload() {
    items = RedditFavoritesManager.getInstance().getPosts();
    refresh();
    updateCommands();
    loadThumbnails();
  }

  private void updateCommands() {
    boolean hasItems = items.size() > 0;
    setCommandVisible(openCommand, hasItems);
    setCommandVisible(selectCommand, hasItems);
    setCommandVisible(removeCommand, hasItems);
    setCommandVisible(shareCommand, hasItems);
    setCommandVisible(clearCommand, hasItems);
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
      append(Lang.tr("reddit.favorites_empty"), null);
      return;
    }
    for (int i = 0; i < items.size(); i++) {
      String[] item = (String[]) items.elementAt(i);
      String url = item[RedditApi.POST_THUMB];
      Image image = url == null || url.length() == 0 ? null : (Image) thumbCache.get(url);
      append(RedditApi.postLabel(item), image);
    }
  }

  protected void handleSelection() {
    openSelected();
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == openCommand) {
      openSelected();
    } else if (c == selectCommand) {
      selectMultiple();
    } else if (c == removeCommand) {
      removeSelected();
    } else if (c == shareCommand) {
      shareSelected();
    } else if (c == clearCommand) {
      clearAll();
    }
  }

  private void selectMultiple() {
    if (items.size() == 0) {
      return;
    }
    navigator.forward(
        new RedditMultiSelectScreen(
            navigator,
            Lang.tr("reddit.select"),
            items,
            new Runnable() {
              public void run() {
                reload();
              }
            }));
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
    if (item == null) {
      return;
    }
    navigator.forward(new RedditPostScreen(navigator, item));
  }

  private void removeSelected() {
    final String[] item = selectedItem();
    if (item == null) {
      return;
    }
    navigator.showConfirmationAlert(
        Lang.tr("reddit.remove_confirm"),
        new Runnable() {
          public void run() {
            RedditFavoritesManager.getInstance().removePost(item[RedditApi.POST_ID]);
            reload();
            navigator.showAlert(Lang.tr("reddit.removed_saved"), AlertType.CONFIRMATION);
          }
        },
        AlertType.WARNING);
  }

  private void shareSelected() {
    String[] item = selectedItem();
    if (item == null) {
      return;
    }
    Sharing.toMPGram(this, RedditApi.postLabel(item) + "\n" + RedditApi.postWebUrl(item));
  }

  private void clearAll() {
    if (items.size() == 0) {
      return;
    }
    navigator.showConfirmationAlert(
        Lang.tr("reddit.clear_confirm"),
        new Runnable() {
          public void run() {
            RedditFavoritesManager.getInstance().clear();
            reload();
            navigator.showAlert(Lang.tr("reddit.cleared"), AlertType.CONFIRMATION);
          }
        },
        AlertType.WARNING);
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
      String url = item[RedditApi.POST_THUMB];
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
                        String url = item[RedditApi.POST_THUMB];
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
                // A single failed thumbnail is ignored; the remaining images still load.
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
