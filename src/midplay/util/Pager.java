package midplay.util;

import java.util.Vector;

/** Splits long text into pages of a bounded size so readers never load everything at once. */
public final class Pager {

  private Pager() {}

  /** Keeps the text within {@code maxChars}, marking the cut with an ellipsis note. */
  public static String truncate(String text, int maxChars) {
    if (text == null) {
      return "";
    }
    if (text.length() <= maxChars) {
      return text;
    }
    return text.substring(0, maxChars) + "\n\n[...]";
  }

  /** Splits the text into chunks of at most {@code limit} chars, preferring line/space breaks. */
  public static String[] paginate(String text, int limit) {
    if (limit < 1) {
      limit = 2000;
    }
    int length = text.length();
    if (length <= limit) {
      return new String[] {text};
    }
    Vector chunks = new Vector();
    int pos = 0;
    while (pos < length) {
      int end = pos + limit;
      if (end >= length) {
        end = length;
      } else {
        end = findBreak(text, pos, end);
      }
      chunks.addElement(text.substring(pos, end));
      pos = end;
    }
    String[] result = new String[chunks.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = (String) chunks.elementAt(i);
    }
    return result;
  }

  private static int findBreak(String text, int start, int end) {
    int lower = start + (end - start) / 2;
    for (int i = end - 1; i >= lower; i--) {
      char c = text.charAt(i);
      if (c == '\n' || c == ' ') {
        return i + 1;
      }
    }
    return end;
  }
}
