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
 *
 * <p>A hard safety page limit still applies so a misbehaving endpoint cannot loop forever. When it
 * is reached the listener is told that more pages remain, and the reader can continue loading from
 * {@code nextPage} instead of silently cutting the chapter.
 */
public class WattpadChapterOperation extends NetworkOperation {
  public interface Listener {
    void onProgress(int page);

    void onSuccess(String text, boolean more, int nextPage);

    void onError(Exception e);
  }

  private static final int MAX_PAGES = 50;
  private static final int MAX_PAGE_BYTES = 256 * 1024;
  private static final long SOCKET_TIMEOUT_MS = 45000L;

  private final String partId;
  private final int startPage;
  private final Listener listener;

  public WattpadChapterOperation(String partId, Listener listener) {
    this(partId, 1, listener);
  }

  public WattpadChapterOperation(String partId, int startPage, Listener listener) {
    this.partId = partId;
    this.startPage = startPage < 1 ? 1 : startPage;
    this.listener = listener;
  }

  /** Stop flag used by the reader; the offline downloader passes null and cancels between parts. */
  private interface Stopper {
    boolean isStopped();
  }

  /** One run of the page loop: text fetched so far plus whether the safety cap was reached. */
  private static final class FetchResult {
    String text;
    boolean more;
    int nextPage;
  }

  /**
   * Fetches a whole chapter as plain text without delivering it to a listener. Used by the story
   * downloader to store chapters on the card. Follows continuation pages until the endpoint signals
   * the end, with a total page guard against a broken server.
   */
  public static String downloadChapter(String partId) throws IOException {
    StringBuffer joined = new StringBuffer();
    int page = 1;
    for (int guard = 0; guard < 1000; guard++) {
      FetchResult result = fetchText(partId, page, null, null);
      if (result.text != null) {
        joined.append(result.text);
      }
      if (!result.more) {
        return joined.toString();
      }
      page = result.nextPage;
    }
    throw new IOException("Chapter too long");
  }

  private static FetchResult fetchText(
      String partId, int startPage, Listener progress, Stopper stopper) throws IOException {
    StringBuffer html = new StringBuffer();
    FetchResult result = new FetchResult();
    result.nextPage = startPage;
    int page = startPage;
    boolean capReached = true;
    for (int fetched = 0; fetched < MAX_PAGES; fetched++, page++) {
      if (stopper != null && stopper.isStopped()) {
        throw new IOException("Stopped");
      }
      if (progress != null) {
        progress.onProgress(page);
      }
      String chunk = fetchPage(partId, page, stopper);
      if (chunk == null) {
        capReached = false;
        break;
      }
      String trimmed = chunk.trim();
      if (trimmed.length() == 0) {
        capReached = false;
        break;
      }
      if (page == startPage && trimmed.charAt(0) == '{') {
        // The endpoint answers with a small JSON error object when a part cannot be served.
        throw new IOException("Chapter unavailable");
      }
      html.append(chunk);
    }
    result.more = capReached;
    result.nextPage = page;
    result.text = HtmlText.toText(html.toString());
    if (result.text.length() == 0 && startPage == 1) {
      throw new IOException("Empty chapter");
    }
    return result;
  }

  protected void execute() {
    try {
      FetchResult result =
          fetchText(
              partId,
              startPage,
              listener,
              new Stopper() {
                public boolean isStopped() {
                  return WattpadChapterOperation.this.isStopped();
                }
              });
      if (!isStopped()) {
        listener.onSuccess(result.text, result.more, result.nextPage);
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

  private static String fetchPage(String partId, int page, Stopper stopper) throws IOException {
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
        if (stopper != null && stopper.isStopped()) {
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
