package midplay.ui.screen;

import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import midplay.store.NotesManager;
import midplay.store.NotesManager.Note;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;

/** Password protected list of vault notes. */
public final class VaultScreen extends List implements CommandListener {
  private final Navigator navigator;
  private final Command newCommand;
  private final Command removeCommand;
  private final Command deleteCommand;
  private final Command passwordCommand;
  private Note[] notes;

  public VaultScreen(Navigator navigator) {
    super(Lang.tr("vault.title"), List.IMPLICIT);
    this.navigator = navigator;
    newCommand = new Command(Lang.tr("note.new"), Command.SCREEN, 1);
    removeCommand = new Command(Lang.tr("vault.remove"), Command.SCREEN, 3);
    deleteCommand = new Command(Lang.tr("note.delete"), Command.SCREEN, 4);
    passwordCommand = new Command(Lang.tr("vault.change_password"), Command.SCREEN, 5);
    addCommand(newCommand);
    addCommand(removeCommand);
    addCommand(deleteCommand);
    addCommand(passwordCommand);
    addCommand(Commands.back());
    addCommand(Commands.playerNowPlaying());
    setCommandListener(this);
    populateItems();
  }

  public void showNotify() {
    populateItems();
  }

  public void commandAction(Command command, Displayable displayable) {
    try {
      if (command == List.SELECT_COMMAND) {
        Note note = selected();
        if (note != null) navigator.forward(new NoteReaderScreen(note, navigator, refreshOnClose()));
      } else if (command == Commands.back()) {
        navigator.back();
      } else if (command == Commands.playerNowPlaying()) {
        PlayerNavHelper.showNowPlaying(navigator);
      } else if (command == newCommand) {
        navigator.forward(new NoteEditorScreen(null, navigator, refreshOnClose(), true));
      } else if (command == removeCommand) {
        removeFromVault();
      } else if (command == deleteCommand) {
        deleteSelected();
      } else if (command == passwordCommand) {
        VaultAuth.changePassword(navigator);
      }
    } catch (Exception e) {
      navigator.showAlert(e.toString(), AlertType.ERROR);
    }
  }

  private void populateItems() {
    deleteAll();
    Note[] all = NotesManager.getInstance().getVaultNotes();
    Vector visible = new Vector();
    for (int i = 0; i < all.length; i++) {
      visible.addElement(all[i]);
    }
    notes = new Note[visible.size()];
    for (int i = 0; i < notes.length; i++) {
      notes[i] = (Note) visible.elementAt(i);
      append((notes[i].pinned ? "[PIN] " : "") + notes[i].title, null);
    }
    if (notes.length == 0) append(Lang.tr("vault.empty"), null);
  }

  private Note selected() {
    int index = getSelectedIndex();
    return notes != null && index >= 0 && index < notes.length ? notes[index] : null;
  }

  private void removeFromVault() {
    final Note note = selected();
    if (note == null) return;
    try {
      NotesManager.getInstance().setVault(note.id, false);
      populateItems();
      navigator.showAlert(Lang.tr("vault.removed"), AlertType.CONFIRMATION, this);
    } catch (Exception e) {
      navigator.showAlert(e.toString(), AlertType.ERROR, this);
    }
  }

  private void deleteSelected() {
    final Note note = selected();
    if (note == null) return;
    navigator.showConfirmationAlert(Lang.tr("note.delete_confirm", note.title), new Runnable() {
      public void run() {
        try {
          NotesManager.getInstance().remove(note.id);
          populateItems();
          navigator.showAlert(Lang.tr("note.deleted"), AlertType.CONFIRMATION, VaultScreen.this);
        } catch (Exception e) {
          navigator.showAlert(e.toString(), AlertType.ERROR, VaultScreen.this);
        }
      }
    });
  }

  private Runnable refreshOnClose() {
    return new Runnable() {
      public void run() {
        populateItems();
      }
    };
  }
}
