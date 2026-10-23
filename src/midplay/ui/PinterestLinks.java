package midplay.ui;

import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import midplay.MIDPlay;
import midplay.net.HttpTextOperation;
import midplay.net.PinterestApi;
import midplay.ui.screen.PinterestBoardScreen;
import midplay.ui.screen.PinterestPinScreen;
import midplay.ui.screen.PinterestUserScreen;
import midplay.util.Lang;

/**
 * Opens Pinterest links inside the app instead of sending them to the system browser. Handles pins,
 * boards and user profiles, and is used both by the "Open..." screen and by links clicked in MPGram.
 */
public final class PinterestLinks {
  private PinterestLinks() {}

  /** True when the URL points at pinterest.com or one of its regional subdomains. */
  public static boolean isPinterest(String url) {
    return path(url) != null;
  }

  /** Opens the link in the matching Pinterest screen. */
  public static void open(final Navigator navigator, String url) {
    String path = path(url);
    if (path == null) {
      error(navigator);
      return;
    }
    String[] segment = segments(path);
    if (segment.length == 0) {
      error(navigator);
      return;
    }
    String first = segment[0].toLowerCase();
    if (first.equals("pin") || first.equals("pins")) {
      if (segment.length > 1 && digits(segment[1]).length() > 0) {
        openPin(navigator, digits(segment[1]));
      } else {
        error(navigator);
      }
      return;
    }
    if (isReserved(first)) {
      error(navigator);
      return;
    }
    if (segment.length >= 2 && !isReserved(segment[1].toLowerCase())) {
      openBoard(navigator, segment[0], segment[1]);
      return;
    }
    openUser(navigator, segment[0]);
  }

  /** Path of a pinterest.com URL ("/user/board/"), or null for any other host. */
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
    if (!lowerHost.equals("pinterest.com") && !lowerHost.endsWith(".pinterest.com")) {
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

  private static String digits(String value) {
    int i = 0;
    while (i < value.length() && value.charAt(i) >= '0' && value.charAt(i) <= '9') {
      i++;
    }
    return value.substring(0, i);
  }

  /** Segments that are Pinterest features rather than a username. */
  private static boolean isReserved(String value) {
    return value.equals("search")
        || value.equals("ideas")
        || value.equals("explore")
        || value.equals("login")
        || value.equals("logout")
        || value.equals("signup")
        || value.equals("settings")
        || value.equals("business")
        || value.equals("today")
        || value.equals("news")
        || value.equals("resource")
        || value.equals("categories")
        || value.equals("discover")
        || value.equals("topic")
        || value.equals("_")
        || value.equals("pin-builder");
  }

  private static void openPin(final Navigator navigator, final String id) {
    navigator.showLoadingAlert(Lang.tr("pinterest.loading"));
    MIDPlay.startOperation(
        new HttpTextOperation(
            PinterestApi.pinUrl(id),
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        navigator.dismissAlert();
                        Vector pins = PinterestApi.parsePins(text);
                        if (pins.size() == 0) {
                          error(navigator);
                          return;
                        }
                        navigator.forward(
                            new PinterestPinScreen(navigator, (String[]) pins.elementAt(0)));
                      }
                    });
              }

              public void onError(Exception e) {
                fail(navigator);
              }
            }));
  }

  private static void openBoard(final Navigator navigator, final String user, final String slug) {
    navigator.showLoadingAlert(Lang.tr("pinterest.loading"));
    MIDPlay.startOperation(
        new HttpTextOperation(
            PinterestApi.boardUrl(user, slug),
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        navigator.dismissAlert();
                        String[] board = PinterestApi.parseBoard(text);
                        if (board == null) {
                          error(navigator);
                          return;
                        }
                        navigator.forward(new PinterestBoardScreen(navigator, board));
                      }
                    });
              }

              public void onError(Exception e) {
                fail(navigator);
              }
            }));
  }

  private static void openUser(final Navigator navigator, String username) {
    navigator.forward(new PinterestUserScreen(navigator, username));
  }

  private static void fail(final Navigator navigator) {
    navigator.callSerially(
        new Runnable() {
          public void run() {
            navigator.dismissAlert();
            error(navigator);
          }
        });
  }

  private static void error(Navigator navigator) {
    navigator.showAlert(Lang.tr("pinterest.link_error"), AlertType.ERROR);
  }
}
