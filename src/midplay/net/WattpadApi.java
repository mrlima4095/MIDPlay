package midplay.net;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import java.util.Vector;
import midplay.store.SettingsManager;
import midplay.util.Lang;
import midplay.util.Utils;

/**
 * URLs and JSON parsing for the Wattpad v3 endpoints. Wattpad has no documented public API (v4 is
 * gone), so this relies on the internal v3 endpoints that the web reader itself uses; the parsing is
 * defensive because the shape may change without notice.
 */
public final class WattpadApi {
  /** Indexes into the story rows returned by {@link #parseStories}. */
  public static final int STORY_ID = 0;

  public static final int STORY_TITLE = 1;
  public static final int STORY_AUTHOR = 2;
  public static final int STORY_PARTS = 3;
  public static final int STORY_MATURE = 4;
  public static final int STORY_SIZE = 5;

  /** Indexes into the chapter rows returned by {@link #parseParts}. */
  public static final int PART_ID = 0;
  public static final int PART_TITLE = 1;
  public static final int PART_SIZE = 2;

  /** Indexes into the user rows returned by {@link #parseUsers}. */
  public static final int USER_NAME = 0;
  public static final int USER_DISPLAY = 1;
  public static final int USER_SIZE = 2;

  private static final String SEARCH_FIELDS =
      "&fields=stories(id,title,numParts,mature,user(name))";
  private static final int SEARCH_LIMIT = 20;

  /**
   * Wattpad language ids (1..57, 51 unused). The names are the native labels the site itself shows,
   * so no translation is needed; ids 47 and 52 repeat a name and are dropped from the picker.
   */
  private static final String[] LANGUAGE_NAMES = {
    null,
    "English",
    "Français",
    "Italiano",
    "Deutsch",
    "Español",
    "Português",
    "Русский",
    "繁體中文",
    "日本語",
    "한국어",
    "Other",
    "简体中文",
    "Nederlands",
    "Polski",
    "Română",
    "العربية",
    "עברית",
    "Filipino",
    "Tiếng Việt",
    "Bahasa Indonesia",
    "हिन्दी",
    "Bahasa Melayu",
    "Türkçe",
    "Česky",
    "മലയാളം",
    "Svenska",
    "Norsk",
    "Magyar",
    "Dansk",
    "ελληνικά",
    "فارسی",
    "ภาษาไทย",
    "Íslenska",
    "Suomi",
    "Eesti",
    "Latviešu",
    "Lietuvių",
    "Català",
    "Босански",
    "Српски",
    "Hrvatski",
    "Slovenščina",
    "Български",
    "Slovenčina",
    "Беларускі",
    "Українська",
    "हिन्दी",
    "اُردُو",
    "தமிழ்",
    "Kiswahili",
    null,
    "Português",
    "ગુજરાતી",
    "ଓଡ଼ିଆ",
    "ਪੰਜਾਬੀ",
    "অসমীয়া",
    "मराठी"
  };

  private static int[] languageIds;

  private WattpadApi() {}

  /** Native name of a Wattpad language id, or null when the id is unknown. */
  public static String getLanguageName(int id) {
    if (id <= 0 || id >= LANGUAGE_NAMES.length) {
      return null;
    }
    return LANGUAGE_NAMES[id];
  }

  /** Language ids offered in the search filter: no gaps and no repeated names, in id order. */
  public static int[] getLanguageIds() {
    if (languageIds == null) {
      Vector ids = new Vector();
      for (int i = 1; i < LANGUAGE_NAMES.length; i++) {
        String name = LANGUAGE_NAMES[i];
        if (name == null) {
          continue;
        }
        boolean duplicate = false;
        for (int k = 0; k < ids.size(); k++) {
          int known = ((Integer) ids.elementAt(k)).intValue();
          if (name.equals(LANGUAGE_NAMES[known])) {
            duplicate = true;
            break;
          }
        }
        if (!duplicate) {
          ids.addElement(new Integer(i));
        }
      }
      int[] result = new int[ids.size()];
      for (int i = 0; i < result.length; i++) {
        result[i] = ((Integer) ids.elementAt(i)).intValue();
      }
      languageIds = result;
    }
    return languageIds;
  }

  public static int getSearchLimit() {
    return SEARCH_LIMIT;
  }

  /** Number of story rows in a response, before the mature-content filter is applied. */
  public static int countStories(String json) {
    try {
      JSONArray stories = JSON.getObject(json).getArray("stories");
      return stories == null ? 0 : stories.size();
    } catch (Exception e) {
      return 0;
    }
  }

  /** Number of user rows in a response. */
  public static int countUsers(String json) {
    try {
      JSONArray users = JSON.getArray(json);
      return users == null ? 0 : users.size();
    } catch (Exception e) {
      return 0;
    }
  }

  /** Search endpoint URL, already routed through the configured proxy. */
  public static String searchUrl(String query, int offset, int language) {
    StringBuffer url = new StringBuffer();
    url.append("https://www.wattpad.com/api/v3/stories?query=")
        .append(Utils.urlEncode(query))
        .append("&limit=")
        .append(SEARCH_LIMIT)
        .append("&offset=")
        .append(offset);
    if (getLanguageName(language) != null) {
      url.append("&language=").append(language);
    }
    url.append(SEARCH_FIELDS);
    return route(url.toString());
  }

  /** Story details (description and chapter list) URL, already routed through the proxy. */
  public static String storyUrl(String storyId) {
    return route("https://www.wattpad.com/api/v3/stories/" + storyId);
  }

  /** User search URL, already routed through the proxy. */
  public static String usersUrl(String query, int offset) {
    return route(
        "https://www.wattpad.com/api/v3/users?query="
            + Utils.urlEncode(query)
            + "&limit="
            + SEARCH_LIMIT
            + "&offset="
            + offset
            + "&fields=username,name,avatar");
  }

  /** Stories published by a user, already routed through the proxy. */
  public static String userStoriesUrl(String username, int offset) {
    return route(
        "https://www.wattpad.com/api/v3/users/"
            + Utils.urlEncode(username)
            + "/stories?limit="
            + SEARCH_LIMIT
            + "&offset="
            + offset
            + SEARCH_FIELDS);
  }

  /** Stories of a public reading list, already routed through the proxy. */
  public static String listStoriesUrl(String listId, int offset) {
    return route(
        "https://www.wattpad.com/api/v3/lists/"
            + listId
            + "/stories?limit="
            + SEARCH_LIMIT
            + "&offset="
            + offset
            + SEARCH_FIELDS);
  }

  /** Similar stories (recommendations) for a story, already routed through the proxy. */
  public static String recommendationsUrl(String storyId) {
    return route(
        "https://www.wattpad.com/api/v3/stories/"
            + storyId
            + "/recommendations?limit="
            + SEARCH_LIMIT);
  }

  /** Public profile of one user, already routed through the proxy. */
  public static String userUrl(String username) {
    return route("https://www.wattpad.com/api/v3/users/" + Utils.urlEncode(username));
  }

  /** Metadata of one chapter (used to find out which story it belongs to). */
  public static String partInfoUrl(String partId) {
    return route("https://www.wattpad.com/api/v3/story_parts/" + partId);
  }

  /** Metadata of a reading list (its name, among other fields). */
  public static String listInfoUrl(String listId) {
    return route("https://www.wattpad.com/api/v3/lists/" + listId);
  }

  /** One page of a chapter's text, already routed through the proxy. */
  public static String chapterUrl(String partId, int page) {
    return route("https://www.wattpad.com/apiv2/?m=storytext&id=" + partId + "&page=" + page);
  }

  private static String route(String url) {
    return SettingsManager.getInstance().routeHttp(url);
  }

  /**
   * Parses a search response into rows laid out with the {@code STORY_*} indexes. Stories marked as
   * mature are skipped unless {@code includeMature} is set.
   */
  public static Vector parseStories(String json, boolean includeMature) {
    Vector result = new Vector();
    try {
      JSONArray stories = JSON.getObject(json).getArray("stories");
      if (stories == null) {
        return result;
      }
      for (int i = 0; i < stories.size(); i++) {
        JSONObject story = stories.getObject(i);
        if (story == null) {
          continue;
        }
        boolean mature = story.getBoolean("mature", false);
        if (mature && !includeMature) {
          continue;
        }
        String id = story.getString("id", "");
        if (id.length() == 0) {
          continue;
        }
        String[] row = new String[STORY_SIZE];
        row[STORY_ID] = id;
        row[STORY_TITLE] = story.getString("title", Lang.tr("wattpad.title"));
        row[STORY_AUTHOR] = authorOf(story);
        row[STORY_PARTS] = Integer.toString(story.getInt("numParts", 0));
        row[STORY_MATURE] = mature ? "1" : "0";
        result.addElement(row);
      }
    } catch (Exception e) {
    }
    return result;
  }

  private static String authorOf(JSONObject story) {
    try {
      JSONObject user = story.getObject("user");
      if (user != null) {
        return user.getString("name", "");
      }
    } catch (Exception e) {
    }
    return "";
  }

  /** Parses the story description; empty when the response cannot be read. */
  public static String parseDescription(String json) {
    try {
      String description = JSON.getObject(json).getString("description", "");
      return description == null ? "" : description.trim();
    } catch (Exception e) {
      return "";
    }
  }

  /** Parses the story title; empty when the response cannot be read. */
  public static String parseTitle(String json) {
    try {
      String title = JSON.getObject(json).getString("title", "");
      return title == null ? "" : title.trim();
    } catch (Exception e) {
      return "";
    }
  }

  /** Username of the author of a story; empty when it cannot be read. */
  public static String parseAuthorUsername(String json) {
    try {
      JSONObject user = JSON.getObject(json).getObject("user");
      if (user != null) {
        String username = user.getString("username", "");
        if (username.length() == 0) {
          username = user.getString("name", "");
        }
        return username;
      }
    } catch (Exception e) {
    }
    return "";
  }

  /** Multi-line overview of a story: author, chapters, reads, votes, status, language and tags. */
  public static String parseStoryDetails(String json) {
    StringBuffer sb = new StringBuffer();
    try {
      JSONObject story = JSON.getObject(json);
      appendLine(sb, Lang.tr("wattpad.story_title"), story.getString("title", ""));
      JSONObject user = null;
      try {
        user = story.getObject("user");
      } catch (Exception ignored) {
      }
      if (user != null) {
        String name = user.getString("fullname", "");
        if (name.length() == 0) {
          name = user.getString("name", "");
        }
        String username = user.getString("username", "");
        if (username.length() == 0) {
          username = user.getString("name", "");
        }
        String author = name;
        if (username.length() > 0 && !username.equals(name)) {
          author =
              author.length() == 0 ? "@" + username : author + " (@" + username + ")";
        }
        appendLine(sb, Lang.tr("wattpad.story_author"), author);
      }
      appendLine(sb, Lang.tr("wattpad.story_parts"), Integer.toString(story.getInt("numParts", 0)));
      appendLine(sb, Lang.tr("wattpad.story_reads"), Long.toString(story.getLong("readCount", 0)));
      appendLine(sb, Lang.tr("wattpad.story_votes"), Long.toString(story.getLong("voteCount", 0)));
      appendLine(
          sb,
          Lang.tr("wattpad.story_completed"),
          story.getBoolean("completed", false) ? Lang.tr("wattpad.yes") : Lang.tr("wattpad.no"));
      String tags = tagsOf(story);
      if (tags.length() > 0) {
        appendLine(sb, Lang.tr("wattpad.story_tags"), tags);
      }
      String language = languageNameOf(story);
      if (language != null) {
        appendLine(sb, Lang.tr("wattpad.story_language"), language);
      }
      String description = story.getString("description", "");
      if (description != null && description.trim().length() > 0) {
        sb.append('\n').append(description.trim());
      }
    } catch (Exception e) {
    }
    return sb.toString();
  }

  /** Language name from either an id or an embedded {"id":..,"name":..} object. */
  private static String languageNameOf(JSONObject obj) {
    try {
      JSONObject language = obj.getObject("language");
      if (language != null) {
        String indexed = getLanguageName(language.getInt("id", 0));
        if (indexed != null) {
          return indexed;
        }
        String named = language.getString("name", "");
        if (named.length() > 0) {
          return named;
        }
      }
    } catch (Exception ignored) {
    }
    try {
      return getLanguageName(obj.getInt("language", 0));
    } catch (Exception e) {
      return null;
    }
  }

  private static String tagsOf(JSONObject story) {    StringBuffer sb = new StringBuffer();
    try {
      JSONArray tags = story.getArray("tags");
      if (tags != null) {
        for (int i = 0; i < tags.size(); i++) {
          String tag = tags.getString(i);
          if (tag == null || tag.length() == 0) {
            continue;
          }
          if (sb.length() > 0) {
            sb.append(", ");
          }
          sb.append(tag);
        }
      }
    } catch (Exception e) {
    }
    return sb.toString();
  }

  private static void appendLine(StringBuffer sb, String label, String value) {
    if (value == null || value.length() == 0) {
      return;
    }
    sb.append(label).append(": ").append(value).append('\n');
  }

  /** Parses the reading list name; empty when the response cannot be read. */
  public static String parseListName(String json) {
    try {
      String name = JSON.getObject(json).getString("name", "");
      return name == null ? "" : name.trim();
    } catch (Exception e) {
      return "";
    }
  }

  /** Story id that owns a chapter, read from a {@code story_parts} response. */
  public static String parsePartStoryId(String json) {
    try {
      String id = JSON.getObject(json).getString("groupId", "");
      return id == null ? "" : id.trim();
    } catch (Exception e) {
      return "";
    }
  }

  /** Parses a user search response into rows laid out with the {@code USER_*} indexes. */
  public static Vector parseUsers(String json) {
    Vector result = new Vector();
    try {
      JSONArray users = JSON.getArray(json);
      for (int i = 0; i < users.size(); i++) {
        JSONObject user = users.getObject(i);
        if (user == null) {
          continue;
        }
        String username = user.getString("username", "");
        if (username.length() == 0) {
          continue;
        }
        String[] row = new String[USER_SIZE];
        row[USER_NAME] = username;
        row[USER_DISPLAY] = user.getString("name", "");
        result.addElement(row);
      }
    } catch (Exception e) {
    }
    return result;
  }

  /** Parses the chapter list into rows laid out with the {@code PART_*} indexes. */
  public static Vector parseParts(String json) {
    Vector result = new Vector();
    try {
      JSONArray parts = JSON.getObject(json).getArray("parts");
      if (parts == null) {
        return result;
      }
      for (int i = 0; i < parts.size(); i++) {
        JSONObject part = parts.getObject(i);
        if (part == null) {
          continue;
        }
        String id = part.getString("id", "");
        if (id.length() == 0) {
          continue;
        }
        String[] row = new String[PART_SIZE];
        row[PART_ID] = id;
        row[PART_TITLE] = part.getString("title", "");
        result.addElement(row);
      }
    } catch (Exception e) {
    }
    return result;
  }

  /** Parses the similar stories (recommendations) endpoint. */
  public static Vector parseRecommendations(String json, boolean includeMature) {
    Vector result = new Vector();
    try {
      JSONArray recs = JSON.getArray(json);
      if (recs == null) {
        return result;
      }
      for (int i = 0; i < recs.size(); i++) {
        JSONObject story = recs.getObject(i);
        if (story == null) {
          continue;
        }
        boolean mature = story.getBoolean("mature", false);
        if (mature && !includeMature) {
          continue;
        }
        String id = story.getString("id", "");
        if (id.length() == 0) {
          continue;
        }
        String[] row = new String[STORY_SIZE];
        row[STORY_ID] = id;
        row[STORY_TITLE] = story.getString("title", Lang.tr("wattpad.title"));
        row[STORY_AUTHOR] = authorOf(story);
        row[STORY_PARTS] = Integer.toString(story.getInt("numParts", 0));
        row[STORY_MATURE] = mature ? "1" : "0";
        result.addElement(row);
      }
    } catch (Exception e) {
    }
    return result;
  }

  /** Multi-line public profile of a user. */
  public static String parseUserDetails(String json) {
    StringBuffer sb = new StringBuffer();
    try {
      JSONObject user = JSON.getObject(json);
      String name = user.getString("name", "");
      String username = user.getString("username", "");
      sb.append(name.length() == 0 ? username : name).append('\n');
      if (username.length() > 0) {
        sb.append('@').append(username).append('\n');
      }
      String bio = user.getString("description", "");
      if (bio == null || bio.trim().length() == 0) {
        bio = Lang.tr("wattpad.author_no_bio");
      }
      sb.append('\n').append(bio.trim()).append('\n');
      appendLine(sb, Lang.tr("wattpad.author_followers"), Long.toString(user.getLong("numFollowers", 0)));
      appendLine(sb, Lang.tr("wattpad.author_votes"), Long.toString(user.getLong("votesReceived", 0)));
      appendLine(
          sb,
          Lang.tr("wattpad.author_published"),
          Integer.toString(user.getInt("numStoriesPublished", 0)));
      String created = user.getString("createDate", "");
      if (created.length() > 10) {
        created = created.substring(0, 10);
      }
      appendLine(sb, Lang.tr("wattpad.author_joined"), created);
      String language = languageNameOf(user);
      if (language != null) {
        appendLine(sb, Lang.tr("wattpad.author_language"), language);
      }
      appendLine(
          sb,
          Lang.tr("wattpad.author_verified"),
          user.getBoolean("verified", false) ? Lang.tr("wattpad.yes") : Lang.tr("wattpad.no"));
    } catch (Exception e) {
    }
    return sb.toString();
  }


  /** Label shown for a chapter row: "{index}. {title}", falling back to a generic chapter name. */
  public static String chapterLabel(int index, String title) {
    String label = title == null ? "" : title.trim();
    if (label.length() == 0) {
      label = Lang.tr("wattpad.chapter") + " " + (index + 1);
    }
    return (index + 1) + ". " + label;
  }
}
