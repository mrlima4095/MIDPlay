package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.StringItem;
import midplay.MIDPlay;
import midplay.net.SpeedTestOperation;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;

/** Hidden network speed test, reachable only by typing {@code speedtest} in the Open... screen. */
public final class SpeedTestScreen extends BaseForm {
  /** Last successful result, kept in memory for the current application session. */
  private static boolean hasLastResult;

  private static long lastPingMs;
  private static long lastDownloadBps;
  private static long lastUploadBps;

  private final StringItem statusItem;
  private final StringItem pingItem;
  private final StringItem downloadItem;
  private final StringItem uploadItem;
  private final Command startCommand;
  private final Command runCommand;
  private boolean running;
  private int token;

  public SpeedTestScreen(Navigator navigator) {
    super(Lang.tr("speedtest.title"), navigator);
    statusItem = new StringItem(null, Lang.tr("speedtest.waiting"));
    pingItem = new StringItem(null, value("speedtest.ping", Lang.tr("speedtest.waiting")));
    downloadItem =
        new StringItem(null, value("speedtest.download", Lang.tr("speedtest.waiting")));
    uploadItem = new StringItem(null, value("speedtest.upload", Lang.tr("speedtest.waiting")));
    append(statusItem);
    append(pingItem);
    append(downloadItem);
    append(uploadItem);
    startCommand = new Command(Lang.tr("speedtest.start"), Command.SCREEN, 1);
    runCommand = new Command(Lang.tr("speedtest.run"), Command.SCREEN, 1);
    addCommand(hasLastResult ? runCommand : startCommand);
    addCommand(Commands.playerNowPlaying());
    if (hasLastResult) {
      // Reopening the screen shows the last result of this session instead of
      // running the test again.
      showLastResult(Lang.tr("speedtest.last"));
    } else {
      // Some MIDP runtimes used with MIDPlay do not deliver
      // Displayable.showNotify(), so the first test starts right here.
      start();
    }
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == startCommand || c == runCommand) {
      start();
    } else if (c == Commands.playerNowPlaying()) {
      PlayerNavHelper.showNowPlaying(navigator);
    }
  }

  protected boolean onBackPressed() {
    if (running) {
      running = false;
      token++;
      MIDPlay.cancelOperation();
    }
    return false;
  }

  private void start() {
    final int myToken = ++token;
    running = true;
    statusItem.setText(Lang.tr("speedtest.phase_ping"));
    resetValues();
    MIDPlay.startOperation(
        new SpeedTestOperation(
            new SpeedTestOperation.Listener() {
              public void onProgress(
                  final int phase, final long bytes, final long elapsedMs) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        if (myToken != token || !running) {
                          return;
                        }
                        String label =
                            phase == SpeedTestOperation.PHASE_DOWNLOAD
                                ? Lang.tr("speedtest.phase_download")
                                : Lang.tr("speedtest.phase_upload");
                        statusItem.setText(label + "  " + format(rate(bytes, elapsedMs)));
                      }
                    });
              }

              public void onResult(
                  final long pingMs, final long downloadBps, final long uploadBps) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        if (myToken != token) {
                          return;
                        }
                        running = false;
                        lastPingMs = pingMs;
                        lastDownloadBps = downloadBps;
                        lastUploadBps = uploadBps;
                        hasLastResult = true;
                        showLastResult(Lang.tr("speedtest.done"));
                        removeCommand(startCommand);
                        addCommand(runCommand);
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        if (myToken != token) {
                          return;
                        }
                        running = false;
                        resetValues();
                        statusItem.setText(Lang.tr("speedtest.error"));
                        removeCommand(startCommand);
                        addCommand(runCommand);
                        navigator.showAlert(Lang.tr("speedtest.error"), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private void resetValues() {
    pingItem.setText(value("speedtest.ping", Lang.tr("speedtest.waiting")));
    downloadItem.setText(value("speedtest.download", Lang.tr("speedtest.waiting")));
    uploadItem.setText(value("speedtest.upload", Lang.tr("speedtest.waiting")));
  }

  private void showLastResult(String status) {
    pingItem.setText(value("speedtest.ping", lastPingMs + " ms"));
    downloadItem.setText(value("speedtest.download", format(lastDownloadBps)));
    uploadItem.setText(value("speedtest.upload", format(lastUploadBps)));
    statusItem.setText(status);
  }

  private static long rate(long bytes, long elapsedMs) {
    if (elapsedMs < 1) {
      elapsedMs = 1;
    }
    return bytes * 8000L / elapsedMs;
  }

  private static String value(String label, String text) {
    return Lang.tr(label) + ": " + text;
  }

  /** Bits per second as "1.2 Mbps" or "245.7 kbps". */
  private static String format(long bps) {
    if (bps >= 1000000L) {
      long tenths = (bps + 50000L) / 100000L;
      return (tenths / 10) + "." + (tenths % 10) + " Mbps";
    }
    long tenths = (bps + 50L) / 100L;
    return (tenths / 10) + "." + (tenths % 10) + " kbps";
  }
}
