package midplay.ui.screen;

import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;
import midplay.MIDPlay;
import midplay.net.HttpTextOperation;
import midplay.net.RedditApi;
import midplay.store.Configuration;
import midplay.store.SettingsManager;
import midplay.ui.BaseList;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;

/** Threaded comments of one Reddit post, indented by their reply depth. */
public final class RedditCommentsScreen extends BaseList {
  /** Long comments are translated in pieces so the proxy query stays small. */
  private static final int TRANSLATE_CHUNK = 1200;

  private final String postId;
  private final Command reloadCommand;
  private Vector items = new Vector();
  private boolean loading;
  private boolean pending;

  public RedditCommentsScreen(Navigator navigator, String postId, String postTitle) {
    super(Lang.tr("reddit.comments_title"), navigator);
    this.postId = postId == null ? "" : postId;
    reloadCommand = new Command(Lang.tr("reddit.reload"), Command.SCREEN, 2);
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
    new CommentView((String[]) items.elementAt(index)).open();
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == reloadCommand) {
      load();
    }
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
                        String error = RedditApi.parseError(response);
                        if (error.length() > 0) {
                          navigator.showAlert(error, AlertType.ERROR);
                          return;
                        }
                        navigator.dismissAlert();
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
                        navigator.showAlert(e.toString(), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  /**
   * One comment opened on its own screen. Translation lives here instead of the list, with a
   * single command that switches between "Translate" and "Show original".
   */
  private final class CommentView implements CommandListener {
    private final Form form;
    private final StringItem body = new StringItem(null, "");
    private final String original;
    private final Command translateCommand;
    private final Command originalCommand;
    private String translated;
    private boolean translating;
    private Vector chunks;
    private int nextChunk;
    private StringBuffer partial;

    CommentView(String[] comment) {
      original =
          comment[RedditApi.COMMENT_BODY] == null ? "" : comment[RedditApi.COMMENT_BODY];
      translateCommand = new Command(Lang.tr("reddit.translate"), Command.SCREEN, 1);
      originalCommand = new Command(Lang.tr("reddit.show_original"), Command.SCREEN, 1);
      form = new Form(Lang.tr("reddit.comment"));
      form.append(
          Lang.tr("reddit.author") + ": u/" + comment[RedditApi.COMMENT_AUTHOR] + "\n");
      form.append(Lang.tr("reddit.score") + ": " + comment[RedditApi.COMMENT_SCORE] + "\n\n");
      body.setText(original);
      form.append(body);
      form.addCommand(Commands.back());
      form.addCommand(Commands.playerNowPlaying());
      form.addCommand(translateCommand);
      form.setCommandListener(this);
    }

    void open() {
      navigator.forward(form);
      if (original.length() > 0
          && SettingsManager.getInstance().getRedditAutoTranslate()
              == Configuration.REDDIT_TRANSLATE_ON) {
        translate(false);
      }
    }

    public void commandAction(Command c, Displayable d) {
      if (c == Commands.back()) {
        navigator.back();
      } else if (c == Commands.playerNowPlaying()) {
        PlayerNavHelper.showNowPlaying(navigator);
      } else if (c == translateCommand) {
        if (translated != null) {
          showTranslated();
        } else {
          translate(true);
        }
      } else if (c == originalCommand) {
        showOriginal();
      }
    }

    private void showTranslated() {
      body.setText(translated);
      form.removeCommand(translateCommand);
      form.addCommand(originalCommand);
    }

    private void showOriginal() {
      body.setText(original);
      form.removeCommand(originalCommand);
      form.addCommand(translateCommand);
    }

    private void translate(final boolean reportErrors) {
      if (translating || original.length() == 0) {
        return;
      }
      translating = true;
      chunks = splitChunks(original);
      nextChunk = 0;
      partial = new StringBuffer();
      if (chunks.size() == 0) {
        translating = false;
        return;
      }
      if (reportErrors) {
        navigator.showLoadingAlert(
            Lang.tr("reddit.translating"),
            new Runnable() {
              public void run() {
                translating = false;
              }
            });
      }
      translateNext(reportErrors);
    }

    private void translateNext(final boolean reportErrors) {
      if (nextChunk >= chunks.size()) {
        translating = false;
        translated = partial.toString().trim();
        if (translated.length() == 0) {
          failTranslation(reportErrors);
          return;
        }
        if (reportErrors) {
          navigator.dismissAlert();
        }
        showTranslated();
        return;
      }
      final String chunk = (String) chunks.elementAt(nextChunk);
      MIDPlay.startOperation(
          new HttpTextOperation(
              RedditApi.translateUrl(chunk, RedditApi.translateTarget()),
              new HttpTextOperation.Listener() {
                public void onText(final String response) {
                  navigator.callSerially(
                      new Runnable() {
                        public void run() {
                          String result = RedditApi.parseTranslation(response).trim();
                          if (result.length() == 0) {
                            failTranslation(reportErrors);
                            return;
                          }
                          if (partial.length() > 0) {
                            partial.append(' ');
                          }
                          partial.append(result);
                          nextChunk++;
                          translateNext(reportErrors);
                        }
                      });
                }

                public void onError(final Exception e) {
                  navigator.callSerially(
                      new Runnable() {
                        public void run() {
                          failTranslation(reportErrors);
                        }
                      });
                }
              }));
    }

    private void failTranslation(boolean reportErrors) {
      translating = false;
      if (reportErrors) {
        navigator.showAlert(Lang.tr("reddit.translate_error"), AlertType.ERROR);
      }
    }
  }

  /** Splits a comment on whitespace into chunks the translation proxy can handle. */
  private static Vector splitChunks(String text) {
    Vector chunks = new Vector();
    StringBuffer current = new StringBuffer();
    int i = 0;
    while (i < text.length()) {
      while (i < text.length() && isSpace(text.charAt(i))) {
        i++;
      }
      int start = i;
      while (i < text.length() && !isSpace(text.charAt(i))) {
        i++;
      }
      String word = text.substring(start, i);
      while (word.length() > TRANSLATE_CHUNK) {
        if (current.length() > 0) {
          chunks.addElement(current.toString());
          current = new StringBuffer();
        }
        chunks.addElement(word.substring(0, TRANSLATE_CHUNK));
        word = word.substring(TRANSLATE_CHUNK);
      }
      if (word.length() == 0) {
        continue;
      }
      if (current.length() > 0 && current.length() + 1 + word.length() > TRANSLATE_CHUNK) {
        chunks.addElement(current.toString());
        current = new StringBuffer();
      }
      if (current.length() > 0) {
        current.append(' ');
      }
      current.append(word);
    }
    if (current.length() > 0) {
      chunks.addElement(current.toString());
    }
    return chunks;
  }

  private static boolean isSpace(char c) {
    return c == ' ' || c == '\n' || c == '\r' || c == '\t';
  }
}
