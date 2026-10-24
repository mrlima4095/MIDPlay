package midplay.ui.screen;

import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import midplay.MIDPlay;
import midplay.net.HttpTextOperation;
import midplay.net.RedditApi;
import midplay.ui.BaseList;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;

/** Threaded comments of one Reddit post, indented by their reply depth. */
public final class RedditCommentsScreen extends BaseList {
  private final String postId;
  private final Command reloadCommand;
  private Vector items = new Vector();
  private boolean loading;
  private boolean pending;

  public RedditCommentsScreen(Navigator navigator, String postId, String postTitle) {
    super(Lang.tr("reddit.comments_title"), navigator);
    this.postId = postId == null ? "" : postId;
    reloadCommand = new Command(Lang.tr("reddit.reload"), Command.SCREEN, 1);
    addCommand(reloadCommand);
    load();
  }

  protected void showNotify() {
    super.showNotify();
    if (items.size() == 0) {
      load();
    }
  }

  protected void populateItems() {
    if (items.size() == 0) {
      append(Lang.tr(pending ? "reddit.comments_pending" : "reddit.no_comments"), null);
      return;
    }
    for (int i = 0; i < items.size(); i++) {
      append(RedditApi.commentLabel((String[]) items.elementAt(i)), null);
    }
  }

  protected void handleSelection() {
    int index = getSelectedIndex();
    if (index < 0 || index >= items.size()) {
      return;
    }
    showComment((String[]) items.elementAt(index));
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == reloadCommand) {
      load();
    }
  }

  private void showComment(String[] comment) {
    final Form form = new Form(Lang.tr("reddit.comment"));
    form.append(
        midplay.util.Lang.tr("reddit.author") + ": u/" + comment[RedditApi.COMMENT_AUTHOR] + "\n");
    form.append(
        midplay.util.Lang.tr("reddit.score") + ": " + comment[RedditApi.COMMENT_SCORE] + "\n\n");
    form.append(comment[RedditApi.COMMENT_BODY]);
    form.addCommand(Commands.back());
    form.setCommandListener(
        new CommandListener() {
          public void commandAction(Command c, Displayable d) {
            if (c == Commands.back()) {
              navigator.back();
            }
          }
        });
    navigator.forward(form);
  }

  private void load() {
    if (loading || postId.length() == 0) {
      return;
    }
    loading = true;
    navigator.showLoadingAlert(
        Lang.tr("reddit.loading_comments"),
        new Runnable() {
          public void run() {
            loading = false;
          }
        });
    MIDPlay.startOperation(
        new HttpTextOperation(
            RedditApi.commentsUrl(postId),
            new HttpTextOperation.Listener() {
              public void onText(final String response) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        String error = RedditApi.parseError(response);
                        if (error.length() > 0) {
                          navigator.showAlert(error, AlertType.ERROR);
                          return;
                        }
                        pending = RedditApi.commentsPending(response);
                        items = RedditApi.parseComments(response);
                        refresh();
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
}
