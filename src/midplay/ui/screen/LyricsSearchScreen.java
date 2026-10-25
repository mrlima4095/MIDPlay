package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;
import midplay.MIDPlay;
import midplay.net.HttpTextOperation;
import midplay.net.LyricsApi;
import midplay.store.LyricsSearchHistoryManager;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;

/**
 * Manual lyrics search. It opens by itself when the automatic lookup finds
 * nothing, and it is also reachable from the lyrics screen to correct a wrong
 * match.
 */
public final class LyricsSearchScreen extends BaseForm {
  private static final int MAX_QUERY_LENGTH = 150;

  private final TextField queryField;
  private final String originKey;
  private final ChoiceGroup history;
  private final Command useHistory;
  private final Command clearHistory;

  public LyricsSearchScreen(
      Navigator navigator, String artist, String title, boolean notFound, String originKey) {
    super(Lang.tr("lyrics.search_title"), navigator);
    this.originKey = originKey;
    if (notFound) {
      append(new StringItem(null, Lang.tr("lyrics.not_found_hint") + "\n"));
    }
    queryField =
        new TextField(
            Lang.tr("lyrics.search_placeholder"), initialQuery(artist, title), MAX_QUERY_LENGTH,
            TextField.ANY);
    append(queryField);
    history =
        new ChoiceGroup(
            Lang.tr("open_uri.history"),
            Choice.POPUP,
            LyricsSearchHistoryManager.getInstance().getItems(),
            null);
    append(history);
    useHistory = new Command(Lang.tr("open_uri.use"), Command.SCREEN, 1);
    clearHistory = new Command(Lang.tr("open_uri.clear"), Command.SCREEN, 2);
    addCommand(Commands.ok());
    addCommand(useHistory);
    addCommand(clearHistory);
  }

  private static String initialQuery(String artist, String title) {
    String a = artist == null ? "" : artist.trim();
    String t = title == null ? "" : title.trim();
    if (a.length() == 0) {
      return t;
    }
    return t.length() == 0 ? a : a + " " + t;
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == Commands.ok()) {
      search();
    } else if (c == useHistory) {
      useSelectedHistory();
    } else if (c == clearHistory) {
      LyricsSearchHistoryManager.getInstance().clear();
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
    String[] items = LyricsSearchHistoryManager.getInstance().getItems();
    for (int i = 0; i < items.length; i++) {
      history.append(items[i], null);
    }
  }

  private void search() {
    final String query = queryField.getString().trim();
    if (query.length() == 0) {
      navigator.showAlert(Lang.tr("lyrics.search_empty"), AlertType.ERROR);
      return;
    }
    LyricsSearchHistoryManager.getInstance().record(query);
    refreshHistory();
    navigator.showLoadingAlert(Lang.tr("lyrics.searching"));
    MIDPlay.startOperation(
        new HttpTextOperation(
            LyricsApi.searchUrl(query),
            new HttpTextOperation.Listener() {
              public void onText(final String response) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        String[][] rows = LyricsApi.parseSearch(response);
                        if (rows == null) {
                          navigator.showAlert(Lang.tr("lyrics.no_results"), AlertType.INFO);
                          return;
                        }
                        navigator.forward(
                            new LyricsResultsScreen(navigator, query, rows, originKey));
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        navigator.showAlert(Lang.tr("lyrics.search_error"), AlertType.ERROR);
                      }
                    });
              }
            }));
  }
}
