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
import midplay.store.WattpadDownloadManager;
import midplay.store.WattpadProgressManager;
import midplay.store.WattpadSearchHistoryManager;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;

/** Searches Wattpad stories or authors and lists the matches. */
public final class WattpadScreen extends BaseForm {
  private static final int MAX_STORY_BYTES = 400 * 1024;
  private static final int TYPE_STORIES = 0;
  private static final int TYPE_USERS = 1;

  private final TextField queryField;
  private final ChoiceGroup searchTypeGroup;
  private final ChoiceGroup history;
  private final Command continueCommand;
  private final Command libraryCommand;
  private final Command settingsCommand;
  private final Command useHistory;
  private final Command clearHistory;

  public WattpadScreen(Navigator navigator) {
    super(Lang.tr("wattpad.title"), navigator);
    queryField = new TextField(Lang.tr("wattpad.search_field"), "", 120, TextField.ANY);
    append(queryField);

    searchTypeGroup = new ChoiceGroup(Lang.tr("search.type"), ChoiceGroup.EXCLUSIVE);
    searchTypeGroup.append(Lang.tr("wattpad.stories"), null);
    searchTypeGroup.append(Lang.tr("wattpad.users"), null);
    searchTypeGroup.setSelectedIndex(TYPE_STORIES, true);
    append(searchTypeGroup);

    history =
        new ChoiceGroup(
            Lang.tr("open_uri.history"),
            Choice.POPUP,
            WattpadSearchHistoryManager.getInstance().getItems(),
            null);
    append(history);

    continueCommand = new Command(Lang.tr("wattpad.continue"), Command.SCREEN, 0);
    libraryCommand = new Command(Lang.tr("wattpad.library"), Command.SCREEN, 3);
    settingsCommand = new Command(Lang.tr("wattpad.settings_title"), Command.SCREEN, 4);
    useHistory = new Command(Lang.tr("open_uri.use"), Command.SCREEN, 5);
    clearHistory = new Command(Lang.tr("open_uri.clear"), Command.SCREEN, 6);
    addCommand(Commands.ok());
    addCommand(continueCommand);
    addCommand(libraryCommand);
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
    } else if (c == continueCommand) {
      continueReading();
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
    String storyId = entry[WattpadProgressManager.ENTRY_ID];
    Vector local = WattpadDownloadManager.getInstance().getLocalParts(storyId);
    if (local != null && local.size() > 0) {
      String title = WattpadDownloadManager.getInstance().getLocalTitle(storyId);
      if (title.length() == 0) {
        title = entry[WattpadProgressManager.ENTRY_TITLE];
      }
      openLatest(entry, local, title);
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
    String storyTitle = WattpadApi.parseTitle(json);
    if (storyTitle.length() == 0) {
      storyTitle = entry[WattpadProgressManager.ENTRY_TITLE];
    }
    openLatest(entry, parts, storyTitle);
  }

  private void openLatest(String[] entry, Vector parts, String storyTitle) {
    int index = parseInt(entry[WattpadProgressManager.ENTRY_INDEX]);
    if (index < 0 || index >= parts.size()) {
      index = 0;
    }
    String storyId = entry[WattpadProgressManager.ENTRY_ID];
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

  /** Runs the search type selected in the ChoiceGroup. */
  private void search() {
    if (searchTypeGroup.getSelectedIndex() == TYPE_USERS) {
      searchUsers();
    } else {
      searchStories();
    }
  }

  private void searchStories() {
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
