package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;
import javax.microedition.rms.RecordStoreException;
import midplay.store.Configuration;
import midplay.store.SettingsManager;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;

/** RSS-only settings: proxy endpoints, the image proxy and the HTTPS browser proxy switch. */
public final class RssSettingsScreen extends BaseForm {
  private final SettingsManager settings = SettingsManager.getInstance();
  private final TextField apiUrlField;
  private final TextField imageProxyField;
  private final TextField browserProxyField;
  private final ChoiceGroup browserProxyGroup;

  private final String savedApiUrl;
  private final String savedImageProxy;
  private final String savedBrowserProxy;
  private final int savedBrowserProxyMode;

  public RssSettingsScreen(Navigator navigator) {
    super(Lang.tr("rss.settings_title"), navigator);
    savedApiUrl = settings.getRssApiUrl();
    savedImageProxy = settings.getRssImageProxy();
    savedBrowserProxy = settings.getRssBrowserProxy();
    savedBrowserProxyMode = settings.getRssBrowserProxyMode();

    apiUrlField = new TextField(Lang.tr("rss.settings_api_url"), savedApiUrl, 256, TextField.URL);
    append(apiUrlField);
    imageProxyField =
        new TextField(Lang.tr("rss.settings_image_proxy"), savedImageProxy, 256, TextField.URL);
    append(imageProxyField);
    browserProxyField =
        new TextField(
            Lang.tr("rss.settings_browser_proxy"), savedBrowserProxy, 256, TextField.URL);
    append(browserProxyField);

    browserProxyGroup = new ChoiceGroup(Lang.tr("settings.options"), ChoiceGroup.MULTIPLE);
    browserProxyGroup.append(Lang.tr("rss.settings_use_browser_proxy"), null);
    browserProxyGroup.setSelectedIndex(
        0, savedBrowserProxyMode == Configuration.RSS_BROWSER_PROXY_ON);
    append(browserProxyGroup);
    append(new StringItem(null, Lang.tr("rss.settings_browser_hint")));

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
        || !savedBrowserProxy.equals(browserProxyField.getString().trim())
        || savedBrowserProxyMode != currentBrowserProxyMode();
  }

  private int currentBrowserProxyMode() {
    return browserProxyGroup.isSelected(0)
        ? Configuration.RSS_BROWSER_PROXY_ON
        : Configuration.RSS_BROWSER_PROXY_OFF;
  }

  private void save() {
    String apiUrl = apiUrlField.getString().trim();
    String imageProxy = imageProxyField.getString().trim();
    String browserProxy = browserProxyField.getString().trim();
    try {
      if (!apiUrl.equals(savedApiUrl)) {
        settings.saveRssApiUrl(apiUrl);
      }
      if (!imageProxy.equals(savedImageProxy)) {
        settings.saveRssImageProxy(imageProxy);
      }
      if (!browserProxy.equals(savedBrowserProxy)) {
        settings.saveRssBrowserProxy(browserProxy);
      }
      if (currentBrowserProxyMode() != savedBrowserProxyMode) {
        settings.saveRssBrowserProxyMode(currentBrowserProxyMode());
      }
      navigator.back();
    } catch (RecordStoreException e) {
      navigator.showAlert(Lang.tr("settings.error.save_failed"), AlertType.ERROR);
    }
  }
}
