package midplay.ui.screen;

import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;
import midplay.MIDPlay;
import midplay.net.WattpadApi;
import midplay.net.WattpadChapterOperation;
import midplay.store.SettingsManager;
import midplay.store.WattpadDownloadManager;
import midplay.store.WattpadProgressManager;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.ui.Sharing;
import midplay.util.Lang;
import midplay.util.Pager;

/**
 * Reads one Wattpad chapter as read-only scrollable text, split into pages of a configurable size
 * (Settings &gt; Wattpad chars per page). The chapter can also be saved to the notes or exported to a
 * file. Moving to another chapter reloads this same screen, so Back always returns to the list that
 * opened the reader.
 */
public final class WattpadReaderScreen extends BaseForm {
  private static final int TITLE_MAX = 40;

  private final String storyId;
  private final String storyTitle;
  private final Vector parts;
  private int index;
  private String partId;
  private String chapterTitle;

  private final Command prevPage;
  private final Command nextPage;
  private final Command gotoChapter;
  private final Command nextChapter;
  private final Command continueLoadingCommand;
  private final Command shareChapterCommand;
  private final Command saveChapterCommand;
  private final StringItem content = new StringItem(null, "");

  private String fullText = "";
  private String[] pages;
  private int page;
  private int loadToken;
  private int nextLoadPage;
  private boolean moreAvailable;
  private boolean continueAdded;
  private boolean loadingMore;
  private boolean prevAdded;
  private boolean nextAdded;
  private boolean chapterNextAdded;

  public WattpadReaderScreen(
      Navigator navigator, String storyId, String storyTitle, Vector parts, int index) {
    this(navigator, storyId, storyTitle, parts, index, 0);
  }

  public WattpadReaderScreen(
      Navigator navigator, String storyId, String storyTitle, Vector parts, int index, int startPage) {
    super(buildTitle(parts, index), navigator);
    this.storyId = storyId == null ? "" : storyId;
    this.storyTitle = storyTitle == null ? "" : storyTitle;
    this.parts = parts;
    this.index = index;
    this.partId = ((String[]) parts.elementAt(index))[WattpadApi.PART_ID];
    this.chapterTitle = chapterTitle(index);
    // Save as soon as the reader opens, before the chapter finishes loading, so any interruption
    // still leaves a usable "Continue reading" entry.
    WattpadProgressManager.getInstance()
        .record(this.storyId, this.storyTitle, index, parts.size(), this.chapterTitle, startPage);
    prevPage = new Command(Lang.tr("wattpad.prev_page"), Command.SCREEN, 1);
    nextPage = new Command(Lang.tr("wattpad.next_page"), Command.SCREEN, 2);
    gotoChapter = new Command(Lang.tr("wattpad.go_to_chapter"), Command.SCREEN, 3);
    nextChapter = new Command(Lang.tr("wattpad.next_chapter"), Command.SCREEN, 2);
    continueLoadingCommand = new Command(Lang.tr("wattpad.continue_loading"), Command.SCREEN, 4);
    shareChapterCommand = new Command(Lang.tr("wattpad.share_chapter"), Command.SCREEN, 5);
    saveChapterCommand = new Command(Lang.tr("wattpad.save_chapter_page"), Command.SCREEN, 6);
    append(content);
    addCommand(gotoChapter);
    addCommand(shareChapterCommand);
    addCommand(saveChapterCommand);
    updateChapterNextCommand();
    addCommand(Commands.playerNowPlaying());
    content.setText(Lang.tr("wattpad.loading_chapter"));
    load(startPage);
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == Commands.playerNowPlaying()) {
      PlayerNavHelper.showNowPlaying(navigator);
    } else if (c == prevPage) {
      showPage(page - 1);
    } else if (c == nextPage) {
      if (pages != null && page < pages.length - 1) {
        showPage(page + 1);
      } else if (moreAvailable) {
        // The chapter was cut by the safety page cap; extend it instead of ending it.
        loadMore();
      } else if (index + 1 < parts.size()) {
        // At the end of a chapter, "next page" continues into the next chapter.
        openChapter(index + 1, 0);
      }
    } else if (c == gotoChapter) {
      promptChapter();
    } else if (c == nextChapter) {
      openChapter(index + 1, 0);
    } else if (c == continueLoadingCommand) {
      loadMore();
    } else if (c == shareChapterCommand) {
      shareChapter();
    } else if (c == saveChapterCommand) {
      saveCurrentPage();
    }
  }

  /** Loads another chapter in place, keeping a single reader screen in the navigation stack. */
  private void openChapter(int target, int startPage) {
    if (target < 0 || target >= parts.size()) {
      return;
    }
    index = target;
    partId = ((String[]) parts.elementAt(target))[WattpadApi.PART_ID];
    chapterTitle = chapterTitle(target);
    setTitle(shorten(chapterTitle));
    // Switching chapters is also an entry point; save before the new chapter loads.
    WattpadProgressManager.getInstance()
        .record(storyId, storyTitle, index, parts.size(), chapterTitle, startPage);
    fullText = "";
    pages = null;
    page = 0;
    nextLoadPage = 1;
    moreAvailable = false;
    updateContinueCommand();
    if (prevAdded) {
      removeCommand(prevPage);
      prevAdded = false;
    }
    if (nextAdded) {
      removeCommand(nextPage);
      nextAdded = false;
    }
    updateChapterNextCommand();
    content.setText(Lang.tr("wattpad.loading_chapter"));
    load(startPage);
  }

  private void updateChapterNextCommand() {
    boolean hasNext = index + 1 < parts.size();
    if (hasNext && !chapterNextAdded) {
      addCommand(nextChapter);
      chapterNextAdded = true;
    } else if (!hasNext && chapterNextAdded) {
      removeCommand(nextChapter);
      chapterNextAdded = false;
    }
  }

  /** Shares the chapter link straight through MPGram, without an intermediate menu. */
  private void shareChapter() {
    Sharing.toMPGram(this, chapterTitle + "\nhttps://www.wattpad.com/" + partId);
  }

  /** Saves only the page currently on screen, not the whole chapter. */
  private void saveCurrentPage() {
    if (pages == null || page < 0 || page >= pages.length || pages[page].length() == 0) {
      navigator.showAlert(Lang.tr("wattpad.empty"), AlertType.INFO);
      return;
    }
    String suffix =
        pages.length > 1
            ? " - " + Lang.tr("wattpad.page") + " " + (page + 1) + "/" + pages.length
            : "";
    navigator.forward(new TextSaveScreen(navigator, this, chapterTitle + suffix, pages[page]));
  }

  private void promptChapter() {
    final Form form = new Form(Lang.tr("wattpad.go_to_chapter"));
    final TextField field =
        new TextField(
            Lang.tr("wattpad.chapter_number"), Integer.toString(index + 1), 5, TextField.NUMERIC);
    form.append(field);
    form.addCommand(Commands.ok());
    form.addCommand(Commands.cancel());
    form.setCommandListener(
        new CommandListener() {
          public void commandAction(Command command, Displayable displayable) {
            if (command == Commands.cancel()) {
              navigator.back();
            } else if (command == Commands.ok()) {
              int target = parseChapter(field.getString());
              if (target < 0 || target >= parts.size()) {
                navigator.showAlert(Lang.tr("wattpad.invalid_chapter"), AlertType.ERROR);
                return;
              }
              navigator.popTo(WattpadReaderScreen.this);
              openChapter(target, 0);
            }
          }
        });
    navigator.forward(form);
  }

  private static int parseChapter(String value) {
    try {
      return Integer.parseInt(value.trim()) - 1;
    } catch (Exception e) {
      return -1;
    }
  }

  private void load(final int startPage) {
    final int token = ++loadToken;
    loadingMore = false;
    // A downloaded chapter is served from the memory card, so the reader also works offline.
    String offlineText = WattpadDownloadManager.getInstance().readChapter(storyId, partId);
    if (offlineText != null) {
      applyText(offlineText, startPage, true, false, 1);
      return;
    }
    MIDPlay.startOperation(
        new WattpadChapterOperation(
            partId,
            new WattpadChapterOperation.Listener() {
              public void onProgress(final int pageNumber) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        if (token == loadToken && pages == null) {
                          content.setText(
                              Lang.tr("wattpad.loading_chapter") + " " + pageNumber);
                        }
                      }
                    });
              }

              public void onSuccess(final String text, final boolean more, final int next) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        if (token != loadToken) {
                          return;
                        }
                        applyText(text, startPage, false, more, next);
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        if (token == loadToken) {
                          content.setText(Lang.tr("wattpad.error") + "\n" + e);
                        }
                      }
                    });
              }
            }));
  }

  /** Loads the next block of a chapter that hit the operation's safety page limit. */
  private void loadMore() {
    if (loadingMore || !moreAvailable) {
      return;
    }
    loadingMore = true;
    removeCommand(continueLoadingCommand);
    continueAdded = false;
    final int token = ++loadToken;
    MIDPlay.startOperation(
        new WattpadChapterOperation(
            partId,
            nextLoadPage,
            new WattpadChapterOperation.Listener() {
              public void onProgress(final int pageNumber) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        if (token == loadToken) {
                          content.setText(
                              Lang.tr("wattpad.loading_chapter") + " " + pageNumber);
                        }
                      }
                    });
              }

              public void onSuccess(final String text, final boolean more, final int next) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        if (token != loadToken) {
                          return;
                        }
                        loadingMore = false;
                        if (text != null && text.length() > 0) {
                          fullText = fullText + text;
                          pages =
                              Pager.paginate(
                                  fullText,
                                  SettingsManager.getInstance().getWattpadPageChars());
                        }
                        moreAvailable = more;
                        nextLoadPage = next;
                        updateContinueCommand();
                        showPage(page < pages.length ? page : pages.length - 1);
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        if (token == loadToken) {
                          loadingMore = false;
                          updateContinueCommand();
                          navigator.showAlert(
                              Lang.tr("wattpad.error") + "\n" + e, AlertType.ERROR);
                        }
                      }
                    });
              }
            }));
  }

  /** Paginates a chapter (from disk or from the network) and shows the requested page. */
  private void applyText(
      String text, int startPage, boolean offline, boolean more, int next) {
    fullText = text;
    pages = Pager.paginate(text, SettingsManager.getInstance().getWattpadPageChars());
    moreAvailable = more;
    nextLoadPage = next;
    int target = startPage;
    if (target < 0 || target >= pages.length) {
      target = 0;
    }
    setTitle(shorten(chapterTitle + (offline ? " [" + Lang.tr("wattpad.offline") + "]" : "")));
    updateContinueCommand();
    showPage(target);
  }

  private void updateContinueCommand() {
    if (moreAvailable) {
      if (!continueAdded) {
        addCommand(continueLoadingCommand);
        continueAdded = true;
      }
    } else if (continueAdded) {
      removeCommand(continueLoadingCommand);
      continueAdded = false;
    }
  }

  private void showPage(int target) {
    if (pages == null || target < 0 || target >= pages.length) {
      return;
    }
    page = target;
    StringBuffer sb = new StringBuffer();
    sb.append(Lang.tr("wattpad.page"))
        .append(' ')
        .append(page + 1)
        .append('/')
        .append(pages.length)
        .append("\n\n");
    sb.append(pages[page]);
    content.setText(sb.toString());
    WattpadProgressManager.getInstance()
        .record(storyId, storyTitle, index, parts.size(), chapterTitle, page);
    updateNavCommands();
  }

  private void updateNavCommands() {
    boolean wantPrev = page > 0;
    boolean wantNext =
        pages != null
            && (page < pages.length - 1 || moreAvailable || index + 1 < parts.size());
    if (wantPrev && !prevAdded) {
      addCommand(prevPage);
      prevAdded = true;
    } else if (!wantPrev && prevAdded) {
      removeCommand(prevPage);
      prevAdded = false;
    }
    if (wantNext && !nextAdded) {
      addCommand(nextPage);
      nextAdded = true;
    } else if (!wantNext && nextAdded) {
      removeCommand(nextPage);
      nextAdded = false;
    }
  }

  private String chapterTitle(int partIndex) {
    return WattpadApi.chapterLabel(
        partIndex, ((String[]) parts.elementAt(partIndex))[WattpadApi.PART_TITLE]);
  }

  private static String buildTitle(Vector parts, int index) {
    String[] part = (String[]) parts.elementAt(index);
    return shorten(WattpadApi.chapterLabel(index, part[WattpadApi.PART_TITLE]));
  }

  private static String shorten(String title) {
    if (title == null || title.length() == 0) {
      return Lang.tr("wattpad.title");
    }
    if (title.length() <= TITLE_MAX) {
      return title;
    }
    return title.substring(0, TITLE_MAX - 3) + "...";
  }
}
