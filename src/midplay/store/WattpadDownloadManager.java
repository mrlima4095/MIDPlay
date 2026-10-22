package midplay.store;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Gauge;
import midplay.net.WattpadApi;
import midplay.net.WattpadChapterOperation;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;
import midplay.util.Utils;

/**
 * Downloads whole Wattpad stories to the memory card so the reader can work offline.
 *
 * <p>Every story lives in its own folder under the configured Wattpad folder:
 *
 * <pre>
 *   &lt;folder&gt;/&lt;storyId&gt;/story.json   title + chapter list
 *   &lt;folder&gt;/&lt;storyId&gt;/&lt;partId&gt;.txt one plain-text chapter
 * </pre>
 *
 * <p>The chapter list is written before the first chapter so an interrupted download can be
 * resumed: chapters whose file already exists are skipped. The reader asks this class for a
 * chapter before going to the network, so a downloaded chapter is always served from disk.
 */
public final class WattpadDownloadManager implements CommandListener {
  /** Called on the UI thread after a download finishes (also after a cancellation). */
  public interface Listener {
    void onDownloadFinished(String storyId, int downloaded, int failed);
  }

  private static final String METADATA_FILE = "story.json";
  private static final String CHAPTER_SUFFIX = ".txt";
  private static final String TEMP_SUFFIX = ".part";

  private static final Command MINIMIZE_COMMAND =
      new Command(Lang.tr("download.minimize"), Command.SCREEN, 1);

  private static WattpadDownloadManager instance;

  public static synchronized WattpadDownloadManager getInstance() {
    if (instance == null) {
      instance = new WattpadDownloadManager();
    }
    return instance;
  }

  /** Metadata persisted with a downloaded story. */
  private static final class LocalStory {
    String id;
    String title;
    Vector parts;
  }

  private static final class Job {
    String storyId;
    Vector parts;
    Listener listener;
    Navigator navigator;
    volatile boolean cancelled;
    int total;
  }

  private final Hashtable storyCache = new Hashtable();
  private final Hashtable chapterCache = new Hashtable();

  private Job job;
  private Alert progressAlert;
  private Gauge progressGauge;
  private boolean minimized;
  private int done;
  private int failed;
  private String currentChapter = "";

  private WattpadDownloadManager() {}

  // ---------------------------------------------------------------------------
  // Storage paths
  // ---------------------------------------------------------------------------

  /** Base folder URL, always ending with a slash. */
  public String getBasePath() {
    String path = SettingsManager.getInstance().getWattpadPath();
    if (path == null || path.length() == 0) {
      path = Configuration.DEFAULT_WATTPAD_PATH;
    }
    return path.endsWith("/") ? path : path + "/";
  }

  private String storyDir(String storyId) {
    return getBasePath() + sanitize(storyId) + "/";
  }

  private String chapterPath(String storyId, String partId) {
    return storyDir(storyId) + sanitize(partId) + CHAPTER_SUFFIX;
  }

  /** Keeps folder/file names safe for every JSR-75 implementation. */
  private static String sanitize(String value) {
    if (value == null) {
      return "unknown";
    }
    StringBuffer sb = new StringBuffer(value.length());
    for (int i = 0; i < value.length() && sb.length() < 64; i++) {
      char c = value.charAt(i);
      if ((c >= 'A' && c <= 'Z')
          || (c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || c == '-'
          || c == '_') {
        sb.append(c);
      } else {
        sb.append('_');
      }
    }
    return sb.length() == 0 ? "unknown" : sb.toString();
  }

  // ---------------------------------------------------------------------------
  // Reading local content
  // ---------------------------------------------------------------------------

  /** Chapter list of a downloaded story, or {@code null} when it was never downloaded. */
  public Vector getLocalParts(String storyId) {
    LocalStory story = story(storyId);
    return story == null ? null : story.parts;
  }

  /** Saved title of a downloaded story, or an empty string. */
  public String getLocalTitle(String storyId) {
    LocalStory story = story(storyId);
    return story == null || story.title == null ? "" : story.title;
  }

  public boolean isChapterDownloaded(String storyId, String partId) {
    if (storyId == null || partId == null) {
      return false;
    }
    String key = storyId + "/" + partId;
    Object hit = chapterCache.get(key);
    if (hit != null) {
      return ((Boolean) hit).booleanValue();
    }
    boolean result = exists(chapterPath(storyId, partId));
    chapterCache.put(key, result ? Boolean.TRUE : Boolean.FALSE);
    return result;
  }

  /**
   * Plain text of a downloaded chapter, or {@code null} when it is not on the card. Callers then
   * fall back to the network.
   */
  public String readChapter(String storyId, String partId) {
    if (storyId == null || partId == null) {
      return null;
    }
    String path = chapterPath(storyId, partId);
    try {
      if (!exists(path)) {
        return null;
      }
      String text = readFile(path);
      if (text == null || text.length() == 0) {
        return null;
      }
      chapterCache.put(storyId + "/" + partId, Boolean.TRUE);
      return text;
    } catch (Exception e) {
      return null;
    }
  }

  /** Number of chapters of a story that are on the card (0 when it was never downloaded). */
  public int getDownloadedCount(String storyId) {
    LocalStory story = story(storyId);
    if (story == null) {
      return 0;
    }
    int count = 0;
    for (int i = 0; i < story.parts.size(); i++) {
      String[] part = (String[]) story.parts.elementAt(i);
      if (isChapterDownloaded(storyId, part[WattpadApi.PART_ID])) {
        count++;
      }
    }
    return count;
  }

  /** True when every chapter of the story is on the card. */
  public boolean isStoryDownloaded(String storyId) {
    LocalStory story = story(storyId);
    return story != null
        && story.parts.size() > 0
        && getDownloadedCount(storyId) >= story.parts.size();
  }

  /**
   * Finds the downloaded story that owns a chapter. Used to open chapter links offline without
   * asking the network which story the chapter belongs to.
   */
  public String findStoryForPart(String partId) {
    if (partId == null || partId.length() == 0) {
      return null;
    }
    String base = getBasePath();
    FileConnection dir = null;
    try {
      if (!exists(base)) {
        return null;
      }
      dir = (FileConnection) Connector.open(base, Connector.READ);
      Enumeration entries = dir.list();
      while (entries.hasMoreElements()) {
        String name = (String) entries.nextElement();
        if (!name.endsWith("/")) {
          continue;
        }
        String folder = name.substring(0, name.length() - 1);
        LocalStory story = story(folder);
        if (story == null) {
          continue;
        }
        for (int i = 0; i < story.parts.size(); i++) {
          String[] part = (String[]) story.parts.elementAt(i);
          if (partId.equals(part[WattpadApi.PART_ID])) {
            return story.id == null || story.id.length() == 0 ? folder : story.id;
          }
        }
      }
    } catch (Exception e) {
    } finally {
      closeQuietly(dir);
    }
    return null;
  }

  /** Deletes a story folder and all of its chapters. */
  public void deleteStory(String storyId) {
    if (storyId == null) {
      return;
    }
    String dirPath = storyDir(storyId);
    FileConnection dir = null;
    try {
      dir = (FileConnection) Connector.open(dirPath, Connector.READ_WRITE);
      if (dir.exists() && dir.isDirectory()) {
        Enumeration entries = dir.list();
        while (entries.hasMoreElements()) {
          String name = (String) entries.nextElement();
          if (!name.endsWith("/")) {
            deleteFile(dirPath + name);
          }
        }
      }
      if (dir.exists()) {
        dir.delete();
      }
    } catch (Exception e) {
    } finally {
      closeQuietly(dir);
    }
    storyCache.remove(storyId);
    chapterCache.clear();
  }

  // ---------------------------------------------------------------------------
  // Downloading
  // ---------------------------------------------------------------------------

  public synchronized boolean isDownloading(String storyId) {
    return job != null && storyId != null && storyId.equals(job.storyId);
  }

  /**
   * Starts downloading the missing chapters in the background. Chapters already on the card are
   * skipped, so calling this again after an interruption resumes the download.
   */
  public synchronized void download(
      Navigator navigator,
      String storyId,
      String storyTitle,
      Vector parts,
      Listener listener) {
    if (navigator == null || storyId == null || parts == null || parts.size() == 0) {
      return;
    }
    if (job != null) {
      navigator.showAlert(Lang.tr("download.in_progress"), AlertType.INFO);
      return;
    }
    // Write the chapter list before any chapter so an interrupted download can be resumed and the
    // story is listed offline even when only some chapters are on the card.
    if (!ensureDirectories(storyDir(storyId)) || !writeStory(storyId, storyTitle, parts)) {
      navigator.showAlert(Lang.tr("wattpad.download_error"), AlertType.ERROR);
      return;
    }
    int missing = 0;
    for (int i = 0; i < parts.size(); i++) {
      String[] part = (String[]) parts.elementAt(i);
      if (!isChapterDownloaded(storyId, part[WattpadApi.PART_ID])) {
        missing++;
      }
    }
    if (missing == 0) {
      navigator.showAlert(Lang.tr("wattpad.download_none"), AlertType.INFO);
      return;
    }

    final Job next = new Job();
    next.storyId = storyId;
    next.parts = parts;
    next.listener = listener;
    next.navigator = navigator;
    next.total = parts.size();
    job = next;
    done = 0;
    failed = 0;
    minimized = false;
    currentChapter = "";
    progressAlert = null;
    progressGauge = null;
    showProgress();

    Thread thread =
        new Thread() {
          public void run() {
            runJob(next);
          }
        };
    thread.start();
  }

  private void runJob(Job current) {
    try {
      for (int i = 0; i < current.parts.size(); i++) {
        if (current.cancelled) {
          break;
        }
        String[] part = (String[]) current.parts.elementAt(i);
        String partId = part[WattpadApi.PART_ID];
        String partTitle = part[WattpadApi.PART_TITLE];
        synchronized (this) {
          currentChapter = partTitle == null ? "" : partTitle;
        }
        if (!isChapterDownloaded(current.storyId, partId)) {
          try {
            String text = WattpadChapterOperation.downloadChapter(partId);
            writeChapter(current.storyId, partId, text);
          } catch (Exception e) {
            synchronized (this) {
              failed++;
            }
          }
        }
        synchronized (this) {
          done++;
        }
        updateProgressAsync();
      }
    } finally {
      finishJob(current);
    }
  }

  private void finishJob(final Job finished) {
    final Navigator nav = finished.navigator;
    int downloaded;
    int failures;
    synchronized (this) {
      if (job != finished) {
        return;
      }
      job = null;
      progressAlert = null;
      progressGauge = null;
      downloaded = done;
      failures = failed;
    }
    final boolean cancelled = finished.cancelled;
    final int finalDownloaded = downloaded;
    final int finalFailures = failures;
    if (finished.listener != null && nav != null) {
      nav.callSerially(
          new Runnable() {
            public void run() {
              finished.listener.onDownloadFinished(
                  finished.storyId, finalDownloaded, finalFailures);
            }
          });
    }
    if (nav == null || cancelled) {
      return;
    }
    nav.callSerially(
        new Runnable() {
          public void run() {
            nav.dismissAlert();
            if (finalFailures > 0) {
              nav.showAlert(
                  Lang.tr("wattpad.download_failed", Integer.toString(finalFailures)),
                  AlertType.WARNING);
            } else {
              nav.showAlert(Lang.tr("wattpad.download_done"), AlertType.CONFIRMATION);
            }
          }
        });
  }

  private void showProgress() {
    Navigator nav;
    Alert alert;
    Gauge gauge;
    synchronized (this) {
      nav = job == null ? null : job.navigator;
      if (nav == null) {
        return;
      }
      alert = new Alert(null, progressMessage(), null, AlertType.INFO);
      alert.setTimeout(Alert.FOREVER);
      alert.addCommand(MINIMIZE_COMMAND);
      alert.addCommand(Commands.cancel());
      alert.setCommandListener(this);
      gauge = new Gauge(null, false, 100, percent());
      alert.setIndicator(gauge);
      progressAlert = alert;
      progressGauge = gauge;
    }
    nav.forward(alert);
  }

  /** Progress is shown from the download thread, so all display calls go through callSerially. */
  private void updateProgressAsync() {
    final Navigator nav;
    final String message;
    final int value;
    synchronized (this) {
      nav = minimized || job == null ? null : job.navigator;
      message = progressMessage();
      value = percent();
    }
    if (nav == null) {
      return;
    }
    nav.callSerially(
        new Runnable() {
          public void run() {
            Alert alert;
            Gauge gauge;
            synchronized (WattpadDownloadManager.this) {
              alert = progressAlert;
              gauge = progressGauge;
            }
            if (alert != null) {
              alert.setString(message);
            }
            if (gauge != null) {
              gauge.setValue(value);
            }
          }
        });
  }

  private int percent() {
    int total = job == null ? 0 : job.total;
    return total <= 0 ? 0 : (done * 100) / total;
  }

  private String progressMessage() {
    int total = job == null ? 0 : job.total;
    StringBuffer sb = new StringBuffer();
    sb.append(Lang.tr("wattpad.downloading"))
        .append(' ')
        .append(done)
        .append('/')
        .append(total);
    if (currentChapter != null && currentChapter.length() > 0) {
      sb.append(" - ").append(currentChapter);
    }
    return sb.toString();
  }

  public void commandAction(Command c, Displayable d) {
    if (c == MINIMIZE_COMMAND) {
      synchronized (this) {
        minimized = true;
        progressAlert = null;
        progressGauge = null;
      }
      if (job != null && job.navigator != null) {
        job.navigator.dismissAlert();
      }
    } else if (c == Commands.cancel()) {
      cancelDownload();
    }
  }

  private void cancelDownload() {
    Job current;
    Navigator nav;
    synchronized (this) {
      current = job;
      if (current != null) {
        current.cancelled = true;
      }
      nav = current == null ? null : current.navigator;
      progressAlert = null;
      progressGauge = null;
    }
    if (nav == null) {
      return;
    }
    nav.dismissAlert();
    nav.showAlert(Lang.tr("wattpad.download_cancelled"), AlertType.INFO);
  }

  // ---------------------------------------------------------------------------
  // Metadata
  // ---------------------------------------------------------------------------

  private LocalStory story(String storyId) {
    if (storyId == null) {
      return null;
    }
    Object cached = storyCache.get(storyId);
    if (cached != null) {
      return (LocalStory) cached;
    }
    LocalStory loaded = readStory(storyId);
    if (loaded != null) {
      storyCache.put(storyId, loaded);
    }
    return loaded;
  }

  private LocalStory readStory(String storyId) {
    String path = storyDir(storyId) + METADATA_FILE;
    try {
      if (!exists(path)) {
        return null;
      }
      JSONObject json = JSON.getObject(readFile(path));
      LocalStory story = new LocalStory();
      String id = json.getString("id", storyId);
      story.id = id == null || id.length() == 0 ? storyId : id;
      String title = json.getString("title", "");
      story.title = title == null ? "" : title;
      story.parts = new Vector();
      JSONArray parts = json.getArray("parts");
      if (parts != null) {
        for (int i = 0; i < parts.size(); i++) {
          JSONObject part = parts.getObject(i);
          String partId = part.getString("id", "");
          if (partId.length() == 0) {
            continue;
          }
          String partTitle = part.getString("title", "");
          story.parts.addElement(
              new String[] {partId, partTitle == null ? "" : partTitle});
        }
      }
      return story;
    } catch (Exception e) {
      return null;
    }
  }

  private boolean writeStory(String storyId, String storyTitle, Vector parts) {
    JSONObject json = new JSONObject();
    json.put("id", storyId);
    json.put("title", storyTitle == null ? "" : storyTitle);
    JSONArray array = new JSONArray();
    for (int i = 0; i < parts.size(); i++) {
      String[] part = (String[]) parts.elementAt(i);
      JSONObject item = new JSONObject();
      item.put("id", part[WattpadApi.PART_ID]);
      item.put("title", part[WattpadApi.PART_TITLE] == null ? "" : part[WattpadApi.PART_TITLE]);
      array.add(item);
    }
    json.put("parts", array);
    try {
      writeFile(storyDir(storyId) + METADATA_FILE, json.toString());
      LocalStory story = new LocalStory();
      story.id = storyId;
      story.title = storyTitle == null ? "" : storyTitle;
      story.parts = parts;
      storyCache.put(storyId, story);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  // ---------------------------------------------------------------------------
  // File helpers
  // ---------------------------------------------------------------------------

  private void writeChapter(String storyId, String partId, String text) throws IOException {
    String path = chapterPath(storyId, partId);
    String temp = path + TEMP_SUFFIX;
    deleteFile(temp);
    writeFile(temp, text);
    boolean renamed = false;
    FileConnection connection = null;
    try {
      connection = (FileConnection) Connector.open(temp, Connector.READ_WRITE);
      deleteFile(path);
      connection.rename(fileNameOf(path));
      renamed = true;
    } catch (Exception e) {
      // Some implementations do not support rename; fall back to a direct write below.
    } finally {
      closeQuietly(connection);
    }
    if (!renamed) {
      writeFile(path, text);
      deleteFile(temp);
    }
    chapterCache.put(storyId + "/" + partId, Boolean.TRUE);
  }

  private static String fileNameOf(String path) {
    int slash = path.lastIndexOf('/');
    return slash >= 0 ? path.substring(slash + 1) : path;
  }

  private static String readFile(String path) throws IOException {
    FileConnection file = null;
    DataInputStream input = null;
    try {
      file = (FileConnection) Connector.open(path, Connector.READ);
      input = new DataInputStream(file.openInputStream());
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[2048];
      int read;
      while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
      return Utils.bytesToUtf8(output.toByteArray());
    } finally {
      Utils.closeQuietly(input);
      closeQuietly(file);
    }
  }

  private static void writeFile(String path, String text) throws IOException {
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
      byte[] bytes = Utils.utf8ToBytes(text);
      output.write(bytes, 0, bytes.length);
      output.flush();
    } finally {
      Utils.closeQuietly(output);
      closeQuietly(file);
    }
  }

  private static boolean exists(String path) {
    FileConnection file = null;
    try {
      file = (FileConnection) Connector.open(path, Connector.READ);
      return file.exists();
    } catch (Exception e) {
      return false;
    } finally {
      closeQuietly(file);
    }
  }

  private static void deleteFile(String path) {
    FileConnection file = null;
    try {
      file = (FileConnection) Connector.open(path, Connector.READ_WRITE);
      if (file.exists()) {
        file.delete();
      }
    } catch (Exception e) {
    } finally {
      closeQuietly(file);
    }
  }

  private static void closeQuietly(FileConnection connection) {
    if (connection != null) {
      try {
        connection.close();
      } catch (Exception e) {
      }
    }
  }

  /** Creates each missing folder of a {@code file:///} URL. */
  private static boolean ensureDirectories(String basePath) {
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
          if (!ensureDirExists(cumulative)) {
            return false;
          }
          token.setLength(0);
        }
      } else {
        token.append(c);
      }
    }
    if (token.length() > 0) {
      cumulative = cumulative + token.toString() + "/";
      if (!ensureDirExists(cumulative)) {
        return false;
      }
    }
    return true;
  }

  private static boolean ensureDirExists(String dirUrl) {
    FileConnection connection = null;
    try {
      connection = (FileConnection) Connector.open(dirUrl, Connector.READ_WRITE);
      if (!connection.exists()) {
        connection.mkdir();
      }
      return true;
    } catch (Exception e) {
      return false;
    } finally {
      closeQuietly(connection);
    }
  }
}
