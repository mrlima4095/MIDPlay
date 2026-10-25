package midplay.ui;

import midplay.MIDPlay;
import midplay.model.Track;
import midplay.player.PlayerGUI;
import midplay.player.PlayerScreen;
import midplay.store.Configuration;
import midplay.store.SettingsManager;
import midplay.ui.screen.AppVaultScreen;
import midplay.ui.screen.LyricsScreen;
import midplay.ui.screen.LyricsSearchScreen;
import midplay.ui.screen.SearchScreen;
import midplay.ui.screen.TaskViewerScreen;
import midplay.ui.screen.VaultAuth;
import midplay.ui.screen.VaultScreen;

/**
 * Single entry point for opening links.
 *
 * <p>It understands native {@code midplay://} links, bare native aliases typed in the Open...
 * screen (such as {@code htop}), links from the configured subapps and lyrics providers, and falls
 * back to the device browser for everything else. The menu, the Open... screen and the MPGram/GitHub
 * link handlers all go through here, so adding a new destination only needs one change.
 */
public final class UriRouter {
  private UriRouter() {}

  /**
   * Opens a URI. Returns false when it is not a native link or a known provider, so the caller can
   * hand it to the platform browser.
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
    String key = nativeKey(value);
    if (key != null) {
      openNative(navigator, key, value);
      return true;
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

  /** Canonical native key for a {@code midplay://} link or a bare alias, or null. */
  public static String nativeKey(String uri) {
    if (uri == null) {
      return null;
    }
    String value = uri.trim();
    if (value.regionMatches(true, 0, "midplay:", 0, 8)) {
      value = value.substring(8);
      while (value.startsWith("//")) {
        value = value.substring(2);
      }
    }
    int cut = value.indexOf('?');
    if (cut >= 0) {
      value = value.substring(0, cut);
    }
    cut = value.indexOf('#');
    if (cut >= 0) {
      value = value.substring(0, cut);
    }
    value = value.trim().toLowerCase();
    while (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    return alias(value);
  }

  private static String alias(String value) {
    if (value.equals("menu.search") || value.equals("search") || value.equals("pesquisar")) {
      return "search";
    }
    if (value.equals("menu.favorites")
        || value.equals("favorites")
        || value.equals("favourites")) {
      return "favorites";
    }
    if (value.equals("menu.discover_playlists")
        || value.equals("discover")
        || value.equals("discover_playlists")) {
      return "discover";
    }
    if (value.equals("menu.recent") || value.equals("recent") || value.equals("recents")) {
      return "recent";
    }
    if (value.equals("menu.downloads") || value.equals("downloads")) {
      return "downloads";
    }
    if (value.equals("menu.files")
        || value.equals("files")
        || value.equals("filebrowser")
        || value.equals("file_browser")) {
      return "files";
    }
    if (value.equals("menu.settings") || value.equals("settings") || value.equals("config")) {
      return "settings";
    }
    if (value.equals("menu.about") || value.equals("about")) {
      return "about";
    }
    if (value.equals("menu.equalizer") || value.equals("equalizer") || value.equals("eq")) {
      return "equalizer";
    }
    if (value.equals("menu.mpgram") || value.equals("mpgram")) {
      return "mpgram";
    }
    if (value.equals("menu.github") || value.equals("github") || value.equals("gh")) {
      return "github";
    }
    if (value.equals("menu.notes") || value.equals("notes")) {
      return "notes";
    }
    if (value.equals("menu.tasks") || value.equals("tasks")) {
      return "tasks";
    }
    if (value.equals("menu.wattpad") || value.equals("wattpad")) {
      return "wattpad";
    }
    if (value.equals("menu.pinterest") || value.equals("pinterest")) {
      return "pinterest";
    }
    if (value.equals("menu.reddit") || value.equals("reddit")) {
      return "reddit";
    }
    if (value.equals("menu.now_playing")
        || value.equals("playing")
        || value.equals("nowplaying")
        || value.equals("now_playing")) {
      return "playing";
    }
    if (value.equals("menu.open_uri") || value.equals("open_uri") || value.equals("open")) {
      return "open_uri";
    }
    if (value.equals("htop")
        || value.equals("taskviewer")
        || value.equals("task_viewer")
        || value.equals("monitor")) {
      return "htop";
    }
    if (value.equals("lyrics") || value.equals("letra") || value.equals("letras")) {
      return "lyrics";
    }
    if (value.equals("vault") || value.equals("cofre")) {
      return "vault";
    }
    if (value.equals("appvault") || value.equals("app_vault") || value.equals("vault.apps")) {
      return "appvault";
    }
    if (value.equals("queue")) {
      return "queue";
    }
    return null;
  }

  private static void openNative(
      final Navigator navigator, final String key, String uri) {
    if ("htop".equals(key)) {
      navigator.forward(new TaskViewerScreen(navigator));
      return;
    }
    if ("lyrics".equals(key)) {
      openLyrics(navigator, uri);
      return;
    }
    if ("vault".equals(key)) {
      VaultAuth.authenticate(
          navigator,
          new Runnable() {
            public void run() {
              navigator.forward(new VaultScreen(navigator));
            }
          });
      return;
    }
    if ("appvault".equals(key)) {
      AppVaultScreen.open(navigator);
      return;
    }
    if ("search".equals(key)) {
      String query = param(uri, "q");
      if (query != null && query.length() > 0) {
        navigator.forward(new SearchScreen(navigator, query));
        return;
      }
    }
    final String lockedApp = vaultKey(key);
    Runnable action =
        new Runnable() {
          public void run() {
            MIDPlay.getInstance().openNativeKey(key);
          }
        };
    if (lockedApp != null && SettingsManager.getInstance().isVaultAppLocked(lockedApp)) {
      VaultAuth.authenticate(navigator, action);
    } else {
      action.run();
    }
  }

  private static String vaultKey(String key) {
    if ("mpgram".equals(key)) {
      return Configuration.MENU_MPGRAM;
    }
    if ("github".equals(key)) {
      return Configuration.MENU_GITHUB;
    }
    if ("notes".equals(key)) {
      return Configuration.MENU_NOTES;
    }
    if ("tasks".equals(key)) {
      return Configuration.MENU_TASKS;
    }
    if ("wattpad".equals(key)) {
      return Configuration.MENU_WATTPAD;
    }
    if ("pinterest".equals(key)) {
      return Configuration.MENU_PINTEREST;
    }
    if ("reddit".equals(key)) {
      return Configuration.MENU_REDDIT;
    }
    return null;
  }

  private static void openLyrics(Navigator navigator, String uri) {
    String path = param(uri, "p");
    if (path != null && path.length() > 0) {
      while (path.startsWith("/")) {
        path = path.substring(1);
      }
      if (!path.endsWith("/")) {
        path = path + "/";
      }
      LyricsScreen.openByPath(
          navigator,
          valueOf(param(uri, "a"), param(uri, "artist")),
          valueOf(param(uri, "t"), param(uri, "title")),
          path,
          null);
      return;
    }
    String artist = valueOf(param(uri, "a"), param(uri, "artist"));
    String title = valueOf(param(uri, "t"), param(uri, "title"));
    if (artist.length() > 0 || title.length() > 0) {
      LyricsScreen.open(navigator, artist, title);
      return;
    }
    String query = param(uri, "q");
    if (query != null && query.length() > 0) {
      navigator.forward(new LyricsSearchScreen(navigator, "", query, false, null));
      return;
    }
    PlayerScreen player = MIDPlay.getPlayerScreen();
    Track track = null;
    if (player != null) {
      PlayerGUI gui = player.getPlayerGUI();
      if (gui != null) {
        track = gui.getCurrentTrack();
      }
    }
    LyricsScreen.open(navigator, track);
  }

  private static String valueOf(String first, String second) {
    if (first != null && first.length() > 0) {
      return first;
    }
    return second == null ? "" : second;
  }

  private static String param(String uri, String name) {
    if (uri == null) {
      return null;
    }
    int start = uri.indexOf('?');
    if (start < 0) {
      return null;
    }
    start++;
    int end = uri.indexOf('#', start);
    if (end < 0) {
      end = uri.length();
    }
    while (start < end) {
      int amp = uri.indexOf('&', start);
      if (amp < 0 || amp > end) {
        amp = end;
      }
      int eq = uri.indexOf('=', start);
      if (eq >= 0 && eq < amp) {
        String key = uri.substring(start, eq);
        if (key.equalsIgnoreCase(name)) {
          return decode(uri.substring(eq + 1, amp));
        }
      }
      start = amp + 1;
    }
    return null;
  }

  private static String decode(String value) {
    StringBuffer result = new StringBuffer(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '+') {
        result.append(' ');
      } else if (c == '%' && i + 2 < value.length()) {
        int hi = hex(value.charAt(i + 1));
        int lo = hex(value.charAt(i + 2));
        if (hi >= 0 && lo >= 0) {
          result.append((char) ((hi << 4) | lo));
          i += 2;
        } else {
          result.append(c);
        }
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }

  private static int hex(char c) {
    if (c >= '0' && c <= '9') {
      return c - '0';
    }
    if (c >= 'a' && c <= 'f') {
      return c - 'a' + 10;
    }
    if (c >= 'A' && c <= 'F') {
      return c - 'A' + 10;
    }
    return -1;
  }

  private static boolean isGitHub(String url) {
    String host = host(url);
    return host != null
        && (host.equals("github.com") || host.endsWith(".github.com"));
  }

  private static boolean isTelegram(String url) {
    String value = url.trim().toLowerCase();
    if (value.startsWith("tg://")) {
      return true;
    }
    String host = host(value);
    return host != null
        && (host.equals("t.me")
            || host.equals("telegram.me")
            || host.equals("telegram.dog"));
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
