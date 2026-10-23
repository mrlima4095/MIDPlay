package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.ImageItem;
import javax.microedition.lcdui.StringItem;
import midplay.MIDPlay;
import midplay.net.BinaryImageLoadOperation;
import midplay.net.PinterestApi;
import midplay.net.PinterestImageDownloadOperation;
import midplay.store.PinterestFavoritesManager;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.ui.Sharing;
import midplay.util.Lang;
import midplay.util.Utils;

/** Details of one Pinterest pin, with its image and links to the author and board. */
public final class PinterestPinScreen extends BaseForm {
  private final String[] pin;
  private final ImageItem imageItem = new ImageItem(null, null, ImageItem.LAYOUT_CENTER, null);
  private final StringItem content = new StringItem(null, "");
  private final Command imageCommand;
  private final Command authorCommand;
  private final Command boardCommand;
  private final Command saveCommand;
  private final Command removeSavedCommand;
  private final Command shareCommand;
  private final Command downloadCommand;
  private boolean loading;

  public PinterestPinScreen(Navigator navigator, String[] pin) {
    super(Lang.tr("pinterest.pin"), navigator);
    this.pin = pin == null ? new String[PinterestApi.PIN_SIZE] : pin;
    for (int i = 0; i < this.pin.length; i++) {
      if (this.pin[i] == null) {
        this.pin[i] = "";
      }
    }
    append(imageItem);
    content.setText(PinterestApi.pinDetails(this.pin));
    append(content);
    imageCommand = new Command(Lang.tr("pinterest.view_image"), Command.SCREEN, 1);
    authorCommand = new Command(Lang.tr("pinterest.author"), Command.SCREEN, 2);
    boardCommand = new Command(Lang.tr("pinterest.board"), Command.SCREEN, 3);
    saveCommand = new Command(Lang.tr("pinterest.save"), Command.SCREEN, 4);
    removeSavedCommand = new Command(Lang.tr("pinterest.remove_saved"), Command.SCREEN, 4);
    shareCommand = new Command(Lang.tr("pinterest.share"), Command.SCREEN, 5);
    downloadCommand = new Command(Lang.tr("pinterest.download"), Command.SCREEN, 6);
    if (imageUrl().length() > 0) {
      addCommand(imageCommand);
    }
    if (this.pin[PinterestApi.PIN_USER].length() > 0) {
      addCommand(authorCommand);
    }
    if (this.pin[PinterestApi.PIN_BOARD_USER].length() > 0
        && this.pin[PinterestApi.PIN_BOARD_SLUG].length() > 0) {
      addCommand(boardCommand);
    }
    if (this.pin[PinterestApi.PIN_ID].length() > 0) {
      addCommand(shareCommand);
      updateSaveCommands();
    }
    if (imageUrl().length() > 0) {
      addCommand(downloadCommand);
    }
    addCommand(Commands.playerNowPlaying());
    loadImage();
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == Commands.playerNowPlaying()) {
      PlayerNavHelper.showNowPlaying(navigator);
    } else if (c == imageCommand) {
      navigator.forward(new PinterestImageViewer(navigator, Lang.tr("pinterest.pin"), imageUrl()));
    } else if (c == authorCommand) {
      navigator.forward(new PinterestUserScreen(navigator, pin[PinterestApi.PIN_USER]));
    } else if (c == boardCommand) {
      String[] board = new String[PinterestApi.BOARD_SIZE];
      board[PinterestApi.BOARD_NAME] = pin[PinterestApi.PIN_BOARD_NAME];
      board[PinterestApi.BOARD_USER] = pin[PinterestApi.PIN_BOARD_USER];
      board[PinterestApi.BOARD_SLUG] = pin[PinterestApi.PIN_BOARD_SLUG];
      navigator.forward(new PinterestBoardScreen(navigator, board));
    } else if (c == saveCommand) {
      save();
    } else if (c == removeSavedCommand) {
      PinterestFavoritesManager.getInstance().removePin(pin[PinterestApi.PIN_ID]);
      updateSaveCommands();
      navigator.showAlert(Lang.tr("pinterest.removed_saved"), AlertType.CONFIRMATION);
    } else if (c == shareCommand) {
      share();
    } else if (c == downloadCommand) {
      downloadPin();
    }
  }

  private void updateSaveCommands() {
    if (PinterestFavoritesManager.getInstance().isPinSaved(pin[PinterestApi.PIN_ID])) {
      removeCommand(saveCommand);
      addCommand(removeSavedCommand);
    } else {
      removeCommand(removeSavedCommand);
      addCommand(saveCommand);
    }
  }

  private void save() {
    navigator.forward(
        new PinterestFolderPickerScreen(
            navigator,
            Lang.tr("pinterest.save_to_folder"),
            new PinterestFolderPickerScreen.Listener() {
              public void onFolderSelected(String folderId) {
                int result = PinterestFavoritesManager.getInstance().savePin(pin, folderId);
                updateSaveCommands();
                if (result == PinterestFavoritesManager.ALREADY_EXISTS) {
                  // The pin was already saved; it may have been moved into the chosen folder.
                  String folder = PinterestFavoritesManager.getInstance().getFolderName(folderId);
                  navigator.showAlert(
                      folder.length() == 0
                          ? Lang.tr("pinterest.added_to_loose")
                          : Lang.tr("pinterest.added_to_folder", folder),
                      AlertType.CONFIRMATION);
                } else if (result == PinterestFavoritesManager.SUCCESS) {
                  navigator.showAlert(Lang.tr("pinterest.saved_pin"), AlertType.CONFIRMATION);
                } else {
                  navigator.showAlert(Lang.tr("pinterest.save_error"), AlertType.ERROR);
                }
              }
            }));
  }

  private void share() {
    Sharing.toMPGram(this, PinterestApi.pinLabel(pin) + "\n" + PinterestApi.pinShareUrl(pin));
  }

  private void downloadPin() {
    if (loading) {
      return;
    }
    final String source = imageUrl();
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
            "pin_" + pin[PinterestApi.PIN_ID] + ".jpg",
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

  private String imageUrl() {
    String large = pin[PinterestApi.PIN_IMAGE_LARGE];
    if (large == null || large.length() == 0) {
      large = pin[PinterestApi.PIN_IMAGE];
    }
    return large == null ? "" : large;
  }

  private void loadImage() {
    String url = imageUrl();
    if (url.length() == 0) {
      return;
    }
    MIDPlay.startOperation(
        new BinaryImageLoadOperation(
            PinterestApi.imageUrl(url),
            new BinaryImageLoadOperation.Listener() {
              public void onImageLoaded(final Image image) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        imageItem.setImage(Utils.resizeImageToFitInside(image, 180, 220));
                      }
                    });
              }

              public void onImageLoadError(Exception e) {
                // The text details are still useful when the image server is unavailable.
              }
            }));
  }
}
