package midplay.net;

import java.io.OutputStream;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import midplay.store.SettingsManager;
import midplay.util.Utils;

/**
 * Downloads one Pinterest image (through the image proxy) to the configured Pinterest folder on the
 * memory card. Only this explicit action writes to disk; browsing and favorites keep images online.
 */
public final class PinterestImageDownloadOperation extends NetworkOperation {
  public interface Listener {
    void onDownloaded(String path);

    void onError(Exception e);
  }

  private final String sourceUrl;
  private final String fileName;
  private final Listener listener;

  public PinterestImageDownloadOperation(
      String sourceUrl, String fileName, Listener listener) {
    this.sourceUrl = sourceUrl == null ? "" : sourceUrl;
    this.fileName = fileName == null ? "" : fileName;
    this.listener = listener;
  }

  protected void execute() {
    if (sourceUrl.length() == 0) {
      listener.onError(new Exception("invalid image"));
      return;
    }
    Network connection = new Network();
    network = connection;
    byte[] data;
    try {
      data = connection.sendHttpGetBytes(PinterestApi.imageUrl(sourceUrl));
    } catch (Exception e) {
      if (!isStopped()) {
        listener.onError(e);
      }
      return;
    }
    if (isStopped()) {
      return;
    }
    if (data == null || data.length == 0) {
      listener.onError(new Exception("empty image"));
      return;
    }
    try {
      String path = write(data);
      if (!isStopped()) {
        listener.onDownloaded(path);
      }
    } catch (Exception e) {
      if (!isStopped()) {
        listener.onError(e);
      }
    }
  }

  protected void handleError(Exception e) {
    listener.onError(e);
  }

  private String write(byte[] data) throws Exception {
    String base = SettingsManager.getInstance().getPinterestPath();
    if (base == null || base.length() == 0) {
      base = midplay.store.Configuration.DEFAULT_PINTEREST_PATH;
    }
    if (!base.endsWith("/")) {
      base = base + "/";
    }
    ensureDirectories(base);
    String path = base + sanitize(fileName);
    FileConnection file = null;
    OutputStream output = null;
    try {
      file = (FileConnection) Connector.open(path, Connector.READ_WRITE);
      if (!file.exists()) {
        file.create();
      } else {
        file.truncate(0);
      }
      output = file.openOutputStream();
      output.write(data, 0, data.length);
      output.flush();
    } finally {
      Utils.closeQuietly(output);
      if (file != null) {
        try {
          file.close();
        } catch (Exception e) {
        }
      }
    }
    return path;
  }

  private static String sanitize(String value) {
    String name = value == null ? "" : value.trim();
    if (name.length() == 0) {
      name = "pin";
    }
    StringBuffer sb = new StringBuffer(name.length());
    for (int i = 0; i < name.length() && sb.length() < 64; i++) {
      char c = name.charAt(i);
      if ((c >= 'A' && c <= 'Z')
          || (c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || c == '-'
          || c == '_'
          || c == '.') {
        sb.append(c);
      } else {
        sb.append('_');
      }
    }
    if (sb.length() == 0) {
      return "pin";
    }
    if (sb.toString().indexOf('.') < 0) {
      sb.append(".jpg");
    }
    return sb.toString();
  }

  /** Creates every missing folder of a {@code file:///} URL. */
  private static void ensureDirectories(String basePath) {
    String rest = basePath;
    String prefix = "";
    if (rest.startsWith("file:///")) {
      rest = rest.substring(8);
      prefix = "file:///";
    } else if (rest.startsWith("file://")) {
      rest = rest.substring(7);
      prefix = "file:///";
    }
    String cumulative = prefix;
    StringBuffer token = new StringBuffer();
    for (int i = 0; i < rest.length(); i++) {
      char c = rest.charAt(i);
      if (c == '/') {
        if (token.length() > 0) {
          cumulative = cumulative + token.toString() + "/";
          ensureDirExists(cumulative);
          token.setLength(0);
        }
      } else {
        token.append(c);
      }
    }
    if (token.length() > 0) {
      cumulative = cumulative + token.toString() + "/";
      ensureDirExists(cumulative);
    }
  }

  private static void ensureDirExists(String dirUrl) {
    FileConnection connection = null;
    try {
      connection = (FileConnection) Connector.open(dirUrl, Connector.READ_WRITE);
      if (!connection.exists()) {
        connection.mkdir();
      }
    } catch (Exception e) {
    } finally {
      if (connection != null) {
        try {
          connection.close();
        } catch (Exception e) {
        }
      }
    }
  }
}
