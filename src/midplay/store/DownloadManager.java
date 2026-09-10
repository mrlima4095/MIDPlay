package midplay.store;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.TimerTask;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.io.file.FileConnection;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import midplay.model.Track;
import midplay.net.MediaHttpClient;
import midplay.net.Network;
import midplay.util.Utils;

public class DownloadManager {
  private static final int INDEX_RECORD_ID = 1;
  private static final int BUFFER_SIZE = 4096;
  private static final long DOWNLOAD_TIMEOUT_MS = 120000L;
  private static final int MAX_REDIRECTS = 5;

  private static DownloadManager instance;

  public static DownloadManager getInstance() {
    if (instance == null) {
      instance = new DownloadManager();
    }
    return instance;
  }

  public interface DownloadListener {
    void onStart();

    void onProgress(int percent);

    void onDone(String path);

    void onError(String message);
  }

  private RecordStore indexStore;
  private JSONArray downloads;
  private final Vector activeKeys = new Vector();

  private DownloadManager() {}

  private synchronized RecordStore openIndexStore() throws RecordStoreException {
    if (indexStore == null) {
      indexStore = RecordStore.openRecordStore(Configuration.STORAGE_DOWNLOADS, true);
    }
    return indexStore;
  }

  private JSONArray getIndex() {
    if (downloads != null) {
      return downloads;
    }
    downloads = new JSONArray();
    try {
      RecordStore rs = openIndexStore();
      byte[] data = rs.getRecord(INDEX_RECORD_ID);
      if (data != null && data.length > 0) {
        String json = Utils.bytesToUtf8(data);
        if (json.length() > 0) {
          downloads = cc.nnproject.json.JSON.getArray(json);
        }
      }
    } catch (Exception e) {
      downloads = new JSONArray();
    }
    return downloads;
  }

  private void saveIndex() {
    try {
      RecordStore rs = openIndexStore();
      byte[] bytes = Utils.utf8ToBytes(downloads.toString());
      try {
        rs.setRecord(INDEX_RECORD_ID, bytes, 0, bytes.length);
      } catch (RecordStoreException e) {
        rs.addRecord(bytes, 0, bytes.length);
      }
    } catch (RecordStoreException e) {
    }
  }

  public static String makeTrackKey(Track track) {
    if (track == null) {
      return null;
    }
    String key = track.getKey();
    if (key != null && key.length() > 0) {
      return sanitize(key);
    }
    String name = track.getName() != null ? track.getName() : "";
    String artist = track.getArtist() != null ? track.getArtist() : "";
    String raw = name + "_" + artist + "_" + track.getDuration();
    return sanitize(raw);
  }

  private static String sanitize(String key) {
    StringBuffer sb = new StringBuffer(key.length());
    for (int i = 0; i < key.length() && sb.length() < 80; i++) {
      char c = key.charAt(i);
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
    if (sb.length() == 0) {
      sb.append("track");
    }
    return sb.toString();
  }

  private String fileNameFor(Track track) {
    return makeTrackKey(track) + ".mp3";
  }

  public boolean isDownloaded(Track track) {
    if (track == null) {
      return false;
    }
    String key = makeTrackKey(track);
    String url = track.getUrl();
    if (key == null) {
      return false;
    }
    JSONArray index = getIndex();
    for (int i = 0; i < index.size(); i++) {
      try {
        JSONObject entry = index.getObject(i);
        if (key.equals(entry.getString("key", ""))
            || (url != null && url.length() > 0 && url.equals(entry.getString("url", "")))) {
          String path = entry.getString("path", "");
          if (path.length() > 0) {
            return exists(path);
          }
        }
      } catch (Exception e) {
      }
    }
    return false;
  }

  public String getDownloadedFilePath(Track track) {
    if (track == null) {
      return null;
    }
    String url = track.getUrl();
    String key = makeTrackKey(track);
    JSONArray index = getIndex();
    for (int i = 0; i < index.size(); i++) {
      try {
        JSONObject entry = index.getObject(i);
        String indexedUrl = entry.getString("url", "");
        // Downloads made before URL indexing used the track key, so retain that lookup.
        boolean sameUrl = url != null && url.length() > 0 && url.equals(indexedUrl);
        String path = entry.getString("path", "");
        boolean samePath = url != null && url.length() > 0 && url.equals(path);
        if (sameUrl
            || samePath
            || (indexedUrl.length() == 0 && key.equals(entry.getString("key", "")))) {
          if (path.length() > 0 && exists(path)) {
            return path;
          }
        }
      } catch (Exception e) {
      }
    }
    return null;
  }

  public boolean isDownloadActive(Track track) {
    String key = makeTrackKey(track);
    if (key == null) {
      return false;
    }
    synchronized (activeKeys) {
      return activeKeys.contains(key);
    }
  }

  private void markActive(String key) {
    if (key == null) {
      return;
    }
    synchronized (activeKeys) {
      if (!activeKeys.contains(key)) {
        activeKeys.addElement(key);
      }
    }
  }

  private void unmarkActive(String key) {
    if (key == null) {
      return;
    }
    synchronized (activeKeys) {
      activeKeys.removeElement(key);
    }
  }

  public void downloadAsync(final Track track, final DownloadListener listener) {
    if (track == null) {
      notifyError(listener, "No track");
      return;
    }
    final String key = makeTrackKey(track);
    if (isDownloaded(track)) {
      notifyDone(listener, getDownloadedFilePath(track));
      return;
    }
    if (isDownloadActive(track)) {
      notifyError(listener, "Download already in progress");
      return;
    }
    synchronized (activeKeys) {
      markActive(key);
    }
    Thread t =
        new Thread(
            new Runnable() {
              public void run() {
                try {
                  performDownload(track, key, listener);
                } finally {
                  unmarkActive(key);
                }
              }
            });
    t.start();
  }

  private void performDownload(Track track, String key, DownloadListener listener) {
    String basePath = SettingsManager.getInstance().getCurrentDownloadPath();
    if (basePath == null || basePath.length() == 0) {
      basePath = Configuration.DEFAULT_DOWNLOAD_PATH;
    }
    if (!basePath.endsWith("/")) {
      basePath = basePath + "/";
    }
    String filePath = basePath + fileNameFor(track);

    HttpConnection connection = null;
    InputStream in = null;
    FileConnection file = null;
    OutputStream out = null;
    TimerTask watchdog = null;
    boolean completed = false;
    try {
      if (listener != null) {
        listener.onStart();
      }
      if (!ensureDirectories(basePath)) {
        notifyError(listener, "Unable to create download directory");
        return;
      }

      String finalUrl = resolveUrl(track.getUrl());
      if (finalUrl == null || finalUrl.length() == 0) {
        notifyError(listener, "Unable to resolve download URL");
        return;
      }

      connection = Network.openConnection(finalUrl);
      if (connection == null) {
        notifyError(listener, "Failed to open download connection");
        return;
      }
      connection.setRequestMethod(HttpConnection.GET);
      watchdog = Network.armPlaybackWatchdog(connection, DOWNLOAD_TIMEOUT_MS);

      int responseCode = connection.getResponseCode();
      if (responseCode != HttpConnection.HTTP_OK && responseCode != HttpConnection.HTTP_PARTIAL) {
        notifyError(listener, "HTTP error: " + responseCode);
        return;
      }

      long total = -1L;
      try {
        total = connection.getLength();
      } catch (Exception e) {
      }

      in = connection.openInputStream();
      file = (FileConnection) Connector.open(filePath, Connector.READ_WRITE);
      if (file.exists()) {
        file.delete();
      }
      file.create();
      out = file.openOutputStream();

      byte[] buffer = new byte[BUFFER_SIZE];
      long written = 0L;
      int read;
      while ((read = in.read(buffer)) != -1) {
        if (read > 0) {
          out.write(buffer, 0, read);
          written += read;
        }
        if (total > 0 && listener != null) {
          listener.onProgress(percent(written, total));
        }
      }
      out.flush();
      out.close();
      out = null;
      file.close();
      file = null;

      addToIndex(track, key, filePath);
      completed = true;
      notifyDone(listener, filePath);
    } catch (Exception e) {
      if (!completed) {
        deleteFileIfExists(filePath);
        notifyError(listener, errorMessage(e));
      }
    } finally {
      if (watchdog != null) {
        watchdog.cancel();
      }
      Utils.closeQuietly(in);
      Utils.closeQuietly(out);
      closeConnection(file);
      Utils.closeQuietly(connection);
    }
  }

  private static int percent(long part, long total) {
    if (total <= 0) {
      return -1;
    }
    int p = (int) ((part * 100L) / total);
    if (p > 100) {
      p = 100;
    }
    if (p < 0) {
      p = 0;
    }
    return p;
  }

  private String resolveUrl(String url) {
    if (url == null || url.length() == 0) {
      return null;
    }
    MediaHttpClient client = new MediaHttpClient();
    try {
      return client.resolveRedirect(
          url,
          0,
          new MediaHttpClient.ResolveContext() {
            public boolean isSessionActive(int sessionId) {
              return true;
            }

            public void onResolveProgress(int progress, String status, int sessionId) {}
          });
    } catch (IOException e) {
      return null;
    }
  }

  private void addToIndex(Track track, String key, String filePath) {
    JSONArray index = getIndex();
    try {
      String url = track.getUrl() != null ? track.getUrl() : "";
      for (int i = 0; i < index.size(); i++) {
        JSONObject entry = index.getObject(i);
        if (url.equals(entry.getString("url", "")) || key.equals(entry.getString("key", ""))) {
          entry.put("path", filePath);
          entry.put("url", url);
          saveIndex();
          return;
        }
      }
      JSONObject entry = new JSONObject();
      entry.put("key", key);
      entry.put("url", url);
      entry.put("name", track.getName() != null ? track.getName() : "");
      entry.put("artist", track.getArtist() != null ? track.getArtist() : "");
      entry.put("path", filePath);
      index.add(entry);
      saveIndex();
    } catch (Exception e) {
    }
  }

  public boolean removeDownload(Track track) {
    if (track == null) {
      return false;
    }
    String key = makeTrackKey(track);
    String trackPath = track.getUrl();
    JSONArray index = getIndex();
    for (int i = 0; i < index.size(); i++) {
      try {
        JSONObject entry = index.getObject(i);
        String path = entry.getString("path", "");
        if (path.equals(trackPath)
            || ((trackPath == null || !trackPath.startsWith("file://"))
                && key.equals(entry.getString("key", "")))) {
          if (path.length() > 0) {
            deleteFileIfExists(path);
          }
          rebuildWithout(i);
          return true;
        }
      } catch (Exception e) {
      }
    }
    if (trackPath != null && trackPath.length() > 0 && exists(trackPath)) {
      deleteFileIfExists(trackPath);
      return true;
    }
    return false;
  }

  private void rebuildWithout(int removedIndex) {
    JSONArray current = getIndex();
    JSONArray rebuilt = new JSONArray();
    for (int i = 0; i < current.size(); i++) {
      if (i == removedIndex) {
        continue;
      }
      try {
        rebuilt.add(current.getObject(i));
      } catch (Exception e) {
      }
    }
    downloads = rebuilt;
    saveIndex();
  }

  public void clearAllDownloads() {
    JSONArray index = getIndex();
    for (int i = 0; i < index.size(); i++) {
      try {
        JSONObject entry = index.getObject(i);
        String path = entry.getString("path", "");
        if (path.length() > 0) {
          deleteFileIfExists(path);
        }
      } catch (Exception e) {
      }
    }
    downloads = new JSONArray();
    saveIndex();
    deleteFilesInDownloadDirectory();
  }

  public Track[] getDownloadedTracks() {
    return getDownloadedTracks(false);
  }

  public Track[] refreshDownloadedTracks() {
    return getDownloadedTracks(true);
  }

  private Track[] getDownloadedTracks(boolean includeExternalFiles) {
    JSONArray index = getIndex();
    Vector trackList = new Vector();
    Vector indexedPaths = new Vector();
    Vector diskPaths = includeExternalFiles ? getDownloadFilePaths() : null;
    for (int i = 0; i < index.size(); i++) {
      try {
        JSONObject entry = index.getObject(i);
        String path = entry.getString("path", "");
        if (diskPaths != null && !diskPaths.contains(path)) {
          continue;
        }
        indexedPaths.addElement(path);
        trackList.addElement(
            new Track(
                entry.getString("key", ""),
                entry.getString("name", ""),
                path,
                0,
                entry.getString("artist", ""),
                ""));
      } catch (Exception e) {
      }
    }
    if (includeExternalFiles) {
      addExternalFiles(trackList, indexedPaths, diskPaths);
    }
    Track[] result = new Track[trackList.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = (Track) trackList.elementAt(i);
    }
    return result;
  }

  private Vector getDownloadFilePaths() {
    Vector paths = new Vector();
    String basePath = getDownloadDirectory();
    FileConnection directory = null;
    try {
      directory = (FileConnection) Connector.open(basePath, Connector.READ);
      if (!directory.exists() || !directory.isDirectory()) {
        return paths;
      }
      Enumeration files = directory.list();
      while (files.hasMoreElements()) {
        String fileName = (String) files.nextElement();
        if (!fileName.endsWith("/")) {
          paths.addElement(basePath + fileName);
        }
      }
    } catch (Exception e) {
    } finally {
      closeConnection(directory);
    }
    return paths;
  }

  private void addExternalFiles(Vector tracks, Vector indexedPaths, Vector diskPaths) {
    for (int i = 0; i < diskPaths.size(); i++) {
      String path = (String) diskPaths.elementAt(i);
      if (indexedPaths.contains(path)) {
        continue;
      }
      String fileName = path.substring(getDownloadDirectory().length());
      tracks.addElement(new Track("local_" + sanitize(fileName), fileName, path, 0, "", ""));
    }
  }

  private void deleteFilesInDownloadDirectory() {
    String basePath = getDownloadDirectory();
    FileConnection directory = null;
    try {
      directory = (FileConnection) Connector.open(basePath, Connector.READ);
      if (!directory.exists() || !directory.isDirectory()) {
        return;
      }
      Enumeration files = directory.list();
      while (files.hasMoreElements()) {
        String fileName = (String) files.nextElement();
        if (!fileName.endsWith("/")) {
          deleteFileIfExists(basePath + fileName);
        }
      }
    } catch (Exception e) {
    } finally {
      closeConnection(directory);
    }
  }

  private String getDownloadDirectory() {
    String path = SettingsManager.getInstance().getCurrentDownloadPath();
    if (path == null || path.length() == 0) {
      path = Configuration.DEFAULT_DOWNLOAD_PATH;
    }
    return path.endsWith("/") ? path : path + "/";
  }

  private boolean ensureDirectories(String basePath) {
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

  private boolean ensureDirExists(String dirUrl) {
    FileConnection conn = null;
    try {
      conn = (FileConnection) Connector.open(dirUrl, Connector.READ_WRITE);
      if (!conn.exists()) {
        conn.mkdir();
      }
      conn.close();
      conn = null;
      return true;
    } catch (Exception e) {
      return false;
    } finally {
      closeConnection(conn);
    }
  }

  private static boolean exists(String path) {
    FileConnection conn = null;
    try {
      conn = (FileConnection) Connector.open(path, Connector.READ);
      return conn.exists();
    } catch (Exception e) {
      return false;
    } finally {
      closeConnection(conn);
    }
  }

  private static void deleteFileIfExists(String path) {
    FileConnection conn = null;
    try {
      conn = (FileConnection) Connector.open(path, Connector.READ_WRITE);
      if (conn.exists()) {
        conn.delete();
      }
    } catch (Exception e) {
    } finally {
      closeConnection(conn);
    }
  }

  private static void closeConnection(FileConnection conn) {
    if (conn != null) {
      try {
        conn.close();
      } catch (Exception e) {
      }
    }
  }

  private static String errorMessage(Exception e) {
    if (e == null) {
      return "Download failed";
    }
    String msg = e.getMessage();
    if (msg == null || msg.length() == 0) {
      msg = e.toString();
    }
    return msg;
  }

  private static void notifyDone(DownloadListener listener, String path) {
    if (listener != null) {
      listener.onDone(path);
    }
  }

  private static void notifyError(DownloadListener listener, String message) {
    if (listener != null) {
      listener.onError(message);
    }
  }

  public void cleanup() {
    try {
      if (indexStore != null) {
        indexStore.closeRecordStore();
        indexStore = null;
      }
    } catch (RecordStoreException e) {
    }
  }
}
