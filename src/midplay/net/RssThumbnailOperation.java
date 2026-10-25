package midplay.net;

import javax.microedition.lcdui.Image;
import midplay.store.Configuration;
import midplay.util.Utils;

/**
 * Loads a small batch of feed images sequentially through the image proxy, scaling each one to a
 * square list cell. Callbacks run on the worker thread, so callers must use
 * {@code Display.callSerially} before touching the UI.
 */
public final class RssThumbnailOperation extends NetworkOperation {
  public interface Listener {
    void onImage(int index, Image image);
  }

  private final String[] urls;
  private final int size;
  private final Listener listener;

  public RssThumbnailOperation(String[] urls, int size, Listener listener) {
    this.urls = urls;
    this.size = size <= 0 ? Configuration.RSS_THUMBNAIL_SIZE : size;
    this.listener = listener;
  }

  protected void execute() {
    if (urls == null || listener == null) {
      return;
    }
    for (int i = 0; i < urls.length; i++) {
      if (isStopped()) {
        return;
      }
      String url = urls[i];
      if (url == null || url.length() == 0) {
        continue;
      }
      byte[] data = fetchBytes(RssApi.imageUrl(url));
      if (isStopped() || data == null) {
        continue;
      }
      try {
        Image image = Image.createImage(data, 0, data.length);
        listener.onImage(i, Utils.resizeImageToFit(image, size, size));
      } catch (Exception e) {
        // One broken image must not abort the rest of the batch.
      }
    }
  }

  protected void handleError(Exception e) {}
}
