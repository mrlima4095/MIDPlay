package midplay.ui.screen;

import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import midplay.store.WattpadProgressManager;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;

/** Multiple selection over the local Wattpad reading list, used to delete several stories at once. */
public final class WattpadLibrarySelectScreen extends List implements CommandListener {
  private final Navigator navigator;
  private final Vector entries;
  private final Runnable onChanged;
  private final Command selectAllCommand =
      new Command(Lang.tr("note.select_all"), Command.SCREEN, 1);
  private final Command deselectAllCommand =
      new Command(Lang.tr("note.deselect_all"), Command.SCREEN, 2);
  private final Command removeCommand =
      new Command(Lang.tr("wattpad.remove_selected"), Command.SCREEN, 4);
  private final Command archiveCommand =
      new Command(Lang.tr("wattpad.archive_selected"), Command.SCREEN, 3);

  public WattpadLibrarySelectScreen(Navigator navigator, Vector entries, Runnable onChanged) {
    this(navigator, entries, onChanged, false);
  }

  public WattpadLibrarySelectScreen(
      Navigator navigator, Vector entries, Runnable onChanged, boolean archived) {
    super(Lang.tr("wattpad.select_multiple"), Choice.MULTIPLE);
    this.navigator = navigator;
    this.entries = entries;
    this.onChanged = onChanged;
    for (int i = 0; i < entries.size(); i++) {
      append(WattpadLibraryScreen.labelFor((String[]) entries.elementAt(i)), null);
    }
    addCommand(selectAllCommand);
    addCommand(deselectAllCommand);
    if (!archived) {
      addCommand(archiveCommand);
    }
    addCommand(removeCommand);
    addCommand(Commands.back());
    addCommand(Commands.playerNowPlaying());
    setCommandListener(this);
  }

  public void commandAction(Command c, Displayable d) {
    if (c == Commands.back()) {
      navigator.back();
    } else if (c == Commands.playerNowPlaying()) {
      PlayerNavHelper.showNowPlaying(navigator);
    } else if (c == selectAllCommand) {
      setAllSelected(true);
    } else if (c == deselectAllCommand) {
      setAllSelected(false);
    } else if (c == removeCommand) {
      removeSelected();
    } else if (c == archiveCommand) {
      archiveSelected();
    }
  }

  private void setAllSelected(boolean selected) {
    boolean[] flags = new boolean[size()];
    for (int i = 0; i < flags.length; i++) {
      flags[i] = selected;
    }
    setSelectedFlags(flags);
  }

  private void removeSelected() {
    final boolean[] flags = new boolean[entries.size()];
    getSelectedFlags(flags);
    int count = 0;
    for (int i = 0; i < flags.length; i++) {
      if (flags[i]) {
        count++;
      }
    }
    if (count == 0) {
      navigator.showAlert(Lang.tr("bulk.select_empty"), AlertType.INFO);
      return;
    }
    final int selected = count;
    navigator.showConfirmationAlert(
        Lang.tr("wattpad.remove_selected_confirm", String.valueOf(selected)),
        new Runnable() {
          public void run() {
            WattpadProgressManager manager = WattpadProgressManager.getInstance();
            boolean[] toRemove = new boolean[entries.size()];
            getSelectedFlags(toRemove);
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
    final boolean[] flags = new boolean[entries.size()];
    getSelectedFlags(flags);
    int count = 0;
    for (int i = 0; i < flags.length; i++) {
      if (flags[i]) {
        count++;
      }
    }
    if (count == 0) {
      navigator.showAlert(Lang.tr("bulk.select_empty"), AlertType.INFO);
      return;
    }
    final int selected = count;
    navigator.showConfirmationAlert(
        Lang.tr("wattpad.archive_selected_confirm", String.valueOf(selected)),
        new Runnable() {
          public void run() {
            WattpadProgressManager manager = WattpadProgressManager.getInstance();
            boolean[] toArchive = new boolean[entries.size()];
            getSelectedFlags(toArchive);
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
