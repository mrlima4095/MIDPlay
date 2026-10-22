package midplay.util;

/**
 * Minimal HTML to plain-text converter. Wattpad returns chapter text as HTML paragraphs ({@code <p>}
 * with inline {@code <b>} / {@code <a>} tags) and the story page embeds the text inside a {@code
 * <pre>} block, so the reader needs to turn either shape into readable text.
 */
public final class HtmlText {

  private HtmlText() {}

  /** Converts an HTML fragment into plain text, keeping paragraphs separated by blank lines. */
  public static String toText(String html) {
    if (html == null || html.length() == 0) {
      return "";
    }
    String cleaned = removeBlocks(html, "script");
    cleaned = removeBlocks(cleaned, "style");
    StringBuffer out = new StringBuffer(cleaned.length());
    int i = 0;
    int length = cleaned.length();
    while (i < length) {
      char c = cleaned.charAt(i);
      if (c == '<') {
        int end = cleaned.indexOf('>', i);
        if (end < 0) {
          break;
        }
        if (isBlockTag(cleaned.substring(i + 1, end))) {
          out.append('\n');
        }
        i = end + 1;
      } else if (c == '\r') {
        i++;
      } else if (c == '\n') {
        out.append('\n');
        i++;
      } else {
        out.append(c);
        i++;
      }
    }
    return collapse(sanitize(decodeEntities(out.toString())));
  }

  private static boolean isBlockTag(String rawTag) {
    String tag = rawTag.trim().toLowerCase();
    int space = tag.indexOf(' ');
    if (space >= 0) {
      tag = tag.substring(0, space);
    }
    if (tag.startsWith("/")) {
      tag = tag.substring(1);
    }
    return tag.equals("p")
        || tag.equals("br")
        || tag.equals("div")
        || tag.equals("li")
        || tag.equals("tr")
        || tag.equals("h1")
        || tag.equals("h2")
        || tag.equals("h3")
        || tag.equals("blockquote")
        || tag.equals("pre");
  }

  private static String decodeEntities(String text) {
    if (text.indexOf('&') < 0) {
      return text;
    }
    StringBuffer out = new StringBuffer(text.length());
    int i = 0;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (c != '&') {
        out.append(c);
        i++;
        continue;
      }
      int semi = text.indexOf(';', i + 1);
      if (semi < 0 || semi - i > 10) {
        out.append(c);
        i++;
        continue;
      }
      String decoded = entity(text.substring(i + 1, semi));
      if (decoded == null) {
        out.append(c);
        i++;
        continue;
      }
      out.append(decoded);
      i = semi + 1;
    }
    return out.toString();
  }

  private static String entity(String name) {
    if (name.equals("amp")) {
      return "&";
    }
    if (name.equals("lt")) {
      return "<";
    }
    if (name.equals("gt")) {
      return ">";
    }
    if (name.equals("quot")) {
      return "\"";
    }
    if (name.equals("apos")) {
      return "'";
    }
    if (name.equals("nbsp")) {
      return " ";
    }
    if (name.equals("hellip")) {
      return "...";
    }
    if (name.equals("mdash")) {
      return "--";
    }
    if (name.equals("ndash")) {
      return "-";
    }
    if (name.equals("lsquo") || name.equals("rsquo")) {
      return "'";
    }
    if (name.equals("ldquo") || name.equals("rdquo")) {
      return "\"";
    }
    if (name.equals("laquo")) {
      return "<<";
    }
    if (name.equals("raquo")) {
      return ">>";
    }
    if (name.length() > 1 && name.charAt(0) == '#') {
      try {
        int code;
        char marker = name.charAt(1);
        if (marker == 'x' || marker == 'X') {
          code = Integer.parseInt(name.substring(2), 16);
        } else {
          code = Integer.parseInt(name.substring(1));
        }
        if (code > 0) {
          if (code <= 0xFFFF && (code < 0xD800 || code > 0xDFFF)) {
            return "" + (char) code;
          }
          // Emoji, surrogate halves and other characters a device cannot render: drop them
          // instead of keeping a literal entity that may break the text layout.
          return " ";
        }
      } catch (Exception e) {
      }
    }
    return null;
  }

  /**
   * Removes characters that some MIDP text renderers cannot display. Wattpad chapters often end a
   * paragraph with an emoji, which is a surrogate pair on CLDC; on those devices a StringItem can
   * stop rendering the rest of the chapter after the first surrogate. Dropping the pair keeps the
   * remaining text visible.
   */
  private static String sanitize(String text) {
    StringBuffer out = new StringBuffer(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c >= 0xD800 && c <= 0xDBFF) {
        boolean pair =
            i + 1 < text.length()
                && text.charAt(i + 1) >= 0xDC00
                && text.charAt(i + 1) <= 0xDFFF;
        if (pair) {
          i++;
          while (i + 1 < text.length()
              && (text.charAt(i + 1) == '\uFE0E' || text.charAt(i + 1) == '\uFE0F')) {
            i++;
          }
        }
        if (out.length() > 0) {
          char previous = out.charAt(out.length() - 1);
          if (previous != ' ' && previous != '\n' && previous != '\t') {
            out.append(' ');
          }
        }
      } else if (c >= 0xDC00 && c <= 0xDFFF) {
        // Unpaired low surrogate: drop it.
      } else if (c == '\n'
          || c == '\t'
          || (c >= 0x20 && c != 0x7F && (c < 0x80 || c > 0x9F))) {
        out.append(c);
      }
    }
    return out.toString();
  }

  /** Normalizes whitespace: no runs of spaces, no blanks at line ends, at most one empty line. */
  private static String collapse(String text) {
    StringBuffer out = new StringBuffer(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      int size = out.length();
      if (c == ' ' || c == '\t') {
        if (size == 0) {
          continue;
        }
        char previous = out.charAt(size - 1);
        if (previous == ' ' || previous == '\n') {
          continue;
        }
        out.append(' ');
      } else if (c == '\n') {
        if (size == 0) {
          continue;
        }
        int newlines = 0;
        for (int k = size - 1; k >= 0 && out.charAt(k) == '\n'; k--) {
          newlines++;
        }
        if (newlines >= 2) {
          continue;
        }
        if (out.charAt(size - 1) == ' ') {
          out.setCharAt(size - 1, '\n');
        } else {
          out.append('\n');
        }
      } else {
        out.append(c);
      }
    }
    int end = out.length();
    while (end > 0) {
      char c = out.charAt(end - 1);
      if (c == ' ' || c == '\n' || c == '\t') {
        end--;
      } else {
        break;
      }
    }
    out.setLength(end);
    return out.toString();
  }

  private static String removeBlocks(String text, String tag) {
    String open = "<" + tag;
    String close = "</" + tag;
    StringBuffer out = new StringBuffer(text.length());
    int from = 0;
    while (true) {
      int start = indexOfIgnoreCase(text, open, from);
      if (start < 0) {
        out.append(text.substring(from));
        break;
      }
      int end = indexOfIgnoreCase(text, close, start);
      if (end < 0) {
        out.append(text.substring(from, start));
        break;
      }
      int endOfTag = text.indexOf('>', end);
      out.append(text.substring(from, start));
      from = endOfTag < 0 ? text.length() : endOfTag + 1;
    }
    return out.toString();
  }

  private static int indexOfIgnoreCase(String text, String needle, int from) {
    int limit = text.length() - needle.length();
    for (int i = Math.max(from, 0); i <= limit; i++) {
      if (text.regionMatches(true, i, needle, 0, needle.length())) {
        return i;
      }
    }
    return -1;
  }
}
