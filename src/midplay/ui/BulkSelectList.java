package midplay.ui;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import midplay.util.Lang;

/**
 * Base class for the "select several items, then act on them" screens used across MIDPlay (notes,
 * tasks, Wattpad library, Pinterest). It owns the {@code Choice.MULTIPLE} list, the Select all /
 * Deselect all commands and the flags bookkeeping, so each subapp only implements its own actions.
 *
 * <p>Subclasses call {@link #installBulkCommands()} once, then in {@code commandAction} first try
 * {@link #handleBack(Command)} and {@link #handleBulkCommand(Command)} before handling their own
 * commands.
 */
public abstract class BulkSelectList extends List implements CommandListener {
  protected final Navigator navigator;

  private final Command selectAllCommand;
  private final Command deselectAllCommand;

  protected BulkSelectList(
      Navigator navigator, String title, String selectAllKey, String deselectAllKey) {
    super(title, Choice.MULTIPLE);
    this.navigator = navigator;
    this.selectAllCommand = new Command(Lang.tr(selectAllKey), Command.SCREEN, 1);
    this.deselectAllCommand = new Command(Lang.tr(deselectAllKey), Command.SCREEN, 2);
  }

  /** Adds the shared Select all / Deselect all commands to the screen. */
  protected final void installBulkCommands() {
    addCommand(selectAllCommand);
    addCommand(deselectAllCommand);
  }

  /** Handles Back, returning true when the command was consumed. */
  protected final boolean handleBack(Command c) {
    if (c == Commands.back()) {
      navigator.back();
      return true;
    }
    return false;
  }

  /** Handles Select all / Deselect all, returning true when the command was consumed. */
  protected final boolean handleBulkCommand(Command c) {
    if (c == selectAllCommand) {
      setAllSelected(true);
      return true;
    }
    if (c == deselectAllCommand) {
      setAllSelected(false);
      return true;
    }
    return false;
  }

  protected final void setAllSelected(boolean selected) {
    boolean[] flags = new boolean[size()];
    for (int i = 0; i < flags.length; i++) {
      flags[i] = selected;
    }
    setSelectedFlags(flags);
  }

  /** One flag per row, true when the row is selected. */
  protected final boolean[] selectedFlags() {
    boolean[] flags = new boolean[size()];
    getSelectedFlags(flags);
    return flags;
  }

  protected final int countSelected() {
    boolean[] flags = selectedFlags();
    int count = 0;
    for (int i = 0; i < flags.length; i++) {
      if (flags[i]) {
        count++;
      }
    }
    return count;
  }

  /** Shows the standard "select at least one item" hint when nothing is selected. */
  protected final boolean requireSelection() {
    if (countSelected() == 0) {
      navigator.showAlert(Lang.tr("bulk.select_empty"), AlertType.INFO);
      return false;
    }
    return true;
  }
}
