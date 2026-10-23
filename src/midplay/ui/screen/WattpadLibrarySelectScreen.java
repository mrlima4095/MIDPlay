package midplay.ui.screen;

import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import midplay.store.WattpadProgressManager;
import midplay.ui.BulkSelectList;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;

/** Multiple selection over the local Wattpad reading list, used to delete several stories at once. */
public final class WattpadLibrarySelectScreen extends BulkSelectList {
  private final Vector entries;
  private final Runnable onChanged;
  private final Command removeCommand =
      new Command(Lang.tr("wattpad.remove_selected"), Command.SCREEN, 4);
  private final Command archiveCommand =
      new Command(Lang.tr("wattpad.archive_selected"), Command.SCREEN, 3);

  public WattpadLibrarySelectScreen(Navigator navigator, Vector entries, Runnable onChanged) {
    this(navigator, entries, onChanged, false);
  }

  public WattpadLibrarySelectScreen(
      Navigator navigator, Vector entries, Runnable onChanged, boolean archived) {
    super(navigator, Lang.tr("wattpad.select_multiple"), "note.select_all", "note.deselect_all");
    this.entries = entries;
    this.onChanged = onChanged;
    for (int i = 0; i < entries.size(); i++) {
      append(WattpadLibraryScreen.labelFor((String[]) entries.elementAt(i)), null);
    }
    installBulkCommands();
    if (!archived) {
      addCommand(archiveCommand);
    }
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
    } else if (c == archiveCommand) {
      archiveSelected();
    }
  }

  private void removeSelected() {
    final int selected = countSelected();
    if (selected == 0) {
      navigator.showAlert(Lang.tr("bulk.select_empty"), AlertType.INFO);
      return;
    }
    navigator.showConfirmationAlert(
        Lang.tr("wattpad.remove_selected_confirm", String.valueOf(selected)),
        new Runnable() {
          public void run() {
            WattpadProgressManager manager = WattpadProgressManager.getInstance();
            boolean[] toRemove = selectedFlags();
            for (int i = 0; i < toRemove.length; i++) {
              if (toRemove[i]) {
                manager.remove(
                    ((String[]) entries.elementAt(i))[WattpadProgressManager.ENTRY_ID]);
              }
            }
            if (onChanged != null) {
              onChanged.run();
            }
            navigator.back();
            navigator.showAlert(
                Lang.tr("wattpad.removed_many", String.valueOf(selected)),
                AlertType.CONFIRMATION);
          }
        },
        AlertType.WARNING);
  }

  private void archiveSelected() {
    final int selected = countSelected();
    if (selected == 0) {
      navigator.showAlert(Lang.tr("bulk.select_empty"), AlertType.INFO);
      return;
    }
    navigator.showConfirmationAlert(
        Lang.tr("wattpad.archive_selected_confirm", String.valueOf(selected)),
        new Runnable() {
          public void run() {
            WattpadProgressManager manager = WattpadProgressManager.getInstance();
            boolean[] toArchive = selectedFlags();
            for (int i = 0; i < toArchive.length; i++) {
              if (toArchive[i]) {
                manager.archive(
                    ((String[]) entries.elementAt(i))[WattpadProgressManager.ENTRY_ID]);
              }
            }
            if (onChanged != null) {
              onChanged.run();
            }
            navigator.back();
            navigator.showAlert(
                Lang.tr("wattpad.archived_many", String.valueOf(selected)),
                AlertType.CONFIRMATION);
          }
        },
        AlertType.WARNING);
  }
}
