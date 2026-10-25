package midplay.ui.screen;

import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import midplay.MIDPlay;
import midplay.model.RssItem;
import midplay.model.RssSubscription;
import midplay.net.HttpTextOperation;
import midplay.net.RssApi;
import midplay.net.RssThumbnailOperation;
import midplay.store.Configuration;
import midplay.store.SettingsManager;
import midplay.ui.BaseList;
import midplay.ui.Navigator;
import midplay.ui.Sharing;
import midplay.util.Lang;

/** Entries published by one feed, loaded from the proxy as compact JSON rows. */
public final class RssItemsScreen extends BaseList {
  private static final int MAX_THUMBNAILS = 25;

  private final RssSubscription subscription;
  private final Vector items = new Vector();
  private final Hashtable thumbCache = new Hashtable();
  private final Command reloadCommand;
  private final Command browserCommand;
  private final Command shareCommand;
  private boolean loaded;
  private boolean loading;
  private RssThumbnailOperation thumbOperation;
  private int thumbToken;

  public RssItemsScreen(Navigator navigator, RssSubscription subscription) {
    super(subscription == null ? Lang.tr("rss.title") : subscription.displayTitle(), navigator);
    this.subscription = subscription;
    reloadCommand = new Command(Lang.tr("rss.refresh"), Command.SCREEN, 2);
    browserCommand = new Command(Lang.tr("rss.open_browser"), Command.SCREEN, 3);
    shareCommand = new Command(Lang.tr("rss.share"), Command.SCREEN, 4);
    addCommand(reloadCommand);
    addCommand(browserCommand);
    addCommand(shareCommand);
  }

  protected void showNotify() {
    super.showNotify();
    if (!loaded && !loading) {
      loadItems();
    } else if (thumbOperation == null) {
      loadThumbnails();
    }
  }

  protected void populateItems() {
    if (items.size() == 0) {
      append(loading ? Lang.tr("rss.loading") : Lang.tr("rss.no_items"), null);
      return;
    }
    for (int i = 0; i < items.size(); i++) {
      RssItem item = (RssItem) items.elementAt(i);
      String image = item.image;
      append(
          RssApi.itemLabel(item),
          image == null || image.length() == 0 ? null : (Image) thumbCache.get(image));
    }
  }

  protected void handleSelection() {
    int index = getSelectedIndex();
    if (!isValidSelection(index, items.size())) {
      return;
    }
    navigator.forward(new RssArticleScreen(navigator, subscription, (RssItem) items.elementAt(index)));
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == reloadCommand) {
      loadItems();
    } else if (c == browserCommand) {
      openInBrowser();
    } else if (c == shareCommand) {
      shareSelected();
    }
  }

  private void loadItems() {
    if (loading || subscription == null) {
      return;
    }
    loading = true;
    loadThumbnailsCancel();
    refresh();
    navigator.showLoadingAlert(
        Lang.tr("rss.loading"),
        new Runnable() {
          public void run() {
            loading = false;
          }
        });
    MIDPlay.startOperation(
        new HttpTextOperation(
            RssApi.feedUrl(subscription.url),
            new HttpTextOperation.Listener() {
              public void onText(final String response) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        finishLoading(response);
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.backAndShowAlert(
                            Lang.tr("rss.error.fetch_failed"), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private void finishLoading(String response) {
    if (!loading) {
      return;
    }
    loading = false;
    String error = RssApi.parseErrorCode(response);
    if (error.length() > 0) {
      navigator.backAndShowAlert(RssApi.errorMessage(error), AlertType.ERROR);
      return;
    }
    RssSubscription feed = RssApi.parseFeed(response);
    Vector parsed = RssApi.parseItems(response);
    if (feed == null && parsed.size() == 0) {
      navigator.backAndShowAlert(Lang.tr("rss.error.generic"), AlertType.ERROR);
      return;
    }
    loaded = true;
    navigator.dismissAlert();
    if (feed != null && feed.displayTitle().length() > 0) {
      setTitle(feed.displayTitle());
    }
    items.removeAllElements();
    for (int i = 0; i < parsed.size(); i++) {
      items.addElement(parsed.elementAt(i));
    }
    refresh();
    loadThumbnails();
  }

  private void loadThumbnails() {
    if (SettingsManager.getInstance().getCurrentThumbnails() == Configuration.THUMBNAILS_OFF) {
      return;
    }
    loadThumbnailsCancel();
    final int token = thumbToken;
    Vector pending = new Vector();
    Vector pendingRows = new Vector();
    for (int i = 0; i < items.size() && pending.size() < MAX_THUMBNAILS; i++) {
      String image = ((RssItem) items.elementAt(i)).image;
      if (image == null || image.length() == 0 || thumbCache.containsKey(image)) {
        continue;
      }
      pending.addElement(image);
      pendingRows.addElement(new Integer(i));
    }
    if (pending.size() == 0) {
      return;
    }
    final String[] urls = new String[pending.size()];
    final int[] rows = new int[pendingRows.size()];
    for (int i = 0; i < urls.length; i++) {
      urls[i] = (String) pending.elementAt(i);
      rows[i] = ((Integer) pendingRows.elementAt(i)).intValue();
    }
    thumbOperation =
        new RssThumbnailOperation(
            urls,
            Configuration.RSS_THUMBNAIL_SIZE,
            new RssThumbnailOperation.Listener() {
              public void onImage(final int index, final Image image) {
                if (image == null || index < 0 || index >= urls.length) {
                  return;
                }
                thumbCache.put(urls[index], image);
                final int row = rows[index];
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        if (token != thumbToken) {
                          return;
                        }
                        if (row >= 0 && row < size()) {
                          set(row, getString(row), image);
                        }
                      }
                    });
              }
            });
    thumbOperation.start();
  }

  private void loadThumbnailsCancel() {
    if (thumbOperation != null) {
      thumbOperation.stop();
      thumbOperation = null;
    }
    thumbToken++;
  }

  protected void hideNotify() {
    loadThumbnailsCancel();
    super.hideNotify();
  }

  private RssItem selectedItem() {
    int index = getSelectedIndex();
    if (!isValidSelection(index, items.size())) {
      return null;
    }
    return (RssItem) items.elementAt(index);
  }

  private void openInBrowser() {
    RssItem item = selectedItem();
    if (item == null) {
      return;
    }
    if (item.link.length() == 0) {
      navigator.showAlert(Lang.tr("rss.no_link"), AlertType.INFO);
      return;
    }
    try {
      MIDPlay.getInstance().openUri(item.link);
    } catch (Exception e) {
      navigator.showAlert(Lang.tr("rss.open_failed"), AlertType.ERROR);
    }
  }

  private void shareSelected() {
    RssItem item = selectedItem();
    if (item == null) {
      return;
    }
    String text = item.title;
    if (item.link.length() > 0) {
      text = text + "\n" + item.link;
    }
    Sharing.toMPGram(this, text);
  }
}
