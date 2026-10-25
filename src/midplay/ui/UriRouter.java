package midplay.ui;

import midplay.MIDPlay;

/**
 * Single entry point for opening links.
 *
 * <p>It resolves the configured lyrics providers and the links handled by the bundled subapps
 * (Wattpad, Pinterest, Reddit, GitHub and Telegram), and returns {@code false} for everything else
 * so the caller can hand the URI to the device browser. The Open... screen and the MPGram/GitHub
 * link bridges all go through here, so a new provider only needs one change.
 */
public final class UriRouter {
  private UriRouter() {}

  /**
   * Opens a URI. Returns false when it is not a known provider, so the caller can hand it to the
   * platform browser.
   */
  public static boolean open(Navigator navigator, String uri) {
    return open(navigator, uri, true);
  }

  /**
   * Link routing used by subapps. It omits whole-app hosts (GitHub/Telegram) to avoid bouncing a
   * link back into the app that is already showing it.
   */
  public static boolean openShared(Navigator navigator, String uri) {
    return open(navigator, uri, false);
  }

  private static boolean open(Navigator navigator, String uri, boolean allowHostApps) {
    if (navigator == null || uri == null) {
      return false;
    }
    String value = uri.trim();
    if (value.length() == 0) {
      return false;
    }
    if (LyricsLinks.isLyrics(value)) {
      LyricsLinks.open(navigator, value);
      return true;
    }
    if (WattpadLinks.isWattpad(value)) {
      WattpadLinks.open(navigator, value);
      return true;
    }
    if (PinterestLinks.isPinterest(value)) {
      PinterestLinks.open(navigator, value);
      return true;
    }
    if (RedditLinks.isReddit(value)) {
      RedditLinks.open(navigator, value);
      return true;
    }
    if (allowHostApps && isGitHub(value)) {
      MIDPlay.getInstance().openGitHubUri(value);
      return true;
    }
    if (allowHostApps && isTelegram(value)) {
      MIDPlay.getInstance().openTelegramUri(value);
      return true;
    }
    return false;
  }

  private static boolean isGitHub(String url) {
    String host = host(url);
    return host != null && (host.equals("github.com") || host.endsWith(".github.com"));
  }

  private static boolean isTelegram(String url) {
    String value = url.trim().toLowerCase();
    if (value.startsWith("tg://")) {
      return true;
    }
    String host = host(value);
    return host != null
        && (host.equals("t.me") || host.equals("telegram.me") || host.equals("telegram.dog"));
  }

  private static String host(String url) {
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
    int colon = host.indexOf(':');
    if (colon >= 0) {
      host = host.substring(0, colon);
    }
    host = host.toLowerCase();
    if (host.startsWith("www.")) {
      host = host.substring(4);
    }
    return host.length() == 0 ? null : host;
  }
}
