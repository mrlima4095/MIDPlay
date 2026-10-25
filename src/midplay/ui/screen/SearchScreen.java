package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextField;
import javax.microedition.rms.RecordStoreException;
import midplay.MIDPlay;
import midplay.net.JsonOperation;
import midplay.store.Configuration;
import midplay.store.MusicSearchHistoryManager;
import midplay.store.SettingsManager;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlaylistsListForwarder;
import midplay.ui.TracksListForwarder;
import midplay.util.Lang;

public final class SearchScreen extends BaseForm {
  private static final int MAX_INPUT_LENGTH = 150;
  private final TextField searchField =
      new TextField(Lang.tr("search.placeholder"), "", MAX_INPUT_LENGTH, 0);
  private final ChoiceGroup searchTypeGroup =
      new ChoiceGroup(Lang.tr("search.type"), ChoiceGroup.EXCLUSIVE);
  private final ChoiceGroup history;
  private final Command useHistory;
  private final Command clearHistory;
  private String searchType;
  private final SettingsManager settingsManager;

  public SearchScreen(Navigator navigator) {
    this(navigator, null);
  }

  /** Opens the search screen with the field already filled, used by {@code midplay://} links. */
  public SearchScreen(Navigator navigator, String initialQuery) {
    super(Lang.tr(Configuration.MENU_SEARCH), navigator);
    this.settingsManager = SettingsManager.getInstance();
    this.searchType = settingsManager.getCurrentSearchType();
    addComponents();
    if (initialQuery != null) {
      searchField.setString(initialQuery.trim());
    }
    history =
        new ChoiceGroup(
            Lang.tr("open_uri.history"),
            Choice.POPUP,
            MusicSearchHistoryManager.getInstance().getItems(),
            null);
    append(history);
    useHistory = new Command(Lang.tr("open_uri.use"), Command.SCREEN, 1);
    clearHistory = new Command(Lang.tr("open_uri.clear"), Command.SCREEN, 2);
    addCommand(Commands.ok());
    addCommand(useHistory);
    addCommand(clearHistory);
  }

  private void addComponents() {
    this.append(this.searchField);
    String[] searchTypeLabels = {
      Lang.tr(Configuration.SEARCH_PLAYLIST),
      Lang.tr(Configuration.SEARCH_ALBUM),
      Lang.tr(Configuration.SEARCH_TRACK)
    };
    for (int i = 0; i < searchTypeLabels.length; i++) {
      this.searchTypeGroup.append(searchTypeLabels[i], null);
    }
    int selectedIndex = getSearchTypeIndex(this.searchType);
    this.searchTypeGroup.setSelectedIndex(selectedIndex, true);
    this.append(this.searchTypeGroup);
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == Commands.ok()) {
      search();
    } else if (c == useHistory) {
      useSelectedHistory();
    } else if (c == clearHistory) {
      MusicSearchHistoryManager.getInstance().clear();
      refreshHistory();
    }
  }

  private void useSelectedHistory() {
    int selected = history.getSelectedIndex();
    if (selected >= 0 && selected < history.size()) {
      searchField.setString(history.getString(selected));
    }
  }

  private void refreshHistory() {
    history.deleteAll();
    String[] items = MusicSearchHistoryManager.getInstance().getItems();
    for (int i = 0; i < items.length; i++) {
      history.append(items[i], null);
    }
  }

  private int getSearchTypeIndex(String searchType) {
    for (int i = 0; i < Configuration.ALL_SEARCH_TYPES.length; i++) {
      if (Configuration.ALL_SEARCH_TYPES[i].equals(searchType)) {
        return i;
      }
    }
    return 0;
  }

  private void search() {
    final String keyword = this.searchField.getString().trim();
    if (keyword.length() == 0) {
      navigator.showAlert(Lang.tr("search.error.empty_keyword"), AlertType.ERROR);
      return;
    }
    int selectedIndex = this.searchTypeGroup.getSelectedIndex();
    this.searchType = Configuration.ALL_SEARCH_TYPES[selectedIndex];
    try {
      settingsManager.saveSearchType(this.searchType);
    } catch (RecordStoreException e) {
    }
    MusicSearchHistoryManager.getInstance().record(keyword);
    refreshHistory();
    navigator.showLoadingAlert(Lang.tr("search.status.searching", keyword));
    final String title = Lang.tr("search.results") + ": " + keyword;
    if (searchType.equals(Configuration.SEARCH_TRACK)) {
      MIDPlay.startOperation(
          JsonOperation.searchTracks(
              keyword, new TracksListForwarder(navigator, title, "search.status.no_results")));
    } else {
      MIDPlay.startOperation(
          JsonOperation.searchPlaylists(
              keyword,
              searchType,
              new PlaylistsListForwarder(
                  navigator, title, "search.status.no_results", keyword, searchType)));
    }
  }
}
