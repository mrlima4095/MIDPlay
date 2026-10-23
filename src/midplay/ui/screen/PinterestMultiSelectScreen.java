package midplay.ui.screen;

import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import midplay.MIDPlay;
import midplay.net.PinterestApi;
import midplay.net.PinterestImageDownloadOperation;
import midplay.store.Configuration;
import midplay.store.PinterestFavoritesManager;
import midplay.ui.BulkSelectList;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;

/**
 * Multiple-selection list used both for online results (save / download several items at once) and
 * for local favorites (remove / move several items at once). Items are passed as {@code Object[]}
 * pairs of {@code {type, row}} where {@code type} is one of {@link #TYPE_PIN},
 * {@link #TYPE_BOARD} or {@link #TYPE_FOLDER}.
 */
public final class PinterestMultiSelectScreen extends BulkSelectList {
  public static final int MODE_RESULTS = 0;
  public static final int MODE_FAVORITES = 1;

  public static final String TYPE_PIN = "pin";
  public static final String TYPE_BOARD = "board";
  public static final String TYPE_FOLDER = "folder";

  private final int mode;
  private final Vector rows;
  private final Runnable onChanged;
  private final Command saveCommand =
      new Command(Lang.tr("pinterest.save_selected"), Command.SCREEN, 3);
  private final Command downloadCommand =
      new Command(Lang.tr("pinterest.download_selected"), Command.SCREEN, 3);
  private final Command moveCommand =
      new Command(Lang.tr("pinterest.move_to_folder"), Command.SCREEN, 4);
  private final Command removeCommand =
      new Command(Lang.tr("pinterest.remove_selected"), Command.SCREEN, 5);
  private boolean downloading;

  public PinterestMultiSelectScreen(
      Navigator navigator, int mode, String title, Vector rows, Runnable onChanged) {
    super(
        navigator,
        title == null ? Lang.tr("pinterest.select") : title,
        "pinterest.select_all",
        "pinterest.deselect_all");
    this.mode = mode;
    this.rows = rows == null ? new Vector() : rows;
    this.onChanged = onChanged;
    for (int i = 0; i < this.rows.size(); i++) {
      Object[] entry = (Object[]) this.rows.elementAt(i);
      append(
          labelFor(entry),
          TYPE_FOLDER.equals(entry[0]) ? Configuration.folderIcon : null);
    }
    installBulkCommands();
    if (mode == MODE_RESULTS) {
      addCommand(saveCommand);
      addCommand(downloadCommand);
    } else {
      addCommand(downloadCommand);
      if (hasPins()) {
        addCommand(moveCommand);
      }
      addCommand(removeCommand);
    }
    addCommand(Commands.back());
    addCommand(Commands.playerNowPlaying());
    setCommandListener(this);
  }

  public void commandAction(Command c, Displayable d) {
    if (handleBack(c) || handleBulkCommand(c)) {
      return;
    }
    if (c == Commands.playerNowPlaying()) {
      PlayerNavHelper.showNowPlaying(navigator);
    } else if (c == saveCommand) {
      saveSelected();
    } else if (c == downloadCommand) {
      downloadSelected();
    } else if (c == moveCommand) {
      moveSelected();
    } else if (c == removeCommand) {
      removeSelected();
    }
  }

  private static String labelFor(Object[] entry) {
    String type = (String) entry[0];
    String[] item = (String[]) entry[1];
    if (TYPE_BOARD.equals(type)) {
      return Lang.tr("pinterest.saved_boards") + ": " + PinterestApi.boardLabel(item);
    }
    if (TYPE_FOLDER.equals(type)) {
      return item[PinterestFavoritesManager.FOLDER_NAME];
    }
    return PinterestApi.pinLabel(item);
  }

  private boolean hasPins() {
    for (int i = 0; i < rows.size(); i++) {
      if (TYPE_PIN.equals(((Object[]) rows.elementAt(i))[0])) {
        return true;
      }
    }
    return false;
  }

  private Vector selectedEntries() {
    boolean[] flags = selectedFlags();
    Vector result = new Vector();
    for (int i = 0; i < flags.length; i++) {
      if (flags[i]) {
        result.addElement(rows.elementAt(i));
      }
    }
    return result;
  }

  private static Vector collect(Vector entries, String type) {
    Vector result = new Vector();
    for (int i = 0; i < entries.size(); i++) {
      Object[] entry = (Object[]) entries.elementAt(i);
      if (type.equals(entry[0])) {
        result.addElement(entry[1]);
      }
    }
    return result;
  }

  // ---------------------------------------------------------------------------
  // Save (results)
  // ---------------------------------------------------------------------------

  private void saveSelected() {
    final Vector selected = selectedEntries();
    if (selected.size() == 0) {
      navigator.showAlert(Lang.tr("bulk.select_empty"), AlertType.INFO);
      return;
    }
    final Vector pins = collect(selected, TYPE_PIN);
    final Vector boards = collect(selected, TYPE_BOARD);
    if (pins.size() == 0) {
      int added = saveBoards(boards);
      navigator.showAlert(
          Lang.tr("pinterest.saved_many", String.valueOf(added)), AlertType.CONFIRMATION);
      return;
    }
    navigator.forward(
        new PinterestFolderPickerScreen(
            navigator,
            Lang.tr("pinterest.save_to_folder"),
            new PinterestFolderPickerScreen.Listener() {
              public void onFolderSelected(String folderId) {
                int added = PinterestFavoritesManager.getInstance().savePins(pins, folderId);
                added += saveBoards(boards);
                if (onChanged != null) {
                  onChanged.run();
                }
                navigator.back();
                navigator.showAlert(
                    Lang.tr("pinterest.saved_many", String.valueOf(added)),
                    AlertType.CONFIRMATION);
              }
            }));
  }

  private static int saveBoards(Vector boards) {
    int added = 0;
    for (int i = 0; i < boards.size(); i++) {
      if (PinterestFavoritesManager.getInstance().saveBoard((String[]) boards.elementAt(i))
          == PinterestFavoritesManager.SUCCESS) {
        added++;
      }
    }
    return added;
  }

  // ---------------------------------------------------------------------------
  // Download
  // ---------------------------------------------------------------------------

  private void downloadSelected() {
    if (downloading) {
      return;
    }
    final Vector selected = selectedEntries();
    final Vector jobs = new Vector();
    for (int i = 0; i < selected.size(); i++) {
      Object[] entry = (Object[]) selected.elementAt(i);
      String type = (String) entry[0];
      String[] item = (String[]) entry[1];
      if (TYPE_PIN.equals(type)) {
        String source = imageUrl(item);
        if (source.length() > 0) {
          jobs.addElement(new String[] {source, pinFileName(item)});
        }
      } else if (TYPE_BOARD.equals(type)) {
        String source = item[PinterestApi.BOARD_COVER];
        if (source != null && source.length() > 0) {
          jobs.addElement(new String[] {source, boardFileName(item)});
        }
      }
    }
    if (jobs.size() == 0) {
      navigator.showAlert(Lang.tr("pinterest.download_error"), AlertType.ERROR);
      return;
    }
    final int total = jobs.size();
    downloading = true;
    navigator.showLoadingAlert(
        Lang.tr("pinterest.downloading"),
        new Runnable() {
          public void run() {
            downloading = false;
          }
        });
    downloadNext(jobs, 0, 0, total);
  }

  private void downloadNext(final Vector jobs, final int index, final int done, final int total) {
    if (index >= jobs.size()) {
      downloading = false;
      navigator.dismissAlert();
      navigator.showAlert(
          Lang.tr("pinterest.downloaded_many", String.valueOf(done)), AlertType.CONFIRMATION);
      return;
    }
    String[] job = (String[]) jobs.elementAt(index);
    MIDPlay.startOperation(
        new PinterestImageDownloadOperation(
            job[0],
            job[1],
            new PinterestImageDownloadOperation.Listener() {
              public void onDownloaded(String path) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        downloadNext(jobs, index + 1, done + 1, total);
                      }
                    });
              }

              public void onError(Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        downloadNext(jobs, index + 1, done, total);
                      }
                    });
              }
            }));
  }

  // ---------------------------------------------------------------------------
  // Move (favorites)
  // ---------------------------------------------------------------------------

  private void moveSelected() {
    final Vector selected = selectedEntries();
    final Vector pins = collect(selected, TYPE_PIN);
    if (pins.size() == 0) {
      navigator.showAlert(Lang.tr("bulk.select_empty"), AlertType.INFO);
      return;
    }
    navigator.forward(
        new PinterestFolderPickerScreen(
            navigator,
            Lang.tr("pinterest.move_to_folder"),
            new PinterestFolderPickerScreen.Listener() {
              public void onFolderSelected(String folderId) {
                String[] ids = new String[pins.size()];
                for (int i = 0; i < pins.size(); i++) {
                  ids[i] = ((String[]) pins.elementAt(i))[PinterestApi.PIN_ID];
                }
                int moved = PinterestFavoritesManager.getInstance().setPinsFolder(ids, folderId);
                if (onChanged != null) {
                  onChanged.run();
                }
                navigator.back();
                navigator.showAlert(
                    Lang.tr("pinterest.moved_many", String.valueOf(moved)),
                    AlertType.CONFIRMATION);
              }
            }));
  }

  // ---------------------------------------------------------------------------
  // Remove (favorites)
  // ---------------------------------------------------------------------------

  private void removeSelected() {
    final Vector selected = selectedEntries();
    if (selected.size() == 0) {
      navigator.showAlert(Lang.tr("bulk.select_empty"), AlertType.INFO);
      return;
    }
    navigator.showConfirmationAlert(
        Lang.tr("pinterest.remove_selected_confirm", String.valueOf(selected.size())),
        new Runnable() {
          public void run() {
            PinterestFavoritesManager manager = PinterestFavoritesManager.getInstance();
            int removed = 0;
            Vector pins = collect(selected, TYPE_PIN);
            String[] pinIds = new String[pins.size()];
            for (int i = 0; i < pins.size(); i++) {
              pinIds[i] = ((String[]) pins.elementAt(i))[PinterestApi.PIN_ID];
            }
            removed += manager.removePins(pinIds);
            Vector boards = collect(selected, TYPE_BOARD);
            String[] boardIds = new String[boards.size()];
            for (int i = 0; i < boards.size(); i++) {
              boardIds[i] = ((String[]) boards.elementAt(i))[PinterestApi.BOARD_ID];
            }
            removed += manager.removeBoards(boardIds);
            Vector folders = collect(selected, TYPE_FOLDER);
            for (int i = 0; i < folders.size(); i++) {
              removed +=
                  manager.deleteFolder(
                      ((String[]) folders.elementAt(i))[PinterestFavoritesManager.FOLDER_ID]);
            }
            if (onChanged != null) {
              onChanged.run();
            }
            navigator.back();
            navigator.showAlert(
                Lang.tr("pinterest.removed_many", String.valueOf(removed)),
                AlertType.CONFIRMATION);
          }
        },
        AlertType.WARNING);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  static String imageUrl(String[] pin) {
    String large = pin[PinterestApi.PIN_IMAGE_LARGE];
    if (large == null || large.length() == 0) {
      large = pin[PinterestApi.PIN_IMAGE];
    }
    return large == null ? "" : large;
  }

  static String pinFileName(String[] pin) {
    return "pin_" + sanitizeId(pin[PinterestApi.PIN_ID]) + ".jpg";
  }

  static String boardFileName(String[] board) {
    return "board_" + sanitizeId(board[PinterestApi.BOARD_ID]) + ".jpg";
  }

  private static String sanitizeId(String value) {
    String id = value == null ? "" : value.trim();
    if (id.length() == 0) {
      id = "img";
    }
    StringBuffer sb = new StringBuffer(id.length());
    for (int i = 0; i < id.length() && sb.length() < 40; i++) {
      char c = id.charAt(i);
      if ((c >= 'a' && c <= 'z')
          || (c >= 'A' && c <= 'Z')
          || (c >= '0' && c <= '9')
          || c == '-'
          || c == '_') {
        sb.append(c);
      } else {
        sb.append('_');
      }
    }
    return sb.toString();
  }
}
