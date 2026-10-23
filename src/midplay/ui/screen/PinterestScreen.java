package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
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

/** Pinterest start screen: search for pins or boards, or open a profile by username. */
public final class PinterestScreen extends BaseForm {
  private final TextField queryField;
  private final ChoiceGroup history;
  private final Command pinsCommand;
  private final Command boardsCommand;
  private final Command userCommand;
  private final Command favoritesCommand;
  private final Command settingsCommand;
  private final Command useHistory;
  private final Command clearHistory;
  private boolean loading;

  public PinterestScreen(Navigator navigator) {
    super(Lang.tr("pinterest.title"), navigator);
    queryField = new TextField(Lang.tr("pinterest.search_field"), "", 120, TextField.ANY);
    append(queryField);
    history =
        new ChoiceGroup(
            Lang.tr("open_uri.history"),
            Choice.POPUP,
            PinterestSearchHistoryManager.getInstance().getItems(),
            null);
    append(history);
    pinsCommand = new Command(Lang.tr("pinterest.search_pins"), Command.SCREEN, 1);
    boardsCommand = new Command(Lang.tr("pinterest.search_boards"), Command.SCREEN, 2);
    userCommand = new Command(Lang.tr("pinterest.open_user"), Command.SCREEN, 3);
    favoritesCommand = new Command(Lang.tr("pinterest.favorites"), Command.SCREEN, 4);
    settingsCommand = new Command(Lang.tr("pinterest.settings_title"), Command.SCREEN, 5);
    useHistory = new Command(Lang.tr("open_uri.use"), Command.SCREEN, 6);
    clearHistory = new Command(Lang.tr("open_uri.clear"), Command.SCREEN, 7);
    addCommand(pinsCommand);
    addCommand(boardsCommand);
    addCommand(userCommand);
    addCommand(favoritesCommand);
    addCommand(settingsCommand);
    addCommand(useHistory);
    addCommand(clearHistory);
    addCommand(Commands.playerNowPlaying());
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == Commands.playerNowPlaying()) {
      PlayerNavHelper.showNowPlaying(navigator);
    } else if (c == pinsCommand) {
      search(false);
    } else if (c == boardsCommand) {
      search(true);
    } else if (c == userCommand) {
      promptUser();
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

  private void search(final boolean boards) {
    if (loading) {
      return;
    }
    final String query = queryField.getString().trim();
    if (query.length() == 0) {
      navigator.showAlert(Lang.tr("search.error.empty_keyword"), AlertType.ERROR);
      return;
    }
    loading = true;
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

  private void promptUser() {
    final Form form = new Form(Lang.tr("pinterest.open_user"));
    final TextField username =
        new TextField(Lang.tr("pinterest.username"), "", 80, TextField.ANY);
    form.append(username);
    form.addCommand(Commands.ok());
    form.addCommand(Commands.cancel());
    form.setCommandListener(
        new CommandListener() {
          public void commandAction(Command command, Displayable displayable) {
            if (command == Commands.cancel()) {
              navigator.back();
              return;
            }
            if (command != Commands.ok()) {
              return;
            }
            String value = PinterestApi.cleanUsername(username.getString());
            if (value.length() == 0) {
              navigator.showAlert(Lang.tr("pinterest.username_required"), AlertType.ERROR);
              return;
            }
            PinterestSearchHistoryManager.getInstance().record(value);
            navigator.popTo(PinterestScreen.this);
            navigator.forward(new PinterestUserScreen(navigator, value));
          }
        });
    navigator.forward(form);
  }
}
