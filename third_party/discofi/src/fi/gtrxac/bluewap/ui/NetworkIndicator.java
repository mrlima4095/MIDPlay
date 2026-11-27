package fi.gtrxac.bluewap.ui;

import fi.gtrxac.bluewap.*;
import javax.microedition.lcdui.Font;

/**
 * Class to determine platform-specific location and estimated width of
 * network activity (packet data) indicator when in full-screen canvas.
 */
public class NetworkIndicator {
    // Any Nokia except Symbian^3 (S60 5.1 and up).
    // So far I've only tested S60 3.1 where it appears, and Belle FP2 where
    // it doesn't, so this is only an estimate of when they might have removed it.

    public static final boolean isOnTopLeft =
        Platform.isNokia &&
        (!Platform.isJ9S60Version("5") || Platform.isJ9S60Version("5.0"));

    public static final boolean isOnTopRight =
        Platform.isSonyEricsson || Platform.isSamsung || Platform.isBlackBerry;

    public static final boolean isShown =
        isOnTopLeft || isOnTopRight;

    public static final int width;

    static {
        Font mediumFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_MEDIUM);
        int mediumFontHeight = mediumFont.getHeight();

        width = !isShown ? 0 :
            Platform.isSonyEricsson ? mediumFontHeight*3/2 :
            Platform.isBlackBerry ? mediumFontHeight/2 :
            mediumFontHeight;
    }
}