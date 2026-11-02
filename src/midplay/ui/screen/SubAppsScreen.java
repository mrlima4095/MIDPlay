package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import midplay.SubAppManager;
import midplay.ui.BulkSelectList;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;
import midplay.util.MemoryUtils;

/**
 * Multiple-choice list of the embedded subapps that are still running. The user can select several
 * apps, use Select all / Deselect all, and kill the selection to release their memory.
 */
public final class SubAppsScreen extends BulkSelectList {
  private final Command killCommand = new Command(Lang.tr("task_viewer.kill"), Command.SCREEN, 3);
  private SubAppManager.SubApp[] apps = new SubAppManager.SubApp[0];

  public SubAppsScreen(Navigator navigator) {
    super(
        navigator,
        Lang.tr("task_viewer.apps"),
        "task_viewer.select_all",
        "task_viewer.deselect_all");
    installBulkCommands();
    addCommand(killCommand);
    addCommand(Commands.back());
    setCommandListener(this);
    reload();
  }

  public void commandAction(Command c, Displayable d) {
    if (handleBack(c) || handleBulkCommand(c)) {
      return;
    }
    if (c == killCommand) {
      killSelected();
    }
  }

  private void killSelected() {
    if (apps.length == 0) {
      navigator.showAlert(Lang.tr("task_viewer.nothing_to_kill"), AlertType.INFO);
      return;
    }
    boolean[] flags = selectedFlags();
    int count = 0;
    for (int i = 0; i < apps.length; i++) {
      if (flags[i]) {
        count++;
      }
    }
    if (count == 0) {
      navigator.showAlert(Lang.tr("bulk.select_empty"), AlertType.INFO);
      return;
    }

    SubAppManager.SubApp[] selected = new SubAppManager.SubApp[count];
    int at = 0;
    for (int i = 0; i < apps.length; i++) {
      if (flags[i]) {
        selected[at++] = apps[i];
      }
    }

    int killed = 0;
    for (int i = 0; i < selected.length; i++) {
      try {
        selected[i].kill();
        killed++;
      } catch (Throwable ignored) {
      }
    }
    MemoryUtils.collect();

    int remaining = reload();
    String message =
        killed == 0
            ? Lang.tr("task_viewer.nothing_to_kill")
            : Lang.tr("task_viewer.killed_all", Integer.toString(killed));
    if (remaining == 0) {
      navigator.backAndShowAlert(message, AlertType.CONFIRMATION);
    } else {
      navigator.showAlert(message, AlertType.CONFIRMATION);
    }
  }

  /** Rebuilds the list from the registry and returns how many subapps are still running. */
  private int reload() {
    deleteAll();
    SubAppManager.SubApp[] all = SubAppManager.getApps();
    int running = 0;
    for (int i = 0; i < all.length; i++) {
      if (all[i].isRunning()) {
        running++;
      }
    }
    apps = new SubAppManager.SubApp[running];
    if (running == 0) {
      append(Lang.tr("task_viewer.subapps_none"), null);
      return 0;
    }
    int at = 0;
    for (int i = 0; i < all.length; i++) {
      if (all[i].isRunning()) {
        apps[at++] = all[i];
        append(Lang.tr(all[i].getNameKey()), null);
      }
    }
    return running;
  }
}
