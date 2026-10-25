package midplay.net;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONObject;
import midplay.store.Configuration;
import midplay.util.Lang;
import midplay.util.Utils;

/** Sends text to the MIDPlay translation proxy and reads the translated string. */
public final class TranslateApi {
  private TranslateApi() {}

  public static String url() {
    return Configuration.DEFAULT_TRANSLATE_URL;
  }

  /** Form body for the POST request; the proxy reads {@code tl} and {@code q}. */
  public static String body(String text) {
    return "tl="
        + Utils.urlEncode(targetLanguage())
        + "&q="
        + Utils.urlEncode(text == null ? "" : text);
  }

  /** Translates into the language the app is currently using. */
  public static String targetLanguage() {
    String lang = Lang.getCurrentLang();
    if (lang == null || lang.length() == 0) {
      return "en";
    }
    return lang;
  }

  /** Translated text, or an empty string when the proxy reported an error. */
  public static String parse(String json) {
    try {
      JSONObject root = JSON.getObject(json);
      if (root.getInt("ok", 0) != 1) {
        return "";
      }
      String text = root.getString("t", "");
      return text == null ? "" : text;
    } catch (Exception e) {
      return "";
    }
  }
}
