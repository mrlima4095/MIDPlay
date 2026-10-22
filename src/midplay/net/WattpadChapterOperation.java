package midplay.net;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.TimerTask;
import javax.microedition.io.HttpConnection;
import midplay.util.HtmlText;
import midplay.util.Utils;

/**
 * Downloads a whole Wattpad chapter. The text endpoint answers with one HTML fragment per page, so
 * this operation keeps requesting pages until an empty one comes back, then hands the joined result
 * over as plain text.
 */
public class WattpadChapterOperation extends NetworkOperation {
  public interface Listener {
    void onProgress(int page);

    void onSuccess(String text);

    void onError(Exception e);
  }

  private static final int MAX_PAGES = 15;
  private static final int MAX_PAGE_BYTES = 256 * 1024;
  private static final long SOCKET_TIMEOUT_MS = 45000L;

  private final String partId;
  private final Listener listener;

  public WattpadChapterOperation(String partId, Listener listener) {
    this.partId = partId;
    this.listener = listener;
  }

  protected void execute() {
    try {
      StringBuffer html = new StringBuffer();
      for (int page = 1; page <= MAX_PAGES; page++) {
        if (isStopped()) {
          return;
        }
        listener.onProgress(page);
        String chunk = fetchPage(page);
        if (chunk == null) {
          break;
        }
        String trimmed = chunk.trim();
        if (trimmed.length() == 0) {
          break;
        }
        if (page == 1 && trimmed.charAt(0) == '{') {
          // The endpoint answers with a small JSON error object when a part cannot be served.
          throw new IOException("Chapter unavailable");
        }
        html.append(chunk);
      }
      if (html.length() == 0) {
        throw new IOException("Empty chapter");
      }
      String text = HtmlText.toText(html.toString());
      if (text.length() == 0) {
        throw new IOException("Empty chapter");
      }
      if (!isStopped()) {
        listener.onSuccess(text);
      }
    } catch (Exception e) {
      if (!isStopped()) {
        listener.onError(e);
      }
    }
  }

  /**
   * Decodes a chapter page after removing four-byte UTF-8 sequences. Some CLDC decoders stop at an
   * emoji (a supplementary character) and silently discard the rest of the page, which made Wattpad
   * chapters appear to end after the first paragraph. Keeping the page in BMP text avoids that.
   */
  private static String decodePage(byte[] data) {
    ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
    for (int i = 0; i < data.length; i++) {
      int b = data[i] & 0xFF;
      if (b >= 0xF0) {
        out.write(' ');
        int continuation = 0;
        while (continuation < 3
            && i + 1 < data.length
            && (data[i + 1] & 0xC0) == 0x80) {
          i++;
          continuation++;
        }
      } else {
        out.write(b);
      }
    }
    return Utils.bytesToUtf8(out.toByteArray());
  }

  private String fetchPage(int page) throws IOException {
    HttpConnection hcon = null;
    DataInputStream in = null;
    TimerTask watchdog = null;
    try {
      hcon = Network.openConnection(WattpadApi.chapterUrl(partId, page));
      if (hcon == null) {
        throw new IOException("Failed to open connection");
      }
      hcon.setRequestProperty("User-Agent", "MIDPlay/1.0 (MIDP-2.0; CLDC-1.1)");
      hcon.setRequestMethod(HttpConnection.GET);
      watchdog = Network.armPlaybackWatchdog(hcon, SOCKET_TIMEOUT_MS);
      int code = hcon.getResponseCode();
      if (code != HttpConnection.HTTP_OK) {
        throw new IOException("HTTP " + code);
      }
      in = new DataInputStream(hcon.openInputStream());
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int read;
      while ((read = in.read(buffer)) != -1) {
        if (isStopped()) {
          break;
        }
        out.write(buffer, 0, read);
        if (out.size() >= MAX_PAGE_BYTES) {
          break;
        }
      }
      return decodePage(out.toByteArray());
    } finally {
      if (watchdog != null) {
        watchdog.cancel();
      }
      Utils.closeQuietly(in);
      Utils.closeQuietly(hcon);
    }
  }

  protected void processResponse(String response) {}

  protected void handleNoData() {}

  protected void handleError(Exception e) {}
}
