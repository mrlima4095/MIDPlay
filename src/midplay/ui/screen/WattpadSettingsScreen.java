package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextField;
import javax.microedition.rms.RecordStoreException;
import midplay.net.WattpadApi;
import midplay.store.Configuration;
import midplay.store.SettingsManager;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;

/** Wattpad-only settings: reading layout, content filter and search language. */
public final class WattpadSettingsScreen extends BaseForm {
  private final SettingsManager settings = SettingsManager.getInstance();
  private final TextField pageField;
  private final ChoiceGroup languageGroup;
  private final int[] languageIds;
  private final ChoiceGroup toggles;
  private final int matureIndex;

  private final int savedPage;
  private final int savedLanguage;
  private final int savedMature;

  public WattpadSettingsScreen(Navigator navigator) {
    super(Lang.tr("wattpad.settings_title"), navigator);
    savedPage = settings.getWattpadPageChars();
    savedLanguage = settings.getWattpadLanguage();
    savedMature = settings.getCurrentWattpadMature();

    pageField =
        new TextField(
            Lang.tr("settings.wattpad_page_chars"),
            Integer.toString(savedPage),
            6,
            TextField.NUMERIC);
    append(pageField);

    languageIds = WattpadApi.getLanguageIds();
    String[] languages = new String[languageIds.length + 1];
    languages[0] = Lang.tr("wattpad.language_any");
    for (int i = 0; i < languageIds.length; i++) {
      languages[i + 1] = WattpadApi.getLanguageName(languageIds[i]);
    }
    languageGroup = new ChoiceGroup(Lang.tr("settings.wattpad_language"), ChoiceGroup.POPUP);
    for (int i = 0; i < languages.length; i++) {
      languageGroup.append(languages[i], null);
    }
    languageGroup.setSelectedIndex(languageIndex(savedLanguage), true);
    append(languageGroup);

    toggles = new ChoiceGroup(Lang.tr("settings.options"), ChoiceGroup.MULTIPLE);
    matureIndex = toggles.append(Lang.tr("settings.wattpad_mature"), null);
    toggles.setSelectedIndex(
        matureIndex, savedMature == Configuration.WATTPAD_MATURE_ON);
    append(toggles);

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
    return savedPage != parsePage(pageField.getString())
        || savedLanguage != selectedLanguage()
        || savedMature != selectedMature();
  }

  private void save() {
    int page = parsePage(pageField.getString());
    int language = selectedLanguage();
    int mature = selectedMature();
    try {
      if (page != savedPage) {
        settings.saveWattpadPageChars(page);
      }
      if (language != savedLanguage) {
        settings.saveWattpadLanguage(language);
      }
      if (mature != savedMature) {
        settings.saveWattpadMature(mature);
      }
      navigator.back();
    } catch (RecordStoreException e) {
      navigator.showAlert(Lang.tr("settings.error.save_failed"), AlertType.ERROR);
    }
  }

  private int languageIndex(int id) {
    for (int i = 0; i < languageIds.length; i++) {
      if (languageIds[i] == id) {
        return i + 1;
      }
    }
    return 0;
  }

  private int selectedLanguage() {
    int index = languageGroup.getSelectedIndex();
    if (index <= 0 || index > languageIds.length) {
      return 0;
    }
    return languageIds[index - 1];
  }

  private int selectedMature() {
    return toggles.isSelected(matureIndex)
        ? Configuration.WATTPAD_MATURE_ON
        : Configuration.WATTPAD_MATURE_OFF;
  }

  private static int parsePage(String value) {
    try {
      int parsed = Integer.parseInt(value.trim());
      if (parsed < Configuration.WATTPAD_PAGE_CHARS_MIN) {
        return Configuration.WATTPAD_PAGE_CHARS_MIN;
      }
      if (parsed > Configuration.WATTPAD_PAGE_CHARS_MAX) {
        return Configuration.WATTPAD_PAGE_CHARS_MAX;
      }
      return parsed;
    } catch (Exception e) {
      return Configuration.DEFAULT_WATTPAD_PAGE_CHARS;
    }
  }
}
