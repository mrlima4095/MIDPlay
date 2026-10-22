package midplay.ui.screen;

import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.TextField;
import midplay.MIDPlay;
import midplay.net.HttpTextOperation;
import midplay.net.WattpadApi;
import midplay.store.Configuration;
import midplay.store.SettingsManager;
import midplay.store.WattpadDownloadManager;
import midplay.store.WattpadProgressManager;
import midplay.ui.BaseList;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;

/**
 * Lists the chapters of a story and lets the user open one in the reader. Chapters already saved on
 * the memory card are marked, and the whole story can be downloaded for offline reading.
 */
public final class WattpadStoryScreen extends BaseList {
  private static final int TITLE_MAX = 40;

  private final String storyId;
  private final String storyTitle;
  private final String description;
  private final String details;
  private final String authorUsername;
  private final Vector parts;
  private final Command synopsisCommand;
  private final Command continueCommand;
  private final Command detailsCommand;
  private final Command authorCommand;
  private final Command gotoCommand;
  private final Command recommendationsCommand;
  private final Command downloadCommand;
  private final Command removeDownloadCommand;
  private boolean continueAdded;

  public WattpadStoryScreen(Navigator navigator, String storyId, String json) {
    this(
        navigator,
        storyId,
        WattpadApi.parseTitle(json),
        WattpadApi.parseParts(json),
        WattpadApi.parseDescription(json),
        WattpadApi.parseStoryDetails(json),
        WattpadApi.parseAuthorUsername(json),
        false);
  }

  /** Opens a story from its downloaded metadata, without any network request. */
  public WattpadStoryScreen(
      Navigator navigator, String storyId, String storyTitle, Vector parts) {
    this(navigator, storyId, storyTitle, parts, "", "", "", true);
  }

  private WattpadStoryScreen(
      Navigator navigator,
      String storyId,
      String storyTitle,
      Vector parts,
      String description,
      String details,
      String authorUsername,
      boolean offline) {
    super(shorten(storyTitle), navigator);
    this.storyId = storyId == null ? "" : storyId;
    String title = storyTitle == null ? "" : storyTitle;
    this.storyTitle = title.length() == 0 ? Lang.tr("wattpad.title") : title;
    this.parts = parts == null ? new Vector() : parts;
    this.description = description == null ? "" : description;
    this.details = details == null ? "" : details;
    this.authorUsername = authorUsername == null ? "" : authorUsername;
    synopsisCommand = new Command(Lang.tr("wattpad.synopsis"), Command.SCREEN, 1);
    continueCommand = new Command(Lang.tr("wattpad.continue"), Command.SCREEN, 2);
    detailsCommand = new Command(Lang.tr("wattpad.story_details"), Command.SCREEN, 3);
    authorCommand = new Command(Lang.tr("wattpad.author_title"), Command.SCREEN, 4);
    gotoCommand = new Command(Lang.tr("wattpad.go_to_chapter"), Command.SCREEN, 5);
    recommendationsCommand = new Command(Lang.tr("wattpad.recommendations"), Command.SCREEN, 6);
    downloadCommand = new Command(Lang.tr("wattpad.download"), Command.SCREEN, 7);
    removeDownloadCommand = new Command(Lang.tr("wattpad.remove_download"), Command.SCREEN, 8);
    if (this.description.length() > 0) {
      addCommand(synopsisCommand);
    }
    if (this.details.length() > 0) {
      addCommand(detailsCommand);
    }
    if (this.authorUsername.length() > 0) {
      addCommand(authorCommand);
    }
    addCommand(gotoCommand);
    if (!offline) {
      addCommand(recommendationsCommand);
    }
    refresh();
    updateHeader();
  }

  protected void showNotify() {
    super.showNotify();
    refresh();
    updateHeader();
  }

  protected void populateItems() {
    int lastRead = WattpadProgressManager.getInstance().getIndex(storyId);
    WattpadDownloadManager downloads = WattpadDownloadManager.getInstance();
    for (int i = 0; i < parts.size(); i++) {
      String[] part = (String[]) parts.elementAt(i);
      String label = WattpadApi.chapterLabel(i, part[WattpadApi.PART_TITLE]);
      if (i == lastRead) {
        label = "» " + label;
      }
      if (downloads.isChapterDownloaded(storyId, part[WattpadApi.PART_ID])) {
        label = label + " [+]";
      }
      append(label, null);
    }
  }

  private void updateHeader() {
    WattpadDownloadManager downloads = WattpadDownloadManager.getInstance();
    int downloaded = downloads.getDownloadedCount(storyId);
    int total = parts.size();
    boolean complete = total > 0 && downloaded >= total;
    setTitle(shorten(storyTitle) + (complete ? " [" + Lang.tr("wattpad.offline") + "]" : ""));
    if (downloaded > 0 && !downloads.isDownloading(storyId)) {
      addCommand(removeDownloadCommand);
    } else {
      removeCommand(removeDownloadCommand);
    }
    if (downloaded < total && !downloads.isDownloading(storyId)) {
      addCommand(downloadCommand);
    } else {
      removeCommand(downloadCommand);
    }
    int lastRead = WattpadProgressManager.getInstance().getIndex(storyId);
    if (lastRead >= 0 && lastRead < total) {
      if (!continueAdded) {
        addCommand(continueCommand);
        continueAdded = true;
      }
    } else if (continueAdded) {
      removeCommand(continueCommand);
      continueAdded = false;
    }
  }

  protected void handleSelection() {
    int index = getSelectedIndex();
    if (index < 0 || index >= parts.size()) {
      return;
    }
    openChapter(index, 0);
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == synopsisCommand) {
      navigator.showAlert(
          description.length() == 0 ? Lang.tr("wattpad.empty") : description, AlertType.INFO);
    } else if (c == detailsCommand) {
      navigator.showAlert(
          details.length() == 0 ? Lang.tr("wattpad.empty") : details, AlertType.INFO);
    } else if (c == authorCommand) {
      navigator.forward(new WattpadAuthorScreen(navigator, authorUsername));
    } else if (c == gotoCommand) {
      promptChapter();
    } else if (c == continueCommand) {
      int index = WattpadProgressManager.getInstance().getIndex(storyId);
      if (index >= 0 && index < parts.size()) {
        openChapter(index, WattpadProgressManager.getInstance().getPage(storyId));
      }
    } else if (c == recommendationsCommand) {
      loadRecommendations();
    } else if (c == downloadCommand) {
      confirmDownload();
    } else if (c == removeDownloadCommand) {
      confirmRemoveDownload();
    }
  }

  private void confirmDownload() {
    int total = parts.size();
    int downloaded = WattpadDownloadManager.getInstance().getDownloadedCount(storyId);
    int missing = total - downloaded;
    if (missing <= 0) {
      navigator.showAlert(Lang.tr("wattpad.download_none"), AlertType.INFO);
      return;
    }
    navigator.showConfirmationAlert(
        Lang.tr("wattpad.download_confirm", Integer.toString(missing)),
        new Runnable() {
          public void run() {
            startDownload();
          }
        },
        AlertType.INFO);
  }

  private void startDownload() {
    WattpadDownloadManager.getInstance()
        .download(
            navigator,
            storyId,
            storyTitle,
            parts,
            new WattpadDownloadManager.Listener() {
              public void onDownloadFinished(String storyId, int downloaded, int failed) {
                refresh();
                updateHeader();
              }
            });
    updateHeader();
  }

  private void confirmRemoveDownload() {
    if (WattpadDownloadManager.getInstance().isDownloading(storyId)) {
      navigator.showAlert(Lang.tr("download.in_progress"), AlertType.INFO);
      return;
    }
    navigator.showConfirmationAlert(
        Lang.tr("wattpad.remove_download_confirm", storyTitle),
        new Runnable() {
          public void run() {
            WattpadDownloadManager.getInstance().deleteStory(storyId);
            refresh();
            updateHeader();
            navigator.showAlert(
                Lang.tr("wattpad.download_removed"), AlertType.CONFIRMATION);
          }
        },
        AlertType.WARNING);
  }

  private void loadRecommendations() {
    navigator.showLoadingAlert(Lang.tr("status.loading"));
    MIDPlay.startOperation(
        new HttpTextOperation(
            WattpadApi.recommendationsUrl(storyId),
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        boolean includeMature =
                            SettingsManager.getInstance().getCurrentWattpadMature()
                                == Configuration.WATTPAD_MATURE_ON;
                        Vector recs = WattpadApi.parseRecommendations(text, includeMature);
                        if (recs.size() == 0) {
                          navigator.dismissAlert();
                          navigator.showAlert(
                              Lang.tr("wattpad.recommendations_empty"), AlertType.INFO);
                          return;
                        }
                        navigator.forward(
                            new WattpadResultsScreen(
                                navigator,
                                WattpadResultsScreen.SOURCE_RECOMMENDATIONS,
                                storyId,
                                Lang.tr("wattpad.recommendations_title"),
                                recs));
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        navigator.dismissAlert();
                        navigator.showAlert(e.toString(), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private void promptChapter() {
    final Form form = new Form(Lang.tr("wattpad.go_to_chapter"));
    final TextField field =
        new TextField(Lang.tr("wattpad.chapter_number"), "", 5, TextField.NUMERIC);
    form.append(field);
    form.addCommand(Commands.ok());
    form.addCommand(Commands.cancel());
    form.setCommandListener(
        new CommandListener() {
          public void commandAction(Command command, Displayable displayable) {
            if (command == Commands.cancel()) {
              navigator.back();
            } else if (command == Commands.ok()) {
              int index = parseChapter(field.getString());
              if (index < 0 || index >= parts.size()) {
                navigator.showAlert(Lang.tr("wattpad.invalid_chapter"), AlertType.ERROR);
                return;
              }
              navigator.popTo(WattpadStoryScreen.this);
              openChapter(index, 0);
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

  private void openChapter(int index, int page) {
    // Remember the chapter right away so "Continue reading" is available when the reader is closed.
    WattpadProgressManager.getInstance()
        .record(
            storyId,
            storyTitle,
            index,
            parts.size(),
            ((String[]) parts.elementAt(index))[WattpadApi.PART_TITLE],
            page);
    if (!continueAdded) {
      addCommand(continueCommand);
      continueAdded = true;
    }
    navigator.forward(new WattpadReaderScreen(navigator, storyId, storyTitle, parts, index, page));
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
