package midplay.store;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import java.util.Vector;
import midplay.net.RedditApi;

/**
 * Local Reddit saved posts: a single flat list (no folders). Only the compact metadata returned by
 * the proxy is stored, so the posts and their images stay online until the user explicitly
 * downloads an image.
 */
public final class RedditFavoritesManager {
  public static final int SUCCESS = 0;
  public static final int ALREADY_EXISTS = 1;
  public static final int FAILED = 2;

  private static RedditFavoritesManager instance;

  public static synchronized RedditFavoritesManager getInstance() {
    if (instance == null) {
      instance = new RedditFavoritesManager();
    }
    return instance;
  }

  private final JsonRecordStore storage = new JsonRecordStore("reddit_fav_posts", 1, "[]");
  private JSONArray posts;

  private RedditFavoritesManager() {}

  /** Saved posts, most recently saved first. */
  public Vector getPosts() {
    Vector result = new Vector();
    JSONArray items = posts();
    for (int i = 0; i < items.size(); i++) {
      String[] row = readPost(items.getObject(i));
      if (row != null) {
        result.addElement(row);
      }
    }
    return result;
  }

  public boolean isSaved(String id) {
    return id != null && indexOf(posts(), id) >= 0;
  }

  public int savePost(String[] post) {
    if (post == null || post[RedditApi.POST_ID].length() == 0) {
      return FAILED;
    }
    String id = post[RedditApi.POST_ID];
    if (isSaved(id)) {
      return ALREADY_EXISTS;
    }
    JSONArray next = new JSONArray();
    next.add(writePost(post));
    JSONArray current = posts();
    for (int i = 0; i < current.size(); i++) {
      next.add(current.getObject(i));
    }
    return save(next) ? SUCCESS : FAILED;
  }

  public boolean removePost(String id) {
    if (id == null) {
      return false;
    }
    return removePosts(new String[] {id}) > 0;
  }

  /** Removes several saved posts at once, returning how many were actually removed. */
  public int removePosts(String[] ids) {
    if (ids == null || ids.length == 0) {
      return 0;
    }
    JSONArray current = posts();
    JSONArray next = new JSONArray();
    int removed = 0;
    for (int i = 0; i < current.size(); i++) {
      JSONObject item = current.getObject(i);
      if (contains(ids, item.getString("id", ""))) {
        removed++;
      } else {
        next.add(item);
      }
    }
    if (removed > 0) {
      save(next);
    }
    return removed;
  }

  public void clear() {
    save(new JSONArray());
  }

  private static JSONObject writePost(String[] post) {
    JSONObject item = new JSONObject();
    item.put("id", post[RedditApi.POST_ID]);
    item.put("ti", post[RedditApi.POST_TITLE]);
    item.put("au", post[RedditApi.POST_AUTHOR]);
    item.put("sr", post[RedditApi.POST_SUBREDDIT]);
    item.put("ct", post[RedditApi.POST_CREATED]);
    item.put("sc", post[RedditApi.POST_SCORE]);
    item.put("nc", post[RedditApi.POST_COMMENTS]);
    item.put("tx", post[RedditApi.POST_TEXT]);
    item.put("u", post[RedditApi.POST_URL]);
    item.put("ns", post[RedditApi.POST_NSFW]);
    item.put("th", post[RedditApi.POST_THUMB]);
    item.put("pl", post[RedditApi.POST_PERMALINK]);
    item.put("fl", post[RedditApi.POST_FLAIR]);
    item.put("sf", post[RedditApi.POST_SELF]);
    return item;
  }

  private static String[] readPost(JSONObject item) {
    if (item == null) {
      return null;
    }
    String id = item.getString("id", "");
    if (id.length() == 0) {
      return null;
    }
    String[] row = new String[RedditApi.POST_SIZE];
    row[RedditApi.POST_ID] = id;
    row[RedditApi.POST_TITLE] = item.getString("ti", "");
    row[RedditApi.POST_AUTHOR] = item.getString("au", "");
    row[RedditApi.POST_SUBREDDIT] = item.getString("sr", "");
    row[RedditApi.POST_CREATED] = item.getString("ct", "");
    row[RedditApi.POST_SCORE] = item.getString("sc", "0");
    row[RedditApi.POST_COMMENTS] = item.getString("nc", "0");
    row[RedditApi.POST_TEXT] = item.getString("tx", "");
    row[RedditApi.POST_URL] = item.getString("u", "");
    row[RedditApi.POST_NSFW] = item.getString("ns", "0");
    row[RedditApi.POST_THUMB] = item.getString("th", "");
    row[RedditApi.POST_PERMALINK] = item.getString("pl", "");
    row[RedditApi.POST_FLAIR] = item.getString("fl", "");
    row[RedditApi.POST_SELF] = item.getString("sf", "0");
    return row;
  }

  private static boolean contains(String[] values, String value) {
    for (int i = 0; i < values.length; i++) {
      if (values[i] != null && values[i].equals(value)) {
        return true;
      }
    }
    return false;
  }

  private static int indexOf(JSONArray items, String id) {
    for (int i = 0; i < items.size(); i++) {
      try {
        if (id.equals(items.getObject(i).getString("id", ""))) {
          return i;
        }
      } catch (Exception e) {
      }
    }
    return -1;
  }

  private JSONArray posts() {
    if (posts == null) {
      try {
        posts = JSON.getArray(storage.load());
      } catch (Exception e) {
        posts = new JSONArray();
      }
    }
    return posts;
  }

  // The in-memory array is the source of truth for the session; the whole JSON document is written
  // by the background writer so a large list does not stall the UI thread.
  private boolean save(JSONArray next) {
    posts = next;
    storage.saveAsync(next.toString());
    return true;
  }
}
