package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import midplay.model.Track;
import midplay.model.Tracks;
import midplay.store.DownloadManager;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;

public final class LinkTrackPickerScreen extends TrackListScreen {
  private final DownloadManager downloadManager = DownloadManager.getInstance();
  private final String path;

  public LinkTrackPickerScreen(String title, Tracks items, Navigator navigator, String path) {
    super(title, items, navigator, null);
    this.path = path;
    removeCommand(Commands.download());
  }

  protected void handleSelection() {
    int index = getSelectedIndex();
    if (!isValidSelection(index, tracks.length)) {
      return;
    }
    Track track = tracks[index];
    if (downloadManager.linkLocalFile(
        path, track.getUrl(), track.getImageUrl(), track.getName(), track.getArtist())) {
      navigator.back();
      navigator.back();
      navigator.showAlert(Lang.tr("download.link_status"), AlertType.CONFIRMATION);
    } else {
      navigator.showAlert(Lang.tr("download.link_error"), AlertType.ERROR);
    }
  }
}
