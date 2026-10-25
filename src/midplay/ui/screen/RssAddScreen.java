package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;
import midplay.MIDPlay;
import midplay.model.RssSubscription;
import midplay.net.HttpTextOperation;
import midplay.net.RssApi;
import midplay.store.RssSubscriptionManager;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;

/**
 * Adds a feed from a site or feed address. The proxy discovers the canonical feed URL, title and
 * icon, then the subscription is stored and its entries are opened right away.
 */
public final class RssAddScreen extends BaseForm {
  private final RssScreen root;
  private final TextField urlField;
  private final Command addCommand;
  private final Command suggestionsCommand;
  private boolean loading;

  public RssAddScreen(Navigator navigator, RssScreen root) {
    super(Lang.tr("rss.add_title"), navigator);
    this.root = root;
    append(new StringItem(null, Lang.tr("rss.add_hint")));
    urlField =
        new TextField(Lang.tr("rss.url_label"), "", 255, TextField.URL);
    append(urlField);
    addCommand = new Command(Lang.tr("rss.add"), Command.OK, 0);
    suggestionsCommand = new Command(Lang.tr("rss.suggestions"), Command.SCREEN, 2);
    addCommand(addCommand);
    addCommand(suggestionsCommand);
    addCommand(Commands.playerNowPlaying());
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == addCommand) {
      addFeed();
    } else if (c == suggestionsCommand) {
      navigator.forward(new RssSuggestionsScreen(navigator, root));
    } else if (c == Commands.playerNowPlaying()) {
      midplay.ui.PlayerNavHelper.showNowPlaying(navigator);
    }
  }

  private void addFeed() {
    if (loading) {
      return;
    }
    final String input = urlField.getString().trim();
    if (input.length() == 0) {
      navigator.showAlert(Lang.tr("rss.error.missing_url"), AlertType.ERROR);
      return;
    }
    loading = true;
    navigator.showLoadingAlert(
        Lang.tr("rss.discovering"),
        new Runnable() {
          public void run() {
            loading = false;
          }
        });
    MIDPlay.startOperation(
        new HttpTextOperation(
            RssApi.discoverUrl(input),
            new HttpTextOperation.Listener() {
              public void onText(final String response) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        finishDiscovery(response);
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.showAlert(Lang.tr("rss.error.fetch_failed"), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private void finishDiscovery(String response) {
    if (!loading) {
      return;
    }
    loading = false;
    String error = RssApi.parseErrorCode(response);
    if (error.length() > 0) {
      navigator.showAlert(RssApi.errorMessage(error), AlertType.ERROR);
      return;
    }
    RssSubscription subscription = RssApi.parseFeed(response);
    if (subscription == null || subscription.url.length() == 0) {
      navigator.showAlert(Lang.tr("rss.not_feed"), AlertType.ERROR);
      return;
    }
    int result = RssSubscriptionManager.getInstance().add(subscription);
    if (result == RssSubscriptionManager.ALREADY_EXISTS) {
      navigator.dismissAlert();
      navigator.showAlert(Lang.tr("rss.already_added"), AlertType.INFO);
      return;
    }
    if (result == RssSubscriptionManager.LIMIT_REACHED) {
      navigator.showAlert(Lang.tr("rss.limit_reached"), AlertType.ERROR);
      return;
    }
    if (result != RssSubscriptionManager.SUCCESS) {
      navigator.showAlert(Lang.tr("rss.error.generic"), AlertType.ERROR);
      return;
    }
    navigator.dismissAlert();
    navigator.popTo(root);
    root.openFeed(subscription);
  }
}
