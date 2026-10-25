package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.StringItem;
import midplay.MIDPlay;
import midplay.model.Track;
import midplay.net.HttpTextOperation;
import midplay.net.LyricsApi;
import midplay.store.NotesManager;
import midplay.store.NotesManager.Note;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;

/** Lyrics of the current or selected song, fetched through the lyrics proxy. */
public final class LyricsScreen extends BaseForm {
  /** Lyrics are split so one very long StringItem does not upset small devices. */
  private static final int TEXT_CHUNK = 1200;

  private final String artist;
  private final String title;
  private final String lyrics;
  private final Command saveNoteCommand =
      new Command(Lang.tr("lyrics.save_note"), Command.SCREEN, 1);
  private final Command searchCommand = new Command(Lang.tr("lyrics.search"), Command.SCREEN, 2);

  private LyricsScreen(Navigator navigator, String artist, String title, String lyrics) {
    super(Lang.tr("lyrics.title"), navigator);
    this.artist = artist == null ? "" : artist.trim();
    this.title = title == null ? "" : title.trim();
    this.lyrics = lyrics == null ? "" : lyrics;

    addCommand(saveNoteCommand);
    addCommand(searchCommand);

    String heading = this.title;
    if (this.artist.length() > 0) {
      heading = heading.length() > 0 ? heading + "\n" + this.artist : this.artist;
    }
    if (heading.length() > 0) {
      append(new StringItem(null, heading + "\n"));
    }
    appendLyrics(this.lyrics);
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
    final String key = LyricsApi.cacheKey(a, t);
    String[] cached = LyricsApi.cacheGet(key);
    if (cached != null) {
      if (LyricsApi.isMissing(cached)) {
        showSearch(navigator, a, t, key);
      } else {
        showLyrics(navigator, cached);
      }
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
                          LyricsApi.cachePut(key, null);
                          showSearch(navigator, a, t, key);
                          return;
                        }
                        LyricsApi.cachePut(key, data);
                        showLyrics(navigator, data);
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

  /** Opens the lyrics of a page picked manually, reusing the session cache. */
  public static void openByPath(
      final Navigator navigator,
      String artist,
      String title,
      final String path,
      final String originKey) {
    final String a = artist == null ? "" : artist.trim();
    final String t = title == null ? "" : title.trim();
    final String pathKey = LyricsApi.pathKey(path);
    String[] cached = LyricsApi.cacheGet(pathKey);
    if (cached != null) {
      if (LyricsApi.isMissing(cached)) {
        navigator.showAlert(Lang.tr("lyrics.not_found"), AlertType.INFO);
      } else {
        showLyrics(navigator, cached);
      }
      return;
    }

    navigator.showLoadingAlert(Lang.tr("lyrics.loading"));
    MIDPlay.startOperation(
        new HttpTextOperation(
            LyricsApi.lyricsByPathUrl(path, a, t),
            new HttpTextOperation.Listener() {
              public void onText(final String response) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        String[] data = LyricsApi.parse(response);
                        if (data == null) {
                          LyricsApi.cachePut(pathKey, null);
                          navigator.showAlert(Lang.tr("lyrics.not_found"), AlertType.INFO);
                          return;
                        }
                        LyricsApi.cachePut(pathKey, data);
                        LyricsApi.cachePut(LyricsApi.cacheKey(data[0], data[1]), data);
                        // Remember the manual match for this track, so the
                        // automatic lookup hits it next time.
                        LyricsApi.cachePut(originKey, data);
                        showLyrics(navigator, data);
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

  private static void showLyrics(Navigator navigator, String[] data) {
    navigator.forward(new LyricsScreen(navigator, data[0], data[1], data[2]));
  }

  private static void showSearch(
      final Navigator navigator, String artist, String title, String originKey) {
    navigator.forward(new LyricsSearchScreen(navigator, artist, title, true, originKey));
  }

  private void appendLyrics(String text) {
    if (text == null) {
      return;
    }
    int start = 0;
    while (start < text.length()) {
      int end = Math.min(text.length(), start + TEXT_CHUNK);
      if (end < text.length()) {
        int newline = text.lastIndexOf('\n', end);
        if (newline > start + TEXT_CHUNK / 3) {
          end = newline + 1;
        }
      }
      append(new StringItem(null, text.substring(start, end)));
      start = end;
    }
  }

  private void saveNote() {
    try {
      NotesManager manager = NotesManager.getInstance();
      Note note = manager.create(lyrics);
      manager.rename(note.id, noteTitle());
      removeCommand(saveNoteCommand);
      navigator.showAlert(Lang.tr("lyrics.note_saved"), AlertType.CONFIRMATION);
    } catch (Exception e) {
      navigator.showAlert(Lang.tr("lyrics.note_error"), AlertType.ERROR);
    }
  }

  private String noteTitle() {
    if (artist.length() == 0) {
      return title.length() > 0 ? title : Lang.tr("lyrics.title");
    }
    return title.length() > 0 ? title + " - " + artist : artist;
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == saveNoteCommand) {
      saveNote();
    } else if (c == searchCommand) {
      navigator.forward(
          new LyricsSearchScreen(
              navigator, artist, title, false, LyricsApi.cacheKey(artist, title)));
    }
  }
}
