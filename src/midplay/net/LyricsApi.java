package midplay.net;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import java.util.Hashtable;
import midplay.store.Configuration;
import midplay.util.Utils;

/**
 * URLs and JSON parsing for the lyrics proxy at
 * {@code http://opentty.fun/lyrics.php}. The proxy resolves the song on
 * letras.mus.br and returns only the plain lyrics, so the device does not have
 * to download the whole page.
 *
 * <p>Lyrics fetched during the current session are kept in memory (hits and
 * misses), so reopening the same song is instant and does not touch the
 * network again.
 */
public final class LyricsApi {
  /** Rows requested from the manual search. */
  public static final int SEARCH_LIMIT = 15;

  /** How many songs stay cached before the table is dropped. */
  private static final int CACHE_LIMIT = 24;

  /** Marker stored for a song the proxy could not find. */
  private static final String[] MISSING = new String[0];

  private static final Hashtable cache = new Hashtable();

  private LyricsApi() {}

  public static String lyricsUrl(String artist, String title) {
    return baseUrl()
        + "?op=lyrics&a="
        + Utils.urlEncode(artist == null ? "" : artist)
        + "&t="
        + Utils.urlEncode(title == null ? "" : title);
  }

  /** Lyrics of an exact letras.mus.br page picked from a manual search. */
  public static String lyricsByPathUrl(String path, String artist, String title) {
    return baseUrl()
        + "?op=lyrics&p="
        + Utils.urlEncode(path == null ? "" : path)
        + "&a="
        + Utils.urlEncode(artist == null ? "" : artist)
        + "&t="
        + Utils.urlEncode(title == null ? "" : title);
  }

  public static String searchUrl(String query) {
    return baseUrl()
        + "?op=search&n="
        + SEARCH_LIMIT
        + "&q="
        + Utils.urlEncode(query == null ? "" : query);
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

  /** Parses a manual search reply into rows of {@code {artist, title, path}}. */
  public static String[][] parseSearch(String json) {
    try {
      JSONObject root = JSON.getObject(json);
      if (root.getInt("ok", 0) == 0) {
        return null;
      }
      JSONArray rows = root.getArray("r");
      if (rows == null || rows.size() == 0) {
        return null;
      }
      String[][] out = new String[rows.size()][];
      int count = 0;
      for (int i = 0; i < rows.size(); i++) {
        JSONArray row = rows.getArray(i);
        if (row == null || row.size() < 3) {
          continue;
        }
        String path = row.getString(2);
        if (path == null || path.length() == 0) {
          continue;
        }
        out[count++] =
            new String[] {
              row.getString(0), row.getString(1), path
            };
      }
      if (count == 0) {
        return null;
      }
      if (count == out.length) {
        return out;
      }
      String[][] trimmed = new String[count][];
      System.arraycopy(out, 0, trimmed, 0, count);
      return trimmed;
    } catch (Exception e) {
      return null;
    }
  }

  // ---------------------------------------------------------------------------
  // Session cache

  public static String cacheKey(String artist, String title) {
    return "q|" + norm(artist) + "|" + norm(title);
  }

  public static String pathKey(String path) {
    return "p|" + norm(path);
  }

  /** Returns the cached entry, the {@link #isMissing missing} marker, or null. */
  public static String[] cacheGet(String key) {
    if (key == null) {
      return null;
    }
    Object value = cache.get(key);
    return value instanceof String[] ? (String[]) value : null;
  }

  public static boolean isMissing(String[] entry) {
    return entry != null && entry.length == 0;
  }

  /** Stores an entry; a null entry records that the song was not found. */
  public static void cachePut(String key, String[] entry) {
    if (key == null) {
      return;
    }
    if (cache.size() >= CACHE_LIMIT) {
      cache.clear();
    }
    cache.put(key, entry == null ? MISSING : entry);
  }

  /** Drops the in-memory lyrics cache (used by the hidden storage panel). */
  public static void clearCache() {
    cache.clear();
  }

  private static String norm(String s) {
    return s == null ? "" : s.trim().toLowerCase();
  }

  private static String baseUrl() {
    String base = Configuration.DEFAULT_LYRICS_API_URL;
    return base == null ? "" : base;
  }
}
