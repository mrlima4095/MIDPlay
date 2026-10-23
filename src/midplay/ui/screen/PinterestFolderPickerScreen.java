package midplay.ui.screen;

import java.util.Vector;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.TextField;
import midplay.store.PinterestFavoritesManager;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;

/**
 * Lets the user choose a destination local folder for saving pins. The first entry keeps pins loose
 * in favorites; a command creates a brand new folder on the spot.
 */
public final class PinterestFolderPickerScreen extends List implements CommandListener {
  public interface Listener {
    void onFolderSelected(String folderId);
  }

  private final Navigator navigator;
  private final Listener listener;
  private final Command newFolderCommand = new Command(Lang.tr("pinterest.new_folder"), Command.SCREEN, 1);
  private final Vector folderIds = new Vector();

  public PinterestFolderPickerScreen(Navigator navigator, String title, Listener listener) {
    super(title == null ? Lang.tr("pinterest.choose_folder") : title, List.IMPLICIT);
    this.navigator = navigator;
    this.listener = listener;
    addCommand(newFolderCommand);
    addCommand(Commands.back());
    setCommandListener(this);
    reload();
  }

  private void reload() {
    deleteAll();
    folderIds.removeAllElements();
    append(Lang.tr("pinterest.loose_favorites"), null);
    folderIds.addElement("");
    Vector folders = PinterestFavoritesManager.getInstance().getFolders();
    for (int i = 0; i < folders.size(); i++) {
      String[] folder = (String[]) folders.elementAt(i);
      append(
          folder[PinterestFavoritesManager.FOLDER_NAME]
              + " ("
              + folder[PinterestFavoritesManager.FOLDER_COUNT]
              + ")",
          null);
      folderIds.addElement(folder[PinterestFavoritesManager.FOLDER_ID]);
    }
  }

  public void commandAction(Command c, Displayable d) {
    if (c == Commands.back()) {
      navigator.back();
    } else if (c == newFolderCommand) {
      promptNewFolder();
    } else if (c == List.SELECT_COMMAND) {
      int index = getSelectedIndex();
      if (index < 0 || index >= folderIds.size()) {
        return;
      }
      String id = (String) folderIds.elementAt(index);
      navigator.back();
      if (listener != null) {
        listener.onFolderSelected(id);
      }
    }
  }

  private void promptNewFolder() {
    final Form form = new Form(Lang.tr("pinterest.new_folder"));
    final TextField name =
        new TextField(Lang.tr("pinterest.folder_name"), "", 60, TextField.ANY);
    form.append(name);
    form.addCommand(Commands.ok());
    form.addCommand(Commands.cancel());
    form.setCommandListener(
        new CommandListener() {
          public void commandAction(Command c, Displayable d) {
            if (c == Commands.cancel()) {
              navigator.closeDialog();
              return;
            }
            if (c != Commands.ok()) {
              return;
            }
            String id = PinterestFavoritesManager.getInstance().createFolder(name.getString());
            if (id == null) {
              navigator.showAlert(Lang.tr("pinterest.folder_name_required"), javax.microedition.lcdui.AlertType.ERROR);
              return;
            }
            navigator.closeDialog();
            navigator.back();
            if (listener != null) {
              listener.onFolderSelected(id);
            }
          }
        });
    navigator.showDialog(form);
  }
}
