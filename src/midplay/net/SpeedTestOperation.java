package midplay.net;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.TimerTask;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import midplay.store.Configuration;
import midplay.util.Utils;

/**
 * Measures round-trip latency, download throughput and upload throughput against the MIDPlay speed
 * test endpoint. Each phase is time-bounded, so the test finishes on slow links too.
 */
public class SpeedTestOperation extends NetworkOperation {
  public interface Listener {
    void onProgress(int phase, long bytes, long elapsedMs);

    void onResult(long pingMs, long downloadBps, long uploadBps);

    void onError(Exception e);
  }

  public static final int PHASE_PING = 1;
  public static final int PHASE_DOWNLOAD = 2;
  public static final int PHASE_UPLOAD = 3;

  private static final String UA = "MIDPlay/1.0 (MIDP-2.0; CLDC-1.1)";
  private static final int DOWNLOAD_BYTES = 4 * 1024 * 1024;
  private static final long DOWNLOAD_MS = 10000L;
  private static final int UPLOAD_BYTES = 1024 * 1024;
  private static final long UPLOAD_MS = 8000L;
  private static final int CHUNK = 16384;
  private static final long SOCKET_TIMEOUT_MS = 20000L;
  private static final long REPORT_MS = 250L;

  private final Listener listener;

  public SpeedTestOperation(Listener listener) {
    this.listener = listener;
  }

  protected void handleError(Exception e) {
    if (!isStopped()) {
      listener.onError(e);
    }
  }

  protected void execute() {
    try {
      long ping = measurePing();
      if (isStopped()) {
        return;
      }
      long download = measureDownload();
      if (isStopped()) {
        return;
      }
      long upload = measureUpload();
      if (isStopped()) {
        return;
      }
      listener.onResult(ping, download, upload);
    } catch (Exception e) {
      if (!isStopped()) {
        listener.onError(e);
      }
    }
  }

  private long measurePing() throws IOException {
    long best = -1;
    for (int attempt = 0; attempt < 3; attempt++) {
      if (isStopped()) {
        break;
      }
      HttpConnection hcon = null;
      DataInputStream in = null;
      TimerTask watchdog = null;
      long start = System.currentTimeMillis();
      try {
        hcon = Network.openConnection(url("op=ping"), Connector.READ);
        hcon.setRequestProperty("User-Agent", UA);
        hcon.setRequestMethod(HttpConnection.GET);
        watchdog = Network.armPlaybackWatchdog(hcon, SOCKET_TIMEOUT_MS);
        int code = hcon.getResponseCode();
        if (code != HttpConnection.HTTP_OK) {
          throw new IOException("HTTP " + code);
        }
        in = new DataInputStream(hcon.openInputStream());
        byte[] buffer = new byte[64];
        while (in.read(buffer) != -1) {
          if (isStopped()) {
            return best;
          }
        }
        long elapsed = System.currentTimeMillis() - start;
        if (best < 0 || elapsed < best) {
          best = elapsed;
        }
      } finally {
        if (watchdog != null) {
          watchdog.cancel();
        }
        Utils.closeQuietly(in);
        Utils.closeQuietly(hcon);
      }
      if (attempt < 2) {
        try {
          Thread.sleep(150);
        } catch (InterruptedException e) {
          break;
        }
      }
    }
    return best < 0 ? 0 : best;
  }

  private long measureDownload() throws IOException {
    HttpConnection hcon = null;
    DataInputStream in = null;
    TimerTask watchdog = null;
    long start = 0;
    long total = 0;
    try {
      hcon =
          Network.openConnection(
              url("op=download&bytes=" + DOWNLOAD_BYTES), Connector.READ);
      hcon.setRequestProperty("User-Agent", UA);
      hcon.setRequestMethod(HttpConnection.GET);
      watchdog = Network.armPlaybackWatchdog(hcon, SOCKET_TIMEOUT_MS);
      int code = hcon.getResponseCode();
      if (code != HttpConnection.HTTP_OK) {
        throw new IOException("HTTP " + code);
      }
      in = new DataInputStream(hcon.openInputStream());
      byte[] buffer = new byte[CHUNK];
      start = System.currentTimeMillis();
      long reported = start;
      while (true) {
        int count = in.read(buffer);
        if (count == -1) {
          break;
        }
        if (isStopped()) {
          return 0;
        }
        total += count;
        long now = System.currentTimeMillis();
        if (now - start >= DOWNLOAD_MS) {
          break;
        }
        if (now - reported >= REPORT_MS) {
          reported = now;
          listener.onProgress(PHASE_DOWNLOAD, total, now - start);
        }
      }
      long elapsed = System.currentTimeMillis() - start;
      if (elapsed < 1) {
        elapsed = 1;
      }
      return total * 8000L / elapsed;
    } finally {
      if (watchdog != null) {
        watchdog.cancel();
      }
      Utils.closeQuietly(in);
      Utils.closeQuietly(hcon);
    }
  }

  private long measureUpload() throws IOException {
    HttpConnection hcon = null;
    OutputStream out = null;
    DataInputStream in = null;
    TimerTask watchdog = null;
    long start = 0;
    long sent = 0;
    boolean complete = false;
    try {
      hcon = Network.openConnection(url("op=upload"), Connector.READ_WRITE);
      hcon.setRequestProperty("User-Agent", UA);
      hcon.setRequestMethod(HttpConnection.POST);
      hcon.setRequestProperty("Content-Type", "application/octet-stream");
      hcon.setRequestProperty("Content-Length", String.valueOf(UPLOAD_BYTES));
      out = hcon.openOutputStream();
      byte[] buffer = new byte[CHUNK];
      start = System.currentTimeMillis();
      long reported = start;
      while (sent < UPLOAD_BYTES) {
        if (isStopped()) {
          return 0;
        }
        int count = (int) Math.min(CHUNK, UPLOAD_BYTES - sent);
        out.write(buffer, 0, count);
        out.flush();
        sent += count;
        long now = System.currentTimeMillis();
        if (now - start >= UPLOAD_MS) {
          break;
        }
        if (now - reported >= REPORT_MS) {
          reported = now;
          listener.onProgress(PHASE_UPLOAD, sent, now - start);
        }
      }
      complete = sent >= UPLOAD_BYTES;
      long elapsed = System.currentTimeMillis() - start;
      if (complete) {
        out.close();
        out = null;
        int code = hcon.getResponseCode();
        if (code == HttpConnection.HTTP_OK) {
          in = new DataInputStream(hcon.openInputStream());
          byte[] buffer2 = new byte[128];
          while (in.read(buffer2) != -1) {
            if (isStopped()) {
              break;
            }
          }
        }
        elapsed = System.currentTimeMillis() - start;
      }
      if (elapsed < 1) {
        elapsed = 1;
      }
      return sent * 8000L / elapsed;
    } finally {
      if (watchdog != null) {
        watchdog.cancel();
      }
      Utils.closeQuietly(in);
      // Close the connection first: when the upload is aborted mid-body, closing
      // the socket releases the output stream instead of waiting for the peer.
      Utils.closeQuietly(hcon);
      Utils.closeQuietly(out);
    }
  }

  private static String url(String query) {
    return Configuration.DEFAULT_SPEEDTEST_URL + "?" + query;
  }
}
