package midplay.ui.screen;

import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.StringItem;
import midplay.MIDPlay;
import midplay.net.HttpTextOperation;
import midplay.net.WattpadApi;
import midplay.store.Configuration;
import midplay.store.SettingsManager;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;

/** Public Wattpad profile of one author, with access to the stories they published. */
public final class WattpadAuthorScreen extends BaseForm {
  private final String username;
  private final StringItem content = new StringItem(null, "");
  private final Command storiesCommand;
  private boolean loading;

  public WattpadAuthorScreen(Navigator navigator, String username) {
    super(Lang.tr("wattpad.author_title"), navigator);
    this.username = username == null ? "" : username;
    storiesCommand = new Command(Lang.tr("wattpad.author_stories"), Command.SCREEN, 1);
    append(content);
    addCommand(storiesCommand);
    addCommand(Commands.playerNowPlaying());
    content.setText(Lang.tr("wattpad.loading"));
    loadProfile();
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == Commands.playerNowPlaying()) {
      PlayerNavHelper.showNowPlaying(navigator);
    } else if (c == storiesCommand) {
      loadStories();
    }
  }

  private void loadProfile() {
    navigator.showLoadingAlert(Lang.tr("wattpad.loading"));
    MIDPlay.startOperation(
        new HttpTextOperation(
            WattpadApi.userUrl(username),
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        String details = WattpadApi.parseUserDetails(text);
                        navigator.dismissAlert();
                        content.setText(
                            details.length() == 0 ? Lang.tr("wattpad.empty") : details);
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        navigator.dismissAlert();
                        content.setText(Lang.tr("wattpad.error") + "\n" + e);
                      }
                    });
              }
            }));
  }

  private void loadStories() {
    if (loading) {
      return;
    }
    loading = true;
    navigator.showLoadingAlert(
        Lang.tr("wattpad.loading"),
        new Runnable() {
          public void run() {
            loading = false;
          }
        });
    MIDPlay.startOperation(
        new HttpTextOperation(
            WattpadApi.userStoriesUrl(username, 0),
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        boolean includeMature =
                            SettingsManager.getInstance().getCurrentWattpadMature()
                                == Configuration.WATTPAD_MATURE_ON;
                        Vector stories = WattpadApi.parseStories(text, includeMature);
                        if (stories.size() == 0) {
                          navigator.dismissAlert();
                          navigator.showAlert(Lang.tr("wattpad.empty"), AlertType.INFO);
                          return;
                        }
                        int rawCount = WattpadApi.countStories(text);
                        boolean hasMore = rawCount >= WattpadApi.getSearchLimit();
                        navigator.forward(
                            new WattpadResultsScreen(
                                navigator,
                                WattpadResultsScreen.SOURCE_USER,
                                username,
                                Lang.tr("wattpad.user_stories", username),
                                stories,
                                hasMore,
                                rawCount));
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
