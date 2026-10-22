package midplay.ui.screen;

import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextField;
import midplay.MIDPlay;
import midplay.net.HttpTextOperation;
import midplay.net.WattpadApi;
import midplay.store.Configuration;
import midplay.store.SettingsManager;
import midplay.store.WattpadProgressManager;
import midplay.store.WattpadSearchHistoryManager;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;

/** Searches Wattpad (stories, authors and tags) and lists the matching stories. */
public final class WattpadScreen extends BaseForm {
  private static final int MAX_STORY_BYTES = 400 * 1024;

  private final TextField queryField;
  private final ChoiceGroup history;
  private final Command continueCommand;
  private final Command searchCommand;
  private final Command usersCommand;
  private final Command libraryCommand;
  private final Command settingsCommand;
  private final Command useHistory;
  private final Command clearHistory;

  public WattpadScreen(Navigator navigator) {
    super(Lang.tr("wattpad.title"), navigator);
    queryField = new TextField(Lang.tr("wattpad.search_field"), "", 120, TextField.ANY);
    append(queryField);
    history =
        new ChoiceGroup(
            Lang.tr("open_uri.history"),
            Choice.POPUP,
            WattpadSearchHistoryManager.getInstance().getItems(),
            null);
    append(history);
    continueCommand = new Command(Lang.tr("wattpad.continue"), Command.SCREEN, 0);
    searchCommand = new Command(Lang.tr("wattpad.search"), Command.SCREEN, 1);
    usersCommand = new Command(Lang.tr("wattpad.search_users"), Command.SCREEN, 2);
    libraryCommand = new Command(Lang.tr("wattpad.library"), Command.SCREEN, 3);
    settingsCommand = new Command(Lang.tr("wattpad.settings_title"), Command.SCREEN, 4);
    useHistory = new Command(Lang.tr("open_uri.use"), Command.SCREEN, 5);
    clearHistory = new Command(Lang.tr("open_uri.clear"), Command.SCREEN, 6);
    addCommand(continueCommand);
    addCommand(searchCommand);
    addCommand(usersCommand);
    addCommand(libraryCommand);
    addCommand(settingsCommand);
    addCommand(useHistory);
    addCommand(clearHistory);
    addCommand(Commands.playerNowPlaying());
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == Commands.playerNowPlaying()) {
      PlayerNavHelper.showNowPlaying(navigator);
    } else if (c == continueCommand) {
      continueReading();
    } else if (c == searchCommand) {
      search();
    } else if (c == usersCommand) {
      searchUsers();
    } else if (c == libraryCommand) {
      navigator.forward(new WattpadLibraryScreen(navigator));
    } else if (c == settingsCommand) {
      navigator.forward(new WattpadSettingsScreen(navigator));
    } else if (c == useHistory) {
      useSelectedHistory();
    } else if (c == clearHistory) {
      WattpadSearchHistoryManager.getInstance().clear();
      refreshHistory();
    }
  }

  /** Opens the most recently read story directly at its saved chapter and page. */
  private void continueReading() {
    final String[] entry = WattpadProgressManager.getInstance().getLatestEntry();
    if (entry == null) {
      navigator.showAlert(Lang.tr("wattpad.library_empty"), AlertType.INFO);
      return;
    }
    navigator.showLoadingAlert(Lang.tr("wattpad.loading"));
    MIDPlay.startOperation(
        new HttpTextOperation(
            WattpadApi.storyUrl(entry[WattpadProgressManager.ENTRY_ID]),
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        // Leave the loading alert in place; forward() replaces it and keeps the
                        // Wattpad menu in the history.
                        openLatest(entry, text);
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        navigator.dismissAlert();
                        navigator.showAlert(e.toString(), AlertType.ERROR);
                      }
                    });
              }
            },
            MAX_STORY_BYTES));
  }

  private void openLatest(String[] entry, String json) {
    Vector parts = WattpadApi.parseParts(json);
    if (parts.size() == 0) {
      navigator.showAlert(Lang.tr("wattpad.empty"), AlertType.INFO);
      return;
    }
    int index = parseInt(entry[WattpadProgressManager.ENTRY_INDEX]);
    if (index < 0 || index >= parts.size()) {
      index = 0;
    }
    String storyId = entry[WattpadProgressManager.ENTRY_ID];
    String storyTitle = WattpadApi.parseTitle(json);
    if (storyTitle.length() == 0) {
      storyTitle = entry[WattpadProgressManager.ENTRY_TITLE];
    }
    String chapterTitle = ((String[]) parts.elementAt(index))[WattpadApi.PART_TITLE];
    int page = WattpadProgressManager.getInstance().getPage(storyId);
    if (page < 0) {
      page = 0;
    }
    WattpadProgressManager.getInstance()
        .record(storyId, storyTitle, index, parts.size(), chapterTitle, page);
    navigator.forward(new WattpadReaderScreen(navigator, storyId, storyTitle, parts, index, page));
  }

  private static int parseInt(String value) {
    try {
      return Integer.parseInt(value);
    } catch (Exception e) {
      return 0;
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
    String[] items = WattpadSearchHistoryManager.getInstance().getItems();
    for (int i = 0; i < items.length; i++) {
      history.append(items[i], null);
    }
  }

  private boolean includeMature() {
    return SettingsManager.getInstance().getCurrentWattpadMature() == Configuration.WATTPAD_MATURE_ON;
  }

  private void search() {
    final String query = queryField.getString().trim();
    if (query.length() == 0) {
      navigator.showAlert(Lang.tr("wattpad.search_field"), AlertType.ERROR);
      return;
    }
    navigator.showLoadingAlert(Lang.tr("status.loading"));
    MIDPlay.startOperation(
        new HttpTextOperation(
            WattpadApi.searchUrl(query, 0, SettingsManager.getInstance().getWattpadLanguage()),
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        WattpadSearchHistoryManager.getInstance().record(query);
                        refreshHistory();
                        showResults(query, text);
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        navigator.dismissAlert();
                        navigator.showAlert(e.toString(), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private void searchUsers() {
    final String query = queryField.getString().trim();
    if (query.length() == 0) {
      navigator.showAlert(Lang.tr("wattpad.search_field"), AlertType.ERROR);
      return;
    }
    navigator.showLoadingAlert(Lang.tr("status.loading"));
    MIDPlay.startOperation(
        new HttpTextOperation(
            WattpadApi.usersUrl(query, 0),
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        WattpadSearchHistoryManager.getInstance().record(query);
                        refreshHistory();
                        showUsers(query, text);
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        navigator.dismissAlert();
                        navigator.showAlert(e.toString(), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private void showUsers(String query, String text) {
    Vector users = WattpadApi.parseUsers(text);
    if (users.size() == 0) {
      navigator.dismissAlert();
      navigator.showAlert(Lang.tr("wattpad.empty"), AlertType.INFO);
      return;
    }
    int rawCount = WattpadApi.countUsers(text);
    boolean hasMore = rawCount >= WattpadApi.getSearchLimit();
    navigator.forward(new WattpadUsersScreen(navigator, query, users, hasMore, rawCount));
  }

  private void showResults(String query, String text) {
    Vector stories = WattpadApi.parseStories(text, includeMature());
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
            WattpadResultsScreen.SOURCE_SEARCH,
            query,
            Lang.tr("wattpad.results"),
            stories,
            hasMore,
            rawCount));
  }
}
