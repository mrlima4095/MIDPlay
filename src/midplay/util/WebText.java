package midplay.util;

import java.util.Vector;

/**
 * Lightweight text helpers for handling web content on CLDC: XML entity decoding, RSS/Atom feed
 * extraction and HTML-to-text conversion. There is no XML/DOM parser on the bootclasspath, so
 * everything here is a small hand-rolled scanner.
 */
public final class WebText {
  private WebText() {}

  /** Keeps only printable ASCII, turning the degree sign into a space and collapsing spaces. */
  public static String ascii(String s) {
    if (s == null) {
      return "";
    }
    StringBuffer sb = new StringBuffer(s.length());
    boolean lastSpace = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < 32 || (c > 126 && c != '\u00B0')) {
        c = ' ';
      }
      if (c == ' ') {
        if (lastSpace) {
          continue;
        }
        lastSpace = true;
      } else {
        lastSpace = false;
      }
      sb.append(c);
    }
    return sb.toString().trim();
  }

  public static String decodeEntities(String s) {
    if (s == null) {
      return "";
    }
    String t = s;
    t = Utils.replace(t, "&lt;", "<");
    t = Utils.replace(t, "&gt;", ">");
    t = Utils.replace(t, "&quot;", "\"");
    t = Utils.replace(t, "&apos;", "'");
    t = Utils.replace(t, "&#39;", "'");
    t = Utils.replace(t, "&nbsp;", " ");
    t = decodeNumericEntities(t);
    t = Utils.replace(t, "&amp;", "&");
    return t;
  }

  private static String decodeNumericEntities(String s) {
    if (s.indexOf("&#") < 0) {
      return s;
    }
    StringBuffer sb = new StringBuffer(s.length());
    int i = 0;
    while (i < s.length()) {
      int amp = s.indexOf("&#", i);
      if (amp < 0) {
        sb.append(s.substring(i));
        break;
      }
      sb.append(s.substring(i, amp));
      int semi = s.indexOf(';', amp);
      if (semi < 0 || semi - amp > 8) {
        sb.append(s.charAt(amp));
        i = amp + 1;
        continue;
      }
      String num = s.substring(amp + 2, semi);
      int code = -1;
      try {
        if (num.length() > 0 && (num.charAt(0) == 'x' || num.charAt(0) == 'X')) {
          code = Integer.parseInt(num.substring(1), 16);
        } else if (num.length() > 0) {
          code = Integer.parseInt(num);
        }
      } catch (NumberFormatException e) {
        code = -1;
      }
      if (code > 0) {
        sb.append((char) code);
      } else {
        sb.append(s.substring(amp, semi + 1));
      }
      i = semi + 1;
    }
    return sb.toString();
  }

  private static String stripCdata(String s) {
    if (s == null) {
      return "";
    }
    String t = s.trim();
    if (t.startsWith("<![CDATA[")) {
      t = t.substring(9);
      int end = t.indexOf("]]>");
      if (end >= 0) {
        t = t.substring(0, end);
      }
    }
    return t.trim();
  }

  private static int indexOfIgnoreCaseFrom(String haystack, String needle, int from) {
    if (haystack == null || needle == null || from < 0) {
      return -1;
    }
    if (from >= haystack.length()) {
      return -1;
    }
    int rel = Utils.indexOfIgnoreCase(haystack.substring(from), needle);
    return rel < 0 ? -1 : from + rel;
  }

  /** Returns decoded text between {@code <tag>} and {@code </tag>} (first match). */
  public static String extractTag(String block, String tag) {
    if (block == null) {
      return "";
    }
    int start = indexOfIgnoreCaseFrom(block, "<" + tag, 0);
    if (start < 0) {
      return "";
    }
    int gt = block.indexOf('>', start);
    if (gt < 0) {
      return "";
    }
    if (gt > 0 && block.charAt(gt - 1) == '/') {
      return "";
    }
    int close = indexOfIgnoreCaseFrom(block, "</" + tag + ">", gt + 1);
    if (close < 0) {
      return "";
    }
    return decodeEntities(stripCdata(block.substring(gt + 1, close)));
  }

  private static String attr(String tagBody, String name) {
    int i = indexOfIgnoreCaseFrom(tagBody, name + "=", 0);
    if (i < 0) {
      return "";
    }
    int q = i + name.length() + 1;
    if (q >= tagBody.length()) {
      return "";
    }
    char quote = tagBody.charAt(q);
    if (quote != '"' && quote != '\'') {
      int end = tagBody.indexOf(' ', q);
      if (end < 0) {
        end = tagBody.length();
      }
      return tagBody.substring(q, end).trim();
    }
    int end = tagBody.indexOf(quote, q + 1);
    if (end < 0) {
      return "";
    }
    return tagBody.substring(q + 1, end);
  }

  /** Extracts a link from an RSS {@code <link>url</link>} or Atom {@code <link href="url"/>}. */
  public static String extractLink(String block) {
    if (block == null) {
      return "";
    }
    int start = indexOfIgnoreCaseFrom(block, "<link", 0);
    if (start < 0) {
      return "";
    }
    int gt = block.indexOf('>', start);
    if (gt < 0) {
      return "";
    }
    String href = attr(block.substring(start, gt), "href");
    if (href.length() > 0) {
      return decodeEntities(href);
    }
    int close = indexOfIgnoreCaseFrom(block, "</link>", gt + 1);
    if (close < 0) {
      return "";
    }
    return decodeEntities(stripCdata(block.substring(gt + 1, close)));
  }

  /**
   * Parses an RSS or Atom feed into {title, link} pairs. Returns an empty vector when nothing is
   * found (caller treats that as an error).
   */
  public static Vector parseFeed(String xml) {
    Vector items = new Vector();
    if (xml == null) {
      return items;
    }
    int pos = 0;
    while (items.size() < 80) {
      int itemAt = indexOfIgnoreCaseFrom(xml, "<item", pos);
      int entryAt = indexOfIgnoreCaseFrom(xml, "<entry", pos);
      int start;
      String closeName;
      if (itemAt < 0 && entryAt < 0) {
        break;
      }
      if (itemAt < 0) {
        start = entryAt;
        closeName = "</entry>";
      } else if (entryAt < 0) {
        start = itemAt;
        closeName = "</item>";
      } else if (itemAt <= entryAt) {
        start = itemAt;
        closeName = "</item>";
      } else {
        start = entryAt;
        closeName = "</entry>";
      }
      int close = indexOfIgnoreCaseFrom(xml, closeName, start);
      if (close < 0) {
        break;
      }
      int end = close + closeName.length();
      String block = xml.substring(start, end);
      pos = end;
      String title = extractTag(block, "title");
      String link = extractLink(block);
      if (title.length() > 0 && link.length() > 0) {
        items.addElement(new String[] {title, link});
      }
    }
    return items;
  }

  private static final String[] BLOCK_TAGS = {
    "p", "br", "div", "li", "ul", "ol", "tr", "td", "th", "table", "h1", "h2", "h3", "h4", "h5",
    "h6", "article", "section", "header", "footer", "blockquote", "pre", "figure", "figcaption",
    "hr", "dd", "dt", "dl"
  };

  private static boolean isBlockTag(String html, int lt) {
    int i = lt + 1;
    if (i < html.length() && html.charAt(i) == '/') {
      i++;
    }
    for (int t = 0; t < BLOCK_TAGS.length; t++) {
      String tag = BLOCK_TAGS[t];
      if (i + tag.length() <= html.length()
          && html.regionMatches(true, i, tag, 0, tag.length())) {
        int after = i + tag.length();
        if (after >= html.length()) {
          return true;
        }
        char c = html.charAt(after);
        if (c == '>' || c == ' ' || c == '/' || c == '\t' || c == '\n' || c == '\r') {
          return true;
        }
      }
    }
    return false;
  }

  private static String removeBlock(String s, String openNeedle, String closeNeedle) {
    if (s == null) {
      return "";
    }
    int start = indexOfIgnoreCaseFrom(s, openNeedle, 0);
    while (start >= 0) {
      int close = indexOfIgnoreCaseFrom(s, closeNeedle, start + openNeedle.length());
      if (close < 0) {
        s = s.substring(0, start);
        break;
      }
      int end = close + closeNeedle.length();
      s = s.substring(0, start) + s.substring(end);
      start = indexOfIgnoreCaseFrom(s, openNeedle, start);
    }
    return s;
  }

  /** Converts an HTML document into readable plain text (tags stripped, entities decoded). */
  public static String htmlToText(String html) {
    if (html == null) {
      return "";
    }
    String s = removeBlock(html, "<script", "</script>");
    s = removeBlock(s, "<style", "</style>");
    s = removeBlock(s, "<!--", "-->");
    StringBuffer sb = new StringBuffer(s.length());
    boolean inTag = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '<') {
        if (isBlockTag(s, i)) {
          sb.append('\n');
        }
        inTag = true;
        continue;
      }
      if (c == '>') {
        inTag = false;
        continue;
      }
      if (!inTag) {
        sb.append(c);
      }
    }
    return collapse(decodeEntities(sb.toString()));
  }

  private static String collapse(String s) {
    StringBuffer sb = new StringBuffer(s.length());
    int newlines = 0;
    boolean space = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\n' || c == '\r') {
        if (newlines < 2) {
          sb.append('\n');
        }
        newlines++;
        space = false;
      } else if (c == ' ' || c == '\t' || c < 32) {
        if (!space) {
          sb.append(' ');
          space = true;
        }
      } else {
        sb.append(c);
        space = false;
        newlines = 0;
      }
    }
    return sb.toString().trim();
  }
}
