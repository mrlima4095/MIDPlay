package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.StringItem;
import midplay.MIDPlay;
import midplay.net.HttpTextOperation;
import midplay.net.PinterestApi;
import midplay.store.PinterestFavoritesManager;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.ui.Sharing;
import midplay.util.Lang;

/** Overview of a Pinterest board with access to its pins and owner. */
public final class PinterestBoardScreen extends BaseForm {
  private final String[] board;
  private final StringItem content = new StringItem(null, "");
  private final Command pinsCommand;
  private final Command ownerCommand;
  private final Command saveCommand;
  private final Command removeSavedCommand;
  private final Command saveAllCommand;
  private final Command shareCommand;
  private boolean loading;

  public PinterestBoardScreen(Navigator navigator, String[] board) {
    super(Lang.tr("pinterest.board"), navigator);
    this.board = board == null ? new String[PinterestApi.BOARD_SIZE] : board;
    for (int i = 0; i < this.board.length; i++) {
      if (this.board[i] == null) {
        this.board[i] = "";
      }
    }
    String name = this.board[PinterestApi.BOARD_NAME];
    if (name.length() > 0) {
      setTitle(name);
    }
    content.setText(PinterestApi.boardDetails(this.board));
    append(content);
    pinsCommand = new Command(Lang.tr("pinterest.view_pins"), Command.SCREEN, 1);
    ownerCommand = new Command(Lang.tr("pinterest.board_owner"), Command.SCREEN, 2);
    saveCommand = new Command(Lang.tr("pinterest.save"), Command.SCREEN, 3);
    removeSavedCommand = new Command(Lang.tr("pinterest.remove_saved"), Command.SCREEN, 3);
    saveAllCommand = new Command(Lang.tr("pinterest.save_whole_board"), Command.SCREEN, 5);
    shareCommand = new Command(Lang.tr("pinterest.share"), Command.SCREEN, 4);
    if (this.board[PinterestApi.BOARD_USER].length() > 0
        && this.board[PinterestApi.BOARD_SLUG].length() > 0) {
      addCommand(pinsCommand);
    }
    if (this.board[PinterestApi.BOARD_USER].length() > 0) {
      addCommand(ownerCommand);
    }
    if (this.board[PinterestApi.BOARD_USER].length() > 0
        && this.board[PinterestApi.BOARD_SLUG].length() > 0) {
      addCommand(shareCommand);
      addCommand(saveAllCommand);
    }
    if (PinterestFavoritesManager.getInstance().isBoardSaved(this.board[PinterestApi.BOARD_ID])
        || this.board[PinterestApi.BOARD_ID].length() > 0) {
      updateSaveCommands();
    }
    addCommand(Commands.playerNowPlaying());
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == Commands.playerNowPlaying()) {
      PlayerNavHelper.showNowPlaying(navigator);
    } else if (c == pinsCommand) {
      loadPins();
    } else if (c == ownerCommand) {
      navigator.forward(
          new PinterestUserScreen(navigator, board[PinterestApi.BOARD_USER]));
    } else if (c == saveCommand) {
      save();
    } else if (c == removeSavedCommand) {
      PinterestFavoritesManager.getInstance().removeBoard(board[PinterestApi.BOARD_ID]);
      updateSaveCommands();
      navigator.showAlert(Lang.tr("pinterest.removed_saved"), AlertType.CONFIRMATION);
    } else if (c == shareCommand) {
      share();
    } else if (c == saveAllCommand) {
      saveWholeBoard();
    }
  }

  private void saveWholeBoard() {
    PinterestBoardImportHelper.saveWholeBoard(
        navigator,
        board[PinterestApi.BOARD_USER],
        board[PinterestApi.BOARD_SLUG],
        board[PinterestApi.BOARD_ID]);
  }

  private void updateSaveCommands() {
    if (PinterestFavoritesManager.getInstance().isBoardSaved(board[PinterestApi.BOARD_ID])) {
      removeCommand(saveCommand);
      addCommand(removeSavedCommand);
    } else {
      removeCommand(removeSavedCommand);
      addCommand(saveCommand);
    }
  }

  private void save() {
    int result = PinterestFavoritesManager.getInstance().saveBoard(board);
    updateSaveCommands();
    if (result == PinterestFavoritesManager.ALREADY_EXISTS) {
      navigator.showAlert(Lang.tr("pinterest.already_saved"), AlertType.INFO);
    } else if (result == PinterestFavoritesManager.SUCCESS) {
      navigator.showAlert(Lang.tr("pinterest.saved_board"), AlertType.CONFIRMATION);
    } else {
      navigator.showAlert(Lang.tr("pinterest.save_error"), AlertType.ERROR);
    }
  }

  private void share() {
    Sharing.toMPGram(
        this,
        PinterestApi.boardLabel(board)
            + "\n"
            + PinterestApi.boardWebUrl(
                board[PinterestApi.BOARD_USER], board[PinterestApi.BOARD_SLUG]));
  }

  private void loadPins() {
    if (loading) {
      return;
    }
    final String owner = board[PinterestApi.BOARD_USER];
    final String slug = board[PinterestApi.BOARD_SLUG];
    loading = true;
    navigator.showLoadingAlert(
        Lang.tr("pinterest.loading"),
        new Runnable() {
          public void run() {
            loading = false;
          }
        });
    MIDPlay.startOperation(
        new HttpTextOperation(
            PinterestApi.boardPinsUrl(owner, slug, board[PinterestApi.BOARD_ID], ""),
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        String error = PinterestApi.parseError(text);
                        if (error.length() > 0) {
                          navigator.dismissAlert();
                          navigator.showAlert(error, AlertType.ERROR);
                          return;
                        }
                        java.util.Vector pins = PinterestApi.parsePins(text);
                        if (pins.size() == 0) {
                          navigator.dismissAlert();
                          navigator.showAlert(Lang.tr("pinterest.empty"), AlertType.INFO);
                          return;
                        }
                        String title = board[PinterestApi.BOARD_NAME];
                        if (title.length() == 0) {
                          title = Lang.tr("pinterest.board_pins");
                        }
                        navigator.forward(
                            new PinterestResultsScreen(
                                navigator,
                                PinterestResultsScreen.SOURCE_BOARD_PINS,
                                owner,
                                slug,
                                board[PinterestApi.BOARD_ID],
                                title,
                                pins,
                                PinterestApi.parseBookmark(text)));
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        navigator.showAlert(e.toString(), AlertType.ERROR);
                      }
                    });
              }
            }));
  }
}
