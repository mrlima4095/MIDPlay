package midplay.net;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.microedition.io.Connector;
import javax.microedition.io.SocketConnection;
import javax.microedition.lcdui.Display;
import midplay.MIDPlay;
import midplay.model.Playlist;
import midplay.model.Track;
import midplay.model.Tracks;
import midplay.player.PlayerGUI;
import midplay.player.PlayerScreen;
import midplay.store.Configuration;
import midplay.store.DownloadManager;
import midplay.store.FavoritesManager;
import midplay.store.NotesManager;
import midplay.store.SettingsManager;
import midplay.store.TasksManager;
import midplay.util.DateTime;
import midplay.util.Utils;

/**
 * Cliente do relay MIDPlay_Web (socket TCP puro, newline-delimited JSON).
 *
 * <p>Fala o protocolo de relay/protocol.py: handshake {@code auth}, resposta {@code auth_ok} /
 * {@code auth_err}, heartbeat {@code ping}/{@code pong}, {@code state} do device, {@code cmd} do site
 * (respondido com {@code ack}) e {@code event}. Roda numa thread propria e reconecta com backoff
 * exponencial enquanto o usuario nao mandar desconectar.
 */
public final class SocketClient {
  public static final int STATE_OFFLINE = 0;
  public static final int STATE_CONNECTING = 1;
  public static final int STATE_ONLINE = 2;

  private static final int MAX_LINE = 32 * 1024;
  private static final int READ_TIMEOUT_MS = 180000;
  private static final long BACKOFF_MIN_MS = 2000L;
  private static final long BACKOFF_MAX_MS = 60000L;
  private static final long STATE_INTERVAL_MS = 2000L;
  private static final int MAX_PLAYLISTS = 40;
  private static final int MAX_PLAYLIST_TRACKS = 80;
  private static final int MAX_NOTES = 30;
  private static final int MAX_TASKS = 60;
  private static final int MAX_DOWNLOADS = 100;
  private static final int MAX_TEXT = 1200;
  // O relay le linhas de ate 128 KB; manter o payload bem abaixo evita derrubar
  // a conexao quando o aparelho tem muitas notas/downloads com texto longo.
  private static final int MAX_DATA_CHARS = 24000;

  public interface Listener {
    void onStateChanged(int state, String message);
  }

  private static SocketClient instance;

  public static synchronized SocketClient getInstance() {
    if (instance == null) {
      instance = new SocketClient();
    }
    return instance;
  }

  private volatile int state = STATE_OFFLINE;
  private volatile boolean wantConnected;
  private volatile long lastReadAt;
  private volatile boolean statePusherRunning;

  private String host;
  private int port;
  private String user;
  private String password;
  private String deviceId;
  private String deviceName;
  private String lastError = "";

  private SocketConnection connection;
  private OutputStream output;
  private Thread worker;
  private Thread stateThread;

  private Listener listener;

  private SocketClient() {}

  public void setListener(Listener l) {
    listener = l;
  }

  public int getState() {
    return state;
  }

  public boolean isOnline() {
    return state == STATE_ONLINE;
  }

  public String getLastError() {
    return lastError;
  }

  public String getDeviceName() {
    return deviceName;
  }

  public String getDeviceId() {
    return deviceId;
  }

  public synchronized void connect(
      String host, int port, String user, String password, String deviceId, String deviceName) {
    if (wantConnected) {
      disconnect();
    }
    this.host = host;
    this.port = port;
    this.user = user;
    this.password = password;
    this.deviceId =
        deviceId == null || deviceId.length() == 0 ? Utils.randomUuid() : deviceId;
    this.deviceName =
        deviceName == null || deviceName.length() == 0 ? this.deviceId : deviceName;
    this.lastError = "";
    this.wantConnected = true;
    setState(STATE_CONNECTING, "Conectando a " + host + ":" + port);
    worker = new Thread(new Runnable() {
      public void run() {
        runLoop();
      }
    });
    worker.start();
  }

  public synchronized void disconnect() {
    wantConnected = false;
    stopStatePusher();
    closeQuietly();
    Thread t = worker;
    worker = null;
    if (t != null) {
      t.interrupt();
    }
    setState(STATE_OFFLINE, "Desconectado");
  }

  private void runLoop() {
    long backoff = BACKOFF_MIN_MS;
    while (wantConnected) {
      try {
        runSession();
        backoff = BACKOFF_MIN_MS;
      } catch (Throwable t) {
        lastError = t.toString();
      }
      stopStatePusher();
      closeQuietly();
      if (!wantConnected) {
        break;
      }
      setState(STATE_CONNECTING, "Reconectando em " + (backoff / 1000) + "s" + (lastError.length() > 0 ? " - " + lastError : ""));
      try {
        Thread.sleep(backoff);
      } catch (InterruptedException e) {
        break;
      }
      backoff *= 2;
      if (backoff > BACKOFF_MAX_MS) {
        backoff = BACKOFF_MAX_MS;
      }
    }
    // Nao emitir outro STATE_OFFLINE aqui: auth_err ja publicou o erro real e
    // disconnect() ja publicou "Desconectado". Reemitir apagaria o ticker de erro.
  }

  private void runSession() throws IOException {
    String url = "socket://" + host + ":" + port;
    if (Utils.isBlackberry) {
      url += ";deviceside=true";
    }
    SocketConnection c = (SocketConnection) Connector.open(url, Connector.READ_WRITE, true);
    connection = c;
    c.setSocketOption(SocketConnection.DELAY, 0);
    output = c.openOutputStream();
    InputStream input = c.openInputStream();

    JSONObject auth = new JSONObject();
    auth.put("t", "auth");
    auth.put("user", user);
    auth.put("pass", password);
    // device_id e o identificador estavel; device/name sao o nome amigavel.
    // O campo "device" continua sendo enviado para relays antigos.
    auth.put("device_id", deviceId);
    auth.put("device", deviceName);
    auth.put("name", deviceName);
    JSONObject banner = new JSONObject();
    String platform = safeProperty("microedition.platform");
    banner.put("app", "MIDPlay");
    banner.put("ver", MIDPlay.APP_VERSION);
    banner.put("version", MIDPlay.APP_VERSION);
    banner.put("model", platform);
    banner.put("platform", platform);
    auth.put("banner", banner);
    send(auth);

    lastReadAt = System.currentTimeMillis();
    startWatchdog();

    while (wantConnected) {
      String line = readLine(input);
      if (line == null) {
        throw new IOException("conexao encerrada pelo servidor");
      }
      lastReadAt = System.currentTimeMillis();
      if (line.length() == 0) {
        continue;
      }
      JSONObject msg;
      try {
        msg = JSONObject.parseObject(line);
      } catch (Exception e) {
        continue;
      }
      if (!handle(msg)) {
        throw new IOException("auth recusado");
      }
    }
  }

  /** @return false quando o servidor recusou a autenticacao (nao adianta reconectar). */
  private boolean handle(JSONObject msg) {
    String t = msg.getString("t", "");
    if ("auth_ok".equals(t)) {
      String devId = msg.getString("device", deviceId);
      String friendly = msg.getString("name", deviceName);
      if (devId != null && devId.length() > 0) {
        deviceId = devId;
      }
      if (friendly == null || friendly.length() == 0) {
        friendly = deviceName != null && deviceName.length() > 0 ? deviceName : deviceId;
      }
      deviceName = friendly;
      setState(STATE_ONLINE, "Online como " + user + " (" + deviceName + ")");
      startStatePusher();
      sendState();
      return true;
    }
    if ("auth_err".equals(t)) {
      lastError = msg.getString("reason", "credenciais invalidas");
      wantConnected = false;
      setState(STATE_OFFLINE, "Falha no login: " + lastError);
      return false;
    }
    if ("ping".equals(t)) {
      JSONObject pong = new JSONObject();
      pong.put("t", "pong");
      pong.put("ts", System.currentTimeMillis() / 1000L);
      try {
        send(pong);
      } catch (IOException e) {
        lastError = e.toString();
      }
      return true;
    }
    if ("pong".equals(t)) {
      return true;
    }
    if ("cmd".equals(t)) {
      executeCommand(msg);
      return true;
    }
    if ("want".equals(t)) {
      String what = msg.getString("what", "");
      int id = msg.getInt("id", -1);
      if ("state".equals(what)) {
        sendState(id);
      } else {
        sendRequestedData(id, what);
      }
      return true;
    }
    return true;
  }

  private void executeCommand(final JSONObject msg) {
    final int id = msg.getInt("id", -1);
    final String action = msg.getString("action", "");
    final JSONObject args = msg.has("args") ? msg.getObject("args") : new JSONObject();
    MIDPlay app = MIDPlay.getInstance();
    if (app == null) {
      sendAck(id, false, "app indisponivel");
      return;
    }
    Display.getDisplay(app).callSerially(new Runnable() {
      public void run() {
        boolean ok = true;
        String error = null;
        try {
          PlayerScreen screen = MIDPlay.getPlayerScreen();
          PlayerGUI gui = screen != null ? screen.getPlayerGUI() : null;
          if ("state".equals(action)) {
            sendState(id);
            return;
          }
          if (gui == null && !"noop".equals(action)) {
            sendAck(id, false, "player indisponivel");
            return;
          }
          if ("play".equals(action)) {
            if (!gui.isPlaying()) {
              gui.toggle();
            }
          } else if ("pause".equals(action)) {
            if (gui.isPlaying()) {
              gui.toggle();
            }
          } else if ("toggle".equals(action)) {
            gui.toggle();
          } else if ("next".equals(action)) {
            gui.next();
          } else if ("prev".equals(action) || "previous".equals(action)) {
            gui.previous();
          } else if ("stop".equals(action)) {
            gui.stop();
          } else if ("volume_up".equals(action)) {
            gui.adjustVolume(true);
          } else if ("volume_down".equals(action)) {
            gui.adjustVolume(false);
          } else if ("volume".equals(action) || "set_volume".equals(action)) {
            gui.setVolumeLevel(args.getInt("level", gui.getVolumeLevel()));
          } else if ("seek".equals(action)) {
            int positionMs = args.getInt("position", -1);
            if (positionMs < 0) {
              ok = false;
              error = "posicao invalida";
            } else {
              gui.seek(((long) positionMs) * 1000L);
            }
          } else if ("noop".equals(action)) {
            // keep-alive vindo do site
          } else {
            ok = false;
            error = "acao desconhecida: " + action;
          }
        } catch (Throwable t) {
          ok = false;
          error = t.toString();
        }
        sendAck(id, ok, error);
        sendState();
      }
    });
  }

  private void sendState() {
    sendState(-1);
  }

  private void sendState(int replyId) {
    JSONObject state = buildState();
    JSONObject msg = new JSONObject();
    if (replyId >= 0) {
      msg.put("t", "data");
      msg.put("what", "state");
      msg.put("id", replyId);
      msg.put("data", state);
    } else {
      msg.put("t", "state");
      msg.put("state", state);
    }
    try {
      send(msg);
    } catch (IOException e) {
      lastError = e.toString();
    }
  }

  private JSONObject buildState() {
    JSONObject s = new JSONObject();
    JSONObject trackJson = new JSONObject();
    trackJson.put("title", "");
    trackJson.put("artist", "");
    trackJson.put("album", "");
    trackJson.put("duration", 0L);
    trackJson.put("url", "");
    trackJson.put("thumb", "");
    s.put("online", true);
    s.put("playing", false);
    s.put("volume", 0);
    s.put("position", 0L);
    s.put("duration", 0L);
    s.put("track", trackJson);
    try {
      PlayerScreen screen = MIDPlay.getPlayerScreen();
      PlayerGUI gui = screen != null ? screen.getPlayerGUI() : null;
      if (gui == null) {
        return s;
      }
      s.put("playing", gui.isPlaying());
      s.put("volume", gui.getVolumeLevel());
      long position = gui.getCurrentTime() / 1000L;
      long duration = gui.getDuration() / 1000L;
      if (position < 0) {
        position = 0L;
      }
      Track track = gui.getCurrentTrack();
      if (track != null) {
        if (duration <= 0 && track.getDuration() > 0) {
          duration = ((long) track.getDuration()) * 1000L;
        }
        trackJson.put("title", track.getName() == null ? "" : track.getName());
        trackJson.put("artist", track.getArtist() == null ? "" : track.getArtist());
        trackJson.put("album", "");
        trackJson.put("duration", duration);
        trackJson.put("url", track.getUrl() == null ? "" : track.getUrl());
        trackJson.put(
            "thumb", track.getImageUrl() == null ? "" : track.getImageUrl());
      }
      s.put("position", position);
      s.put("duration", duration > 0 ? duration : (track != null ? ((long) track.getDuration()) * 1000L : 0L));
      Tracks list = gui.getCurrentTracks();
      if (list != null) {
        s.put("queue_size", list.size());
      }
    } catch (Throwable t) {
    }
    return s;
  }

  private void startStatePusher() {
    synchronized (this) {
      if (statePusherRunning && stateThread != null && stateThread.isAlive()) {
        return;
      }
      statePusherRunning = true;
      stateThread =
          new Thread(
              new Runnable() {
                public void run() {
                  try {
                    while (wantConnected && statePusherRunning) {
                      try {
                        Thread.sleep(STATE_INTERVAL_MS);
                      } catch (InterruptedException e) {
                        return;
                      }
                      if (!wantConnected || !statePusherRunning || state != STATE_ONLINE) {
                        return;
                      }
                      sendState();
                    }
                  } finally {
                    synchronized (SocketClient.this) {
                      if (stateThread == Thread.currentThread()) {
                        statePusherRunning = false;
                        stateThread = null;
                      }
                    }
                  }
                }
              });
      stateThread.start();
    }
  }

  private synchronized void stopStatePusher() {
    statePusherRunning = false;
    Thread t = stateThread;
    stateThread = null;
    if (t != null) {
      t.interrupt();
    }
  }

  private void sendRequestedData(final int id, final String what) {
    if (!("playlists".equals(what)
        || "notes".equals(what)
        || "tasks".equals(what)
        || "downloads".equals(what))) {
      sendAck(id, false, "unsupported: " + what);
      return;
    }
    Thread t =
        new Thread(
            new Runnable() {
              public void run() {
                try {
                  JSONArray data;
                  if ("playlists".equals(what)) {
                    data = buildPlaylists();
                  } else if ("notes".equals(what)) {
                    data = buildNotes();
                  } else if ("tasks".equals(what)) {
                    data = buildTasks();
                  } else {
                    data = buildDownloads();
                  }
                  sendData(id, what, data);
                } catch (Throwable t) {
                  lastError = t.toString();
                  sendAck(id, false, t.toString());
                }
              }
            });
    t.start();
  }

  private void sendData(int id, String what, JSONArray data) {
    JSONObject msg = new JSONObject();
    msg.put("t", "data");
    msg.put("id", id);
    msg.put("what", what);
    msg.put("data", data);
    try {
      send(msg);
    } catch (IOException e) {
      lastError = e.toString();
    }
  }

  private JSONArray buildPlaylists() {
    JSONArray out = new JSONArray();
    FavoritesManager favorites = FavoritesManager.getInstance();
    Playlist[] playlists = favorites.getPlaylists().getPlaylists();
    if (playlists == null) {
      return out;
    }
    int playlistCount = Math.min(playlists.length, MAX_PLAYLISTS);
    int trackBudget = MAX_PLAYLIST_TRACKS;
    int charBudget = MAX_DATA_CHARS;
    for (int i = 0; i < playlistCount; i++) {
      Playlist playlist = playlists[i];
      JSONObject item = new JSONObject();
      item.put("name", truncate(playlist.getName()));
      item.put("artist", truncate(playlist.getArtist()));
      item.put("image", truncate(playlist.getImageUrl()));
      JSONArray tracks = new JSONArray();
      if (playlist.isCustom() && trackBudget > 0) {
        try {
          Tracks list = favorites.getCustomPlaylistTracks(playlist);
          Track[] arr = list != null ? list.getTracks() : null;
          if (arr != null) {
            int count = Math.min(arr.length, trackBudget);
            for (int j = 0; j < count; j++) {
              tracks.add(trackJson(arr[j]));
            }
            trackBudget -= count;
          }
        } catch (Throwable t) {
        }
      }
      item.put("tracks", tracks);
      int size = item.toString().length();
      if (charBudget - size < 0 && out.size() > 0) {
        break;
      }
      charBudget -= size;
      out.add(item);
    }
    return out;
  }

  private JSONArray buildNotes() {
    JSONArray out = new JSONArray();
    NotesManager.Note[] notes = NotesManager.getInstance().getNotes();
    int count = notes == null ? 0 : Math.min(notes.length, MAX_NOTES);
    int charBudget = MAX_DATA_CHARS;
    for (int i = 0; i < count; i++) {
      NotesManager.Note note = notes[i];
      JSONObject item = new JSONObject();
      String title = note.title;
      if (title == null || title.length() == 0) {
        title = firstLine(note.text);
      }
      item.put("title", truncate(title));
      item.put("text", truncate(note.text));
      item.put("pinned", note.pinned);
      int size = item.toString().length();
      if (charBudget - size < 0 && out.size() > 0) {
        break;
      }
      charBudget -= size;
      out.add(item);
    }
    return out;
  }

  private JSONArray buildTasks() {
    JSONArray out = new JSONArray();
    TasksManager.Task[] tasks = TasksManager.getInstance().getTasks();
    int count = tasks == null ? 0 : Math.min(tasks.length, MAX_TASKS);
    int charBudget = MAX_DATA_CHARS;
    for (int i = 0; i < count; i++) {
      TasksManager.Task task = tasks[i];
      JSONObject item = new JSONObject();
      item.put("title", truncate(task.title));
      item.put("description", truncate(task.description));
      item.put("done", task.done);
      item.put("due", task.due == DateTime.NONE ? 0L : task.due);
      item.put("priority", task.priority);
      item.put("pinned", task.pinned);
      int size = item.toString().length();
      if (charBudget - size < 0 && out.size() > 0) {
        break;
      }
      charBudget -= size;
      out.add(item);
    }
    return out;
  }

  private JSONArray buildDownloads() {
    JSONArray out = new JSONArray();
    Track[] tracks = DownloadManager.getInstance().refreshDownloadedTracks();
    int count = tracks == null ? 0 : Math.min(tracks.length, MAX_DOWNLOADS);
    int charBudget = MAX_DATA_CHARS;
    for (int i = 0; i < count; i++) {
      Track track = tracks[i];
      JSONObject item = new JSONObject();
      item.put("name", truncate(track.getName()));
      item.put("artist", truncate(track.getArtist()));
      item.put("duration", ((long) track.getDuration()) * 1000L);
      item.put("path", truncate(track.getUrl()));
      item.put("thumb", truncate(track.getImageUrl()));
      int size = item.toString().length();
      if (charBudget - size < 0 && out.size() > 0) {
        break;
      }
      charBudget -= size;
      out.add(item);
    }
    return out;
  }

  private JSONObject trackJson(Track track) {
    JSONObject item = new JSONObject();
    item.put("title", truncate(track.getName()));
    item.put("artist", truncate(track.getArtist()));
    item.put("duration", ((long) track.getDuration()) * 1000L);
    item.put("url", truncate(track.getUrl()));
    item.put("thumb", truncate(track.getImageUrl()));
    return item;
  }

  private static String truncate(String value) {
    if (value == null) {
      return "";
    }
    return value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT);
  }

  private static String firstLine(String text) {
    if (text == null) {
      return "";
    }
    int end = text.indexOf('\n');
    String line = end >= 0 ? text.substring(0, end) : text;
    return truncate(line.trim());
  }

  private void sendAck(int id, boolean ok, String error) {
    JSONObject msg = new JSONObject();
    msg.put("t", "ack");
    msg.put("id", id);
    msg.put("ok", ok);
    if (error != null) {
      msg.put("error", error);
    }
    try {
      send(msg);
    } catch (IOException e) {
      lastError = e.toString();
    }
  }

  private synchronized void send(JSONObject msg) throws IOException {
    OutputStream out = output;
    if (out == null) {
      throw new IOException("sem conexao");
    }
    byte[] bytes = (msg.toString() + "\n").getBytes("UTF-8");
    out.write(bytes);
    out.flush();
  }

  private String readLine(InputStream in) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream(256);
    while (true) {
      int b = in.read();
      if (b == -1) {
        return buffer.size() > 0 ? decode(buffer) : null;
      }
      if (b == '\n') {
        return decode(buffer);
      }
      if (b != '\r') {
        buffer.write(b);
        if (buffer.size() > MAX_LINE) {
          throw new IOException("linha muito longa");
        }
      }
    }
  }

  private static String decode(ByteArrayOutputStream buffer) {
    try {
      return new String(buffer.toByteArray(), "UTF-8");
    } catch (Exception e) {
      return new String(buffer.toByteArray());
    }
  }

  private void startWatchdog() {
    Thread w = new Thread(new Runnable() {
      public void run() {
        while (wantConnected && state == STATE_ONLINE) {
          try {
            Thread.sleep(30000L);
          } catch (InterruptedException e) {
            return;
          }
          if (!wantConnected) {
            return;
          }
          if (System.currentTimeMillis() - lastReadAt > READ_TIMEOUT_MS) {
            lastError = "sem dados do servidor (watchdog)";
            closeQuietly();
            return;
          }
        }
      }
    });
    w.start();
  }

  private void closeQuietly() {
    try {
      if (connection != null) {
        connection.close();
      }
    } catch (Exception e) {
    }
    connection = null;
    try {
      if (output != null) {
        output.close();
      }
    } catch (Exception e) {
    }
    output = null;
  }

  private void setState(int newState, String message) {
    state = newState;
    Listener l = listener;
    if (l != null) {
      final Listener target = l;
      final int s = newState;
      final String m = message;
      MIDPlay app = MIDPlay.getInstance();
      if (app != null) {
        Display.getDisplay(app).callSerially(new Runnable() {
          public void run() {
            try {
              target.onStateChanged(s, m);
            } catch (Throwable t) {
            }
          }
        });
      }
    }
  }

  private static String safeProperty(String name) {
    try {
      String v = System.getProperty(name);
      return v == null ? "" : v;
    } catch (Throwable t) {
      return "";
    }
  }
}
