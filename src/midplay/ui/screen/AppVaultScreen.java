package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import midplay.store.Configuration;
import midplay.store.SettingsManager;
import midplay.ui.BaseList;
import midplay.ui.Navigator;
import midplay.util.Lang;

/**
 * Lets the user choose which subapps ask for the vault password before opening.
 *
 * <p>Rows use the same {@code [x]}/{@code [  ]} marks as the menu visibility screen, but here
 * {@code [x]} means locked. Every tap is saved immediately.
 */
public final class AppVaultScreen extends BaseList {
  private final SettingsManager settingsManager;
  private final Command passwordCommand;

  public AppVaultScreen(Navigator navigator) {
    super(Lang.tr("vault.apps"), navigator);
    this.settingsManager = SettingsManager.getInstance();
    passwordCommand = new Command(Lang.tr("vault.change_password"), Command.SCREEN, 5);
    addCommand(passwordCommand);
    populateItems();
  }

  /** Asks for the vault password (creating one on first use) and then opens this screen. */
  public static void open(final Navigator navigator) {
    VaultAuth.authenticate(
        navigator,
        new Runnable() {
          public void run() {
            AppVaultScreen screen = new AppVaultScreen(navigator);
            navigator.forward(screen);
            // Anchor the alert on the vault screen itself: capturing the current view here can
            // send the user back to the previous screen when setCurrent is asynchronous.
            navigator.showAlert(Lang.tr("vault.apps_instructions"), AlertType.INFO, screen);
          }
        });
  }

  protected void populateItems() {
    for (int i = 0; i < Configuration.VAULT_APP_KEYS.length; i++) {
      String key = Configuration.VAULT_APP_KEYS[i];
      append(rowText(key), Configuration.getMenuIcon(key));
    }
  }

  protected void handleSelection() {
    int index = getSelectedIndex();
    if (!isValidSelection(index, Configuration.VAULT_APP_KEYS.length)) {
      return;
    }
    String key = Configuration.VAULT_APP_KEYS[index];
    boolean locked = !getString(index).startsWith(Configuration.VISIBILITY_ICON);
    settingsManager.setVaultAppLocked(key, locked);
    String prefix =
        locked ? Configuration.VISIBILITY_ICON : Configuration.HIDDEN_ICON;
    set(index, prefix + Lang.tr(key), getImage(index));
  }

  private String rowText(String key) {
    String prefix =
        settingsManager.isVaultAppLocked(key)
            ? Configuration.VISIBILITY_ICON
            : Configuration.HIDDEN_ICON;
    return prefix + Lang.tr(key);
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == passwordCommand) {
      VaultAuth.changePassword(navigator);
    }
  }
}
