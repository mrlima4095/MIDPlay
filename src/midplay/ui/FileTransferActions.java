package midplay.ui;

import javax.microedition.lcdui.AlertType;
import midplay.util.FileTransfer;
import midplay.util.Lang;

/** Runs a file copy/move on a worker thread with a cancellable loading alert. */
public final class FileTransferActions {
  public interface Listener {
    void onFinished(FileTransfer.Result result);
  }

  public static void start(
      final Navigator navigator,
      final String[] sources,
      final String destination,
      final boolean move,
      final Listener listener) {
    if (navigator == null || listener == null) {
      return;
    }
    final FileTransfer.CancelSignal signal = new FileTransfer.CancelSignal();
    navigator.showLoadingAlert(
        Lang.tr(move ? "files.moving" : "files.copying"),
        new Runnable() {
          public void run() {
            signal.cancel();
          }
        });
    new Thread(
            new Runnable() {
              public void run() {
                final FileTransfer.Result result =
                    move
                        ? FileTransfer.moveMany(sources, destination, signal)
                        : FileTransfer.copyMany(sources, destination, signal);
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        navigator.dismissAlert();
                        // Let the alert restore the screen underneath before the caller reacts.
                        navigator.callSerially(
                            new Runnable() {
                              public void run() {
                                listener.onFinished(result);
                              }
                            });
                      }
                    });
              }
            })
        .start();
  }

  public static AlertType resultType(FileTransfer.Result result) {
    return result != null && (result.cancelled || result.failed > 0)
        ? AlertType.WARNING
        : AlertType.CONFIRMATION;
  }

  private FileTransferActions() {}
}
