package midplay.net;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import java.util.Vector;
import midplay.store.Configuration;
import midplay.store.SettingsManager;
import midplay.util.Lang;
import midplay.util.Utils;

/**
 * URLs, compact row indexes and JSON parsing for the Pinterest proxy at
 * {@code http://opentty.fun/pinterest.php}. Images are always fetched through
 * {@code http://opentty.fun/proxy.php} so old MIDP devices never need to open an
 * HTTPS connection directly.
 */
public final class PinterestApi {
  /** Compact pin row indexes (the proxy uses these short names to save bandwidth). */
  public static final int PIN_ID = 0;
  public static final int PIN_TITLE = 1;
  public static final int PIN_DESCRIPTION = 2;
  public static final int PIN_IMAGE = 3;
  public static final int PIN_IMAGE_LARGE = 4;
  public static final int PIN_USER_NAME = 5;
  public static final int PIN_USER = 6;
  public static final int PIN_BOARD_NAME = 7;
  public static final int PIN_BOARD_USER = 8;
  public static final int PIN_BOARD_SLUG = 9;
  public static final int PIN_CREATED = 10;
  public static final int PIN_SIZE = 11;

  /** Compact board row indexes. */
  public static final int BOARD_ID = 0;
  public static final int BOARD_NAME = 1;
  public static final int BOARD_DESCRIPTION = 2;
  public static final int BOARD_COVER = 3;
  public static final int BOARD_USER = 4;
  public static final int BOARD_SLUG = 5;
  public static final int BOARD_PIN_COUNT = 6;
  public static final int BOARD_FOLLOWER_COUNT = 7;
  public static final int BOARD_SIZE = 8;

  /** Compact user row indexes. */
  public static final int USER_USERNAME = 0;
  public static final int USER_NAME = 1;
  public static final int USER_ABOUT = 2;
  public static final int USER_IMAGE = 3;
  public static final int USER_FOLLOWERS = 4;
  public static final int USER_PIN_COUNT = 5;
  public static final int USER_BOARD_COUNT = 6;
  public static final int USER_SIZE = 7;

  public static final int MAX_THUMBNAILS = 12;

  private PinterestApi() {}

  private static int pageSize() {
    return SettingsManager.getInstance().getPinterestPinsPerPage();
  }

  private static String apiBase() {
    String base = SettingsManager.getInstance().getPinterestApiUrl();
    if (base == null || base.length() == 0) {
      base = Configuration.DEFAULT_PINTEREST_API_URL;
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

  public static String searchPinsUrl(String query, String bookmark) {
    return searchUrl(query, "pins", bookmark);
  }

  public static String searchBoardsUrl(String query, String bookmark) {
    return searchUrl(query, "boards", bookmark);
  }

  private static String searchUrl(String query, String scope, String bookmark) {
    StringBuffer url =
        new StringBuffer(apiQuery("op=search&scope=" + scope + "&n=" + pageSize()));
    url.append("&q=").append(Utils.urlEncode(query));
    appendBookmark(url, bookmark);
    return url.toString();
  }

  public static String userUrl(String username) {
    return apiQuery("op=user&u=" + Utils.urlEncode(username));
  }

  /** Pin detail by numeric id; ids with a non-numeric prefix are rejected by the proxy. */
  public static String pinUrl(String id) {
    return apiQuery("op=pin&id=" + Utils.urlEncode(id));
  }

  public static String boardUrl(String username, String slug) {
    return apiQuery(
        "op=board&u=" + Utils.urlEncode(username) + "&s=" + Utils.urlEncode(slug));
  }

  public static String userPinsUrl(String username, String bookmark) {
    StringBuffer url =
        new StringBuffer(
            apiQuery("op=userpins&n=" + pageSize() + "&u=" + Utils.urlEncode(username)));
    appendBookmark(url, bookmark);
    return url.toString();
  }

  public static String userBoardsUrl(String username, String bookmark) {
    StringBuffer url =
        new StringBuffer(
            apiQuery("op=userboards&n=" + pageSize() + "&u=" + Utils.urlEncode(username)));
    appendBookmark(url, bookmark);
    return url.toString();
  }

  public static String boardPinsUrl(String username, String slug, String bookmark) {
    return boardPinsUrl(username, slug, "", bookmark);
  }

  public static String boardPinsUrl(
      String username, String slug, String boardId, String bookmark) {
    StringBuffer url =
        new StringBuffer(
            apiQuery(
                "op=boardpins&n="
                    + pageSize()
                    + "&u="
                    + Utils.urlEncode(username)
                    + "&s="
                    + Utils.urlEncode(slug)));
    if (boardId != null && boardId.length() > 0) {
      url.append("&bid=").append(Utils.urlEncode(boardId));
    }
    appendBookmark(url, bookmark);
    return url.toString();
  }

  private static void appendBookmark(StringBuffer url, String bookmark) {
    if (bookmark != null && bookmark.length() > 0) {
      url.append("&bm=").append(Utils.urlEncode(bookmark));
    }
  }

  /** Routes an i.pinimg.com URL through the configured image proxy. */
  public static String imageUrl(String source) {
    if (source == null || source.length() == 0) {
      return "";
    }
    String proxy = SettingsManager.getInstance().getPinterestImageProxy();
    if (proxy == null || proxy.length() == 0) {
      proxy = Configuration.DEFAULT_PINTEREST_IMAGE_PROXY;
    }
    return proxy + Utils.urlEncode(source);
  }

  public static String pinWebUrl(String id) {
    return "https://www.pinterest.com/pin/" + id + "/";
  }

  /**
   * Best shareable link for a pin: the public page when the id is numeric, otherwise the direct
   * image URL (search results use internal ids that have no public Pinterest page).
   */
  public static String pinShareUrl(String[] pin) {
    String id = pin[PIN_ID];
    if (isNumeric(id)) {
      return pinWebUrl(id);
    }
    String image = pin[PIN_IMAGE_LARGE];
    if (image == null || image.length() == 0) {
      image = pin[PIN_IMAGE];
    }
    return image == null ? "" : image;
  }

  private static boolean isNumeric(String value) {
    if (value == null || value.length() == 0) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }

  public static String boardWebUrl(String username, String slug) {
    return "https://www.pinterest.com/" + username + "/" + slug + "/";
  }

  public static String userWebUrl(String username) {
    return "https://www.pinterest.com/" + username + "/";
  }

  public static Vector parsePins(String json) {
    Vector result = new Vector();
    try {
      JSONArray items = JSON.getObject(json).getArray("it");
      if (items == null) {
        return result;
      }
      for (int i = 0; i < items.size(); i++) {
        JSONObject item = items.getObject(i);
        if (item == null) {
          continue;
        }
        String[] row = new String[PIN_SIZE];
        row[PIN_ID] = item.getString("id", "");
        row[PIN_TITLE] = item.getString("ti", "");
        row[PIN_DESCRIPTION] = item.getString("de", "");
        row[PIN_IMAGE] = item.getString("im", "");
        row[PIN_IMAGE_LARGE] = item.getString("im4", "");
        row[PIN_USER_NAME] = item.getString("un", "");
        row[PIN_USER] = item.getString("us", "");
        row[PIN_BOARD_NAME] = item.getString("bd", "");
        row[PIN_BOARD_USER] = item.getString("bus", "");
        row[PIN_BOARD_SLUG] = item.getString("bs", "");
        row[PIN_CREATED] = item.getString("ct", "");
        result.addElement(row);
      }
    } catch (Exception e) {
    }
    return result;
  }

  public static Vector parseBoards(String json) {
    Vector result = new Vector();
    try {
      JSONArray items = JSON.getObject(json).getArray("it");
      if (items == null) {
        return result;
      }
      for (int i = 0; i < items.size(); i++) {
        JSONObject item = items.getObject(i);
        if (item == null) {
          continue;
        }
        String[] row = new String[BOARD_SIZE];
        row[BOARD_ID] = item.getString("id", "");
        row[BOARD_NAME] = item.getString("n", "");
        row[BOARD_DESCRIPTION] = item.getString("d", "");
        row[BOARD_COVER] = item.getString("c", "");
        row[BOARD_USER] = item.getString("us", "");
        row[BOARD_SLUG] = item.getString("sl", "");
        row[BOARD_PIN_COUNT] = integerString(item, "pc");
        row[BOARD_FOLLOWER_COUNT] = integerString(item, "fc");
        result.addElement(row);
      }
    } catch (Exception e) {
    }
    return result;
  }

  /** Returns the compact board row from an {@code op=board} response, or null. */
  public static String[] parseBoard(String json) {
    try {
      JSONObject item = JSON.getObject(json).getObject("b");
      if (item == null) {
        return null;
      }
      String[] row = new String[BOARD_SIZE];
      row[BOARD_ID] = item.getString("id", "");
      row[BOARD_NAME] = item.getString("n", "");
      row[BOARD_DESCRIPTION] = item.getString("d", "");
      row[BOARD_COVER] = item.getString("c", "");
      row[BOARD_USER] = item.getString("us", "");
      row[BOARD_SLUG] = item.getString("sl", "");
      row[BOARD_PIN_COUNT] = integerString(item, "pc");
      row[BOARD_FOLLOWER_COUNT] = integerString(item, "fc");
      if (row[BOARD_ID].length() == 0) {
        return null;
      }
      return row;
    } catch (Exception e) {
      return null;
    }
  }

  /** Returns the compact user row, or null when the response has no valid user. */
  public static String[] parseUser(String json) {
    try {
      JSONObject user = JSON.getObject(json).getObject("u");
      if (user == null) {
        return null;
      }
      String username = user.getString("us", "");
      if (username.length() == 0) {
        return null;
      }
      String[] row = new String[USER_SIZE];
      row[USER_USERNAME] = username;
      row[USER_NAME] = user.getString("un", "");
      row[USER_ABOUT] = user.getString("ab", "");
      row[USER_IMAGE] = user.getString("im", "");
      row[USER_FOLLOWERS] = integerString(user, "fc");
      row[USER_PIN_COUNT] = integerString(user, "pc");
      row[USER_BOARD_COUNT] = integerString(user, "bc");
      return row;
    } catch (Exception e) {
      return null;
    }
  }

  /** Bookmark for the next page, or an empty string when there are no more items. */
  public static String parseBookmark(String json) {
    try {
      String next = JSON.getObject(json).getString("nx", "");
      return next == null ? "" : next;
    } catch (Exception e) {
      return "";
    }
  }

  /** Error message returned by the proxy, or an empty string when the response is valid. */
  public static String parseError(String json) {
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

  public static int countItems(String json) {
    try {
      JSONArray items = JSON.getObject(json).getArray("it");
      return items == null ? 0 : items.size();
    } catch (Exception e) {
      return 0;
    }
  }

  /** Strips a leading @ from a typed username and removes surrounding whitespace. */
  public static String cleanUsername(String username) {
    String value = username == null ? "" : username.trim();
    if (value.startsWith("@")) {
      value = value.substring(1).trim();
    }
    return value;
  }

  public static String pinLabel(String[] pin) {
    String title = pin[PIN_TITLE];
    if (title == null || title.length() == 0) {
      title = Lang.tr("pinterest.pin");
    }
    StringBuffer label = new StringBuffer(title);
    String user = pin[PIN_USER_NAME];
    if (user == null || user.length() == 0) {
      user = pin[PIN_USER];
    }
    if (user != null && user.length() > 0) {
      label.append(" - ").append(user);
    }
    String board = pin[PIN_BOARD_NAME];
    if (board != null && board.length() > 0) {
      label.append(" [").append(board).append(']');
    }
    return label.toString();
  }

  public static String boardLabel(String[] board) {
    String name = board[BOARD_NAME];
    if (name == null || name.length() == 0) {
      name = Lang.tr("pinterest.board");
    }
    StringBuffer label = new StringBuffer(name);
    String user = board[BOARD_USER];
    if (user != null && user.length() > 0) {
      label.append(" - @").append(user);
    }
    String count = board[BOARD_PIN_COUNT];
    if (count != null && count.length() > 0 && !"0".equals(count)) {
      label.append(" (").append(count).append(' ').append(Lang.tr("pinterest.pins_short")).append(')');
    }
    return label.toString();
  }

  public static String pinDetails(String[] pin) {
    StringBuffer sb = new StringBuffer();
    appendLine(sb, Lang.tr("pinterest.title_label"), pin[PIN_TITLE]);
    appendLine(sb, Lang.tr("pinterest.author"), userLabel(pin[PIN_USER_NAME], pin[PIN_USER]));
    String board = pin[PIN_BOARD_NAME];
    if (board != null && board.length() > 0) {
      appendLine(sb, Lang.tr("pinterest.board"), board);
    }
    String description = pin[PIN_DESCRIPTION];
    if (description != null && description.length() > 0) {
      sb.append('\n').append(description).append('\n');
    }
    return sb.toString();
  }

  public static String boardDetails(String[] board) {
    StringBuffer sb = new StringBuffer();
    appendLine(sb, Lang.tr("pinterest.board"), board[BOARD_NAME]);
    appendLine(sb, Lang.tr("pinterest.board_owner"), userLabel("", board[BOARD_USER]));
    appendLine(sb, Lang.tr("pinterest.board_pin_count"), board[BOARD_PIN_COUNT]);
    appendLine(sb, Lang.tr("pinterest.board_followers"), board[BOARD_FOLLOWER_COUNT]);
    String description = board[BOARD_DESCRIPTION];
    if (description != null && description.length() > 0) {
      sb.append('\n').append(description).append('\n');
    }
    return sb.toString();
  }

  public static String userDetails(String[] user) {
    StringBuffer sb = new StringBuffer();
    String name = user[USER_NAME];
    if (name == null || name.length() == 0) {
      name = user[USER_USERNAME];
    }
    sb.append(name).append('\n');
    sb.append('@').append(user[USER_USERNAME]).append('\n');
    String about = user[USER_ABOUT];
    if (about == null || about.length() == 0) {
      about = Lang.tr("pinterest.no_description");
    }
    sb.append('\n').append(about).append('\n');
    appendLine(sb, Lang.tr("pinterest.followers"), user[USER_FOLLOWERS]);
    appendLine(sb, Lang.tr("pinterest.pin_count"), user[USER_PIN_COUNT]);
    appendLine(sb, Lang.tr("pinterest.board_count"), user[USER_BOARD_COUNT]);
    return sb.toString();
  }

  private static String userLabel(String name, String username) {
    StringBuffer sb = new StringBuffer();
    if (name != null && name.length() > 0) {
      sb.append(name);
    }
    if (username != null && username.length() > 0) {
      if (sb.length() > 0) {
        sb.append(" (@").append(username).append(')');
      } else {
        sb.append('@').append(username);
      }
    }
    return sb.toString();
  }

  private static void appendLine(StringBuffer sb, String label, String value) {
    if (value == null || value.length() == 0 || "0".equals(value)) {
      return;
    }
    sb.append(label).append(": ").append(value).append('\n');
  }

  private static String integerString(JSONObject obj, String key) {
    try {
      return Integer.toString(obj.getInt(key, 0));
    } catch (Exception e) {
      return "0";
    }
  }
}
