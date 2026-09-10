package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import midplay.model.Track;
import midplay.model.Tracks;
import midplay.store.Configuration;
import midplay.store.DownloadManager;
import midplay.ui.BaseList;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;

public final class DownloadsScreen extends BaseList {
  private static final String DOWNLOADED_MARKER = "[v] ";

  private final DownloadManager downloadManager = DownloadManager.getInstance();
  private Track[] tracks;

  public DownloadsScreen(Navigator navigator) {
    super(Lang.tr("menu.downloads"), navigator);
    addCommand(Commands.downloadPlay());
    addCommand(Commands.downloadDelete());
    addCommand(Commands.downloadDeleteAll());
    addCommand(Commands.downloadRefresh());
    populateItems();
  }

  protected void populateItems() {
    tracks = downloadManager.getDownloadedTracks();
    populateTrackItems();
  }

  private String buildTrackLabel(Track track) {
    String base = track.getName() != null && track.getName().length() > 0
        ? track.getName()
        : Lang.tr("details.unknown_artist");
    String artist = track.getArtist();
    if (artist != null && artist.length() > 0) {
      base = base + "\n" + artist;
    }
    return base;
  }

  protected void handleSelection() {
    int index = getSelectedIndex();
    if (!isValidSelection(index, tracks.length)) {
      return;
    }
    playSelected(index);
  }

  protected void handleCommand(Command c, Displayable d) {
    int selected = getSelectedIndex();
    if (c == Commands.downloadPlay()) {
      if (isValidSelection(selected, tracks.length)) {
        playSelected(selected);
      }
    } else if (c == Commands.downloadDelete()) {
      if (isValidSelection(selected, tracks.length)) {
        deleteSelected(selected);
      }
    } else if (c == Commands.downloadDeleteAll()) {
      confirmClearAll();
    } else if (c == Commands.downloadRefresh()) {
      refreshFromDisk();
    }
  }

  private void playSelected(int index) {
    Track track = tracks[index];
    Tracks single = new Tracks();
    single.setTracks(new Track[] {track});
    PlayerNavHelper.playTrackFromList(
        Lang.tr("menu.downloads"), single, 0, 0L, navigator);
  }

  private void deleteSelected(int index) {
    Track track = tracks[index];
    if (downloadManager.removeDownload(track)) {
      navigator.showAlert(Lang.tr("download.status.deleted"), AlertType.CONFIRMATION);
      refresh();
    } else {
      navigator.showAlert(Lang.tr("download.error"), AlertType.ERROR);
    }
  }

  private void confirmClearAll() {
    if (tracks == null || tracks.length == 0) {
      return;
    }
    navigator.showConfirmationAlert(
        Lang.tr("download.confirm.clear"),
        new Runnable() {
          public void run() {
            downloadManager.clearAllDownloads();
            navigator.showAlert(Lang.tr("download.status.cleared"), AlertType.CONFIRMATION);
            refresh();
          }
        },
        AlertType.WARNING);
  }

  private void refreshFromDisk() {
    tracks = downloadManager.refreshDownloadedTracks();
    this.deleteAll();
    populateTrackItems();
  }

  private void populateTrackItems() {
    if (tracks == null || tracks.length == 0) {
      return;
    }
    for (int i = 0; i < tracks.length; i++) {
      String label = DOWNLOADED_MARKER + buildTrackLabel(tracks[i]);
      this.append(label, Configuration.musicIcon);
    }
  }

  protected void showNotify() {
    super.showNotify();
    refresh();
  }
}
