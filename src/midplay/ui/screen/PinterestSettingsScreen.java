package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextField;
import javax.microedition.rms.RecordStoreException;
import midplay.store.Configuration;
import midplay.store.SettingsManager;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;

/** Pinterest-only settings: proxy URLs, page size and the photo download folder. */
public final class PinterestSettingsScreen extends BaseForm {
  private final SettingsManager settings = SettingsManager.getInstance();
  private final TextField apiUrlField;
  private final TextField imageProxyField;
  private final TextField pinsField;
  private final TextField pathField;

  private final String savedApiUrl;
  private final String savedImageProxy;
  private final int savedPins;
  private final String savedPath;

  public PinterestSettingsScreen(Navigator navigator) {
    super(Lang.tr("pinterest.settings_title"), navigator);
    savedApiUrl = settings.getPinterestApiUrl();
    savedImageProxy = settings.getPinterestImageProxy();
    savedPins = settings.getPinterestPinsPerPage();
    savedPath = settings.getPinterestPath();

    apiUrlField =
        new TextField(Lang.tr("pinterest.settings_api_url"), savedApiUrl, 256, TextField.URL);
    append(apiUrlField);

    imageProxyField =
        new TextField(
            Lang.tr("pinterest.settings_image_proxy"), savedImageProxy, 256, TextField.URL);
    append(imageProxyField);

    pinsField =
        new TextField(
            Lang.tr("pinterest.settings_pins_per_page"),
            Integer.toString(savedPins),
            2,
            TextField.NUMERIC);
    append(pinsField);

    pathField =
        new TextField(Lang.tr("pinterest.settings_path"), savedPath, 256, TextField.URL);
    append(pathField);

    addCommand(Commands.formSave());
    addCommand(Commands.playerNowPlaying());
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == Commands.formSave()) {
      save();
    } else if (c == Commands.playerNowPlaying()) {
      PlayerNavHelper.showNowPlaying(navigator);
    }
  }

  protected boolean onBackPressed() {
    if (!isDirty()) {
      return false;
    }
    navigator.showConfirmationAlert(
        Lang.tr("confirm.discard_changes"),
        new Runnable() {
          public void run() {
            navigator.back();
          }
        },
        AlertType.WARNING);
    return true;
  }

  private boolean isDirty() {
    return !savedApiUrl.equals(apiUrlField.getString().trim())
        || !savedImageProxy.equals(imageProxyField.getString().trim())
        || savedPins != parsePins(pinsField.getString())
        || !savedPath.equals(pathField.getString().trim());
  }

  private void save() {
    String apiUrl = apiUrlField.getString().trim();
    String imageProxy = imageProxyField.getString().trim();
    int pins = parsePins(pinsField.getString());
    String path = pathField.getString().trim();
    try {
      if (!apiUrl.equals(savedApiUrl)) {
        settings.savePinterestApiUrl(apiUrl);
      }
      if (!imageProxy.equals(savedImageProxy)) {
        settings.savePinterestImageProxy(imageProxy);
      }
      if (pins != savedPins) {
        settings.savePinterestPinsPerPage(pins);
      }
      if (!path.equals(savedPath)) {
        settings.savePinterestPath(path);
      }
      navigator.back();
    } catch (RecordStoreException e) {
      navigator.showAlert(Lang.tr("settings.error.save_failed"), AlertType.ERROR);
    }
  }

  private static int parsePins(String value) {
    try {
      int parsed = Integer.parseInt(value.trim());
      if (parsed < Configuration.PINTEREST_PINS_PER_PAGE_MIN) {
        return Configuration.PINTEREST_PINS_PER_PAGE_MIN;
      }
      if (parsed > Configuration.PINTEREST_PINS_PER_PAGE_MAX) {
        return Configuration.PINTEREST_PINS_PER_PAGE_MAX;
      }
      return parsed;
    } catch (Exception e) {
      return Configuration.DEFAULT_PINTEREST_PINS_PER_PAGE;
    }
  }
}
