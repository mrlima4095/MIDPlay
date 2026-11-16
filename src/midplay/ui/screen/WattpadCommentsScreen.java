package midplay.ui.screen;

import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;
import midplay.MIDPlay;
import midplay.net.HttpTextOperation;
import midplay.net.WattpadApi;
import midplay.ui.BaseList;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;

/**
 * Comments of one Wattpad chapter, or the replies of one comment. The v5 endpoint returns a cursor
 * for the next page instead of an offset, so the screen keeps the last cursor and appends pages on
 * demand.
 */
public final class WattpadCommentsScreen extends BaseList {
  private final String namespace;
  private final String resourceId;
  private final Command reloadCommand;
  private final Command moreCommand;
  private final Vector items = new Vector();
  private final Hashtable ids = new Hashtable();
  private String cursor = "";
  private boolean hasMore;
  private boolean loading;
  private boolean moreAdded;

  /** Comments of a chapter. */
  public WattpadCommentsScreen(Navigator navigator, String partId, String chapterTitle) {
    this(navigator, WattpadApi.COMMENTS_PARTS, partId, commentsTitle(chapterTitle));
  }

  private static String commentsTitle(String chapterTitle) {
    if (chapterTitle == null || chapterTitle.length() == 0) {
      return Lang.tr("wattpad.comments_title");
    }
    String title = chapterTitle;
    if (title.length() > 24) {
      title = title.substring(0, 21) + "...";
    }
    return Lang.tr("wattpad.comments") + " - " + title;
  }

  private WattpadCommentsScreen(
      Navigator navigator, String namespace, String resourceId, String title) {
    super(title, navigator);
    this.namespace = namespace == null ? WattpadApi.COMMENTS_PARTS : namespace;
    this.resourceId = resourceId == null ? "" : resourceId;
    reloadCommand = new Command(Lang.tr("wattpad.reload"), Command.SCREEN, 2);
    moreCommand = new Command(Lang.tr("wattpad.more_comments"), Command.SCREEN, 3);
    addCommand(reloadCommand);
    load(false);
  }

  protected void showNotify() {
    super.showNotify();
    if (items.size() == 0 && !loading) {
      load(false);
    }
  }

  protected void populateItems() {
    if (items.size() == 0) {
      append(
          Lang.tr(
              loading
                  ? "wattpad.loading_comments"
                  : (isReplyList() ? "wattpad.no_replies" : "wattpad.no_comments")),
          null);
      return;
    }
    for (int i = 0; i < items.size(); i++) {
      append(WattpadApi.commentLabel((String[]) items.elementAt(i)), null);
    }
  }

  protected void handleSelection() {
    int index = getSelectedIndex();
    if (index < 0 || index >= items.size()) {
      return;
    }
    new CommentView((String[]) items.elementAt(index)).open();
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == reloadCommand) {
      load(false);
    } else if (c == moreCommand) {
      load(true);
    }
  }

  private boolean isReplyList() {
    return WattpadApi.COMMENTS_REPLIES.equals(namespace);
  }

  /** Loads the first page, or appends the next one when {@code append} is set. */
  private void load(final boolean append) {
    if (loading || resourceId.length() == 0) {
      return;
    }
    loading = true;
    final String requestCursor = append ? cursor : "";
    navigator.showLoadingAlert(
        Lang.tr("wattpad.loading_comments"),
        new Runnable() {
          public void run() {
            loading = false;
          }
        });
    MIDPlay.startOperation(
        new HttpTextOperation(
            WattpadApi.commentsUrl(namespace, resourceId, requestCursor),
            new HttpTextOperation.Listener() {
              public void onText(final String response) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        String error = WattpadApi.parseError(response);
                        if (error.length() > 0) {
                          navigator.dismissAlert();
                          navigator.showAlert(error, AlertType.ERROR);
                          return;
                        }
                        navigator.dismissAlert();
                        if (!append) {
                          items.removeAllElements();
                          ids.clear();
                          cursor = "";
                        }
                        Vector page = WattpadApi.parseComments(response);
                        int added = 0;
                        for (int i = 0; i < page.size(); i++) {
                          String[] comment = (String[]) page.elementAt(i);
                          String id = comment[WattpadApi.COMMENT_ID];
                          String key =
                              id != null && id.length() > 0
                                  ? id
                                  : comment[WattpadApi.COMMENT_AUTHOR]
                                      + "\n"
                                      + comment[WattpadApi.COMMENT_CREATED]
                                      + "\n"
                                      + comment[WattpadApi.COMMENT_BODY];
                          if (ids.containsKey(key)) {
                            continue;
                          }
                          ids.put(key, key);
                          items.addElement(comment);
                          added++;
                        }
                        String next = WattpadApi.parseCommentsAfter(response);
                        hasMore = next.length() > 0 && !next.equals(requestCursor);
                        cursor = next;
                        updateMoreCommand();
                        refresh();
                        if (append && added == 0) {
                          navigator.showAlert(
                              Lang.tr("wattpad.end_of_results"), AlertType.INFO);
                        }
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        navigator.showAlert(e.toString(), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private void updateMoreCommand() {
    if (hasMore && !moreAdded) {
      addCommand(moreCommand);
      moreAdded = true;
    } else if (!hasMore && moreAdded) {
      removeCommand(moreCommand);
      moreAdded = false;
    }
  }

  /** One comment with its full text and a shortcut to its replies. */
  private final class CommentView implements CommandListener {
    private final Form form = new Form(Lang.tr("wattpad.comment"));
    private final String[] comment;
    private final Command repliesCommand;

    CommentView(String[] comment) {
      this.comment = comment;
      String author = comment[WattpadApi.COMMENT_AUTHOR];
      String created = WattpadApi.commentDate(comment[WattpadApi.COMMENT_CREATED]);
      String likes = comment[WattpadApi.COMMENT_LIKES];
      int replies = 0;
      try {
        replies = Integer.parseInt(comment[WattpadApi.COMMENT_REPLIES]);
      } catch (Exception ignored) {
      }
      form.append(author + "\n");
      if (created.length() > 0) {
        form.append(Lang.tr("wattpad.comment_date") + ": " + created + "\n");
      }
      if (likes != null && likes.length() > 0 && !"0".equals(likes)) {
        form.append(Lang.tr("wattpad.comment_likes") + ": " + likes + "\n");
      }
      if (replies > 0) {
        form.append(
            Lang.tr("wattpad.replies_count", Integer.toString(replies)) + "\n");
      }
      form.append("\n");
      form.append(
          new StringItem(
              null,
              comment[WattpadApi.COMMENT_BODY] == null ? "" : comment[WattpadApi.COMMENT_BODY]));
      form.addCommand(Commands.back());
      form.addCommand(Commands.playerNowPlaying());
      Command repliesCmd = null;
      String id = comment[WattpadApi.COMMENT_ID];
      if (replies > 0 && id != null && id.length() > 0) {
        repliesCmd = new Command(Lang.tr("wattpad.view_replies"), Command.SCREEN, 1);
        form.addCommand(repliesCmd);
      }
      repliesCommand = repliesCmd;
      form.setCommandListener(this);
    }

    void open() {
      navigator.forward(form);
    }

    public void commandAction(Command c, Displayable d) {
      if (c == Commands.back()) {
        navigator.back();
      } else if (c == Commands.playerNowPlaying()) {
        PlayerNavHelper.showNowPlaying(navigator);
      } else if (c == repliesCommand) {
        navigator.forward(
            new WattpadCommentsScreen(
                navigator,
                WattpadApi.COMMENTS_REPLIES,
                comment[WattpadApi.COMMENT_ID],
                Lang.tr("wattpad.replies_title")));
      }
    }
  }
}
