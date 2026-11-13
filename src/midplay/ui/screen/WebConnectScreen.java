package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextField;
import javax.microedition.rms.RecordStoreException;
import midplay.MIDPlay;
import midplay.net.SocketClient;
import midplay.store.Configuration;
import midplay.store.SettingsManager;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;

public final class WebConnectScreen extends BaseForm implements SocketClient.Listener {
  private final TextField hostField;
  private final TextField portField;
  private final TextField userField;
  private final TextField passField;
  private final TextField deviceField;
  private final Command connectCmd;
  private final Command disconnectCmd;
  private final Command forgetCmd;
  private boolean awaitingConnection;
  private String pendingHost;
  private int pendingPort;
  private String pendingUser;
  private String pendingPassword;
  private String pendingDevice;

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
    addCommand(connectCmd);
    addCommand(Commands.back());
  }

  protected void showNotify() {
    SettingsManager sm = SettingsManager.getInstance();
    hostField.setString(sm.getRelayHost());
    portField.setString(String.valueOf(sm.getRelayPort()));
    userField.setString(sm.getRelayUser());
    passField.setString(sm.getRelayPassword());
    String deviceName = sm.getRelayDevice();
    if (deviceName.length() == 0) {
      deviceName = SocketClient.getInstance().getDeviceName();
    }
    if (deviceName != null && deviceName.length() > 0) {
      deviceField.setString(deviceName);
    } else {
      deviceField.setString("MIDPlay-" + System.currentTimeMillis() % 10000);
    }
    updateCommands();
    SocketClient.getInstance().setListener(this);
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == connectCmd) {
      doConnect();
    } else if (c == disconnectCmd) {
      doDisconnect();
    } else if (c == forgetCmd) {
      doForget();
    }
  }

  private void doConnect() {
    String host = hostField.getString().trim();
    String portStr = portField.getString().trim();
    String user = userField.getString().trim();
    String pass = passField.getString().trim();
    String device = deviceField.getString().trim();
    if (host.length() == 0) {
      navigator.showAlert(Lang.tr("web.host_empty"), AlertType.ERROR);
      return;
    }
    if (user.length() == 0) {
      navigator.showAlert(Lang.tr("web.user_empty"), AlertType.ERROR);
      return;
    }
    int port = Configuration.DEFAULT_RELAY_PORT;
    try {
      port = Integer.parseInt(portStr);
    } catch (NumberFormatException e) {
      port = Configuration.DEFAULT_RELAY_PORT;
    }
    if (port <= 0 || port > 65535) {
      port = Configuration.DEFAULT_RELAY_PORT;
    }
    if (device.length() == 0) {
      device = "MIDPlay-" + System.currentTimeMillis() % 10000;
    }
    pendingHost = host;
    pendingPort = port;
    pendingUser = user;
    pendingPassword = pass;
    pendingDevice = device;
    awaitingConnection = true;
    navigator.back();
    SocketClient.getInstance().connect(host, port, user, pass, device);
  }

  private void savePending() {
    SettingsManager sm = SettingsManager.getInstance();
    try {
      sm.saveRelayHost(pendingHost);
      sm.saveRelayPort(pendingPort);
      sm.saveRelayUser(pendingUser);
      sm.saveRelayPassword(pendingPassword);
      sm.saveRelayDevice(pendingDevice);
    } catch (RecordStoreException e) {
      navigator.showAlert(Lang.tr("settings.error.save_failed"), AlertType.ERROR);
      return;
    }
    navigator.showAlert(Lang.tr("web.saved"), AlertType.CONFIRMATION);
  }

  private void showConnectedAlert() {
    String detail = pendingHost + ":" + pendingPort + "\n" + pendingUser + " (" + pendingDevice + ")";
    navigator.showActionAlert(
        Lang.tr("web.connected") + "\n" + detail + "\n" + Lang.tr("web.save_prompt"),
        Lang.tr("action.save"),
        new Runnable() {
          public void run() {
            savePending();
          }
        });
  }

  private void doDisconnect() {
    SocketClient.getInstance().disconnect();
    navigator.showAlert(Lang.tr("web.disconnected"), AlertType.CONFIRMATION);
    updateCommands();
  }

  private void doForget() {
    SettingsManager sm = SettingsManager.getInstance();
    try {
      sm.saveRelayHost(Configuration.DEFAULT_RELAY_HOST);
      sm.saveRelayPort(Configuration.DEFAULT_RELAY_PORT);
      sm.saveRelayUser("");
      sm.saveRelayPassword("");
      sm.saveRelayDevice("");
    } catch (RecordStoreException e) {
      navigator.showAlert(Lang.tr("settings.error.save_failed"), AlertType.ERROR);
      return;
    }
    SocketClient.getInstance().disconnect();
    hostField.setString(sm.getRelayHost());
    portField.setString(String.valueOf(sm.getRelayPort()));
    userField.setString("");
    passField.setString("");
    navigator.showAlert(Lang.tr("web.forgotten"), AlertType.CONFIRMATION);
    updateCommands();
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

  public void onStateChanged(int state, String message) {
    if (state == SocketClient.STATE_ONLINE) {
      if (awaitingConnection) {
        awaitingConnection = false;
        showConnectedAlert();
      }
    } else if (state == SocketClient.STATE_OFFLINE) {
      if (awaitingConnection) {
        awaitingConnection = false;
        navigator.showAlert(message, AlertType.ERROR);
      }
    }
    updateCommands();
  }
}