package midplay.ui.screen;

import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import midplay.model.Playlist;
import midplay.model.Playlists;
import midplay.model.Track;
import midplay.store.Configuration;
import midplay.store.FavoritesManager;
import midplay.ui.BaseList;
import midplay.ui.Commands;
import midplay.ui.FormHelpers;
import midplay.ui.Navigator;
import midplay.util.Lang;

public final class PlaylistPickerScreen extends BaseList {
  private final FavoritesManager favoritesManager;
  private final Track[] tracksToAdd;
  private final Displayable previousScreen;
  private final Command createCommand = Commands.playlistCreate();
  private Playlist[] customPlaylists = new Playlist[0];

  public PlaylistPickerScreen(Navigator navigator, Track trackToAdd, Displayable previousScreen) {
    this(navigator, trackToAdd == null ? new Track[0] : new Track[] {trackToAdd}, previousScreen);
  }

  public PlaylistPickerScreen(Navigator navigator, Track[] tracksToAdd, Displayable previousScreen) {
    super(Lang.tr("playlist.select"), navigator);
    this.favoritesManager = FavoritesManager.getInstance();
    this.tracksToAdd = tracksToAdd == null ? new Track[0] : tracksToAdd;
    this.previousScreen = previousScreen;
    addCommand(createCommand);
    populateItems();
  }

  protected void populateItems() {
    Playlists favorites = favoritesManager.getPlaylists();
    Playlist[] all = favorites.getPlaylists();
    Vector custom = new Vector();
    if (all != null) {
      for (int i = 0; i < all.length; i++) {
        if (all[i].isCustom()) {
          custom.addElement(all[i]);
        }
      }
    }
    customPlaylists = new Playlist[custom.size()];
    custom.copyInto(customPlaylists);
    for (int i = 0; i < customPlaylists.length; i++) {
      this.append(customPlaylists[i].getDisplayTitle(), Configuration.folderIcon);
    }
    if (customPlaylists.length == 0) {
      this.append(Lang.tr("playlist.status.no_custom"), null);
    }
  }

  protected void handleSelection() {
    int selectedIndex = getSelectedIndex();
    if (!isValidSelection(selectedIndex, customPlaylists.length)) {
      return;
    }
    Playlist selectedPlaylist = customPlaylists[selectedIndex];
    int[] result = favoritesManager.addTracksToPlaylist(selectedPlaylist, tracksToAdd);
    navigator.back();
    showAddResult(result[0], result[1], result[2], false);
  }

  /**
   * Asks for a name, creates the playlist and stores the selected tracks in it. The name form is on
   * top of this screen, so both are closed to land back where the selection started.
   */
  private void showCreateForm() {
    FormHelpers.promptName(
        navigator,
        Lang.tr("playlist.create"),
        "",
        new FormHelpers.NameSubmitHandler() {
          public void onSubmit(String name) {
            createPlaylistAndAdd(name);
          }
        });
  }

  private void createPlaylistAndAdd(String name) {
    Playlist created = new Playlist("custom_" + System.currentTimeMillis(), name, "");
    if (favoritesManager.addPlaylist(created) != FavoritesManager.SUCCESS) {
      navigator.back();
      navigator.showAlert(Lang.tr("playlist.error.create_failed"), AlertType.ERROR);
      return;
    }
    int[] result = favoritesManager.addTracksToPlaylist(created, tracksToAdd);
    navigator.back();
    navigator.back();
    showAddResult(result[0], result[1], result[2], true);
  }

  private void showAddResult(int added, int exists, int failed, boolean created) {
    if (tracksToAdd.length == 0 || (created && failed == 0)) {
      navigator.showAlert(
          Lang.tr("playlist.status.created_with_tracks", String.valueOf(added)),
          AlertType.CONFIRMATION,
          previousScreen);
      return;
    }
    if (tracksToAdd.length == 1 && added == 1) {
      navigator.showAlert(Lang.tr("playlist.status.track_added"), AlertType.CONFIRMATION, previousScreen);
    } else if (tracksToAdd.length == 1 && exists == 1) {
      navigator.showAlert(Lang.tr("playlist.error.track_already_exists"), AlertType.INFO, previousScreen);
    } else if (tracksToAdd.length == 1) {
      navigator.showAlert(Lang.tr("playlist.error.add_track_failed"), AlertType.ERROR, previousScreen);
    } else {
      navigator.showAlert(
          Lang.tr("bulk.playlist_result", added + "/" + exists + "/" + failed),
          failed == tracksToAdd.length ? AlertType.ERROR : AlertType.CONFIRMATION,
          previousScreen);
    }
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == createCommand) {
      showCreateForm();
    }
  }
}
