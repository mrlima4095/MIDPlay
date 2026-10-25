package midplay.net;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONObject;
import midplay.store.Configuration;
import midplay.util.Utils;

/**
 * URLs and JSON parsing for the lyrics proxy at
 * {@code http://opentty.fun/lyrics.php}. The proxy resolves the song on
 * letras.mus.br and returns only the plain lyrics, so the device does not have
 * to download the whole page.
 */
public final class LyricsApi {
  private LyricsApi() {}

  public static String lyricsUrl(String artist, String title) {
    return Configuration.DEFAULT_LYRICS_API_URL
        + "?op=lyrics&a="
        + Utils.urlEncode(artist == null ? "" : artist)
        + "&t="
        + Utils.urlEncode(title == null ? "" : title);
  }

  /**
   * Parses a successful proxy reply into {@code {artist, title, lyrics}}, or
   * returns null when the song was not found.
   */
  public static String[] parse(String json) {
    try {
      JSONObject root = JSON.getObject(json);
      if (root.getInt("ok", 0) == 0) {
        return null;
      }
      String lyrics = root.getString("l", "");
      if (lyrics == null || lyrics.trim().length() == 0) {
        return null;
      }
      return new String[] {
        root.getString("a", ""), root.getString("t", ""), lyrics
      };
    } catch (Exception e) {
      return null;
    }
  }
}
