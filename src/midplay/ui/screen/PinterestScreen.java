package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextField;
import midplay.MIDPlay;
import midplay.net.HttpTextOperation;
import midplay.net.PinterestApi;
import midplay.store.PinterestSearchHistoryManager;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;

/** Pinterest start screen: search pins, boards or users, and reach favorites/settings. */
public final class PinterestScreen extends BaseForm {
  private static final int TYPE_PINS = 0;
  private static final int TYPE_BOARDS = 1;
  private static final int TYPE_USER = 2;

  private final TextField queryField;
  private final ChoiceGroup searchTypeGroup;
  private final ChoiceGroup history;
  private final Command favoritesCommand;
  private final Command settingsCommand;
  private final Command useHistory;
  private final Command clearHistory;
  private boolean loading;

  public PinterestScreen(Navigator navigator) {
    super(Lang.tr("pinterest.title"), navigator);
    queryField = new TextField(Lang.tr("pinterest.search_field"), "", 120, TextField.ANY);
    append(queryField);

    searchTypeGroup = new ChoiceGroup(Lang.tr("search.type"), ChoiceGroup.EXCLUSIVE);
    searchTypeGroup.append(Lang.tr("pinterest.pins"), null);
    searchTypeGroup.append(Lang.tr("pinterest.boards"), null);
    searchTypeGroup.append(Lang.tr("pinterest.search_user"), null);
    searchTypeGroup.setSelectedIndex(TYPE_PINS, true);
    append(searchTypeGroup);

    history =
        new ChoiceGroup(
            Lang.tr("open_uri.history"),
            Choice.POPUP,
            PinterestSearchHistoryManager.getInstance().getItems(),
            null);
    append(history);

    favoritesCommand = new Command(Lang.tr("pinterest.favorites"), Command.SCREEN, 4);
    settingsCommand = new Command(Lang.tr("pinterest.settings_title"), Command.SCREEN, 5);
    useHistory = new Command(Lang.tr("open_uri.use"), Command.SCREEN, 6);
    clearHistory = new Command(Lang.tr("open_uri.clear"), Command.SCREEN, 7);
    addCommand(Commands.ok());
    addCommand(favoritesCommand);
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
      navigator.forward(new PinterestFavoritesScreen(navigator));
    } else if (c == settingsCommand) {
      navigator.forward(new PinterestSettingsScreen(navigator));
    } else if (c == useHistory) {
      useSelectedHistory();
    } else if (c == clearHistory) {
      PinterestSearchHistoryManager.getInstance().clear();
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
    String[] items = PinterestSearchHistoryManager.getInstance().getItems();
    for (int i = 0; i < items.length; i++) {
      history.append(items[i], null);
    }
  }

  private void search() {
    final String query = queryField.getString().trim();
    if (query.length() == 0) {
      navigator.showAlert(Lang.tr("search.error.empty_keyword"), AlertType.ERROR);
      return;
    }
    int type = searchTypeGroup.getSelectedIndex();
    if (type == TYPE_USER) {
      openUser(query);
      return;
    }
    if (loading) {
      return;
    }
    loading = true;
    final boolean boards = type == TYPE_BOARDS;
    navigator.showLoadingAlert(
        Lang.tr("pinterest.loading"),
        new Runnable() {
          public void run() {
            loading = false;
          }
        });
    MIDPlay.startOperation(
        new HttpTextOperation(
            boards ? PinterestApi.searchBoardsUrl(query, "") : PinterestApi.searchPinsUrl(query, ""),
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        showResults(query, boards, text);
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

  private void openUser(String query) {
    String username = PinterestApi.cleanUsername(query);
    if (username.length() == 0) {
      navigator.showAlert(Lang.tr("pinterest.username_required"), AlertType.ERROR);
      return;
    }
    PinterestSearchHistoryManager.getInstance().record(username);
    refreshHistory();
    navigator.forward(new PinterestUserScreen(navigator, username));
  }

  private void showResults(String query, boolean boards, String text) {
    PinterestSearchHistoryManager.getInstance().record(query);
    refreshHistory();
    String error = PinterestApi.parseError(text);
    if (error.length() > 0) {
      navigator.dismissAlert();
      navigator.showAlert(error, AlertType.ERROR);
      return;
    }
    if (boards) {
      java.util.Vector items = PinterestApi.parseBoards(text);
      if (items.size() == 0) {
        showEmpty();
        return;
      }
      navigator.forward(
          new PinterestResultsScreen(
              navigator,
              PinterestResultsScreen.SOURCE_SEARCH_BOARDS,
              query,
              "",
              Lang.tr("pinterest.results_boards", query),
              items,
              PinterestApi.parseBookmark(text)));
    } else {
      java.util.Vector items = PinterestApi.parsePins(text);
      if (items.size() == 0) {
        showEmpty();
        return;
      }
      navigator.forward(
          new PinterestResultsScreen(
              navigator,
              PinterestResultsScreen.SOURCE_SEARCH_PINS,
              query,
              "",
              Lang.tr("pinterest.results_pins", query),
              items,
              PinterestApi.parseBookmark(text)));
    }
  }

  private void showEmpty() {
    navigator.dismissAlert();
    navigator.showAlert(Lang.tr("pinterest.empty"), AlertType.INFO);
  }
}
