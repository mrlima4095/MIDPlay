package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.TextField;
import midplay.store.NotesManager;
import midplay.store.NotesManager.Note;
import midplay.ui.BulkSelectList;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;

public final class NotesMultiSelectScreen extends BulkSelectList {
  private final Note[] notes;
  private final Runnable onDone;
  private final Command deleteCommand = new Command(Lang.tr("note.delete_selected"), Command.SCREEN, 3);
  private final Command exportCommand = new Command(Lang.tr("note.export_selected"), Command.SCREEN, 4);
  private final Command vaultCommand = new Command(Lang.tr("vault.protect"), Command.SCREEN, 5);

  public NotesMultiSelectScreen(Navigator navigator, Note[] notes, Runnable onDone) {
    super(navigator, Lang.tr("note.select_multiple"), "note.select_all", "note.deselect_all");
    this.notes = notes;
    this.onDone = onDone;
    for (int i = 0; i < notes.length; i++) {
      append(notes[i].title, null);
    }
    installBulkCommands();
    addCommand(deleteCommand);
    addCommand(exportCommand);
    addCommand(vaultCommand);
    addCommand(Commands.back());
    setCommandListener(this);
  }

  public void commandAction(Command command, Displayable displayable) {
    if (handleBack(command) || handleBulkCommand(command)) {
      return;
    }
    if (command == deleteCommand) {
      deleteSelected();
    } else if (command == exportCommand) {
      exportSelected();
    } else if (command == vaultCommand) {
      moveSelectedToVault();
    }
  }

  private Note[] getSelectedNotes() {
    boolean[] flags = selectedFlags();
    int count = 0;
    for (int i = 0; i < flags.length; i++) {
      if (flags[i]) {
        count++;
      }
    }
    Note[] selected = new Note[count];
    int idx = 0;
    for (int i = 0; i < flags.length; i++) {
      if (flags[i]) {
        selected[idx++] = notes[i];
      }
    }
    return selected;
  }

  private void deleteSelected() {
    final Note[] selected = getSelectedNotes();
    if (selected.length == 0) {
      navigator.showAlert(Lang.tr("bulk.select_empty"), AlertType.INFO);
      return;
    }
    navigator.showConfirmationAlert(Lang.tr("note.delete_selected_confirm"), new Runnable() {
      public void run() {
        NotesManager manager = NotesManager.getInstance();
        for (int i = 0; i < selected.length; i++) {
          try { manager.remove(selected[i].id); } catch (Exception ignored) {}
        }
        if (onDone != null) onDone.run();
        navigator.back();
        navigator.showAlert(Lang.tr("bulk.notes_deleted", String.valueOf(selected.length)), AlertType.CONFIRMATION);
      }
    }, AlertType.WARNING);
  }

  private void moveSelectedToVault() {
    final Note[] selected = getSelectedNotes();
    if (selected.length == 0) {
      navigator.showAlert(Lang.tr("bulk.select_empty"), AlertType.INFO);
      return;
    }
    final Displayable previous = navigator.getPrevious();
    VaultAuth.authenticate(
        navigator,
        new Runnable() {
          public void run() {
            NotesManager manager = NotesManager.getInstance();
            for (int i = 0; i < selected.length; i++) {
              try {
                manager.setVault(selected[i].id, true);
              } catch (Exception ignored) {
              }
            }
            if (onDone != null) onDone.run();
            navigator.back();
            navigator.showAlert(
                Lang.tr("vault.moved_many", String.valueOf(selected.length)),
                AlertType.CONFIRMATION,
                previous);
          }
        });
  }

  private void exportSelected() {
    final Note[] selected = getSelectedNotes();
    if (selected.length == 0) {
      navigator.showAlert(Lang.tr("bulk.select_empty"), AlertType.INFO);
      return;
    }
    navigator.forward(
        new FileBrowserScreen(
            navigator,
            new FileBrowserScreen.DirectorySelectionListener() {
              public void onDirectorySelected(final String directory) {
                navigator.back();
                promptFileName(directory, selected);
              }
            }));
  }

  private void promptFileName(final String directory, final Note[] selected) {
    final Form form = new Form(Lang.tr("note.export_filename"));
    final TextField name = new TextField(Lang.tr("note.file_name"), "MIDPlay.vnt", 100, TextField.ANY);
    form.append(name);
    form.addCommand(Commands.ok());
    form.addCommand(Commands.cancel());
    form.setCommandListener(
        new CommandListener() {
          public void commandAction(Command command, Displayable displayable) {
            if (command == Commands.cancel()) {
              navigator.back();
            } else if (command == Commands.ok()) {
              try {
                String path = NotesManager.getInstance().exportToDevice(directory, selected, name.getString());
                if (onDone != null) onDone.run();
                navigator.back();
                navigator.back();
                navigator.showAlert(Lang.tr("note.exported", path), AlertType.CONFIRMATION);
              } catch (Exception e) {
                navigator.showAlert(e.toString(), AlertType.ERROR);
              }
            }
          }
        });
    navigator.forward(form);
  }
}
