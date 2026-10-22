package midplay.ui.screen;

import java.util.Hashtable;
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
import midplay.store.Configuration;
import midplay.store.SettingsManager;
import midplay.ui.BaseList;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;

/** Lists Wattpad search results; selecting a story loads its chapter list. */
public final class WattpadResultsScreen extends BaseList {
  /** Where the shown stories come from, so "more results" can page the same source. */
  public static final int SOURCE_SEARCH = 0;
  public static final int SOURCE_USER = 1;
  public static final int SOURCE_LIST = 2;
  public static final int SOURCE_RECOMMENDATIONS = 4;

  private static final int MAX_STORY_BYTES = 400 * 1024;
  private static final int SORT_RELEVANCE = 0;
  private static final int SORT_TITLE = 1;
  private static final int SORT_AUTHOR = 2;
  private static final int SORT_CHAPTERS = 3;

  private final int source;
  private final String sourceId;
  private final Vector stories;
  private final Command moreCommand;
  private final Command sortCommand;
  private final Hashtable storyIds = new Hashtable();
  private boolean hasMore;
  private boolean loading;
  private int offset;
  private int sortMode = SORT_RELEVANCE;

  public WattpadResultsScreen(
      Navigator navigator, int source, String sourceId, String title, Vector stories) {
    this(navigator, source, sourceId, title, stories, true, stories.size());
  }

  public WattpadResultsScreen(
      Navigator navigator,
      int source,
      String sourceId,
      String title,
      Vector stories,
      boolean hasMore) {
    this(navigator, source, sourceId, title, stories, hasMore, stories.size());
  }

  public WattpadResultsScreen(
      Navigator navigator,
      int source,
      String sourceId,
      String title,
      Vector stories,
      boolean hasMore,
      int offset) {
    super(title, navigator);
    this.source = source;
    this.sourceId = sourceId;
    this.stories = stories;
    this.hasMore = source != SOURCE_RECOMMENDATIONS && hasMore;
    this.offset = offset;
    moreCommand = new Command(Lang.tr("wattpad.more"), Command.SCREEN, 1);
    sortCommand = new Command(Lang.tr("wattpad.sort"), Command.SCREEN, 2);
    for (int i = 0; i < stories.size(); i++) {
      String id = ((String[]) stories.elementAt(i))[WattpadApi.STORY_ID];
      storyIds.put(id, id);
    }
    if (this.hasMore) {
      addCommand(moreCommand);
    }
    if (stories.size() > 1) {
      addCommand(sortCommand);
    }
    refresh();
  }

  public WattpadResultsScreen(Navigator navigator, String query, Vector stories) {
    this(navigator, SOURCE_SEARCH, query, Lang.tr("wattpad.results"), stories, true, stories.size());
  }

  protected void populateItems() {
    for (int i = 0; i < stories.size(); i++) {
      append(labelFor((String[]) stories.elementAt(i)), null);
    }
  }

  /** Row label: "Title - Author (47 ch.) [+18]". */
  private static String labelFor(String[] story) {
    StringBuffer sb = new StringBuffer();
    sb.append(story[WattpadApi.STORY_TITLE]);
    String author = story[WattpadApi.STORY_AUTHOR];
    if (author.length() > 0) {
      sb.append(" - ").append(author);
    }
    int parts = 0;
    try {
      parts = Integer.parseInt(story[WattpadApi.STORY_PARTS]);
    } catch (Exception e) {
    }
    if (parts > 0) {
      sb.append(" (").append(parts).append(' ').append(Lang.tr("wattpad.chapters_short")).append(')');
    }
    if ("1".equals(story[WattpadApi.STORY_MATURE])) {
      sb.append(" [").append(Lang.tr("wattpad.mature")).append(']');
    }
    return sb.toString();
  }

  protected void handleSelection() {
    int index = getSelectedIndex();
    if (index < 0 || index >= stories.size() || loading) {
      return;
    }
    openStory((String[]) stories.elementAt(index));
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == sortCommand) {
      showSortMenu();
    } else if (c == moreCommand) {
      loadMore();
    }
  }

  private void openStory(final String[] story) {
    loading = true;
    navigator.showLoadingAlert(Lang.tr("wattpad.loading"), cancelLoading());
    MIDPlay.startOperation(
        new HttpTextOperation(
            WattpadApi.storyUrl(story[WattpadApi.STORY_ID]),
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        showStory(story, text);
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

  private void showStory(String[] story, String json) {
    Vector parts = WattpadApi.parseParts(json);
    if (parts.size() == 0) {
      navigator.showAlert(Lang.tr("wattpad.empty"), AlertType.INFO);
      return;
    }
    // Keep the loading alert as the current view until forward() replaces it: forward() then
    // pushes the results screen (beforeAlert) into the history, so Back returns here.
    navigator.forward(
        new WattpadStoryScreen(navigator, story[WattpadApi.STORY_ID], json));
  }

  private void loadMore() {
    if (loading || !hasMore) {
      return;
    }
    loading = true;
    navigator.showLoadingAlert(Lang.tr("status.loading"), cancelLoading());
    MIDPlay.startOperation(
        new HttpTextOperation(
            nextPageUrl(),
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        appendMore(text);
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

  private String nextPageUrl() {
    if (source == SOURCE_USER) {
      return WattpadApi.userStoriesUrl(sourceId, offset);
    }
    if (source == SOURCE_LIST) {
      return WattpadApi.listStoriesUrl(sourceId, offset);
    }
    if (source == SOURCE_RECOMMENDATIONS) {
      return WattpadApi.recommendationsUrl(sourceId);
    }
    return WattpadApi.searchUrl(
        sourceId, offset, SettingsManager.getInstance().getWattpadLanguage());
  }

  private Runnable cancelLoading() {
    return new Runnable() {
      public void run() {
        loading = false;
      }
    };
  }

  private void appendMore(String text) {
    boolean includeMature =
        SettingsManager.getInstance().getCurrentWattpadMature() == Configuration.WATTPAD_MATURE_ON;
    Vector more = WattpadApi.parseStories(text, includeMature);
    int rawCount = WattpadApi.countStories(text);
    offset += rawCount;
    boolean end = rawCount < WattpadApi.getSearchLimit();
    int added = 0;
    for (int i = 0; i < more.size(); i++) {
      String[] story = (String[]) more.elementAt(i);
      String id = story[WattpadApi.STORY_ID];
      if (storyIds.containsKey(id)) {
        continue;
      }
      storyIds.put(id, id);
      stories.addElement(story);
      added++;
    }
    if (end) {
      hasMore = false;
      removeCommand(moreCommand);
    }
    if (added > 0) {
      if (sortMode != SORT_RELEVANCE) {
        sortStories();
      }
      refresh();
    }
    if (end) {
      navigator.showAlert(Lang.tr("wattpad.end_of_results"), AlertType.INFO);
    }
  }

  private void showSortMenu() {
    final List menu = new List(Lang.tr("wattpad.sort"), Choice.IMPLICIT);
    menu.append(Lang.tr("wattpad.sort_relevance"), null);
    menu.append(Lang.tr("wattpad.sort_title"), null);
    menu.append(Lang.tr("wattpad.sort_author"), null);
    menu.append(Lang.tr("wattpad.sort_chapters"), null);
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
                sortStories();
                refresh();
              }
              navigator.back();
            }
          }
        });
    navigator.forward(menu);
  }

  private void sortStories() {
    for (int i = 0; i < stories.size() - 1; i++) {
      for (int j = i + 1; j < stories.size(); j++) {
        if (compareStories(
                (String[]) stories.elementAt(i), (String[]) stories.elementAt(j))
            > 0) {
          Object tmp = stories.elementAt(i);
          stories.setElementAt(stories.elementAt(j), i);
          stories.setElementAt(tmp, j);
        }
      }
    }
  }

  private int compareStories(String[] first, String[] second) {
    if (sortMode == SORT_TITLE) {
      return lower(first[WattpadApi.STORY_TITLE])
          .compareTo(lower(second[WattpadApi.STORY_TITLE]));
    }
    if (sortMode == SORT_AUTHOR) {
      return lower(first[WattpadApi.STORY_AUTHOR])
          .compareTo(lower(second[WattpadApi.STORY_AUTHOR]));
    }
    if (sortMode == SORT_CHAPTERS) {
      return parseInt(second[WattpadApi.STORY_PARTS]) - parseInt(first[WattpadApi.STORY_PARTS]);
    }
    return 0;
  }

  private static String lower(String value) {
    return value == null ? "" : value.toLowerCase();
  }

  private static int parseInt(String value) {
    try {
      return Integer.parseInt(value);
    } catch (Exception e) {
      return 0;
    }
  }
}
