package midplay.ui.screen;

import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.TextField;
import midplay.MIDPlay;
import midplay.net.PinterestApi;
import midplay.net.PinterestImageDownloadOperation;
import midplay.store.Configuration;
import midplay.store.PinterestFavoritesManager;
import midplay.ui.BaseList;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.Sharing;
import midplay.util.Lang;

/**
 * Local Pinterest favorites. Pins can be kept loose or grouped inside user-created local folders
 * (like Pinterest boards, but on the device). Saved online boards are also listed. Everything keeps
 * only online references, so nothing is downloaded until the user explicitly downloads a photo.
 */
public final class PinterestFavoritesScreen extends BaseList {
  private final Command newFolderCommand;
  private final Command selectCommand;
  private final Command openCommand;
  private final Command renameFolderCommand;
  private final Command deleteFolderCommand;
  private final Command removeCommand;
  private final Command shareCommand;
  private final Command downloadCommand;
  private Vector entries = new Vector();
  private boolean loading;

  public PinterestFavoritesScreen(Navigator navigator) {
    super(Lang.tr("pinterest.favorites"), navigator);
    newFolderCommand = new Command(Lang.tr("pinterest.new_folder"), Command.SCREEN, 1);
    selectCommand = new Command(Lang.tr("pinterest.select"), Command.SCREEN, 2);
    openCommand = new Command(Lang.tr("pinterest.open"), Command.SCREEN, 3);
    renameFolderCommand = new Command(Lang.tr("pinterest.rename_folder"), Command.SCREEN, 4);
    deleteFolderCommand = new Command(Lang.tr("pinterest.delete_folder"), Command.SCREEN, 5);
    removeCommand = new Command(Lang.tr("pinterest.remove_saved"), Command.SCREEN, 6);
    shareCommand = new Command(Lang.tr("pinterest.share"), Command.SCREEN, 7);
    downloadCommand = new Command(Lang.tr("pinterest.download"), Command.SCREEN, 8);
    addCommand(newFolderCommand);
    addCommand(selectCommand);
    addCommand(openCommand);
    addCommand(removeCommand);
    addCommand(shareCommand);
    addCommand(downloadCommand);
    reload();
  }

  protected void showNotify() {
    super.showNotify();
    reload();
  }

  private void reload() {
    entries = new Vector();
    Vector folders = PinterestFavoritesManager.getInstance().getFolders();
    for (int i = 0; i < folders.size(); i++) {
      entries.addElement(
          new Object[] {PinterestMultiSelectScreen.TYPE_FOLDER, folders.elementAt(i)});
    }
    Vector pins = PinterestFavoritesManager.getInstance().getLoosePins();
    for (int i = 0; i < pins.size(); i++) {
      entries.addElement(new Object[] {PinterestMultiSelectScreen.TYPE_PIN, pins.elementAt(i)});
    }
    Vector boards = PinterestFavoritesManager.getInstance().getBoards();
    for (int i = 0; i < boards.size(); i++) {
      entries.addElement(new Object[] {PinterestMultiSelectScreen.TYPE_BOARD, boards.elementAt(i)});
    }
    refresh();
    updateCommands();
    loadThumbnails();
  }

  private void updateCommands() {
    boolean hasItems = entries.size() > 0;
    setCommandVisible(selectCommand, hasItems);
    setCommandVisible(openCommand, hasItems);
    setCommandVisible(removeCommand, hasItems);
    setCommandVisible(shareCommand, hasItems);
    setCommandVisible(downloadCommand, hasItems);
    boolean folderSelected = isFolderSelected();
    setCommandVisible(renameFolderCommand, folderSelected);
    setCommandVisible(deleteFolderCommand, folderSelected);
  }

  private boolean isFolderSelected() {
    Object[] entry = selectedEntry();
    return entry != null && PinterestMultiSelectScreen.TYPE_FOLDER.equals(entry[0]);
  }

  private void setCommandVisible(Command command, boolean visible) {
    if (visible) {
      addCommand(command);
    } else {
      removeCommand(command);
    }
  }

  protected void populateItems() {
    if (entries.size() == 0) {
      append(Lang.tr("pinterest.favorites_empty"), null);
      return;
    }
    for (int i = 0; i < entries.size(); i++) {
      Object[] entry = (Object[]) entries.elementAt(i);
      String type = (String) entry[0];
      String[] item = (String[]) entry[1];
      if (PinterestMultiSelectScreen.TYPE_FOLDER.equals(type)) {
        append(item[PinterestFavoritesManager.FOLDER_NAME], Configuration.folderIcon);
      } else if (PinterestMultiSelectScreen.TYPE_BOARD.equals(type)) {
        append(Lang.tr("pinterest.saved_boards") + ": " + PinterestApi.boardLabel(item), null);
      } else {
        append(PinterestApi.pinLabel(item), null);
      }
    }
  }

  /**
   * Loads the thumbnail of each saved pin/board next to its row. Local folders keep the static
   * folder icon, so their slots stay empty.
   */
  private void loadThumbnails() {
    String[] urls = new String[entries.size()];
    for (int i = 0; i < entries.size(); i++) {
      Object[] entry = (Object[]) entries.elementAt(i);
      String type = (String) entry[0];
      if (PinterestMultiSelectScreen.TYPE_FOLDER.equals(type)) {
        continue;
      }
      String[] item = (String[]) entry[1];
      if (PinterestMultiSelectScreen.TYPE_BOARD.equals(type)) {
        urls[i] = item[PinterestApi.BOARD_COVER];
      } else {
        urls[i] = item[PinterestApi.PIN_IMAGE];
        if (urls[i] == null || urls[i].length() == 0) {
          urls[i] = item[PinterestApi.PIN_IMAGE_LARGE];
        }
      }
    }
    loadArt(urls);
  }

  protected void handleSelection() {
    openSelected();
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == newFolderCommand) {
      createFolder();
    } else if (c == selectCommand) {
      selectMultiple();
    } else if (c == openCommand) {
      openSelected();
    } else if (c == renameFolderCommand) {
      renameFolder();
    } else if (c == deleteFolderCommand) {
      deleteFolder();
    } else if (c == removeCommand) {
      removeSelected();
    } else if (c == shareCommand) {
      shareSelected();
    } else if (c == downloadCommand) {
      downloadSelected();
    }
  }

  private Object[] selectedEntry() {
    int index = getSelectedIndex();
    if (index < 0 || index >= entries.size()) {
      return null;
    }
    return (Object[]) entries.elementAt(index);
  }

  private void openSelected() {
    Object[] entry = selectedEntry();
    if (entry == null) {
      return;
    }
    String type = (String) entry[0];
    String[] item = (String[]) entry[1];
    if (PinterestMultiSelectScreen.TYPE_FOLDER.equals(type)) {
      navigator.forward(
          new PinterestFolderScreen(
              navigator,
              item[PinterestFavoritesManager.FOLDER_ID],
              item[PinterestFavoritesManager.FOLDER_NAME]));
    } else if (PinterestMultiSelectScreen.TYPE_BOARD.equals(type)) {
      navigator.forward(new PinterestBoardScreen(navigator, item));
    } else {
      navigator.forward(new PinterestPinScreen(navigator, item));
    }
  }

  private void selectMultiple() {
    if (entries.size() == 0) {
      return;
    }
    navigator.forward(
        new PinterestMultiSelectScreen(
            navigator,
            PinterestMultiSelectScreen.MODE_FAVORITES,
            Lang.tr("pinterest.select"),
            entries,
            new Runnable() {
              public void run() {
                reload();
              }
            }));
  }

  private void createFolder() {
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
              navigator.showAlert(
                  Lang.tr("pinterest.folder_name_required"), AlertType.ERROR);
              return;
            }
            navigator.restoreDialogAndThen(
                new Runnable() {
                  public void run() {
                    reload();
                    navigator.showAlert(
                        Lang.tr("pinterest.folder_created"),
                        AlertType.CONFIRMATION,
                        PinterestFavoritesScreen.this);
                  }
                });
          }
        });
    navigator.showDialog(form);
  }

  private void renameFolder() {
    Object[] entry = selectedEntry();
    if (entry == null || !PinterestMultiSelectScreen.TYPE_FOLDER.equals(entry[0])) {
      return;
    }
    final String[] folder = (String[]) entry[1];
    final Form form = new Form(Lang.tr("pinterest.rename_folder"));
    final TextField name =
        new TextField(
            Lang.tr("pinterest.folder_name"),
            folder[PinterestFavoritesManager.FOLDER_NAME],
            60,
            TextField.ANY);
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
            if (!PinterestFavoritesManager.getInstance()
                .renameFolder(folder[PinterestFavoritesManager.FOLDER_ID], name.getString())) {
              navigator.showAlert(Lang.tr("pinterest.folder_name_required"), AlertType.ERROR);
              return;
            }
            navigator.restoreDialogAndThen(
                new Runnable() {
                  public void run() {
                    reload();
                    navigator.showAlert(
                        Lang.tr("pinterest.folder_renamed"),
                        AlertType.CONFIRMATION,
                        PinterestFavoritesScreen.this);
                  }
                });
          }
        });
    navigator.showDialog(form);
  }

  private void deleteFolder() {
    Object[] entry = selectedEntry();
    if (entry == null || !PinterestMultiSelectScreen.TYPE_FOLDER.equals(entry[0])) {
      return;
    }
    final String[] folder = (String[]) entry[1];
    navigator.showConfirmationAlert(
        Lang.tr(
            "pinterest.delete_folder_confirm",
            folder[PinterestFavoritesManager.FOLDER_COUNT]),
        new Runnable() {
          public void run() {
            PinterestFavoritesManager.getInstance()
                .deleteFolder(folder[PinterestFavoritesManager.FOLDER_ID]);
            reload();
            navigator.showAlert(Lang.tr("pinterest.folder_deleted"), AlertType.CONFIRMATION);
          }
        },
        AlertType.WARNING);
  }

  private void removeSelected() {
    final Object[] entry = selectedEntry();
    if (entry == null) {
      return;
    }
    if (PinterestMultiSelectScreen.TYPE_FOLDER.equals(entry[0])) {
      deleteFolder();
      return;
    }
    final String[] item = (String[]) entry[1];
    final boolean board = PinterestMultiSelectScreen.TYPE_BOARD.equals(entry[0]);
    navigator.showConfirmationAlert(
        Lang.tr("pinterest.remove_confirm"),
        new Runnable() {
          public void run() {
            if (board) {
              PinterestFavoritesManager.getInstance()
                  .removeBoard(item[PinterestApi.BOARD_ID]);
            } else {
              PinterestFavoritesManager.getInstance().removePin(item[PinterestApi.PIN_ID]);
            }
            reload();
            navigator.showAlert(
                Lang.tr("pinterest.removed_saved"), AlertType.CONFIRMATION);
          }
        },
        AlertType.WARNING);
  }

  private void shareSelected() {
    Object[] entry = selectedEntry();
    if (entry == null || PinterestMultiSelectScreen.TYPE_FOLDER.equals(entry[0])) {
      return;
    }
    final String[] item = (String[]) entry[1];
    String text;
    if (PinterestMultiSelectScreen.TYPE_BOARD.equals(entry[0])) {
      text =
          PinterestApi.boardLabel(item)
              + "\n"
              + PinterestApi.boardWebUrl(
                  item[PinterestApi.BOARD_USER], item[PinterestApi.BOARD_SLUG]);
    } else {
      text = PinterestApi.pinLabel(item) + "\n" + PinterestApi.pinShareUrl(item);
    }
    Sharing.toMPGram(this, text);
  }

  private void downloadSelected() {
    final Object[] entry = selectedEntry();
    if (entry == null || loading || PinterestMultiSelectScreen.TYPE_FOLDER.equals(entry[0])) {
      return;
    }
    final String[] item = (String[]) entry[1];
    final boolean board = PinterestMultiSelectScreen.TYPE_BOARD.equals(entry[0]);
    final String source;
    final String fileName;
    if (board) {
      source = item[PinterestApi.BOARD_COVER];
      fileName = PinterestMultiSelectScreen.boardFileName(item);
    } else {
      source = PinterestMultiSelectScreen.imageUrl(item);
      fileName = PinterestMultiSelectScreen.pinFileName(item);
    }
    if (source == null || source.length() == 0) {
      navigator.showAlert(Lang.tr("pinterest.download_error"), AlertType.ERROR);
      return;
    }
    loading = true;
    navigator.showLoadingAlert(
        Lang.tr("pinterest.downloading"),
        new Runnable() {
          public void run() {
            loading = false;
          }
        });
    MIDPlay.startOperation(
        new PinterestImageDownloadOperation(
            source,
            fileName,
            new PinterestImageDownloadOperation.Listener() {
              public void onDownloaded(final String path) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        navigator.showAlert(
                            Lang.tr("pinterest.downloaded", path), AlertType.CONFIRMATION);
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        navigator.showAlert(
                            Lang.tr("pinterest.download_error"), AlertType.ERROR);
                      }
                    });
              }
            }));
  }
}
