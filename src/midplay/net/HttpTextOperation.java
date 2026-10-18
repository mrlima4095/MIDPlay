package midplay.net;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.TimerTask;
import javax.microedition.io.HttpConnection;
import midplay.util.Utils;

/**
 * Fetches a URL as UTF-8 text, following HTTP redirects manually. Many feeds and services bounce
 * {@code http://} to {@code https://} or to a canonical host, and the plain MIDP HttpConnection
 * cannot be relied on to follow those automatically.
 */
public class HttpTextOperation extends NetworkOperation {
  public interface Listener {
    void onText(String text);

    void onError(Exception e);
  }

  private static final int MAX_REDIRECTS = 5;
  private static final int DEFAULT_MAX_BYTES = 600 * 1024;
  private static final long SOCKET_TIMEOUT_MS = 60000L;

  private final String url;
  private final Listener listener;
  private final int maxBytes;

  public HttpTextOperation(String url, Listener listener) {
    this(url, listener, DEFAULT_MAX_BYTES);
  }

  public HttpTextOperation(String url, Listener listener, int maxBytes) {
    this.url = url;
    this.listener = listener;
    this.maxBytes = maxBytes > 0 ? maxBytes : DEFAULT_MAX_BYTES;
  }

  protected void execute() {
    String current = url;
    try {
      for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
        if (isStopped()) {
          return;
        }
        HttpConnection hcon = null;
        TimerTask watchdog = null;
        try {
          hcon = Network.openConnection(current);
          if (hcon == null) {
            throw new IOException("Failed to open connection");
          }
          hcon.setRequestProperty("User-Agent", "MIDPlay/1.0 (MIDP-2.0; CLDC-1.1)");
          hcon.setRequestMethod(HttpConnection.GET);
          hcon.setRequestProperty("Accept", "text/html,application/xhtml+xml,text/plain,*/*");
          watchdog = Network.armPlaybackWatchdog(hcon, SOCKET_TIMEOUT_MS);
          int code = hcon.getResponseCode();
          if (code == HttpConnection.HTTP_OK || code == HttpConnection.HTTP_PARTIAL) {
            String text = Utils.bytesToUtf8(readAll(hcon));
            if (!isStopped()) {
              listener.onText(text);
            }
            return;
          }
          if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
            String location = hcon.getHeaderField("Location");
            if (location == null || location.length() == 0) {
              throw new IOException("Redirect without Location");
            }
            current = resolve(current, location);
            continue;
          }
          throw new IOException("HTTP " + code);
        } finally {
          if (watchdog != null) {
            watchdog.cancel();
          }
          Utils.closeQuietly(hcon);
        }
      }
      throw new IOException("Too many redirects");
    } catch (Exception e) {
      if (!isStopped()) {
        listener.onError(e);
      }
    }
  }

  private byte[] readAll(HttpConnection hcon) throws IOException {
    DataInputStream dis = null;
    try {
      dis = new DataInputStream(hcon.openInputStream());
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int n;
      while ((n = dis.read(buffer)) != -1) {
        if (isStopped()) {
          break;
        }
        out.write(buffer, 0, n);
        if (out.size() >= maxBytes) {
          break;
        }
      }
      return out.toByteArray();
    } finally {
      Utils.closeQuietly(dis);
    }
  }

  private static String resolve(String base, String location) {
    if (location.startsWith("http://") || location.startsWith("https://")) {
      return location;
    }
    int schemeEnd = base.indexOf("://");
    if (schemeEnd < 0) {
      return location;
    }
    int hostEnd = base.indexOf('/', schemeEnd + 3);
    String root = hostEnd < 0 ? base : base.substring(0, hostEnd);
    if (location.startsWith("/")) {
      return root + location;
    }
    int slash = base.lastIndexOf('/');
    if (slash > schemeEnd + 2) {
      return base.substring(0, slash + 1) + location;
    }
    return root + "/" + location;
  }

  protected void processResponse(String response) {}

  protected void handleNoData() {}

  protected void handleError(Exception e) {}
}
