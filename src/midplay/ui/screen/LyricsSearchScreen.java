package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;
import midplay.MIDPlay;
import midplay.net.HttpTextOperation;
import midplay.net.LyricsApi;
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
    addCommand(Commands.ok());
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
    }
  }

  private void search() {
    final String query = queryField.getString().trim();
    if (query.length() == 0) {
      navigator.showAlert(Lang.tr("lyrics.search_empty"), AlertType.ERROR);
      return;
    }
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
