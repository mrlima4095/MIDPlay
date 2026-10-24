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
import midplay.net.RedditApi;
import midplay.store.Configuration;
import midplay.store.SettingsManager;
import midplay.ui.BaseList;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;

/** Threaded comments of one Reddit post, indented by their reply depth. */
public final class RedditCommentsScreen extends BaseList {
  /**
   * Comments are translated in batches because the proxy caps the translation query and the
   * upstream service does not like very long URLs.
   */
  private static final int TRANSLATE_CHUNK = 1500;

  private final String postId;
  private final Command reloadCommand;
  private final Command translateCommand;
  private final Command originalCommand;
  private Vector items = new Vector();
  private final Hashtable translatedBodies = new Hashtable();
  private boolean loading;
  private boolean pending;
  private boolean translating;
  private boolean commentsTranslated;
  private boolean showOriginal;

  public RedditCommentsScreen(Navigator navigator, String postId, String postTitle) {
    super(Lang.tr("reddit.comments_title"), navigator);
    this.postId = postId == null ? "" : postId;
    reloadCommand = new Command(Lang.tr("reddit.reload"), Command.SCREEN, 1);
    translateCommand = new Command(Lang.tr("reddit.translate"), Command.SCREEN, 2);
    originalCommand = new Command(Lang.tr("reddit.show_original"), Command.SCREEN, 2);
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
      append(
          RedditApi.commentLabel((String[]) items.elementAt(i), translatedBody(i)), null);
    }
  }

  protected void handleSelection() {
    int index = getSelectedIndex();
    if (index < 0 || index >= items.size()) {
      return;
    }
    showComment((String[]) items.elementAt(index), index);
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == reloadCommand) {
      load();
    } else if (c == translateCommand) {
      if (commentsTranslated) {
        showOriginal = false;
        refresh();
        updateTranslateCommands();
      } else {
        translateComments(true);
      }
    } else if (c == originalCommand) {
      showOriginal = true;
      refresh();
      updateTranslateCommands();
    }
  }

  private void showComment(final String[] comment, final int index) {
    final Form form = new Form(Lang.tr("reddit.comment"));
    form.append(
        Lang.tr("reddit.author") + ": u/" + comment[RedditApi.COMMENT_AUTHOR] + "\n");
    form.append(Lang.tr("reddit.score") + ": " + comment[RedditApi.COMMENT_SCORE] + "\n\n");
    final StringItem body = new StringItem(null, commentBody(comment, index, false));
    form.append(body);
    form.addCommand(Commands.back());
    if (translatedBodies.get(new Integer(index)) != null) {
      final Command showTranslated = new Command(Lang.tr("reddit.translate"), Command.SCREEN, 1);
      final Command showOrig = new Command(Lang.tr("reddit.show_original"), Command.SCREEN, 1);
      form.addCommand(showTranslated);
      form.addCommand(showOrig);
      form.setCommandListener(
          new CommandListener() {
            public void commandAction(Command c, Displayable d) {
              if (c == Commands.back()) {
                navigator.back();
              } else if (c == showTranslated) {
                body.setText(commentBody(comment, index, false));
              } else if (c == showOrig) {
                body.setText(commentBody(comment, index, true));
              }
            }
          });
    } else {
      form.setCommandListener(
          new CommandListener() {
            public void commandAction(Command c, Displayable d) {
              if (c == Commands.back()) {
                navigator.back();
              }
            }
          });
    }
    navigator.forward(form);
  }

  /** Translated body for a comment row, or null when the original text should be shown. */
  private String translatedBody(int index) {
    if (!commentsTranslated || showOriginal) {
      return null;
    }
    return (String) translatedBodies.get(new Integer(index));
  }

  private String commentBody(String[] comment, int index, boolean original) {
    if (!original) {
      String translated = (String) translatedBodies.get(new Integer(index));
      if (translated != null && translated.length() > 0) {
        return translated;
      }
    }
    return comment[RedditApi.COMMENT_BODY];
  }

  private void updateTranslateCommands() {
    if (items.size() == 0) {
      removeCommand(translateCommand);
      removeCommand(originalCommand);
      return;
    }
    if (commentsTranslated && !showOriginal) {
      removeCommand(translateCommand);
      addCommand(originalCommand);
    } else {
      removeCommand(originalCommand);
      addCommand(translateCommand);
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
                        translatedBodies.clear();
                        commentsTranslated = false;
                        showOriginal = false;
                        refresh();
                        updateTranslateCommands();
                        if (items.size() > 0
                            && SettingsManager.getInstance().getRedditAutoTranslate()
                                == Configuration.REDDIT_TRANSLATE_ON) {
                          translateComments(false);
                        }
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
   * Translates the comments in chunks of at most {@link #TRANSLATE_CHUNK} characters. Each batch is
   * one line per comment, so the reply can be split back into the right rows.
   */
  private void translateComments(final boolean reportErrors) {
    if (translating || items.size() == 0) {
      return;
    }
    Vector chunks = new Vector();
    Vector indexes = new Vector();
    int size = 0;
    for (int i = 0; i < items.size(); i++) {
      String body = normalizedBody((String[]) items.elementAt(i));
      if (body.length() == 0) {
        continue;
      }
      int extra = body.length() + (indexes.size() > 0 ? 1 : 0);
      if (indexes.size() > 0 && size + extra > TRANSLATE_CHUNK) {
        chunks.addElement(toIntArray(indexes));
        indexes = new Vector();
        size = 0;
        extra = body.length();
      }
      indexes.addElement(new Integer(i));
      size += extra;
    }
    if (indexes.size() > 0) {
      chunks.addElement(toIntArray(indexes));
    }
    if (chunks.size() == 0) {
      return;
    }
    translating = true;
    if (reportErrors) {
      navigator.showLoadingAlert(
          Lang.tr("reddit.translating"),
          new Runnable() {
            public void run() {
              translating = false;
            }
          });
    }
    translateChunk(chunks, 0, reportErrors);
  }

  private void translateChunk(
      final Vector chunks, final int chunkIndex, final boolean reportErrors) {
    final int[] current = (int[]) chunks.elementAt(chunkIndex);
    StringBuffer joined = new StringBuffer();
    for (int i = 0; i < current.length; i++) {
      if (i > 0) {
        joined.append('\n');
      }
      joined.append(normalizedBody((String[]) items.elementAt(current[i])));
    }
    MIDPlay.startOperation(
        new HttpTextOperation(
            RedditApi.translateUrl(joined.toString(), RedditApi.translateTarget()),
            new HttpTextOperation.Listener() {
              public void onText(final String response) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        String result = RedditApi.parseTranslation(response);
                        String[] lines = splitLines(result);
                        if (lines.length != current.length) {
                          failTranslation(reportErrors);
                          return;
                        }
                        for (int i = 0; i < lines.length; i++) {
                          translatedBodies.put(new Integer(current[i]), lines[i].trim());
                        }
                        if (chunkIndex + 1 < chunks.size()) {
                          translateChunk(chunks, chunkIndex + 1, reportErrors);
                        } else {
                          translating = false;
                          commentsTranslated = true;
                          showOriginal = false;
                          if (reportErrors) {
                            navigator.dismissAlert();
                          }
                          refresh();
                          updateTranslateCommands();
                        }
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

  private static String normalizedBody(String[] comment) {
    String body = comment[RedditApi.COMMENT_BODY];
    if (body == null) {
      return "";
    }
    StringBuffer out = new StringBuffer(body.length());
    boolean space = false;
    for (int i = 0; i < body.length(); i++) {
      char c = body.charAt(i);
      if (c == '\n' || c == '\r' || c == '\t') {
        space = true;
      } else {
        if (space && out.length() > 0) {
          out.append(' ');
        }
        out.append(c);
        space = false;
      }
    }
    return out.toString().trim();
  }

  private static int[] toIntArray(Vector values) {
    int[] result = new int[values.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = ((Integer) values.elementAt(i)).intValue();
    }
    return result;
  }

  private static String[] splitLines(String text) {
    String value = text;
    while (value.endsWith("\n")) {
      value = value.substring(0, value.length() - 1);
    }
    Vector lines = new Vector();
    int start = 0;
    for (int i = 0; i < value.length(); i++) {
      if (value.charAt(i) == '\n') {
        String line = value.substring(start, i);
        if (line.endsWith("\r")) {
          line = line.substring(0, line.length() - 1);
        }
        lines.addElement(line);
        start = i + 1;
      }
    }
    String last = value.substring(start);
    if (last.endsWith("\r")) {
      last = last.substring(0, last.length() - 1);
    }
    lines.addElement(last);
    String[] result = new String[lines.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = (String) lines.elementAt(i);
    }
    return result;
  }
}
