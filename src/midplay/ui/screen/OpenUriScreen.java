package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextField;
import javax.microedition.rms.RecordStoreException;
import midplay.MIDPlay;
import midplay.store.Configuration;
import midplay.store.SettingsManager;
import midplay.store.UriHistoryManager;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;
import mpgram.MP;

public final class OpenUriScreen extends BaseForm {
  private final TextField uriField;
  private final ChoiceGroup history;
  private final Command useHistory;
  private final Command clearHistory;
  private final Command enableEconomyCommand;
  private final Command disableEconomyCommand;

  public OpenUriScreen(Navigator navigator) {
    super(Lang.tr("open_uri.title"), navigator);
    uriField = new TextField(null, "", 255, TextField.ANY);
    append(uriField);
    history = new ChoiceGroup(Lang.tr("open_uri.history"), Choice.POPUP, UriHistoryManager.getInstance().getItems(), null);
    append(history);
    useHistory = new Command(Lang.tr("open_uri.use"), Command.SCREEN, 1);
    clearHistory = new Command(Lang.tr("open_uri.clear"), Command.SCREEN, 2);
    enableEconomyCommand = new Command(Lang.tr("open_uri.economy_enable"), Command.SCREEN, 3);
    disableEconomyCommand = new Command(Lang.tr("open_uri.economy_disable"), Command.SCREEN, 3);
    addCommand(Commands.ok());
    addCommand(useHistory);
    addCommand(clearHistory);
    updateEconomyCommand();
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == useHistory) {
      useSelectedHistory();
      return;
    }
    if (c == clearHistory) {
      UriHistoryManager.getInstance().clear();
      refreshHistory();
      return;
    }
    if (c == enableEconomyCommand || c == disableEconomyCommand) {
      toggleEconomyMode();
      return;
    }
    if (c != Commands.ok()) {
      return;
    }
    String uri = uriField.getString().trim();
    if (uri.length() == 0) {
      navigator.showAlert(Lang.tr("open_uri.empty"), AlertType.ERROR);
      return;
    }
    if (uri.equalsIgnoreCase("htop")) {
      // Hidden diagnostic command; it stays in the history like any other
      // shortcut typed in this screen.
      UriHistoryManager.getInstance().record("htop");
      refreshHistory();
      navigator.forward(new TaskViewerScreen(navigator));
      return;
    }
    if (uri.equalsIgnoreCase("speedtest")) {
      // Hidden network tool. It stays in the history so it can be reopened
      // without typing the keyword again.
      UriHistoryManager.getInstance().record("speedtest");
      refreshHistory();
      navigator.forward(new SpeedTestScreen(navigator));
      return;
    }
    if (uri.equalsIgnoreCase("rms")) {
      // Hidden storage panel; kept in the history like the other diagnostic shortcuts.
      UriHistoryManager.getInstance().record("rms");
      refreshHistory();
      navigator.forward(new StorageScreen(navigator));
      return;
    }
    if (MIDPlay.getInstance().openInternalUri(uri)) {
      UriHistoryManager.getInstance().record(uri);
      refreshHistory();
      return;
    }
    try {
      MIDPlay.getInstance().openUri(uri);
      UriHistoryManager.getInstance().record(uri);
      refreshHistory();
    } catch (Exception e) {
      navigator.showAlert(e.toString(), AlertType.ERROR);
    }
  }

  private void useSelectedHistory() {
    int selected = history.getSelectedIndex();
    if (selected >= 0) {
      uriField.setString(history.getString(selected));
    }
  }

  private void refreshHistory() {
    history.deleteAll();
    String[] items = UriHistoryManager.getInstance().getItems();
    for (int i = 0; i < items.length; i++) {
      history.append(items[i], null);
    }
  }

  /**
   * Toggles battery-saving mode. When enabled, MPGram's background keep-alive and notification
   * loops stop; turning it off starts them again exactly as they were configured.
   */
  private void toggleEconomyMode() {
    int current = SettingsManager.getInstance().getEconomyMode();
    final boolean enable = current != Configuration.ECONOMY_MODE_ON;
    try {
      SettingsManager.getInstance()
          .saveEconomyMode(enable ? Configuration.ECONOMY_MODE_ON : Configuration.ECONOMY_MODE_OFF);
    } catch (RecordStoreException e) {
      navigator.showAlert(Lang.tr("settings.error.save_failed"), AlertType.ERROR);
      return;
    }
    try {
      MP.setEconomyMode(enable);
    } catch (Throwable t) {
    }
    updateEconomyCommand();
    navigator.showAlert(
        Lang.tr(enable ? "open_uri.economy_enabled" : "open_uri.economy_disabled"),
        AlertType.CONFIRMATION);
  }

  private void updateEconomyCommand() {
    boolean on = SettingsManager.getInstance().getEconomyMode() == Configuration.ECONOMY_MODE_ON;
    removeCommand(enableEconomyCommand);
    removeCommand(disableEconomyCommand);
    addCommand(on ? disableEconomyCommand : enableEconomyCommand);
  }
}
