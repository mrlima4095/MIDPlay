package midplay.net;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import java.util.Vector;
import midplay.model.RssArticle;
import midplay.model.RssItem;
import midplay.model.RssSubscription;
import midplay.store.Configuration;
import midplay.util.Lang;
import midplay.util.Utils;

/**
 * URLs and JSON parsing for the RSS proxy at {@code http://opentty.fun/rss.php}.
 *
 * <p>The proxy downloads and parses RSS 2.0, Atom, RDF and JSON Feed documents, strips their HTML
 * and returns compact JSON. The phone never opens an HTTPS connection and never parses XML; feed
 * images are loaded through the same image proxy used by the other subapps.
 */
public final class RssApi {
  /** Compact item row indexes. */
  public static final int ITEM_ID = 0;
  public static final int ITEM_TITLE = 1;
  public static final int ITEM_LINK = 2;
  public static final int ITEM_TIME = 3;
  public static final int ITEM_AUTHOR = 4;
  public static final int ITEM_SUMMARY = 5;
  public static final int ITEM_IMAGE = 6;
  public static final int ITEM_SIZE = 7;

  private RssApi() {}

  private static String apiBase() {
    String base = Configuration.DEFAULT_RSS_API_URL;
    if (base == null || base.length() == 0) {
      return "http://opentty.fun/rss.php";
    }
    int hash = base.indexOf('#');
    if (hash >= 0) {
      base = base.substring(0, hash);
    }
    return base;
  }

  private static String apiQuery(String params) {
    String base = apiBase();
    return base + (base.indexOf('?') >= 0 ? "&" : "?") + params;
  }

  public static String discoverUrl(String url) {
    return apiQuery("op=discover&url=" + Utils.urlEncode(url));
  }

  public static String feedUrl(String feed) {
    return apiQuery("op=feed&url=" + Utils.urlEncode(feed));
  }

  public static String articleUrl(String feed, String id) {
    return apiQuery("op=article&url=" + Utils.urlEncode(feed) + "&id=" + Utils.urlEncode(id));
  }

  /** Routes a feed image through the configured image proxy. */
  public static String imageUrl(String source) {
    if (source == null || source.length() == 0) {
      return "";
    }
    return Configuration.DEFAULT_RSS_IMAGE_PROXY + Utils.urlEncode(source);
  }

  /** Returns the proxy error code, or an empty string when the response is a success. */
  public static String parseErrorCode(String json) {
    try {
      JSONObject root = JSON.getObject(json);
      if (root.getInt("ok", 0) != 0) {
        return "";
      }
      String error = root.getString("e", "");
      return error == null ? "" : error;
    } catch (Exception e) {
      return "";
    }
  }

  /** Localized message for a proxy error code. */
  public static String errorMessage(String code) {
    if (code == null || code.length() == 0) {
      return Lang.tr("rss.error.generic");
    }
    if (code.startsWith("http_")) {
      return Lang.tr("rss.error.http");
    }
    if ("missing_url".equals(code)) {
      return Lang.tr("rss.error.missing_url");
    }
    if ("invalid_url".equals(code)) {
      return Lang.tr("rss.error.invalid_url");
    }
    if ("not_a_feed".equals(code)) {
      return Lang.tr("rss.not_feed");
    }
    if ("not_found".equals(code)) {
      return Lang.tr("rss.error.not_found");
    }
    if ("missing_id".equals(code)) {
      return Lang.tr("rss.error.not_found");
    }
    if ("blocked".equals(code)) {
      return Lang.tr("rss.error.blocked");
    }
    if ("too_large".equals(code)) {
      return Lang.tr("rss.error.too_large");
    }
    if ("too_many_redirects".equals(code)) {
      return Lang.tr("rss.error.too_many_redirects");
    }
    if ("fetch_failed".equals(code)) {
      return Lang.tr("rss.error.fetch_failed");
    }
    return Lang.tr("rss.error.generic");
  }

  public static RssSubscription parseFeed(String json) {
    try {
      JSONObject feed = JSON.getObject(json).getObject("f");
      if (feed == null) {
        return null;
      }
      return new RssSubscription(
          feed.getString("u", ""),
          feed.getString("t", ""),
          feed.getString("s", ""),
          feed.getString("d", ""),
          feed.getString("i", ""));
    } catch (Exception e) {
      return null;
    }
  }

  public static Vector parseItems(String json) {
    Vector result = new Vector();
    try {
      JSONArray items = JSON.getObject(json).getArray("it");
      if (items == null) {
        return result;
      }
      for (int i = 0; i < items.size(); i++) {
        JSONArray row = items.getArray(i);
        if (row == null || row.size() < ITEM_SIZE) {
          continue;
        }
        result.addElement(
            new RssItem(
                row.getString(ITEM_ID),
                row.getString(ITEM_TITLE),
                row.getString(ITEM_LINK),
                JSONObject.getLong(row.get(ITEM_TIME)),
                row.getString(ITEM_AUTHOR),
                row.getString(ITEM_SUMMARY),
                row.getString(ITEM_IMAGE)));
      }
    } catch (Exception e) {
    }
    return result;
  }

  public static RssArticle parseArticle(String json) {
    try {
      JSONObject item = JSON.getObject(json).getObject("i");
      if (item == null) {
        return null;
      }
      return new RssArticle(
          item.getString("t", ""),
          item.getString("l", ""),
          item.getString("a", ""),
          item.getLong("ts", 0L),
          item.getString("c", ""),
          item.getString("m", ""));
    } catch (Exception e) {
      return null;
    }
  }

  /** Row label: the title, with a compact date when the feed publishes one. */
  public static String itemLabel(RssItem item) {
    String title = item.title;
    if (title == null || title.length() == 0) {
      title = item.link;
    }
    if (title == null || title.length() == 0) {
      title = Lang.tr("rss.item");
    }
    return title;
  }

  /** "dd/MM/yyyy HH:mm" for the article header, or an empty string. */
  public static String formatTimestamp(long seconds) {
    if (seconds <= 0) {
      return "";
    }
    return midplay.util.DateTime.format(seconds * 1000L);
  }
}
