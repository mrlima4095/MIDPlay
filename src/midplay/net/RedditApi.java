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
 * URLs, compact row indexes and JSON parsing for the Reddit proxy at
 * {@code http://opentty.fun/reddit.php}. The proxy reads Reddit's public data from
 * the Arctic Shift archive, so no OAuth app or login is needed. Reddit's own
 * thumbnails and images are fetched through {@code http://opentty.fun/proxy.php}
 * so old MIDP devices never need to open an HTTPS connection directly.
 */
public final class RedditApi {
  /** Compact post row indexes (the proxy uses these short names to save bandwidth). */
  public static final int POST_ID = 0;
  public static final int POST_TITLE = 1;
  public static final int POST_AUTHOR = 2;
  public static final int POST_SUBREDDIT = 3;
  public static final int POST_CREATED = 4;
  public static final int POST_SCORE = 5;
  public static final int POST_COMMENTS = 6;
  public static final int POST_TEXT = 7;
  public static final int POST_URL = 8;
  public static final int POST_NSFW = 9;
  public static final int POST_THUMB = 10;
  public static final int POST_PERMALINK = 11;
  public static final int POST_FLAIR = 12;
  public static final int POST_SELF = 13;
  public static final int POST_SIZE = 14;

  /** Compact subreddit row indexes. */
  public static final int SUB_NAME = 0;
  public static final int SUB_TITLE = 1;
  public static final int SUB_DESCRIPTION = 2;
  public static final int SUB_SUBSCRIBERS = 3;
  public static final int SUB_NSFW = 4;
  public static final int SUB_ICON = 5;
  public static final int SUB_CREATED = 6;
  public static final int SUB_SIZE = 7;

  /** Compact user row indexes. */
  public static final int USER_NAME = 0;
  public static final int USER_KARMA = 1;
  public static final int USER_POSTS = 2;
  public static final int USER_COMMENTS = 3;
  public static final int USER_SIZE = 4;

  /** Compact threaded-comment row indexes. */
  public static final int COMMENT_BODY = 0;
  public static final int COMMENT_AUTHOR = 1;
  public static final int COMMENT_SCORE = 2;
  public static final int COMMENT_DEPTH = 3;
  public static final int COMMENT_CREATED = 4;
  public static final int COMMENT_SIZE = 5;

  public static final int MAX_THUMBNAILS = 12;

  private RedditApi() {}

  private static int pageSize() {
    return SettingsManager.getInstance().getRedditPostsPerPage();
  }

  private static String apiBase() {
    String base = SettingsManager.getInstance().getRedditApiUrl();
    if (base == null || base.length() == 0) {
      base = Configuration.DEFAULT_REDDIT_API_URL;
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

  private static void appendBookmark(StringBuffer url, String bookmark) {
    if (bookmark != null && bookmark.length() > 0) {
      url.append("&bm=").append(Utils.urlEncode(bookmark));
    }
  }

  public static String searchSubredditsUrl(String query) {
    return apiQuery(
        "op=search&type=subreddits&n=" + pageSize() + "&q=" + Utils.urlEncode(query));
  }

  public static String searchUsersUrl(String query) {
    return apiQuery("op=search&type=users&n=" + pageSize() + "&q=" + Utils.urlEncode(query));
  }

  public static String searchPostsUrl(String subreddit, String query, String bookmark) {
    StringBuffer url =
        new StringBuffer(
            apiQuery(
                "op=search&type=posts&n="
                    + pageSize()
                    + "&sub="
                    + Utils.urlEncode(subreddit)
                    + "&q="
                    + Utils.urlEncode(query)));
    appendBookmark(url, bookmark);
    return url.toString();
  }

  /** New or top posts of a subreddit. {@code sort} is {@code new} or {@code top}. */
  public static String subPostsUrl(String subreddit, String sort, String bookmark) {
    StringBuffer url =
        new StringBuffer(
            apiQuery(
                "op=subposts&n="
                    + pageSize()
                    + "&sub="
                    + Utils.urlEncode(subreddit)
                    + "&sort="
                    + Utils.urlEncode(sort == null ? "new" : sort)));
    appendBookmark(url, bookmark);
    return url.toString();
  }

  public static String userPostsUrl(String username, String bookmark) {
    StringBuffer url =
        new StringBuffer(
            apiQuery("op=userposts&n=" + pageSize() + "&u=" + Utils.urlEncode(username)));
    appendBookmark(url, bookmark);
    return url.toString();
  }

  public static String postUrl(String id) {
    return apiQuery("op=post&id=" + Utils.urlEncode(id));
  }

  public static String commentsUrl(String id) {
    return apiQuery("op=comments&id=" + Utils.urlEncode(id) + "&n=50");
  }

  /** Translates {@code text} into {@code target} through the proxy. */
  public static String translateUrl(String text, String target) {
    return apiQuery(
        "op=translate&tl=" + Utils.urlEncode(target) + "&q=" + Utils.urlEncode(text));
  }

  /** Target language for translation: the language the app is currently using. */
  public static String translateTarget() {
    String lang = Lang.getCurrentLang();
    if (lang == null || lang.length() == 0) {
      return "en";
    }
    return lang;
  }

  /** Routes a reddit thumbnail/link URL through the configured image proxy. */
  public static String imageUrl(String source) {
    if (source == null || source.length() == 0) {
      return "";
    }
    String proxy = SettingsManager.getInstance().getRedditImageProxy();
    if (proxy == null || proxy.length() == 0) {
      proxy = Configuration.DEFAULT_REDDIT_IMAGE_PROXY;
    }
    return proxy + Utils.urlEncode(source);
  }

  /** Public Reddit page of a post, built from its permalink. */
  public static String postWebUrl(String[] post) {
    String permalink = post[POST_PERMALINK];
    if (permalink != null && permalink.length() > 0) {
      if (permalink.startsWith("http://") || permalink.startsWith("https://")) {
        return permalink;
      }
      if (permalink.charAt(0) == '/') {
        return "https://www.reddit.com" + permalink;
      }
      return "https://www.reddit.com/" + permalink;
    }
    String id = post[POST_ID];
    if (id == null || id.length() == 0) {
      return "";
    }
    return "https://www.reddit.com/comments/" + id + "/";
  }

  public static String subredditWebUrl(String name) {
    return "https://www.reddit.com/r/" + (name == null ? "" : name) + "/";
  }

  public static String userWebUrl(String name) {
    return "https://www.reddit.com/user/" + (name == null ? "" : name) + "/";
  }

  public static Vector parsePosts(String json) {
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
        String[] row = new String[POST_SIZE];
        row[POST_ID] = item.getString("id", "");
        row[POST_TITLE] = item.getString("ti", "");
        row[POST_AUTHOR] = item.getString("au", "");
        row[POST_SUBREDDIT] = item.getString("sr", "");
        row[POST_CREATED] = item.getString("ct", "");
        row[POST_SCORE] = integerString(item, "sc");
        row[POST_COMMENTS] = integerString(item, "nc");
        row[POST_TEXT] = item.getString("tx", "");
        row[POST_URL] = item.getString("u", "");
        row[POST_NSFW] = integerString(item, "ns");
        row[POST_THUMB] = item.getString("th", "");
        row[POST_PERMALINK] = item.getString("pl", "");
        row[POST_FLAIR] = item.getString("fl", "");
        row[POST_SELF] = integerString(item, "sf");
        result.addElement(row);
      }
    } catch (Exception e) {
    }
    return result;
  }

  public static Vector parseSubreddits(String json) {
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
        String[] row = new String[SUB_SIZE];
        row[SUB_NAME] = item.getString("n", "");
        row[SUB_TITLE] = item.getString("ti", "");
        row[SUB_DESCRIPTION] = item.getString("d", "");
        row[SUB_SUBSCRIBERS] = integerString(item, "sc");
        row[SUB_NSFW] = integerString(item, "ns");
        row[SUB_ICON] = item.getString("ic", "");
        row[SUB_CREATED] = item.getString("ct", "");
        result.addElement(row);
      }
    } catch (Exception e) {
    }
    return result;
  }

  public static Vector parseUsers(String json) {
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
        String[] row = new String[USER_SIZE];
        row[USER_NAME] = item.getString("n", "");
        row[USER_KARMA] = integerString(item, "ka");
        row[USER_POSTS] = integerString(item, "pc");
        row[USER_COMMENTS] = integerString(item, "cc");
        result.addElement(row);
      }
    } catch (Exception e) {
    }
    return result;
  }

  public static Vector parseComments(String json) {
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
        String[] row = new String[COMMENT_SIZE];
        row[COMMENT_BODY] = item.getString("b", "");
        row[COMMENT_AUTHOR] = item.getString("a", "");
        row[COMMENT_SCORE] = integerString(item, "sc");
        row[COMMENT_DEPTH] = integerString(item, "dp");
        row[COMMENT_CREATED] = item.getString("ct", "");
        result.addElement(row);
      }
    } catch (Exception e) {
    }
    return result;
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

  /**
   * True when the proxy has no comments yet and could not reach the fallback feed; the comments
   * may appear in a moment, so the screen should invite the user to reload instead of saying the
   * post has none.
   */
  public static boolean commentsPending(String json) {
    try {
      return JSON.getObject(json).getInt("pd", 0) != 0;
    } catch (Exception e) {
      return false;
    }
  }

  /** Translated text returned by the {@code translate} op, or an empty string on failure. */
  public static String parseTranslation(String json) {
    try {
      String text = JSON.getObject(json).getString("t", "");
      return text == null ? "" : text;
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

  public static String postLabel(String[] post) {
    return postLabel(post, null);
  }

  /** Post label with an optional translated title while the subreddit/author stay original. */
  public static String postLabel(String[] post, String titleOverride) {
    String title = titleOverride == null ? post[POST_TITLE] : titleOverride;
    if (title == null || title.length() == 0) {
      title = Lang.tr("reddit.post");
    }
    StringBuffer label = new StringBuffer(title);
    String sub = post[POST_SUBREDDIT];
    if (sub != null && sub.length() > 0) {
      label.append(" [r/").append(sub).append(']');
    }
    String author = post[POST_AUTHOR];
    if (author != null && author.length() > 0) {
      label.append(" - u/").append(author);
    }
    return label.toString();
  }

  public static String subredditLabel(String[] sub) {
    String name = sub[SUB_NAME];
    if (name == null || name.length() == 0) {
      name = Lang.tr("reddit.subreddit");
    }
    StringBuffer label = new StringBuffer("r/").append(name);
    if (isNsfw(sub[SUB_NSFW])) {
      label.append(" [18+]");
    }
    String subscribers = sub[SUB_SUBSCRIBERS];
    if (subscribers != null && subscribers.length() > 0 && !"0".equals(subscribers)) {
      label.append(" - ").append(subscribers).append(' ').append(Lang.tr("reddit.members"));
    }
    return label.toString();
  }

  public static String userLabel(String[] user) {
    String name = user[USER_NAME];
    if (name == null || name.length() == 0) {
      name = Lang.tr("reddit.user");
    }
    StringBuffer label = new StringBuffer("u/").append(name);
    String karma = user[USER_KARMA];
    if (karma != null && karma.length() > 0 && !"0".equals(karma)) {
      label.append(" - ").append(karma).append(' ').append(Lang.tr("reddit.karma"));
    }
    return label.toString();
  }

  /** A single comment row rendered with indentation for its threading depth. */
  public static String commentLabel(String[] comment) {
    return commentLabel(comment, null);
  }

  /**
   * Same as {@link #commentLabel(String[])} but shows {@code bodyOverride} instead of the original
   * body when it is not null, so callers can render a translated comment.
   */
  public static String commentLabel(String[] comment, String bodyOverride) {
    StringBuffer label = new StringBuffer();
    int depth = intValue(comment[COMMENT_DEPTH]);
    if (depth > 0) {
      for (int i = 0; i < depth && i < 8; i++) {
        label.append("  ");
      }
      label.append("> ");
    }
    String author = comment[COMMENT_AUTHOR];
    if (author == null || author.length() == 0) {
      author = Lang.tr("reddit.unknown_author");
    }
    label.append("u/").append(author);
    String score = comment[COMMENT_SCORE];
    if (score != null && score.length() > 0 && !"0".equals(score)) {
      label.append(" (").append(score).append(')');
    }
    label.append('\n')
        .append(
            singleLine(
                bodyOverride != null && bodyOverride.length() > 0
                    ? bodyOverride
                    : comment[COMMENT_BODY],
                160));
    return label.toString();
  }

  public static String postDetails(String[] post) {
    return postDetails(post, null, null);
  }

  /**
   * Post details, optionally with a translated title/body while the author, score, flair and
   * link keep their original (untranslated) values.
   */
  public static String postDetails(String[] post, String titleOverride, String textOverride) {
    StringBuffer sb = new StringBuffer();
    String title = titleOverride == null ? post[POST_TITLE] : titleOverride;
    if (title != null && title.length() > 0) {
      sb.append(title).append('\n');
    }
    appendLine(sb, Lang.tr("reddit.author"), "u/" + post[POST_AUTHOR]);
    appendLine(sb, Lang.tr("reddit.subreddit_label"), "r/" + post[POST_SUBREDDIT]);
    appendLine(sb, Lang.tr("reddit.score"), post[POST_SCORE]);
    appendLine(sb, Lang.tr("reddit.comments_count"), post[POST_COMMENTS]);
    if (isNsfw(post[POST_NSFW])) {
      appendLine(sb, Lang.tr("reddit.nsfw"), "18+");
    }
    appendLine(sb, Lang.tr("reddit.flair"), post[POST_FLAIR]);
    String text = textOverride == null ? post[POST_TEXT] : textOverride;
    if (text != null && text.length() > 0) {
      sb.append('\n').append(text).append('\n');
    }
    String link = post[POST_URL];
    if (!isSelf(post[POST_SELF]) && link != null && link.length() > 0) {
      appendLine(sb, Lang.tr("reddit.link"), link);
    }
    return sb.toString();
  }

  public static String subredditDetails(String[] sub) {
    StringBuffer sb = new StringBuffer();
    String name = sub[SUB_NAME];
    sb.append("r/").append(name).append('\n');
    String title = sub[SUB_TITLE];
    if (title != null && title.length() > 0 && !title.equals(name)) {
      sb.append(title).append('\n');
    }
    if (isNsfw(sub[SUB_NSFW])) {
      sb.append(Lang.tr("reddit.nsfw")).append(": 18+\n");
    }
    appendLine(sb, Lang.tr("reddit.members"), sub[SUB_SUBSCRIBERS]);
    String description = sub[SUB_DESCRIPTION];
    if (description != null && description.length() > 0) {
      sb.append('\n').append(description).append('\n');
    }
    return sb.toString();
  }

  public static String userDetails(String[] user) {
    StringBuffer sb = new StringBuffer();
    sb.append("u/").append(user[USER_NAME]).append('\n');
    appendLine(sb, Lang.tr("reddit.karma"), user[USER_KARMA]);
    appendLine(sb, Lang.tr("reddit.post_count"), user[USER_POSTS]);
    appendLine(sb, Lang.tr("reddit.comment_count"), user[USER_COMMENTS]);
    return sb.toString();
  }

  public static boolean isNsfw(String value) {
    return "1".equals(value);
  }

  private static boolean isSelf(String value) {
    return "1".equals(value);
  }

  private static int intValue(String value) {
    try {
      return Integer.parseInt(value);
    } catch (Exception e) {
      return 0;
    }
  }

  /** Collapses whitespace and bounds a block of text to a single preview line. */
  private static String singleLine(String text, int max) {
    String value = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
    if (value.length() > max) {
      value = value.substring(0, max) + "...";
    }
    return value;
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
