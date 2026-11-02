package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import midplay.MIDPlay;
import midplay.net.BinaryImageLoadOperation;
import midplay.net.RedditApi;
import midplay.net.RedditImageDownloadOperation;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;
import midplay.util.Utils;

/** Full-screen viewer for the images of a Reddit gallery post. */
public final class RedditGalleryScreen extends Canvas implements CommandListener {
  private final Navigator navigator;
  private final String[] urls;
  private final String title;
  private final Command prevCommand;
  private final Command nextCommand;
  private final Command downloadCommand;
  private final Image[] cache;
  private int index;
  private int loadToken;
  private String error;
  private boolean visible = true;
  private boolean downloading;

  public RedditGalleryScreen(Navigator navigator, String title, String[] urls, int start) {
    this.navigator = navigator;
    this.title = title == null ? "" : title;
    this.urls = urls == null ? new String[0] : urls;
    this.cache = new Image[this.urls.length];
    this.index = start < 0 || start >= this.urls.length ? 0 : start;
    setTitle(buildTitle());
    prevCommand = new Command(Lang.tr("wattpad.prev_page"), Command.SCREEN, 1);
    nextCommand = new Command(Lang.tr("wattpad.next_page"), Command.SCREEN, 2);
    downloadCommand = new Command(Lang.tr("reddit.download"), Command.SCREEN, 3);
    addCommand(Commands.back());
    addCommand(downloadCommand);
    setCommandListener(this);
    updateNavCommands();
    load();
  }

  public void commandAction(Command command, Displayable displayable) {
    if (command == Commands.back()) {
      navigator.back();
    } else if (command == prevCommand) {
      show(index - 1);
    } else if (command == nextCommand) {
      show(index + 1);
    } else if (command == downloadCommand) {
      download();
    }
  }

  protected void paint(Graphics graphics) {
    graphics.setColor(0x000000);
    graphics.fillRect(0, 0, getWidth(), getHeight());
    Image image = cache.length == 0 ? null : cache[index];
    if (image != null) {
      graphics.drawImage(
          image, getWidth() / 2, getHeight() / 2, Graphics.HCENTER | Graphics.VCENTER);
    } else {
      graphics.setColor(0xFFFFFF);
      String message = error == null ? Lang.tr("reddit.image_loading") : error;
      graphics.drawString(
          message, getWidth() / 2, getHeight() / 2, Graphics.HCENTER | Graphics.BASELINE);
    }
  }

  protected void showNotify() {
    visible = true;
    repaint();
  }

  protected void hideNotify() {
    visible = false;
  }

  private void show(int target) {
    if (target < 0 || target >= urls.length) {
      return;
    }
    index = target;
    setTitle(buildTitle());
    updateNavCommands();
    error = null;
    if (cache[index] == null) {
      load();
    } else {
      repaint();
    }
  }

  private void updateNavCommands() {
    if (index > 0) {
      addCommand(prevCommand);
    } else {
      removeCommand(prevCommand);
    }
    if (index + 1 < urls.length) {
      addCommand(nextCommand);
    } else {
      removeCommand(nextCommand);
    }
  }

  private String buildTitle() {
    String suffix = urls.length > 1 ? " " + (index + 1) + "/" + urls.length : "";
    return title.length() == 0 ? Lang.tr("reddit.gallery") + suffix : title + suffix;
  }

  private void load() {
    if (urls.length == 0) {
      error = Lang.tr("reddit.image_error");
      repaint();
      return;
    }
    final int token = ++loadToken;
    final int requested = index;
    String url = urls[requested];
    if (url == null || url.length() == 0) {
      error = Lang.tr("reddit.image_error");
      repaint();
      return;
    }
    MIDPlay.startOperation(
        new BinaryImageLoadOperation(
            RedditApi.imageUrl(url),
            new BinaryImageLoadOperation.Listener() {
              public void onImageLoaded(final Image image) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        if (token != loadToken) {
                          return;
                        }
                        cache[requested] =
                            Utils.resizeImageToFitInside(image, getWidth(), getHeight());
                        if (visible && requested == index) {
                          repaint();
                        }
                      }
                    });
              }

              public void onImageLoadError(Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        if (token == loadToken) {
                          error = Lang.tr("reddit.image_error");
                          repaint();
                        }
                      }
                    });
              }
            }));
  }

  private void download() {
    if (downloading || urls.length == 0) {
      return;
    }
    final String source = urls[index];
    if (source == null || source.length() == 0) {
      navigator.showAlert(Lang.tr("reddit.download_error"), AlertType.ERROR);
      return;
    }
    downloading = true;
    navigator.showLoadingAlert(
        Lang.tr("reddit.downloading"),
        new Runnable() {
          public void run() {
            downloading = false;
          }
        });
    MIDPlay.startOperation(
        new RedditImageDownloadOperation(
            source,
            "reddit_gallery_" + (index + 1) + ".jpg",
            new RedditImageDownloadOperation.Listener() {
              public void onDownloaded(final String path) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        downloading = false;
                        navigator.dismissAlert();
                        navigator.showAlert(
                            Lang.tr("reddit.downloaded", path), AlertType.CONFIRMATION);
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        downloading = false;
                        navigator.dismissAlert();
                        navigator.showAlert(
                            Lang.tr("reddit.download_error"), AlertType.ERROR);
                      }
                    });
              }
            }));
  }
}
