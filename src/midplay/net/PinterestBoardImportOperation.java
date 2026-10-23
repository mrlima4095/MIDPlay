package midplay.net;

import java.util.Vector;
import midplay.util.Utils;

/**
 * Walks through every page of a Pinterest board and collects all of its pins. Used by the "save the
 * whole board into a local folder" action; the proxy paginates with a bookmark, so this keeps
 * requesting pages until the bookmark stops changing or the safety cap is reached.
 */
public final class PinterestBoardImportOperation extends NetworkOperation {
  public interface Listener {
    void onImported(Vector pins, boolean truncated);

    void onError(Exception e);
  }

  /** Safety cap so a malformed bookmark can never keep the device requesting forever. */
  private static final int MAX_PAGES = 25;

  private final String owner;
  private final String slug;
  private final String boardId;
  private final Listener listener;

  public PinterestBoardImportOperation(
      String owner, String slug, String boardId, Listener listener) {
    this.owner = owner == null ? "" : owner;
    this.slug = slug == null ? "" : slug;
    this.boardId = boardId == null ? "" : boardId;
    this.listener = listener;
  }

  protected void execute() {
    if (owner.length() == 0 || slug.length() == 0) {
      listener.onError(new Exception("invalid board"));
      return;
    }
    Vector pins = new Vector();
    String bookmark = "";
    boolean truncated = false;
    try {
      for (int page = 0; page < MAX_PAGES; page++) {
        if (isStopped()) {
          return;
        }
        Network connection = new Network();
        network = connection;
        String text =
            Utils.bytesToUtf8(
                connection.sendHttpGetBytes(
                    PinterestApi.boardPinsUrl(owner, slug, boardId, bookmark)));
        String error = PinterestApi.parseError(text);
        if (error.length() > 0 && pins.size() == 0) {
          listener.onError(new Exception(error));
          return;
        }
        Vector pagePins = PinterestApi.parsePins(text);
        for (int i = 0; i < pagePins.size(); i++) {
          pins.addElement(pagePins.elementAt(i));
        }
        String next = PinterestApi.parseBookmark(text);
        if (next.length() == 0 || next.equals(bookmark)) {
          break;
        }
        bookmark = next;
        if (page == MAX_PAGES - 1) {
          truncated = true;
        }
      }
      if (!isStopped()) {
        listener.onImported(pins, truncated);
      }
    } catch (Exception e) {
      if (!isStopped()) {
        listener.onError(e);
      }
    }
  }

  protected void handleError(Exception e) {
    if (!isStopped()) {
      listener.onError(e);
    }
  }
}
