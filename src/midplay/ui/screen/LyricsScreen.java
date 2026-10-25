package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.StringItem;
import midplay.MIDPlay;
import midplay.model.Track;
import midplay.net.HttpTextOperation;
import midplay.net.LyricsApi;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;

/** Lyrics of the current or selected song, fetched through the lyrics proxy. */
public final class LyricsScreen extends BaseForm {
  /** Lyrics are split so one very long StringItem does not upset small devices. */
  private static final int TEXT_CHUNK = 1200;

  private LyricsScreen(Navigator navigator, String artist, String title, String lyrics) {
    super(Lang.tr("lyrics.title"), navigator);
    addCommand(Commands.playerNowPlaying());
    String heading = title == null ? "" : title.trim();
    String by = artist == null ? "" : artist.trim();
    if (by.length() > 0) {
      heading = heading.length() > 0 ? heading + "\n" + by : by;
    }
    if (heading.length() > 0) {
      append(new StringItem(null, heading + "\n"));
    }
    appendLyrics(lyrics);
  }

  /** Opens the lyrics of a track, warning when there is nothing playing. */
  public static void open(Navigator navigator, Track track) {
    if (track == null) {
      navigator.showAlert(Lang.tr("lyrics.no_track"), AlertType.INFO);
      return;
    }
    open(navigator, track.getArtist(), track.getName());
  }

  public static void open(final Navigator navigator, String artist, String title) {
    final String a = artist == null ? "" : artist.trim();
    final String t = title == null ? "" : title.trim();
    if (a.length() == 0 && t.length() == 0) {
      navigator.showAlert(Lang.tr("lyrics.no_track"), AlertType.INFO);
      return;
    }
    navigator.showLoadingAlert(Lang.tr("lyrics.loading"));
    MIDPlay.startOperation(
        new HttpTextOperation(
            LyricsApi.lyricsUrl(a, t),
            new HttpTextOperation.Listener() {
              public void onText(final String response) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        String[] data = LyricsApi.parse(response);
                        if (data == null) {
                          navigator.showAlert(Lang.tr("lyrics.not_found"), AlertType.INFO);
                          return;
                        }
                        navigator.forward(new LyricsScreen(navigator, data[0], data[1], data[2]));
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        navigator.showAlert(Lang.tr("lyrics.error"), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private void appendLyrics(String lyrics) {
    if (lyrics == null) {
      return;
    }
    int start = 0;
    while (start < lyrics.length()) {
      int end = Math.min(lyrics.length(), start + TEXT_CHUNK);
      if (end < lyrics.length()) {
        int newline = lyrics.lastIndexOf('\n', end);
        if (newline > start + TEXT_CHUNK / 3) {
          end = newline + 1;
        }
      }
      append(new StringItem(null, lyrics.substring(start, end)));
      start = end;
    }
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == Commands.playerNowPlaying()) {
      PlayerNavHelper.showNowPlaying(navigator);
    }
  }
}
