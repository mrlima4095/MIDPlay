package midplay.ui.screen;

import java.util.Timer;
import java.util.TimerTask;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.Ticker;
import javax.microedition.rms.RecordStoreException;
import midplay.net.SocketClient;
import midplay.store.Configuration;
import midplay.store.SettingsManager;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;
import midplay.util.Utils;

public final class WebConnectScreen extends BaseForm implements SocketClient.Listener {
  private final TextField hostField;
  private final TextField portField;
  private final TextField userField;
  private final TextField passField;
  private final TextField deviceField;
  private final Command connectCmd;
  private final Command disconnectCmd;
  private final Command forgetCmd;
  private final Command saveCmd;
  private boolean awaitingConnection;
  private boolean initialized;
  private String deviceId;
  private Timer tickerTimer;

  public WebConnectScreen(Navigator navigator) {
    super(Lang.tr("web.title"), navigator);
    hostField = new TextField(Lang.tr("web.host"), "", 64, TextField.ANY);
    portField = new TextField(Lang.tr("web.port"), "", 5, TextField.NUMERIC);
    userField = new TextField(Lang.tr("web.user"), "", 64, TextField.ANY);
    passField = new TextField(Lang.tr("web.password"), "", 64, TextField.PASSWORD);
    deviceField = new TextField(Lang.tr("web.device"), "", 32, TextField.ANY);

    append(hostField);
    append(portField);
    append(userField);
    append(passField);
    append(deviceField);

    connectCmd = new Command(Lang.tr("web.connect"), Command.OK, 1);
    disconnectCmd = new Command(Lang.tr("web.disconnect"), Command.SCREEN, 1);
    forgetCmd = new Command(Lang.tr("web.forget"), Command.SCREEN, 2);
    saveCmd = Commands.formSave();
    addCommand(saveCmd);
    addCommand(connectCmd);
  }

  protected void showNotify() {
    if (!initialized) {
      loadFields();
      initialized = true;
    }
    SocketClient.getInstance().setListener(this);
    updateCommands();
  }

  private void loadFields() {
    SettingsManager settings = SettingsManager.getInstance();
    hostField.setString(settings.getRelayHost());
    portField.setString(String.valueOf(settings.getRelayPort()));
    userField.setString(settings.getRelayUser());
    passField.setString(settings.getRelayPassword());
    deviceId = settings.getRelayDeviceId();
    if (deviceId == null || deviceId.length() == 0) {
      deviceId = Utils.randomUuid();
    }
    String device = settings.getRelayDevice();
    if (device == null || device.length() == 0) {
      device = defaultDeviceName();
    }
    deviceField.setString(device);
  }

  private String defaultDeviceName() {
    String suffix =
        deviceId != null && deviceId.length() >= 4
            ? deviceId.substring(0, 4)
            : String.valueOf(System.currentTimeMillis() % 10000);
    return "MIDPlay-" + suffix;
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == saveCmd) {
      doSave();
    } else if (c == connectCmd) {
      doConnect();
    } else if (c == disconnectCmd) {
      doDisconnect();
    } else if (c == forgetCmd) {
      doForget();
    }
  }

  protected boolean onBackPressed() {
    if (!isDirty()) {
      return false;
    }
    navigator.showConfirmationAlert(
        Lang.tr("web.unsaved"),
        new Runnable() {
          public void run() {
            navigator.back();
          }
        });
    return true;
  }

  private void doSave() {
    if (saveFields()) {
      showTicker(Lang.tr("web.saved"));
    }
  }

  private boolean saveFields() {
    SettingsManager settings = SettingsManager.getInstance();
    String host = hostField.getString().trim();
    int port = parsePort(portField.getString());
    String user = userField.getString().trim();
    String password = passField.getString();
    String device = deviceField.getString().trim();
    if (device.length() == 0) {
      device = defaultDeviceName();
      deviceField.setString(device);
    }
    if (deviceId == null || deviceId.length() == 0) {
      deviceId = Utils.randomUuid();
    }
    try {
      settings.saveRelayHost(host);
      settings.saveRelayPort(port);
      settings.saveRelayUser(user);
      settings.saveRelayPassword(password);
      settings.saveRelayDevice(device);
      settings.saveRelayDeviceId(deviceId);
      return true;
    } catch (RecordStoreException e) {
      showTicker(Lang.tr("settings.error.save_failed"));
      return false;
    }
  }

  private void doConnect() {
    String host = hostField.getString().trim();
    String user = userField.getString().trim();
    if (host.length() == 0) {
      showTicker(Lang.tr("web.host_empty"));
      return;
    }
    if (user.length() == 0) {
      showTicker(Lang.tr("web.user_empty"));
      return;
    }
    // Conectar salva tudo automaticamente, sem sair da tela.
    if (!saveFields()) {
      return;
    }
    awaitingConnection = true;
    showTicker(Lang.tr("web.connecting"));
    SocketClient.getInstance()
        .connect(
            host,
            parsePort(portField.getString()),
            user,
            passField.getString(),
            deviceId,
            deviceField.getString().trim());
  }

  private void doDisconnect() {
    SocketClient.getInstance().disconnect();
    showTicker(Lang.tr("web.disconnected"));
    updateCommands();
  }

  private void doForget() {
    SettingsManager settings = SettingsManager.getInstance();
    try {
      settings.saveRelayHost(Configuration.DEFAULT_RELAY_HOST);
      settings.saveRelayPort(Configuration.DEFAULT_RELAY_PORT);
      settings.saveRelayUser("");
      settings.saveRelayPassword("");
      settings.saveRelayDevice("");
      settings.saveRelayDeviceId("");
    } catch (RecordStoreException e) {
      showTicker(Lang.tr("settings.error.save_failed"));
      return;
    }
    SocketClient.getInstance().disconnect();
    deviceId = Utils.randomUuid();
    hostField.setString(settings.getRelayHost());
    portField.setString(String.valueOf(settings.getRelayPort()));
    userField.setString("");
    passField.setString("");
    deviceField.setString(defaultDeviceName());
    showTicker(Lang.tr("web.forgotten"));
    updateCommands();
  }

  private int parsePort(String value) {
    try {
      int port = Integer.parseInt(value.trim());
      return port > 0 && port <= 65535 ? port : Configuration.DEFAULT_RELAY_PORT;
    } catch (Exception e) {
      return Configuration.DEFAULT_RELAY_PORT;
    }
  }

  private boolean isDirty() {
    SettingsManager settings = SettingsManager.getInstance();
    String savedDevice = settings.getRelayDevice();
    if (savedDevice == null || savedDevice.length() == 0) {
      savedDevice = defaultDeviceName();
    }
    return !hostField.getString().trim().equals(settings.getRelayHost())
        || parsePort(portField.getString()) != settings.getRelayPort()
        || !userField.getString().trim().equals(settings.getRelayUser())
        || !passField.getString().equals(settings.getRelayPassword())
        || !deviceField.getString().trim().equals(savedDevice);
  }

  private void updateCommands() {
    int state = SocketClient.getInstance().getState();
    removeCommand(connectCmd);
    removeCommand(disconnectCmd);
    removeCommand(forgetCmd);
    if (state == SocketClient.STATE_ONLINE || state == SocketClient.STATE_CONNECTING) {
      addCommand(disconnectCmd);
      addCommand(forgetCmd);
    } else {
      addCommand(connectCmd);
    }
  }

  private void showTicker(String text) {
    if (text == null || text.length() == 0) {
      return;
    }
    setTicker(new Ticker(text));
    if (tickerTimer != null) {
      tickerTimer.cancel();
    }
    tickerTimer = new Timer();
    tickerTimer.schedule(
        new TimerTask() {
          public void run() {
            navigator.callSerially(
                new Runnable() {
                  public void run() {
                    setTicker(null);
                  }
                });
          }
        },
        5000L);
  }

  public void onStateChanged(int state, String message) {
    if (state == SocketClient.STATE_ONLINE) {
      if (awaitingConnection) {
        awaitingConnection = false;
        showTicker(Lang.tr("web.connected"));
      } else {
        showTicker(message);
      }
    } else if (state == SocketClient.STATE_OFFLINE) {
      if (awaitingConnection) {
        awaitingConnection = false;
        showTicker(Lang.tr("web.error_ticker", message == null ? "" : message));
      } else if (message != null && message.length() > 0) {
        showTicker(message);
      }
    } else if (state == SocketClient.STATE_CONNECTING) {
      showTicker(message);
    }
    updateCommands();
  }
}
