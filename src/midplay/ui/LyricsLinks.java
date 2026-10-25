package midplay.ui;

import javax.microedition.lcdui.AlertType;
import midplay.store.Configuration;
import midplay.ui.screen.LyricsScreen;
import midplay.ui.screen.LyricsSearchScreen;
import midplay.util.Lang;

/**
 * Opens lyrics-provider pages inside MIDPlay. letras.mus.br links are passed to the proxy by their
 * exact page path; the other hosts listed in {@link Configuration#LYRICS_LINK_HOSTS} are reduced to
 * an artist/title query, so the same lyrics cache and manual fallback are used.
 */
public final class LyricsLinks {
  private LyricsLinks() {}

  /** True when the URL belongs to one of the configured lyrics providers. */
  public static boolean isLyrics(String url) {
    return parse(url) != null;
  }

  /** Opens the provider page in the matching lyrics screen. */
  public static void open(Navigator navigator, String url) {
    String[] link = parse(url);
    if (link == null) {
      navigator.showAlert(Lang.tr("lyrics.error"), AlertType.ERROR);
      return;
    }
    if ("p".equals(link[0])) {
      LyricsScreen.openByPath(navigator, link[1], link[2], link[3], null);
    } else if ("s".equals(link[0])) {
      navigator.forward(new LyricsSearchScreen(navigator, link[1], link[2], false, null));
    } else {
      LyricsScreen.open(navigator, link[1], link[2]);
    }
  }

  /**
   * Parses a provider URL into {@code {mode, artist, title, path}} where mode is {@code p} for an
   * exact letras.mus.br page, {@code s} for a manual search and {@code q} for an automatic query.
   */
  private static String[] parse(String url) {
    String host = host(url);
    if (host == null) {
      return null;
    }
    String[] segments = segments(path(url));
    if (segments.length == 0) {
      return search("");
    }
    if (host.equals("letras.mus.br")) {
      if (segments.length >= 2) {
        String first = segments[0];
        String second = segments[1];
        // Older pages use the numeric song id (legiao-urbana/22489/), so the URL
        // carries no readable title; the proxy fills it from the page itself.
        String title = isNumeric(second) ? "" : pretty(second);
        return new String[] {"p", pretty(first), title, first + "/" + second + "/"};
      }
      return search(segments[0]);
    }
    if (host.equals("vagalume.com.br")) {
      // Artist pages are /artist/ or /artist/index.html; only /artist/song.html
      // identifies a single song.
      String last = segments.length >= 2 ? stripSuffix(segments[1]) : "";
      if (last.length() == 0 || last.equalsIgnoreCase("index")) {
        return search(segments[0]);
      }
      return query(pretty(segments[0]), pretty(last));
    }
    if (host.equals("musixmatch.com")) {
      int offset = 0;
      for (int i = 0; i < segments.length; i++) {
        if (segments[i].equalsIgnoreCase("lyrics")) {
          offset = i + 1;
          break;
        }
      }
      if (offset == 0 && segments.length > 0 && isReserved(segments[0])) {
        offset = 1;
      }
      String artist = segments.length > offset ? pretty(segments[offset]) : "";
      String title = segments.length > offset + 1 ? pretty(segments[offset + 1]) : "";
      if (artist.length() > 0 || title.length() > 0) {
        return query(artist, title);
      }
    }
    String last = stripSuffix(segments[segments.length - 1]);
    String previous = segments.length >= 2 ? stripSuffix(segments[segments.length - 2]) : "";
    if (isReserved(previous)) {
      previous = "";
    }
    if (last.length() == 0) {
      return search("");
    }
    if (last.equalsIgnoreCase("index")) {
      // Artist/channel index page: search for the name instead of the file name.
      return search(previous.length() > 0 ? previous : segments[0]);
    }
    if (isNumeric(last)) {
      // Some providers also use numeric ids; searching for the id would fail.
      return search(previous);
    }
    return query(pretty(previous), pretty(last));
  }

  private static String[] search(String query) {
    return new String[] {"s", pretty(query), ""};
  }

  private static String[] query(String artist, String title) {
    return new String[] {"q", artist, title};
  }

  private static boolean isNumeric(String value) {
    if (value.length() == 0) {
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

  private static boolean isReserved(String value) {
    String lower = value.toLowerCase();
    return lower.equals("lyrics")
        || lower.equals("letra")
        || lower.equals("en")
        || lower.equals("pt")
        || lower.equals("www");
  }

  /** Host without scheme, port and leading www. */
  private static String host(String url) {
    String value = schemeLess(url);
    if (value == null) {
      return null;
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
    for (int i = 0; i < Configuration.LYRICS_LINK_HOSTS.length; i++) {
      String known = Configuration.LYRICS_LINK_HOSTS[i];
      if (host.equals(known) || host.endsWith("." + known)) {
        return host;
      }
    }
    return null;
  }

  private static String path(String url) {
    String value = schemeLess(url);
    if (value == null) {
      return "/";
    }
    int slash = value.indexOf('/');
    String rest = slash < 0 ? "/" : value.substring(slash);
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

  private static String schemeLess(String url) {
    if (url == null) {
      return null;
    }
    String value = url.trim();
    if (value.length() == 0) {
      return null;
    }
    int scheme = value.indexOf("://");
    if (scheme >= 0) {
      String protocol = value.substring(0, scheme).toLowerCase();
      if (!protocol.equals("http") && !protocol.equals("https")) {
        return null;
      }
      value = value.substring(scheme + 3);
    }
    return value;
  }

  private static String[] segments(String path) {
    java.util.Vector parts = new java.util.Vector();
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

  private static String stripSuffix(String value) {
    String result = value;
    if (result.toLowerCase().endsWith(".html")) {
      result = result.substring(0, result.length() - 5);
    }
    if (result.toLowerCase().endsWith("-lyrics")) {
      result = result.substring(0, result.length() - 7);
    }
    return result;
  }

  /** "legiao-urbana" -> "Legiao Urbana". */
  private static String pretty(String slug) {
    String value = decode(slug);
    value = value.replace('-', ' ').replace('_', ' ').trim();
    StringBuffer result = new StringBuffer(value.length());
    boolean upper = true;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == ' ') {
        upper = true;
        result.append(c);
      } else if (upper) {
        result.append(Character.toUpperCase(c));
        upper = false;
      } else {
        result.append(c);
      }
    }
    return result.toString();
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
}
