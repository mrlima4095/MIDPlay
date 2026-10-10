package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import midplay.store.NotesManager;
import midplay.store.NotesManager.Note;
import midplay.ui.BaseList;
import midplay.ui.Navigator;
import midplay.util.Lang;

public final class NotesScreen extends BaseList {
  private final Command newCommand;
  private final Command deleteCommand;
  private final Command exportCommand;
  private final Command importCommand;
  private Note[] notes;

  public NotesScreen(Navigator navigator) {
    super(Lang.tr("note.title"), navigator);
    newCommand = new Command(Lang.tr("note.new"), Command.SCREEN, 1);
    deleteCommand = new Command(Lang.tr("note.delete"), Command.SCREEN, 2);
    exportCommand = new Command(Lang.tr("note.export"), Command.SCREEN, 3);
    importCommand = new Command(Lang.tr("note.import"), Command.SCREEN, 4);
    addCommand(newCommand);
    addCommand(deleteCommand);
    addCommand(exportCommand);
    addCommand(importCommand);
    populateItems();
  }

  protected void populateItems() {
    notes = NotesManager.getInstance().getNotes();
    for (int i = 0; i < notes.length; i++) {
      append(notes[i].title, null);
    }
    if (notes.length == 0) append(Lang.tr("note.empty"), null);
  }

  protected void handleSelection() {
    Note note = selected();
    if (note != null) navigator.forward(new NoteReaderScreen(note, navigator, refreshOnClose()));
  }

  protected void showNotify() {
    super.showNotify();
    refresh();
  }

  protected void handleCommand(Command command, Displayable displayable) {
    if (command == newCommand) createNote();
    else if (command == deleteCommand) deleteNote();
    else if (command == exportCommand) exportNotes();
    else if (command == importCommand) importNotes();
  }

  private Note selected() {
    int index = getSelectedIndex();
    return notes != null && index >= 0 && index < notes.length ? notes[index] : null;
  }

  private void createNote() {
    navigator.forward(new NoteEditorScreen(null, navigator, refreshOnClose()));
  }

  private Runnable refreshOnClose() {
    return new Runnable() {
      public void run() {
        refresh();
      }
    };
  }

  private void deleteNote() {
    final Note note = selected();
    if (note == null) return;
    navigator.showConfirmationAlert(Lang.tr("note.delete"), new Runnable() {
      public void run() {
        try {
          NotesManager.getInstance().remove(note.id);
          refresh();
          navigator.dismissAlert();
        } catch (Exception e) {
          navigator.showAlert(e.toString(), AlertType.ERROR);
        }
      }
    }, AlertType.WARNING);
  }

  private void exportNotes() {
    navigator.forward(new FileBrowserScreen(navigator, new FileBrowserScreen.DirectorySelectionListener() {
      public void onDirectorySelected(String directory) {
        try {
          String path = NotesManager.getInstance().exportToDevice(directory);
          navigator.back();
          navigator.showAlert(Lang.tr("note.exported", path), AlertType.CONFIRMATION);
        } catch (Exception e) {
          navigator.showAlert(e.toString(), AlertType.ERROR);
        }
      }
    }));
  }

  private void importNotes() {
    navigator.forward(new FileBrowserScreen(navigator, new FileBrowserScreen.FileSelectionListener() {
      public void onFileSelected(String path) {
        try {
          NotesManager.getInstance().importFromDevice(path);
          navigator.back();
          refresh();
          navigator.showAlert(Lang.tr("note.imported"), AlertType.CONFIRMATION);
        } catch (Exception e) {
          navigator.showAlert(e.toString(), AlertType.ERROR);
        }
      }
    }));
  }
}
