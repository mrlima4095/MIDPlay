package midplay.ui.screen;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.StringItem;
import midplay.MIDPlay;
import midplay.store.DownloadQueueManager;
import midplay.ui.BaseForm;
import midplay.ui.Navigator;
import midplay.util.Lang;

/**
 * Hidden runtime monitor. It is reachable by typing {@code htop} in the Open... screen, so it does
 * not add another item to the main menu.
 */
public final class TaskViewerScreen extends BaseForm {
  private final StringItem content = new StringItem(null, "");
  private final Command refreshCommand;
  private final Command gcCommand;

  public TaskViewerScreen(Navigator navigator) {
    super(Lang.tr("task_viewer.title"), navigator);
    refreshCommand = new Command(Lang.tr("task_viewer.refresh"), Command.SCREEN, 2);
    gcCommand = new Command(Lang.tr("task_viewer.gc"), Command.SCREEN, 3);
    append(content);
    addCommand(refreshCommand);
    addCommand(gcCommand);
    refresh();
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == refreshCommand) {
      refresh();
    } else if (c == gcCommand) {
      Runtime.getRuntime().gc();
      refresh();
    }
  }

  private void refresh() {
    Runtime runtime = Runtime.getRuntime();
    long total = runtime.totalMemory();
    long free = runtime.freeMemory();
    long used = total - free;
    int usedPercent = total > 0 ? (int) ((used * 100L) / total) : 0;

    StringBuffer text = new StringBuffer();
    text.append(Lang.tr("task_viewer.memory")).append('\n');
    text.append(Lang.tr("task_viewer.memory_used", kb(used)))
        .append(" (")
        .append(usedPercent)
        .append("%)")
        .append('\n');
    text.append(Lang.tr("task_viewer.memory_free", kb(free))).append('\n');
    text.append(Lang.tr("task_viewer.memory_total", kb(total)));

    text.append("\n\n").append(Lang.tr("task_viewer.threads")).append('\n');
    text.append(
            Lang.tr(
                "task_viewer.threads_active", Integer.toString(Thread.activeCount())))
        .append('\n');
    String currentThread = Thread.currentThread().getName();
    text.append(
        Lang.tr(
            "task_viewer.thread_current", currentThread == null ? "" : currentThread));

    text.append("\n\n").append(Lang.tr("task_viewer.network")).append('\n');
    String operation = MIDPlay.getRunningOperationName();
    text.append(
        operation == null
            ? Lang.tr("task_viewer.network_none")
            : Lang.tr("task_viewer.network_active", operation));

    int activeDownloads = 0;
    int pendingDownloads = 0;
    DownloadQueueManager.Entry[] entries =
        DownloadQueueManager.getInstance().getQueueSnapshot();
    for (int i = 0; i < entries.length; i++) {
      if (entries[i].active) {
        activeDownloads++;
      } else {
        pendingDownloads++;
      }
    }
    text.append("\n\n").append(Lang.tr("task_viewer.downloads")).append('\n');
    text.append(Lang.tr("task_viewer.downloads_active", Integer.toString(activeDownloads)))
        .append('\n');
    text.append(Lang.tr("task_viewer.downloads_pending", Integer.toString(pendingDownloads)));

    content.setText(text.toString());
  }

  private static String kb(long bytes) {
    return Long.toString(bytes / 1024);
  }
}
