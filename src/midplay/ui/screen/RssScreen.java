package midplay.ui.screen;

import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import midplay.model.RssSubscription;
import midplay.net.RssThumbnailOperation;
import midplay.store.Configuration;
import midplay.store.RssSubscriptionManager;
import midplay.ui.BaseList;
import midplay.ui.Navigator;
import midplay.util.Lang;

/** Root of the RSS reader: the feeds the user follows, with commands to add or remove them. */
public final class RssScreen extends BaseList {
  private static final int MAX_THUMBNAILS = 20;

  private final Command addCommand;
  private final Command suggestionsCommand;
  private final Command reloadCommand;
  private final Command removeCommand;
  private final Hashtable thumbCache = new Hashtable();
  private final Vector subscriptions = new Vector();
  private RssThumbnailOperation thumbOperation;
  private int thumbToken;

  public RssScreen(Navigator navigator) {
    super(Lang.tr("rss.title"), navigator);
    addCommand = new Command(Lang.tr("rss.add"), Command.SCREEN, 1);
    suggestionsCommand = new Command(Lang.tr("rss.suggestions"), Command.SCREEN, 2);
    reloadCommand = new Command(Lang.tr("rss.refresh"), Command.SCREEN, 3);
    removeCommand = new Command(Lang.tr("rss.remove"), Command.SCREEN, 4);
    addCommand(addCommand);
    addCommand(suggestionsCommand);
    addCommand(reloadCommand);
    reload();
  }

  /** Re-reads the stored subscriptions and repaints the list, keeping the selected row. */
  public void reload() {
    subscriptions.removeAllElements();
    Vector stored = RssSubscriptionManager.getInstance().getSubscriptions();
    for (int i = 0; i < stored.size(); i++) {
      subscriptions.addElement(stored.elementAt(i));
    }
    refresh();
    if (subscriptions.size() > 0) {
      addCommand(removeCommand);
    } else {
      removeCommand(removeCommand);
    }
    loadThumbnails();
  }

  /** Called after a feed is saved: show its entries immediately. */
  public void openFeed(RssSubscription subscription) {
    reload();
    if (subscription != null) {
      navigator.forward(new RssItemsScreen(navigator, subscription));
    }
  }

  public void refreshSubscriptions() {
    reload();
  }

  protected void populateItems() {
    if (subscriptions.size() == 0) {
      append(Lang.tr("rss.empty_hint"), null);
      return;
    }
    for (int i = 0; i < subscriptions.size(); i++) {
      RssSubscription subscription = (RssSubscription) subscriptions.elementAt(i);
      String image = subscription.image;
      append(
          subscription.displayTitle(),
          image == null || image.length() == 0 ? null : (Image) thumbCache.get(image));
    }
  }

  protected void handleSelection() {
    if (subscriptions.size() == 0) {
      navigator.forward(new RssAddScreen(navigator, this));
      return;
    }
    int index = getSelectedIndex();
    if (!isValidSelection(index, subscriptions.size())) {
      return;
    }
    navigator.forward(new RssItemsScreen(navigator, (RssSubscription) subscriptions.elementAt(index)));
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == addCommand) {
      navigator.forward(new RssAddScreen(navigator, this));
    } else if (c == suggestionsCommand) {
      navigator.forward(new RssSuggestionsScreen(navigator, this));
    } else if (c == reloadCommand) {
      reload();
      navigator.showAlert(Lang.tr("rss.reloaded"), AlertType.CONFIRMATION);
    } else if (c == removeCommand) {
      removeSelected();
    }
  }

  private void removeSelected() {
    int index = getSelectedIndex();
    if (!isValidSelection(index, subscriptions.size())) {
      return;
    }
    final RssSubscription subscription = (RssSubscription) subscriptions.elementAt(index);
    navigator.showConfirmationAlert(
        Lang.tr("rss.remove_confirm", subscription.displayTitle()),
        new Runnable() {
          public void run() {
            RssSubscriptionManager.getInstance().remove(subscription.url);
            reload();
            navigator.showAlert(Lang.tr("rss.removed"), AlertType.CONFIRMATION);
          }
        });
  }

  private void loadThumbnails() {
    cancelThumbnails();
    final int token = thumbToken;
    Vector pending = new Vector();
    Vector pendingRows = new Vector();
    for (int i = 0; i < subscriptions.size() && pending.size() < MAX_THUMBNAILS; i++) {
      String image = ((RssSubscription) subscriptions.elementAt(i)).image;
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
                        if (isValidSelection(row, size())) {
                          set(row, getString(row), image);
                        }
                      }
                    });
              }
            });
    thumbOperation.start();
  }

  private void cancelThumbnails() {
    if (thumbOperation != null) {
      thumbOperation.stop();
      thumbOperation = null;
    }
    thumbToken++;
  }

  protected void hideNotify() {
    cancelThumbnails();
    super.hideNotify();
  }

  protected void showNotify() {
    super.showNotify();
    if (thumbOperation == null) {
      loadThumbnails();
    }
  }
}
