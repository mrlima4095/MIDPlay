package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.ImageItem;
import javax.microedition.lcdui.StringItem;
import midplay.MIDPlay;
import midplay.net.BinaryImageLoadOperation;
import midplay.net.HttpTextOperation;
import midplay.net.PinterestApi;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.ui.Sharing;
import midplay.util.Lang;
import midplay.util.Utils;

/** Public Pinterest profile with entry points to the user's pins and boards. */
public final class PinterestUserScreen extends BaseForm {
  private final String username;
  private final ImageItem avatarItem = new ImageItem(null, null, ImageItem.LAYOUT_CENTER, null);
  private final StringItem content = new StringItem(null, "");
  private final Command pinsCommand;
  private final Command boardsCommand;
  private final Command shareCommand;
  private String[] profile;
  private boolean loading;

  public PinterestUserScreen(Navigator navigator, String username) {
    super(Lang.tr("pinterest.user"), navigator);
    this.username = username == null ? "" : username;
    append(avatarItem);
    append(content);
    content.setText(Lang.tr("pinterest.loading"));
    pinsCommand = new Command(Lang.tr("pinterest.pins"), Command.SCREEN, 1);
    boardsCommand = new Command(Lang.tr("pinterest.boards"), Command.SCREEN, 2);
    shareCommand = new Command(Lang.tr("pinterest.share"), Command.SCREEN, 3);
    addCommand(pinsCommand);
    addCommand(boardsCommand);
    addCommand(Commands.playerNowPlaying());
    loadProfile();
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == Commands.playerNowPlaying()) {
      PlayerNavHelper.showNowPlaying(navigator);
    } else if (c == pinsCommand) {
      loadItems(false);
    } else if (c == boardsCommand) {
      loadItems(true);
    } else if (c == shareCommand) {
      share();
    }
  }

  private void share() {
    String name = username;
    if (profile != null) {
      String display = profile[PinterestApi.USER_NAME];
      if (display != null && display.length() > 0) {
        name = display + " (@" + username + ")";
      }
    }
    Sharing.toMPGram(this, name + "\n" + PinterestApi.userWebUrl(username));
  }

  private void loadProfile() {
    loading = true;
    navigator.showLoadingAlert(Lang.tr("pinterest.loading"));
    MIDPlay.startOperation(
        new HttpTextOperation(
            PinterestApi.userUrl(username),
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        String error = PinterestApi.parseError(text);
                        if (error.length() > 0) {
                          content.setText(error);
                          navigator.showAlert(error, AlertType.ERROR);
                          return;
                        }
                        String[] user = PinterestApi.parseUser(text);
                        if (user == null) {
                          content.setText(Lang.tr("pinterest.user_not_found"));
                          navigator.showAlert(
                              Lang.tr("pinterest.user_not_found"), AlertType.ERROR);
                          return;
                        }
                        profile = user;
                        String name = user[PinterestApi.USER_NAME];
                        if (name != null && name.length() > 0) {
                          setTitle(name);
                        }
                        content.setText(PinterestApi.userDetails(user));
                        addCommand(shareCommand);
                        loadAvatar(user[PinterestApi.USER_IMAGE]);
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        content.setText(Lang.tr("pinterest.error"));
                        navigator.showAlert(e.toString(), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private void loadAvatar(String source) {
    if (source == null || source.length() == 0) {
      return;
    }
    MIDPlay.startOperation(
        new BinaryImageLoadOperation(
            PinterestApi.imageUrl(source),
            new BinaryImageLoadOperation.Listener() {
              public void onImageLoaded(final Image image) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        avatarItem.setImage(Utils.resizeImageToFitInside(image, 80, 80));
                      }
                    });
              }

              public void onImageLoadError(Exception e) {
                // The textual profile is still shown when the avatar is unavailable.
              }
            }));
  }

  private void loadItems(final boolean boards) {
    if (loading) {
      return;
    }
    loading = true;
    navigator.showLoadingAlert(
        Lang.tr("pinterest.loading"),
        new Runnable() {
          public void run() {
            loading = false;
          }
        });
    String url =
        boards
            ? PinterestApi.userBoardsUrl(username, "")
            : PinterestApi.userPinsUrl(username, "");
    MIDPlay.startOperation(
        new HttpTextOperation(
            url,
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
                        java.util.Vector items =
                            boards ? PinterestApi.parseBoards(text) : PinterestApi.parsePins(text);
                        if (items.size() == 0) {
                          navigator.dismissAlert();
                          navigator.showAlert(Lang.tr("pinterest.empty"), AlertType.INFO);
                          return;
                        }
                        navigator.forward(
                            new PinterestResultsScreen(
                                navigator,
                                boards
                                    ? PinterestResultsScreen.SOURCE_USER_BOARDS
                                    : PinterestResultsScreen.SOURCE_USER_PINS,
                                username,
                                "",
                                Lang.tr(
                                    boards ? "pinterest.user_boards" : "pinterest.user_pins",
                                    username),
                                items,
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
