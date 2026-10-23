package midplay.ui;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import midplay.MIDPlay;
import mpgram.MP;

/**
 * Shared helper for sending text/links through the embedded MPGram chat picker. Pinterest, Wattpad
 * and any other subapp share items the same way, so the wiring lives here instead of being copied
 * into every screen.
 */
public final class Sharing {
  private Sharing() {}

  /**
   * Opens MPGram's chat list so the text is sent to the chat the user picks. When MPGram is closed
   * again, {@code returnTo} is restored (pass {@code null} to leave MPGram's own handling in place).
   */
  public static void toMPGram(final Displayable returnTo, String text) {
    if (text == null || text.length() == 0) {
      return;
    }
    MP.shareText(
        MIDPlay.getInstance(),
        returnTo == null
            ? null
            : new Runnable() {
              public void run() {
                Display.getDisplay(MIDPlay.getInstance()).setCurrent(returnTo);
              }
            },
        text);
  }
}
