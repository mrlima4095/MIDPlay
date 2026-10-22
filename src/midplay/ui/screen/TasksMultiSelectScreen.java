package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import midplay.store.TasksManager;
import midplay.store.TasksManager.Task;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.DateTime;
import midplay.util.Lang;

/** Multiple-selection mode for tasks: select all, deselect all and bulk delete. */
public final class TasksMultiSelectScreen extends List implements CommandListener {
  private final Navigator navigator;
  private final Runnable onClose;
  private final Command selectAllCommand;
  private final Command deselectAllCommand;
  private final Command deleteCommand;
  private Task[] tasks;

  public TasksMultiSelectScreen(Navigator navigator, Task[] tasks, Runnable onClose) {
    super(Lang.tr("task.select_multiple"), List.MULTIPLE);
    this.navigator = navigator;
    this.onClose = onClose;
    this.tasks = tasks == null ? new Task[0] : tasks;
    selectAllCommand = new Command(Lang.tr("task.select_all"), Command.SCREEN, 1);
    deselectAllCommand = new Command(Lang.tr("task.deselect_all"), Command.SCREEN, 2);
    deleteCommand = new Command(Lang.tr("task.delete_selected"), Command.SCREEN, 3);
    addCommand(selectAllCommand);
    addCommand(deselectAllCommand);
    addCommand(deleteCommand);
    addCommand(Commands.back());
    setCommandListener(this);
    populateItems();
  }

  private void populateItems() {
    deleteAll();
    for (int i = 0; i < tasks.length; i++) {
      append(label(tasks[i]), null);
    }
  }

  private String label(Task task) {
    StringBuffer text = new StringBuffer();
    text.append(task.done ? "[X] " : "[ ] ");
    if (task.pinned) {
      text.append("[PIN] ");
    }
    if (task.priority == TasksManager.PRIORITY_HIGH) {
      text.append("[!] ");
    } else if (task.priority == TasksManager.PRIORITY_LOW) {
      text.append("[-] ");
    }
    text.append(task.title);
    if (task.due != DateTime.NONE) {
      text.append(' ');
      if (DateTime.isOverdue(task.due, task.done)) {
        text.append("!! ");
      }
      text.append(DateTime.formatShort(task.due));
    }
    return text.toString();
  }

  public void commandAction(Command command, Displayable displayable) {
    try {
      if (command == Commands.back()) {
        navigator.back();
      } else if (command == selectAllCommand) {
        setAllSelected(true);
      } else if (command == deselectAllCommand) {
        setAllSelected(false);
      } else if (command == deleteCommand) {
        deleteSelected();
      }
    } catch (Exception e) {
      navigator.showAlert(e.toString(), AlertType.ERROR);
    }
  }

  private void setAllSelected(boolean selected) {
    boolean[] flags = new boolean[size()];
    for (int i = 0; i < flags.length; i++) {
      flags[i] = selected;
    }
    setSelectedFlags(flags);
  }

  private Task[] getSelectedTasks() {
    boolean[] flags = new boolean[tasks.length];
    getSelectedFlags(flags);
    int count = 0;
    for (int i = 0; i < flags.length; i++) {
      if (flags[i]) {
        count++;
      }
    }
    Task[] selected = new Task[count];
    int index = 0;
    for (int i = 0; i < flags.length; i++) {
      if (flags[i]) {
        selected[index++] = tasks[i];
      }
    }
    return selected;
  }

  private void deleteSelected() {
    final Task[] selected = getSelectedTasks();
    if (selected.length == 0) {
      navigator.showAlert(Lang.tr("bulk.select_empty"), AlertType.INFO);
      return;
    }
    String message =
        selected.length == 1
            ? Lang.tr("task.delete_confirm", selected[0].title)
            : Lang.tr("task.delete_selected_confirm", String.valueOf(selected.length));
    navigator.showConfirmationAlert(
        message,
        new Runnable() {
          public void run() {
            try {
              int[] ids = new int[selected.length];
              for (int i = 0; i < selected.length; i++) {
                ids[i] = selected[i].id;
              }
              TasksManager.getInstance().removeMany(ids);
              if (onClose != null) {
                onClose.run();
              }
              String message =
                  selected.length == 1
                      ? Lang.tr("task.deleted")
                      : Lang.tr("task.deleted_many", String.valueOf(selected.length));
              navigator.backAndShowAlert(message, AlertType.CONFIRMATION);
            } catch (Exception e) {
              navigator.showAlert(e.toString(), AlertType.ERROR);
            }
          }
        },
        AlertType.WARNING);
  }
}
