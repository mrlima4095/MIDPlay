package midplay.ui.screen;

import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import midplay.net.RedditApi;
import midplay.store.RedditFavoritesManager;
import midplay.ui.BulkSelectList;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;

/**
 * Multiple-selection list of saved Reddit posts, used to remove several at once. Rows are the same
 * compact post arrays stored by {@link RedditFavoritesManager}.
 */
public final class RedditMultiSelectScreen extends BulkSelectList {
  private final Vector rows;
  private final Runnable onChanged;
  private final Command removeCommand =
      new Command(Lang.tr("reddit.remove_selected"), Command.SCREEN, 3);

  public RedditMultiSelectScreen(
      Navigator navigator, String title, Vector rows, Runnable onChanged) {
    super(navigator, title, "reddit.select_all", "reddit.deselect_all");
    this.rows = rows == null ? new Vector() : rows;
    this.onChanged = onChanged;
    for (int i = 0; i < this.rows.size(); i++) {
      append(RedditApi.postLabel((String[]) this.rows.elementAt(i)), null);
    }
    installBulkCommands();
    addCommand(removeCommand);
    addCommand(Commands.back());
    addCommand(Commands.playerNowPlaying());
    setCommandListener(this);
  }

  public void commandAction(Command c, Displayable d) {
    if (handleBack(c) || handleBulkCommand(c)) {
      return;
    }
    if (c == Commands.playerNowPlaying()) {
      PlayerNavHelper.showNowPlaying(navigator);
    } else if (c == removeCommand) {
      removeSelected();
    }
  }

  private void removeSelected() {
    if (!requireSelection()) {
      return;
    }
    final Vector selected = new Vector();
    boolean[] flags = selectedFlags();
    for (int i = 0; i < flags.length; i++) {
      if (flags[i] && i < rows.size()) {
        selected.addElement(rows.elementAt(i));
      }
    }
    navigator.showConfirmationAlert(
        Lang.tr("reddit.remove_selected_confirm", String.valueOf(selected.size())),
        new Runnable() {
          public void run() {
            String[] ids = new String[selected.size()];
            for (int i = 0; i < selected.size(); i++) {
              ids[i] = ((String[]) selected.elementAt(i))[RedditApi.POST_ID];
            }
            int removed = RedditFavoritesManager.getInstance().removePosts(ids);
            if (onChanged != null) {
              onChanged.run();
            }
            navigator.backAndShowAlert(
                Lang.tr("reddit.removed_many", String.valueOf(removed)),
                AlertType.CONFIRMATION);
          }
        },
        AlertType.WARNING);
  }
}
