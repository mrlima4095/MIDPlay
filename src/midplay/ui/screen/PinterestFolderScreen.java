package midplay.ui.screen;

import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import midplay.MIDPlay;
import midplay.net.PinterestApi;
import midplay.net.PinterestImageDownloadOperation;
import midplay.store.PinterestFavoritesManager;
import midplay.ui.BaseList;
import midplay.ui.Navigator;
import midplay.ui.Sharing;
import midplay.util.Lang;

/** Contents of one local Pinterest folder: the pins the user stored inside it. */
public final class PinterestFolderScreen extends BaseList {
  private final String folderId;
  private final Command selectCommand;
  private final Command shareCommand;
  private final Command downloadCommand;
  private final Command removeCommand;
  private final Command moveCommand;
  private Vector entries = new Vector();
  private boolean loading;

  public PinterestFolderScreen(Navigator navigator, String folderId, String folderName) {
    super(
        folderName == null || folderName.length() == 0
            ? Lang.tr("pinterest.local_folder")
            : folderName,
        navigator);
    this.folderId = folderId == null ? "" : folderId;
    selectCommand = new Command(Lang.tr("pinterest.select"), Command.SCREEN, 1);
    shareCommand = new Command(Lang.tr("pinterest.share"), Command.SCREEN, 2);
    downloadCommand = new Command(Lang.tr("pinterest.download"), Command.SCREEN, 3);
    moveCommand = new Command(Lang.tr("pinterest.move_to_folder"), Command.SCREEN, 4);
    removeCommand = new Command(Lang.tr("pinterest.remove_saved"), Command.SCREEN, 5);
    addCommand(selectCommand);
    addCommand(shareCommand);
    addCommand(downloadCommand);
    addCommand(moveCommand);
    addCommand(removeCommand);
    reload();
  }

  protected void showNotify() {
    super.showNotify();
    reload();
  }

  private void reload() {
    entries = PinterestFavoritesManager.getInstance().getPinsInFolder(folderId);
    refresh();
    boolean hasItems = entries.size() > 0;
    setCommandVisible(selectCommand, hasItems);
    setCommandVisible(shareCommand, hasItems);
    setCommandVisible(downloadCommand, hasItems);
    setCommandVisible(moveCommand, hasItems);
    setCommandVisible(removeCommand, hasItems);
    loadThumbnails();
  }

  /** Loads a thumbnail for each saved pin in this folder. */
  private void loadThumbnails() {
    String[] urls = new String[entries.size()];
    for (int i = 0; i < entries.size(); i++) {
      String[] pin = (String[]) entries.elementAt(i);
      urls[i] = pin[PinterestApi.PIN_IMAGE];
      if (urls[i] == null || urls[i].length() == 0) {
        urls[i] = pin[PinterestApi.PIN_IMAGE_LARGE];
      }
    }
    loadArt(urls);
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
      append(Lang.tr("pinterest.folder_empty"), null);
      return;
    }
    for (int i = 0; i < entries.size(); i++) {
      append(PinterestApi.pinLabel((String[]) entries.elementAt(i)), null);
    }
  }

  protected void handleSelection() {
    int index = getSelectedIndex();
    if (index < 0 || index >= entries.size()) {
      return;
    }
    navigator.forward(new PinterestPinScreen(navigator, (String[]) entries.elementAt(index)));
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == selectCommand) {
      select();
    } else if (c == shareCommand) {
      shareSelected();
    } else if (c == downloadCommand) {
      downloadSelected();
    } else if (c == moveCommand) {
      moveSelected();
    } else if (c == removeCommand) {
      removeSelected();
    }
  }

  private String[] selectedPin() {
    int index = getSelectedIndex();
    if (index < 0 || index >= entries.size()) {
      return null;
    }
    return (String[]) entries.elementAt(index);
  }

  private void select() {
    Vector rows = new Vector();
    for (int i = 0; i < entries.size(); i++) {
      rows.addElement(
          new Object[] {PinterestMultiSelectScreen.TYPE_PIN, entries.elementAt(i)});
    }
    navigator.forward(
        new PinterestMultiSelectScreen(
            navigator,
            PinterestMultiSelectScreen.MODE_FAVORITES,
            Lang.tr("pinterest.select"),
            rows,
            new Runnable() {
              public void run() {
                reload();
              }
            }));
  }

  private void shareSelected() {
    String[] item = selectedPin();
    if (item == null) {
      return;
    }
    Sharing.toMPGram(this, PinterestApi.pinLabel(item) + "\n" + PinterestApi.pinShareUrl(item));
  }

  private void downloadSelected() {
    final String[] item = selectedPin();
    if (item == null || loading) {
      return;
    }
    final String source = imageUrl(item);
    if (source.length() == 0) {
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
            PinterestMultiSelectScreen.pinFileName(item),
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

  private void moveSelected() {
    final String[] item = selectedPin();
    if (item == null) {
      return;
    }
    navigator.forward(
        new PinterestFolderPickerScreen(
            navigator,
            Lang.tr("pinterest.move_to_folder"),
            new PinterestFolderPickerScreen.Listener() {
              public void onFolderSelected(String target) {
                if (target != null && target.equals(folderId)) {
                  return;
                }
                PinterestFavoritesManager.getInstance()
                    .setPinFolder(item[PinterestApi.PIN_ID], target);
                reload();
                navigator.showAlert(
                    Lang.tr("pinterest.moved_one"), AlertType.CONFIRMATION);
              }
            }));
  }

  private void removeSelected() {
    final String[] item = selectedPin();
    if (item == null) {
      return;
    }
    navigator.showConfirmationAlert(
        Lang.tr("pinterest.remove_confirm"),
        new Runnable() {
          public void run() {
            PinterestFavoritesManager.getInstance().removePin(item[PinterestApi.PIN_ID]);
            reload();
            navigator.showAlert(Lang.tr("pinterest.removed_saved"), AlertType.CONFIRMATION);
          }
        },
        AlertType.WARNING);
  }

  static String imageUrl(String[] pin) {
    String large = pin[PinterestApi.PIN_IMAGE_LARGE];
    if (large == null || large.length() == 0) {
      large = pin[PinterestApi.PIN_IMAGE];
    }
    return large == null ? "" : large;
  }
}
