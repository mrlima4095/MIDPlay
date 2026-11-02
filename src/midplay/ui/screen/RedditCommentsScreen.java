package midplay.ui.screen;

import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.List;
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
  private final Command sortCommand;
  private final Command moreCommand;
  private Vector items = new Vector();
  private final Hashtable ids = new Hashtable();
  private String bookmark = "";
  private String sort = RedditApi.COMMENT_SORT_TOP;
  private boolean hasMore;
  private boolean loading;
  private boolean pending;
  private boolean moreAdded;

  public RedditCommentsScreen(Navigator navigator, String postId, String postTitle) {
    super(Lang.tr("reddit.comments_title"), navigator);
    this.postId = postId == null ? "" : postId;
    reloadCommand = new Command(Lang.tr("reddit.reload"), Command.SCREEN, 2);
    sortCommand = new Command(Lang.tr("reddit.sort"), Command.SCREEN, 3);
    moreCommand = new Command(Lang.tr("reddit.more"), Command.SCREEN, 4);
    addCommand(reloadCommand);
    addCommand(sortCommand);
    load(false);
  }

  protected void showNotify() {
    super.showNotify();
    if (items.size() == 0) {
      load(false);
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
      load(false);
    } else if (c == sortCommand) {
      showSortMenu();
    } else if (c == moreCommand) {
      load(true);
    }
  }

  private void showSortMenu() {
    final List menu = new List(Lang.tr("reddit.sort"), Choice.IMPLICIT);
    menu.append(Lang.tr("reddit.sort_top"), null);
    menu.append(Lang.tr("reddit.sort_new"), null);
    menu.append(Lang.tr("reddit.sort_old"), null);
    int selected =
        RedditApi.COMMENT_SORT_NEW.equals(sort)
            ? 1
            : (RedditApi.COMMENT_SORT_OLD.equals(sort) ? 2 : 0);
    menu.setSelectedIndex(selected, true);
    menu.addCommand(Commands.back());
    menu.setCommandListener(
        new CommandListener() {
          public void commandAction(Command command, Displayable displayable) {
            if (command == Commands.back()) {
              navigator.back();
              return;
            }
            if (command == List.SELECT_COMMAND) {
              int choice = menu.getSelectedIndex();
              String next =
                  choice == 1
                      ? RedditApi.COMMENT_SORT_NEW
                      : (choice == 2 ? RedditApi.COMMENT_SORT_OLD : RedditApi.COMMENT_SORT_TOP);
              navigator.back();
              if (!next.equals(sort)) {
                sort = next;
                load(false);
              }
            }
          }
        });
    navigator.forward(menu);
  }

  /** Loads the first page, or appends the next one when {@code append} is set. */
  private void load(final boolean append) {
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
            RedditApi.commentsUrl(postId, sort, append ? bookmark : ""),
            new HttpTextOperation.Listener() {
              public void onText(final String response) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        String error = RedditApi.parseError(response);
                        if (error.length() > 0) {
                          navigator.dismissAlert();
                          navigator.showAlert(error, AlertType.ERROR);
                          return;
                        }
                        navigator.dismissAlert();
                        if (!append) {
                          pending = RedditApi.commentsPending(response);
                          items = new Vector();
                          ids.clear();
                        }
                        Vector page = RedditApi.parseComments(response);
                        int added = 0;
                        for (int i = 0; i < page.size(); i++) {
                          String[] comment = (String[]) page.elementAt(i);
                          String id = comment[RedditApi.COMMENT_ID];
                          String key =
                              id != null && id.length() > 0
                                  ? id
                                  : comment[RedditApi.COMMENT_AUTHOR]
                                      + "\n"
                                      + comment[RedditApi.COMMENT_CREATED]
                                      + "\n"
                                      + comment[RedditApi.COMMENT_BODY];
                          if (ids.containsKey(key)) {
                            continue;
                          }
                          ids.put(key, key);
                          items.addElement(comment);
                          added++;
                        }
                        String next = RedditApi.parseBookmark(response);
                        boolean end = next.length() == 0 || next.equals(bookmark);
                        bookmark = next;
                        hasMore = !end;
                        updateMoreCommand();
                        refresh();
                        if (append && added == 0) {
                          navigator.showAlert(Lang.tr("reddit.end"), AlertType.INFO);
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
