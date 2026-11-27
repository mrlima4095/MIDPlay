package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import midplay.model.StatsEntry;
import midplay.model.Track;
import midplay.store.Configuration;
import midplay.store.HistoryPrivacy;
import midplay.store.SettingsManager;
import midplay.store.StatsManager;
import midplay.ui.BaseList;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;
import midplay.util.Utils;

/** Most played tracks, with a toggle for recently played tracks and artist aggregation. */
public final class StatsScreen extends BaseList {
  private static final long SELECT_IGNORE_MS = 700L;

  private StatsEntry[] entries = new StatsEntry[0];
  private boolean recentMode;
  private long shownAt;

  public StatsScreen(Navigator navigator) {
    super(Lang.tr("menu.stats"), navigator);
    addCommand(Commands.statsView());
    addCommand(Commands.statsArtists());
    addCommand(Commands.statsClear());
    setTitle(Lang.tr("stats.most_played"));
    populateItems();
  }

  protected void populateItems() {
    loadArt(null);
    if (HistoryPrivacy.isAnonymous()) {
      entries = new StatsEntry[0];
      append(Lang.tr("stats.anonymous_disabled"), null);
      return;
    }
    StatsManager manager = StatsManager.getInstance();
    entries = recentMode ? manager.getRecentlyPlayed() : manager.getMostPlayed();
    if (entries.length == 0) {
      append(Lang.tr("stats.empty"), null);
      return;
    }
    String unknownArtist = Lang.tr("details.unknown_artist");
    for (int i = 0; i < entries.length; i++) {
      Track track = entries[i].getTrack();
      String title = track != null ? track.getDisplayTitle(unknownArtist) : "";
      String plays = Lang.tr("stats.plays", String.valueOf(entries[i].getCount()));
      append(title + "  (" + plays + ")", Configuration.musicIcon);
    }
    String[] artUrls = null;
    if (SettingsManager.getInstance().isStatsThumbnailsEnabled()) {
      artUrls = new String[entries.length];
      for (int i = 0; i < entries.length; i++) {
        Track track = entries[i].getTrack();
        artUrls[i] = Utils.withArtType(track != null ? track.getImageUrl() : null, 1);
      }
    }
    loadArt(artUrls);
  }

  protected void refresh() {
    super.refresh();
    shownAt = System.currentTimeMillis();
  }

  protected void showNotify() {
    shownAt = System.currentTimeMillis();
    super.showNotify();
  }

  protected void handleSelection() {
    // Some devices deliver the menu key that opened this screen as a list selection.
    if (System.currentTimeMillis() - shownAt < SELECT_IGNORE_MS) {
      return;
    }
    int index = getSelectedIndex();
    if (!isValidSelection(index, entries.length)) {
      return;
    }
    Track track = entries[index].getTrack();
    if (track == null) {
      return;
    }
    PlayerNavHelper.playSingleTrack(
        track.getDisplayTitle(Lang.tr("details.unknown_artist")), track, navigator);
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == Commands.statsView()) {
      recentMode = !recentMode;
      setTitle(Lang.tr(recentMode ? "stats.recent" : "stats.most_played"));
      refresh();
    } else if (c == Commands.statsArtists()) {
      navigator.forward(new ArtistStatsScreen(navigator));
    } else if (c == Commands.statsClear()) {
      confirmClear();
    }
  }

  private void confirmClear() {
    if (entries.length == 0) {
      return;
    }
    navigator.showYesNoAlert(
        Lang.tr("stats.clear_confirm"),
        new Runnable() {
          public void run() {
            StatsManager.getInstance().clear();
            navigator.showAlert(Lang.tr("stats.status.cleared"), AlertType.CONFIRMATION);
            refresh();
          }
        });
  }
}
