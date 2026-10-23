package midplay.ui.screen;

import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import midplay.MIDPlay;
import midplay.net.PinterestBoardImportOperation;
import midplay.store.PinterestFavoritesManager;
import midplay.ui.Navigator;
import midplay.util.Lang;
import midplay.util.Utils;

/**
 * Shared "save a whole online Pinterest board into a local folder" flow, used by the board overview
 * and by the board results list. It asks for the destination folder, imports every pin page and then
 * stores the pins locally, reporting how many were saved.
 */
public final class PinterestBoardImportHelper {
  private PinterestBoardImportHelper() {}

  public static void saveWholeBoard(
      final Navigator navigator, final String owner, final String slug, final String boardId) {
    if (owner == null || owner.length() == 0 || slug == null || slug.length() == 0) {
      navigator.showAlert(Lang.tr("pinterest.empty"), AlertType.INFO);
      return;
    }
    navigator.forward(
        new PinterestFolderPickerScreen(
            navigator,
            Lang.tr("pinterest.save_whole_board"),
            new PinterestFolderPickerScreen.Listener() {
              public void onFolderSelected(final String folderId) {
                importAndSave(navigator, owner, slug, boardId, folderId);
              }
            }));
  }

  private static void importAndSave(
      final Navigator navigator,
      final String owner,
      final String slug,
      final String boardId,
      final String folderId) {
    navigator.showLoadingAlert(Lang.tr("pinterest.importing"));
    MIDPlay.startOperation(
        new PinterestBoardImportOperation(
            owner,
            slug,
            boardId,
            new PinterestBoardImportOperation.Listener() {
              public void onImported(final Vector pins, final boolean truncated) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        navigator.dismissAlert();
                        if (pins.size() == 0) {
                          navigator.showAlert(
                              Lang.tr("pinterest.import_empty"), AlertType.INFO);
                          return;
                        }
                        int added =
                            PinterestFavoritesManager.getInstance().savePins(pins, folderId);
                        String folder =
                            PinterestFavoritesManager.getInstance().getFolderName(folderId);
                        if (folder.length() == 0) {
                          folder = Lang.tr("pinterest.loose_favorites");
                        }
                        String message = Lang.tr("pinterest.imported_many");
                        message = Utils.replace(message, "{0}", String.valueOf(added));
                        message = Utils.replace(message, "{1}", String.valueOf(pins.size()));
                        message = Utils.replace(message, "{2}", folder);
                        if (truncated) {
                          message = Lang.tr("pinterest.import_truncated", message);
                        }
                        navigator.showAlert(message, AlertType.CONFIRMATION);
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        navigator.dismissAlert();
                        navigator.showAlert(
                            Lang.tr("pinterest.import_error"), AlertType.ERROR);
                      }
                    });
              }
            }));
  }
}
