package midplay.net;

import javax.microedition.lcdui.Image;
import midplay.util.Utils;

/**
 * Loads a small set of Pinterest thumbnails sequentially through the image proxy and scales them to
 * a square list cell. Callbacks run on the worker thread, so screens must move UI updates to
 * {@code Display.callSerially}.
 */
public final class PinterestThumbnailOperation extends NetworkOperation {
  private final String[] urls;
  private final int size;
  private final Listener listener;

  public PinterestThumbnailOperation(String[] urls, int size, Listener listener) {
    this.urls = urls;
    this.size = size <= 0 ? 36 : size;
    this.listener = listener;
  }

  protected void execute() {
    if (urls == null) {
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
      byte[] data = fetchBytes(PinterestApi.imageUrl(url));
      if (isStopped() || data == null) {
        continue;
      }
      try {
        Image image = Image.createImage(data, 0, data.length);
        listener.onImage(i, Utils.resizeImageToFit(image, size, size));
      } catch (Exception e) {
        // One broken thumbnail must not stop the rest of the list.
      }
    }
    if (!isStopped()) {
      listener.onDone();
    }
  }

  protected void handleError(Exception e) {
    listener.onError(e);
  }

  public interface Listener {
    void onImage(int index, Image image);

    void onDone();

    void onError(Exception e);
  }
}
