package midplay.ui.screen;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import midplay.ui.BaseList;
import midplay.ui.Navigator;
import midplay.util.Lang;

/** Songs returned by a manual lyrics search. */
public final class LyricsResultsScreen extends BaseList {
  private final String[][] rows;
  private final String originKey;

  public LyricsResultsScreen(
      Navigator navigator, String query, String[][] rows, String originKey) {
    super(Lang.tr("lyrics.results") + ": " + query, navigator);
    this.rows = rows == null ? new String[0][] : rows;
    this.originKey = originKey;
    populateItems();
  }

  protected void populateItems() {
    for (int i = 0; i < rows.length; i++) {
      String title = rows[i][1] == null ? "" : rows[i][1].trim();
      String artist = rows[i][0] == null ? "" : rows[i][0].trim();
      String label = title;
      if (artist.length() > 0) {
        label = label.length() > 0 ? label + " - " + artist : artist;
      }
      append(label, null);
    }
  }

  protected void handleSelection() {
    int index = getSelectedIndex();
    if (!isValidSelection(index, rows.length)) {
      return;
    }
    LyricsScreen.openByPath(
        navigator, rows[index][0], rows[index][1], rows[index][2], originKey);
  }

  protected void handleCommand(Command c, Displayable d) {
    // No extra commands; Back and Now Playing come from BaseList.
  }
}
