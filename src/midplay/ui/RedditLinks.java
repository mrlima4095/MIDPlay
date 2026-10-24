package midplay.ui;

import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import midplay.MIDPlay;
import midplay.net.HttpTextOperation;
import midplay.net.RedditApi;
import midplay.ui.screen.RedditPostScreen;
import midplay.ui.screen.RedditResultsScreen;
import midplay.ui.screen.RedditScreen;
import midplay.util.Lang;

/**
 * Opens Reddit links inside the app instead of sending them to the system browser. Post links load
 * the post itself; subreddit and user links open their newest posts. Used by the "Open..." screen,
 * by links clicked in MPGram and by links clicked in GitHub.
 */
public final class RedditLinks {
  private RedditLinks() {}

  /** True when the URL points at reddit.com, one of its subdomains, or redd.it. */
  public static boolean isReddit(String url) {
    return path(url) != null;
  }

  /** Opens the link in the matching Reddit screen. */
  public static void open(final Navigator navigator, String url) {
    String path = path(url);
    if (path == null) {
      error(navigator);
      return;
    }
    String[] segment = segments(path);
    if (segment.length == 0) {
      // A bare reddit.com link opens the Reddit start screen.
      navigator.forward(new RedditScreen(navigator));
      return;
    }
    String first = segment[0].toLowerCase();
    if (first.equals("comments")) {
      openPost(navigator, segment.length > 1 ? segment[1] : "");
      return;
    }
    if (first.equals("r") && segment.length > 1) {
      String subreddit = segment[1];
      if (segment.length > 2 && segment[2].equalsIgnoreCase("comments")) {
        openPost(navigator, segment.length > 3 ? segment[3] : "");
      } else {
        openSubreddit(navigator, subreddit);
      }
      return;
    }
    if (first.equals("u") || first.equals("user") || first.equals("users")) {
      if (segment.length > 1) {
        openUser(navigator, segment[1]);
      } else {
        error(navigator);
      }
      return;
    }
    // redd.it/<id> short links.
    if (segment.length == 1) {
      openPost(navigator, segment[0]);
      return;
    }
    error(navigator);
  }

  /** Path of a Reddit URL ("/r/sub/comments/id/..."), or null for any other host. */
  private static String path(String url) {
    if (url == null) {
      return null;
    }
    String value = url.trim();
    int scheme = value.indexOf("://");
    if (scheme >= 0) {
      String protocol = value.substring(0, scheme).toLowerCase();
      if (!protocol.equals("http") && !protocol.equals("https")) {
        return null;
      }
      value = value.substring(scheme + 3);
    }
    int slash = value.indexOf('/');
    String host = slash < 0 ? value : value.substring(0, slash);
    String rest = slash < 0 ? "/" : value.substring(slash);
    String lowerHost = host.toLowerCase();
    int colon = lowerHost.indexOf(':');
    if (colon >= 0) {
      lowerHost = lowerHost.substring(0, colon);
    }
    boolean redditHost =
        lowerHost.equals("reddit.com")
            || lowerHost.endsWith(".reddit.com")
            || lowerHost.equals("redd.it");
    if (!redditHost) {
      return null;
    }
    int cut = rest.indexOf('?');
    if (cut >= 0) {
      rest = rest.substring(0, cut);
    }
    cut = rest.indexOf('#');
    if (cut >= 0) {
      rest = rest.substring(0, cut);
    }
    return rest;
  }

  private static String[] segments(String path) {
    Vector parts = new Vector();
    int start = 0;
    for (int i = 0; i <= path.length(); i++) {
      if (i == path.length() || path.charAt(i) == '/') {
        String part = path.substring(start, i);
        if (part.length() > 0) {
          parts.addElement(part);
        }
        start = i + 1;
      }
    }
    String[] result = new String[parts.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = (String) parts.elementAt(i);
    }
    return result;
  }

  private static void openPost(final Navigator navigator, final String id) {
    if (id == null || id.length() == 0) {
      error(navigator);
      return;
    }
    navigator.showLoadingAlert(Lang.tr("reddit.loading"));
    MIDPlay.startOperation(
        new HttpTextOperation(
            RedditApi.postUrl(id),
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        Vector posts = RedditApi.parsePosts(text);
                        if (posts.size() == 0) {
                          error(navigator);
                          return;
                        }
                        navigator.forward(
                            new RedditPostScreen(navigator, (String[]) posts.elementAt(0)));
                      }
                    });
              }

              public void onError(Exception e) {
                fail(navigator);
              }
            }));
  }

  private static void openSubreddit(final Navigator navigator, final String subreddit) {
    if (subreddit == null || subreddit.length() == 0) {
      error(navigator);
      return;
    }
    loadPosts(
        navigator,
        RedditApi.subPostsUrl(subreddit, "new", ""),
        RedditResultsScreen.SOURCE_SUBPOSTS,
        subreddit,
        "",
        "new",
        Lang.tr("reddit.results_posts", "r/" + subreddit));
  }

  private static void openUser(final Navigator navigator, final String username) {
    if (username == null || username.length() == 0) {
      error(navigator);
      return;
    }
    loadPosts(
        navigator,
        RedditApi.userPostsUrl(username, ""),
        RedditResultsScreen.SOURCE_USER_POSTS,
        username,
        "",
        "",
        Lang.tr("reddit.results_user_posts", username));
  }

  private static void loadPosts(
      final Navigator navigator,
      final String url,
      final int source,
      final String id,
      final String query,
      final String sort,
      final String title) {
    navigator.showLoadingAlert(Lang.tr("reddit.loading"));
    MIDPlay.startOperation(
        new HttpTextOperation(
            url,
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        Vector posts = RedditApi.parsePosts(text);
                        if (posts.size() == 0) {
                          error(navigator);
                          return;
                        }
                        navigator.forward(
                            new RedditResultsScreen(
                                navigator,
                                source,
                                id,
                                query,
                                sort,
                                title,
                                posts,
                                RedditApi.parseBookmark(text)));
                      }
                    });
              }

              public void onError(Exception e) {
                fail(navigator);
              }
            }));
  }

  private static void fail(final Navigator navigator) {
    navigator.callSerially(
        new Runnable() {
          public void run() {
            error(navigator);
          }
        });
  }

  private static void error(Navigator navigator) {
    navigator.showAlert(Lang.tr("reddit.link_error"), AlertType.ERROR);
  }
}
