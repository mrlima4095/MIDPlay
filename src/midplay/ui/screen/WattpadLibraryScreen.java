package midplay.ui.screen;

import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import midplay.MIDPlay;
import midplay.net.HttpTextOperation;
import midplay.net.WattpadApi;
import midplay.store.WattpadDownloadManager;
import midplay.store.WattpadProgressManager;
import midplay.ui.BaseList;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.Sharing;
import midplay.util.Lang;

/**
 * Local Wattpad reading list: stories opened before, each one showing the chapter where the reader
 * stopped. In the normal list, selecting a row resumes it; in the archived list, selecting a row
 * opens the story's chapter list, where the "Continue reading" command resumes from the saved page.
 */
public final class WattpadLibraryScreen extends BaseList {
  private static final int MAX_STORY_BYTES = 400 * 1024;
  private static final int SORT_RECENT = 0;
  private static final int SORT_TITLE = 1;
  private static final int SORT_PROGRESS = 2;

  private final Command synopsisCommand;
  private final Command shareCommand;
  private final Command selectCommand;
  private final Command removeCommand;
  private final Command clearCommand;
  private final Command archiveCommand;
  private final Command unarchiveCommand;
  private final Command viewArchivedCommand;
  private final Command sortCommand;
  private final boolean archived;
  private Vector entries;
  private boolean loading;
  private int sortMode = SORT_RECENT;

  public WattpadLibraryScreen(Navigator navigator) {
    this(navigator, false);
  }

  public WattpadLibraryScreen(Navigator navigator, boolean archived) {
    super(Lang.tr(archived ? "wattpad.archived" : "wattpad.library"), navigator);
    this.archived = archived;
    synopsisCommand = new Command(Lang.tr("wattpad.synopsis"), Command.SCREEN, 1);
    shareCommand = new Command(Lang.tr("wattpad.share"), Command.SCREEN, 2);
    selectCommand = new Command(Lang.tr("wattpad.select"), Command.SCREEN, 3);
    removeCommand = new Command(Lang.tr("wattpad.remove"), Command.SCREEN, 4);
    archiveCommand = new Command(Lang.tr("wattpad.archive"), Command.SCREEN, 5);
    unarchiveCommand = new Command(Lang.tr("wattpad.unarchive"), Command.SCREEN, 5);
    viewArchivedCommand = new Command(Lang.tr("wattpad.view_archived"), Command.SCREEN, 6);
    clearCommand =
        new Command(
            Lang.tr(archived ? "wattpad.clear_archive" : "wattpad.clear_library"),
            Command.SCREEN,
            7);
    sortCommand = new Command(Lang.tr("wattpad.sort"), Command.SCREEN, 8);
    entries = WattpadProgressManager.getInstance().getEntries(archived);
    sortEntries();
    refresh();
    updateCommands();
  }

  protected void showNotify() {
    super.showNotify();
    reload();
  }

  /** Rebuilds the rows from the persisted progress (also called after multi-select deletions). */
  void reload() {
    entries = WattpadProgressManager.getInstance().getEntries(archived);
    sortEntries();
    refresh();
    updateCommands();
  }

  /** Keeps action commands out of the menu while there is nothing they could act on. */
  private void updateCommands() {
    int size = entries.size();
    setCommandVisible(synopsisCommand, size > 0);
    setCommandVisible(shareCommand, size > 0);
    setCommandVisible(selectCommand, size > 0);
    setCommandVisible(removeCommand, size > 0);
    setCommandVisible(clearCommand, size > 0);
    setCommandVisible(sortCommand, size > 1);
    if (archived) {
      setCommandVisible(archiveCommand, false);
      setCommandVisible(viewArchivedCommand, false);
      setCommandVisible(unarchiveCommand, size > 0);
    } else {
      setCommandVisible(unarchiveCommand, false);
      setCommandVisible(archiveCommand, size > 0);
      setCommandVisible(
          viewArchivedCommand,
          WattpadProgressManager.getInstance().getEntries(true).size() > 0);
    }
  }

  private void setCommandVisible(Command command, boolean visible) {
    if (visible) {
      addCommand(command);
    } else {
      removeCommand(command);
    }
  }

  protected void populateItems() {
    if (entries.size() == 0) {
      append(
          Lang.tr(archived ? "wattpad.archived_empty" : "wattpad.library_empty"), null);
      return;
    }
    for (int i = 0; i < entries.size(); i++) {
      append(labelFor((String[]) entries.elementAt(i)), null);
    }
  }

  /** Row label: "Title" plus a second line "Chapter 3/15 - name". */
  static String labelFor(String[] entry) {
    String title = displayTitle(entry);
    int index = parseInt(entry[WattpadProgressManager.ENTRY_INDEX]);
    int total = parseInt(entry[WattpadProgressManager.ENTRY_TOTAL]);
    StringBuffer sb = new StringBuffer(title);
    sb.append('\n').append(Lang.tr("wattpad.chapter")).append(' ').append(index + 1);
    if (total > 0) {
      sb.append('/').append(total);
    }
    String chapter = entry[WattpadProgressManager.ENTRY_CHAPTER];
    if (chapter.length() > 0) {
      sb.append(" - ").append(chapter);
    }
    int page = parseInt(entry[WattpadProgressManager.ENTRY_PAGE]);
    if (page > 0) {
      sb.append(" - ").append(Lang.tr("wattpad.page")).append(' ').append(page + 1);
    }
    return sb.toString();
  }

  private static String displayTitle(String[] entry) {
    String title = entry[WattpadProgressManager.ENTRY_TITLE];
    return title.length() == 0 ? entry[WattpadProgressManager.ENTRY_ID] : title;
  }

  private static String storyUrl(String[] entry) {
    return "https://www.wattpad.com/story/" + entry[WattpadProgressManager.ENTRY_ID];
  }

  protected void handleSelection() {
    if (loading) {
      return;
    }
    int index = getSelectedIndex();
    if (index < 0 || index >= entries.size()) {
      return;
    }
    open((String[]) entries.elementAt(index));
  }

  protected void handleCommand(Command c, Displayable d) {
    int index = getSelectedIndex();
    if (c == viewArchivedCommand) {
      navigator.forward(new WattpadLibraryScreen(navigator, true));
      return;
    }
    if (c == sortCommand) {
      showSortMenu();
      return;
    }
    if (c == selectCommand) {
      if (entries.size() > 0) {
        navigator.forward(
            new WattpadLibrarySelectScreen(
                navigator,
                entries,
                new Runnable() {
                  public void run() {
                    reload();
                  }
                },
                archived));
      }
      return;
    }
    if (index < 0 || index >= entries.size()) {
      return;
    }
    String[] entry = (String[]) entries.elementAt(index);
    if (c == synopsisCommand) {
      loadSynopsis(entry);
    } else if (c == shareCommand) {
      share(entry);
    } else if (c == removeCommand) {
      confirmRemove(entry);
    } else if (c == archiveCommand) {
      confirmArchive(entry);
    } else if (c == unarchiveCommand) {
      unarchive(entry);
    } else if (c == clearCommand) {
      confirmClear();
    }
  }

  private void loadSynopsis(final String[] entry) {
    loading = true;
    navigator.showLoadingAlert(Lang.tr("wattpad.loading"), cancelLoading());
    MIDPlay.startOperation(
        new HttpTextOperation(
            WattpadApi.storyUrl(entry[WattpadProgressManager.ENTRY_ID]),
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        String description = WattpadApi.parseDescription(text);
                        navigator.showAlert(
                            description.length() == 0 ? Lang.tr("wattpad.empty") : description,
                            AlertType.INFO);
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

  /** Sends the story link through MPGram; the user picks the chat that will receive it. */
  private void share(String[] entry) {
    Sharing.toMPGram(this, displayTitle(entry) + "\n" + storyUrl(entry));
  }

  private void confirmRemove(final String[] entry) {
    navigator.showConfirmationAlert(
        Lang.tr("wattpad.remove_confirm", displayTitle(entry)),
        new Runnable() {
          public void run() {
            WattpadProgressManager.getInstance()
                .remove(entry[WattpadProgressManager.ENTRY_ID]);
            reload();
            navigator.showAlert(
                Lang.tr("wattpad.removed"), AlertType.CONFIRMATION, WattpadLibraryScreen.this);
          }
        },
        AlertType.WARNING);
  }

  private void confirmArchive(final String[] entry) {
    final String storyId = entry[WattpadProgressManager.ENTRY_ID];
    navigator.showConfirmationAlert(
        Lang.tr("wattpad.archive_confirm", displayTitle(entry)),
        new Runnable() {
          public void run() {
            WattpadProgressManager.getInstance().archive(storyId);
            reload();
            navigator.showAlert(
                Lang.tr("wattpad.archived_done"), AlertType.CONFIRMATION, WattpadLibraryScreen.this);
          }
        },
        AlertType.WARNING);
  }

  private void unarchive(final String[] entry) {
    WattpadProgressManager.getInstance().unarchive(entry[WattpadProgressManager.ENTRY_ID]);
    reload();
    navigator.showAlert(
        Lang.tr("wattpad.archived_restored"), AlertType.CONFIRMATION, WattpadLibraryScreen.this);
  }

  private void confirmClear() {
    if (entries.size() == 0) {
      return;
    }
    navigator.showConfirmationAlert(
        Lang.tr(archived ? "wattpad.clear_archive_confirm" : "wattpad.clear_confirm"),
        new Runnable() {
          public void run() {
            WattpadProgressManager.getInstance().clear(archived);
            reload();
            navigator.showAlert(
                Lang.tr(archived ? "wattpad.archived_cleared" : "wattpad.library_cleared"),
                AlertType.CONFIRMATION,
                WattpadLibraryScreen.this);
          }
        },
        AlertType.WARNING);
  }

  private void open(final String[] entry) {
    // Downloaded stories open straight from the card, so the library also works offline.
    String storyId = entry[WattpadProgressManager.ENTRY_ID];
    Vector local = WattpadDownloadManager.getInstance().getLocalParts(storyId);
    if (local != null && local.size() > 0) {
      openLocal(entry, local);
      return;
    }
    loading = true;
    navigator.showLoadingAlert(Lang.tr("wattpad.loading"), cancelLoading());
    MIDPlay.startOperation(
        new HttpTextOperation(
            WattpadApi.storyUrl(entry[WattpadProgressManager.ENTRY_ID]),
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        // Do not dismiss the loading alert here. forward() replaces it and pushes
                        // the library (beforeAlert) into the history, so Back returns to the list.
                        if (archived) {
                          openChapters(entry, text);
                        } else {
                          resume(entry, text);
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
            },
            MAX_STORY_BYTES));
  }

  /** Opens a downloaded story without touching the network. */
  private void openLocal(String[] entry, Vector parts) {
    String title =
        WattpadDownloadManager.getInstance()
            .getLocalTitle(entry[WattpadProgressManager.ENTRY_ID]);
    if (title.length() == 0) {
      title = entry[WattpadProgressManager.ENTRY_TITLE];
    }
    if (archived) {
      navigator.forward(
          new WattpadStoryScreen(
              navigator, entry[WattpadProgressManager.ENTRY_ID], title, parts));
    } else {
      resume(entry, parts, title);
    }
  }

  /** Archived stories open on their chapter list, not directly on the saved reading position. */
  private void openChapters(String[] entry, String json) {
    if (WattpadApi.parseParts(json).size() == 0) {
      navigator.showAlert(Lang.tr("wattpad.empty"), AlertType.INFO);
      return;
    }
    navigator.forward(
        new WattpadStoryScreen(navigator, entry[WattpadProgressManager.ENTRY_ID], json));
  }

  private void resume(String[] entry, String json) {
    Vector parts = WattpadApi.parseParts(json);
    if (parts.size() == 0) {
      navigator.showAlert(Lang.tr("wattpad.empty"), AlertType.INFO);
      return;
    }
    String storyTitle = WattpadApi.parseTitle(json);
    if (storyTitle.length() == 0) {
      storyTitle = entry[WattpadProgressManager.ENTRY_TITLE];
    }
    resume(entry, parts, storyTitle);
  }

  private void resume(String[] entry, Vector parts, String storyTitle) {
    int index = parseInt(entry[WattpadProgressManager.ENTRY_INDEX]);
    if (index < 0 || index >= parts.size()) {
      index = 0;
    }
    String chapterTitle = ((String[]) parts.elementAt(index))[WattpadApi.PART_TITLE];
    String storyId = entry[WattpadProgressManager.ENTRY_ID];
    int page = WattpadProgressManager.getInstance().getPage(storyId);
    if (page < 0) {
      page = 0;
    }
    WattpadProgressManager.getInstance()
        .record(storyId, storyTitle, index, parts.size(), chapterTitle, page);
    navigator.forward(
        new WattpadReaderScreen(navigator, storyId, storyTitle, parts, index, page));
  }

  private void showSortMenu() {
    final List menu = new List(Lang.tr("wattpad.sort"), Choice.IMPLICIT);
    menu.append(Lang.tr("wattpad.sort_recent"), null);
    menu.append(Lang.tr("wattpad.sort_title"), null);
    menu.append(Lang.tr("wattpad.sort_progress"), null);
    menu.setSelectedIndex(sortMode, true);
    menu.addCommand(Commands.back());
    menu.setCommandListener(
        new CommandListener() {
          public void commandAction(Command c, Displayable d) {
            if (c == Commands.back()) {
              navigator.back();
              return;
            }
            if (c == List.SELECT_COMMAND) {
              int selected = menu.getSelectedIndex();
              if (selected >= 0) {
                sortMode = selected;
                sortEntries();
                refresh();
              }
              navigator.back();
            }
          }
        });
    navigator.forward(menu);
  }

  private void sortEntries() {
    if (sortMode == SORT_RECENT) {
      return;
    }
    for (int i = 0; i < entries.size() - 1; i++) {
      for (int j = i + 1; j < entries.size(); j++) {
        if (compareEntries(
                (String[]) entries.elementAt(i), (String[]) entries.elementAt(j))
            > 0) {
          Object tmp = entries.elementAt(i);
          entries.setElementAt(entries.elementAt(j), i);
          entries.setElementAt(tmp, j);
        }
      }
    }
  }

  private int compareEntries(String[] first, String[] second) {
    if (sortMode == SORT_TITLE) {
      return displayTitle(first).toLowerCase().compareTo(displayTitle(second).toLowerCase());
    }
    if (sortMode == SORT_PROGRESS) {
      int firstProgress = progressOf(first);
      int secondProgress = progressOf(second);
      if (firstProgress != secondProgress) {
        return secondProgress - firstProgress;
      }
      return displayTitle(first).toLowerCase().compareTo(displayTitle(second).toLowerCase());
    }
    return 0;
  }

  private static int progressOf(String[] entry) {
    int index = parseInt(entry[WattpadProgressManager.ENTRY_INDEX]);
    int total = parseInt(entry[WattpadProgressManager.ENTRY_TOTAL]);
    if (total <= 0) {
      return index;
    }
    return index * 100 / total;
  }

  private Runnable cancelLoading() {
    return new Runnable() {
      public void run() {
        loading = false;
      }
    };
  }

  static int parseInt(String value) {
    try {
      return Integer.parseInt(value);
    } catch (Exception e) {
      return 0;
    }
  }
}
