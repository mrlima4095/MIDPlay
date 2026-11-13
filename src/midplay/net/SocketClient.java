package midplay.net;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.microedition.io.Connector;
import javax.microedition.io.SocketConnection;
import javax.microedition.lcdui.Display;
import midplay.MIDPlay;
import midplay.model.Track;
import midplay.model.Tracks;
import midplay.player.PlayerGUI;
import midplay.player.PlayerScreen;
import midplay.store.Configuration;
import midplay.store.SettingsManager;
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
  private volatile boolean playing;

  private String host;
  private int port;
  private String user;
  private String password;
  private String deviceName;
  private String lastError = "";

  private SocketConnection connection;
  private OutputStream output;
  private Thread worker;

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

  public synchronized void connect(
      String host, int port, String user, String password, String deviceName) {
    if (wantConnected) {
      disconnect();
    }
    this.host = host;
    this.port = port;
    this.user = user;
    this.password = password;
    this.deviceName = deviceName;
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
    setState(STATE_OFFLINE, "Desconectado");
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
    auth.put("device", deviceName);
    JSONObject banner = new JSONObject();
    banner.put("app", "MIDPlay");
    banner.put("version", MIDPlay.APP_VERSION);
    banner.put("platform", safeProperty("microedition.platform"));
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
      String dev = msg.getString("device", deviceName);
      deviceName = dev;
      setState(STATE_ONLINE, "Online como " + user + " (" + dev + ")");
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
        sendAck(id, false, "unsupported: " + what);
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
            if (!playing) {
              gui.toggle();
              playing = true;
            }
          } else if ("pause".equals(action)) {
            if (playing) {
              gui.toggle();
              playing = false;
            }
          } else if ("toggle".equals(action)) {
            gui.toggle();
            playing = !playing;
          } else if ("next".equals(action)) {
            gui.next();
          } else if ("prev".equals(action) || "previous".equals(action)) {
            gui.previous();
          } else if ("stop".equals(action)) {
            gui.stop();
            playing = false;
          } else if ("volume_up".equals(action)) {
            gui.adjustVolume(true);
          } else if ("volume_down".equals(action)) {
            gui.adjustVolume(false);
          } else if ("volume".equals(action)) {
            gui.setVolumeLevel(args.getInt("level", gui.getVolumeLevel()));
          } else if ("seek".equals(action)) {
            ok = false;
            error = "seek nao suportado neste device";
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
    s.put("online", true);
    s.put("playing", playing);
    s.put("volume", 0);
    s.put("position", 0);
    s.put("duration", 0);
    s.put("track", "");
    s.put("artist", "");
    try {
      PlayerScreen screen = MIDPlay.getPlayerScreen();
      PlayerGUI gui = screen != null ? screen.getPlayerGUI() : null;
      if (gui == null) {
        return s;
      }
      s.put("volume", gui.getVolumeLevel());
      Track track = gui.getCurrentTrack();
      if (track != null) {
        s.put("track", track.getDisplayTitle(""));
        s.put("artist", track.getArtist() == null ? "" : track.getArtist());
        s.put("duration", track.getDuration());
      }
      Tracks list = gui.getCurrentTracks();
      if (list != null) {
        s.put("queue_size", list.size());
      }
    } catch (Throwable t) {
    }
    return s;
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
