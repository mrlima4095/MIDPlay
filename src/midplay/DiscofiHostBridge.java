package midplay;

import javax.microedition.lcdui.Displayable;
import midplay.store.NotesManager;
import midplay.util.Lang;

/** Host hooks used by the embedded Discofi Lite client. */
public final class DiscofiHostBridge implements discofi.HostBridge {
  public boolean saveNote(String text) {
    if (text == null || text.trim().length() == 0) {
      return false;
    }
    try {
      NotesManager.getInstance().create(text);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public void openNowPlaying() {
    MIDPlay midlet = MIDPlay.getInstance();
    if (midlet == null) {
      return;
    }
    if (MIDPlay.getPlayerScreen() == null) {
      midlet.openNowPlayingFromDiscord();
      return;
    }
    // Keep the Discofi screen stack alive, but stop it painting over the player.
    final Displayable returnScreen;
    try {
      returnScreen = discofi.App.suspend();
    } catch (Throwable ignored) {
      midlet.openNowPlayingFromDiscord();
      return;
    }
    if (returnScreen == null) {
      midlet.openNowPlayingFromDiscord();
      return;
    }
    midlet.openNowPlayingFromSubapp(
        returnScreen,
        new Runnable() {
          public void run() {
            discofi.App.resume();
          }
        });
  }

  public String saveNoteLabel() {
    return Lang.tr("discofi.save_note");
  }

  public String playerLabel() {
    return Lang.tr("discofi.player");
  }

  public String importLabel() {
    return Lang.tr("discofi.import_legacy");
  }

  public String importTokenNoteLabel() {
    return Lang.tr("discofi.import_token_note");
  }

  private static final DiscofiHostBridge INSTANCE = new DiscofiHostBridge();

  /** Returns the shared bridge used by the embedded client. */
  public static DiscofiHostBridge getInstance() {
    return INSTANCE;
  }
}
