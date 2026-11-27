package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import midplay.model.ArtistStats;
import midplay.model.Tracks;
import midplay.store.Configuration;
import midplay.store.HistoryPrivacy;
import midplay.store.StatsManager;
import midplay.ui.BaseList;
import midplay.ui.Navigator;
import midplay.util.Lang;

/** Artists ranked by the number of times their tracks were played. */
public final class ArtistStatsScreen extends BaseList {
  private static final long SELECT_IGNORE_MS = 700L;

  private ArtistStats[] artists = new ArtistStats[0];
  private long shownAt;

  public ArtistStatsScreen(Navigator navigator) {
    super(Lang.tr("stats.artists"), navigator);
    populateItems();
  }

  protected void populateItems() {
    if (HistoryPrivacy.isAnonymous()) {
      artists = new ArtistStats[0];
      append(Lang.tr("stats.anonymous_disabled"), null);
      return;
    }
    artists = StatsManager.getInstance().getTopArtists();
    if (artists.length == 0) {
      append(Lang.tr("stats.empty"), null);
      return;
    }
    for (int i = 0; i < artists.length; i++) {
      ArtistStats artist = artists[i];
      String plays = Lang.tr("stats.plays", String.valueOf(artist.getCount()));
      append(artist.getName() + "  (" + plays + ")", Configuration.playlistIcon);
    }
  }

  protected void handleCommand(Command c, Displayable d) {
  }

  protected void showNotify() {
    shownAt = System.currentTimeMillis();
    super.showNotify();
  }

  protected void handleSelection() {
    if (System.currentTimeMillis() - shownAt < SELECT_IGNORE_MS) {
      return;
    }
    int index = getSelectedIndex();
    if (!isValidSelection(index, artists.length)) {
      return;
    }
    String artist = artists[index].getName();
    Tracks tracks = new Tracks();
    tracks.setTracks(StatsManager.getInstance().getTracksByArtist(artist));
    if (tracks.size() == 0) {
      navigator.showAlert(Lang.tr("stats.empty"), AlertType.INFO);
      return;
    }
    navigator.forward(new TrackListScreen(artist, tracks, navigator, null));
  }
}
