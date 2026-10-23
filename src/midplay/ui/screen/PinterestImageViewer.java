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
import midplay.net.PinterestApi;
import midplay.net.PinterestImageDownloadOperation;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;
import midplay.util.Utils;

/** Full-screen viewer for a pin image fetched through the Pinterest image proxy. */
public final class PinterestImageViewer extends Canvas implements CommandListener {
  private final Navigator navigator;
  private final String imageUrl;
  private final Command downloadCommand;
  private Image original;
  private Image scaled;
  private String error;
  private boolean visible = true;
  private boolean downloading;

  public PinterestImageViewer(Navigator navigator, String title, String imageUrl) {
    this.navigator = navigator;
    this.imageUrl = imageUrl == null ? "" : imageUrl;
    setTitle(title == null ? Lang.tr("pinterest.pin") : title);
    downloadCommand = new Command(Lang.tr("pinterest.download"), Command.SCREEN, 2);
    addCommand(Commands.back());
    addCommand(downloadCommand);
    setCommandListener(this);
    load();
  }

  public void commandAction(Command command, Displayable displayable) {
    if (command == Commands.back()) {
      navigator.back();
    } else if (command == downloadCommand) {
      download();
    }
  }

  protected void paint(Graphics graphics) {
    graphics.setColor(0x000000);
    graphics.fillRect(0, 0, getWidth(), getHeight());
    if (scaled != null) {
      graphics.drawImage(
          scaled, getWidth() / 2, getHeight() / 2, Graphics.HCENTER | Graphics.VCENTER);
    } else {
      graphics.setColor(0xFFFFFF);
      String message = error == null ? Lang.tr("pinterest.image_loading") : error;
      graphics.drawString(
          message, getWidth() / 2, getHeight() / 2, Graphics.HCENTER | Graphics.BASELINE);
    }
  }

  protected void sizeChanged(int width, int height) {
    scaleToCanvas();
  }

  protected void showNotify() {
    visible = true;
    scaleToCanvas();
  }

  protected void hideNotify() {
    visible = false;
  }

  private void download() {
    if (downloading) {
      return;
    }
    if (imageUrl.length() == 0) {
      navigator.showAlert(Lang.tr("pinterest.download_error"), AlertType.ERROR);
      return;
    }
    downloading = true;
    navigator.showLoadingAlert(
        Lang.tr("pinterest.downloading"),
        new Runnable() {
          public void run() {
            downloading = false;
          }
        });
    MIDPlay.startOperation(
        new PinterestImageDownloadOperation(
            imageUrl,
            fileName(),
            new PinterestImageDownloadOperation.Listener() {
              public void onDownloaded(final String path) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        downloading = false;
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
                        downloading = false;
                        navigator.dismissAlert();
                        navigator.showAlert(
                            Lang.tr("pinterest.download_error"), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private String fileName() {
    String value = imageUrl;
    int cut = value.indexOf('?');
    if (cut >= 0) {
      value = value.substring(0, cut);
    }
    cut = value.lastIndexOf('/');
    if (cut >= 0 && cut + 1 < value.length()) {
      value = value.substring(cut + 1);
    }
    if (value.length() == 0) {
      value = "pinterest.jpg";
    }
    return value;
  }

  private void load() {
    if (imageUrl.length() == 0) {
      error = Lang.tr("pinterest.image_error");
      return;
    }
    MIDPlay.startOperation(
        new BinaryImageLoadOperation(
            PinterestApi.imageUrl(imageUrl),
            new BinaryImageLoadOperation.Listener() {
              public void onImageLoaded(final Image image) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        original = image;
                        scaleToCanvas();
                      }
                    });
              }

              public void onImageLoadError(Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        error = Lang.tr("pinterest.image_error");
                        repaint();
                      }
                    });
              }
            }));
  }

  private void scaleToCanvas() {
    if (original == null || getWidth() <= 0 || getHeight() <= 0) {
      return;
    }
    scaled = Utils.resizeImageToFitInside(original, getWidth(), getHeight());
    if (visible) {
      repaint();
    }
  }
}
