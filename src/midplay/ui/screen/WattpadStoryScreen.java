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
import midplay.store.WattpadProgressManager;
import midplay.ui.BaseList;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;

/** Lists the chapters of a story and lets the user open one in the reader. */
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
  private boolean continueAdded;

  public WattpadStoryScreen(Navigator navigator, String storyId, String json) {
    super(shorten(WattpadApi.parseTitle(json)), navigator);
    this.storyId = storyId == null ? "" : storyId;
    String title = WattpadApi.parseTitle(json);
    this.storyTitle = title.length() == 0 ? Lang.tr("wattpad.title") : title;
    this.description = WattpadApi.parseDescription(json);
    this.details = WattpadApi.parseStoryDetails(json);
    this.authorUsername = WattpadApi.parseAuthorUsername(json);
    this.parts = WattpadApi.parseParts(json);
    synopsisCommand = new Command(Lang.tr("wattpad.synopsis"), Command.SCREEN, 1);
    continueCommand = new Command(Lang.tr("wattpad.continue"), Command.SCREEN, 2);
    detailsCommand = new Command(Lang.tr("wattpad.story_details"), Command.SCREEN, 3);
    authorCommand = new Command(Lang.tr("wattpad.author_title"), Command.SCREEN, 4);
    gotoCommand = new Command(Lang.tr("wattpad.go_to_chapter"), Command.SCREEN, 5);
    recommendationsCommand = new Command(Lang.tr("wattpad.recommendations"), Command.SCREEN, 6);
    addCommand(synopsisCommand);
    addCommand(detailsCommand);
    addCommand(gotoCommand);
    addCommand(recommendationsCommand);
    if (authorUsername.length() > 0) {
      addCommand(authorCommand);
    }
    int lastRead = WattpadProgressManager.getInstance().getIndex(this.storyId);
    if (lastRead >= 0 && lastRead < parts.size()) {
      addCommand(continueCommand);
      continueAdded = true;
    }
    refresh();
  }

  protected void showNotify() {
    super.showNotify();
    refresh();
  }

  protected void populateItems() {
    int lastRead = WattpadProgressManager.getInstance().getIndex(storyId);
    for (int i = 0; i < parts.size(); i++) {
      String[] part = (String[]) parts.elementAt(i);
      String label = WattpadApi.chapterLabel(i, part[WattpadApi.PART_TITLE]);
      if (i == lastRead) {
        label = "» " + label;
      }
      append(label, null);
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
    }
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
