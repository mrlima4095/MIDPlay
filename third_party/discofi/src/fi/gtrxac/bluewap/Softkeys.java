package fi.gtrxac.bluewap;

import fi.gtrxac.bluewap.ui.AppCanvas;

/**
 * Reference: 
 * https://github.com/gtrxAC/J2ME-phone-quirks/blob/main/J2ME-phone-quirks.csv
 * https://github.com/davidw/hecl/blob/master/midp20gui/org/hecl/midp20/lcdui/HeclCanvas.java#L684
 */
public class Softkeys {
    private static final boolean isStandardKeyCodes =
        (Platform.isNokia || Platform.isSonyEricsson || Platform.isKEmulator || Platform.isJ2MELoader);

    public static boolean isLeftSoftkey(int keyCode) {
        if (isStandardKeyCodes) {
            return (keyCode == -6);
        }
        if (Platform.isBlackBerry) {
            // a for azerty
            return (keyCode == 'q' || keyCode == 'a' || keyCode == 'Q' || keyCode == 'A');

            // return (keyCode == 0x80000);  // from hecl - I don't know which key this corresponds to
        }
        if (Platform.isSiemens) {
            return (keyCode == -1);
        }
        if (Platform.isMotorola) {
            return (keyCode == -21 || keyCode == 21);
        }

        String keyName = AppCanvas.instance.getKeyName(keyCode).toLowerCase();

		return keyName.indexOf("soft") != -1 &&
			(keyName.indexOf("1") != -1 || keyName.indexOf("left") != -1);
    }

    public static boolean isRightSoftkey(int keyCode) {
        if (isStandardKeyCodes) {
            return (keyCode == -7);
        }
        if (Platform.isBlackBerry) {
            return (keyCode == 'p' || keyCode == 'P');
            // return (keyCode == 0x1b0000);
        }
        if (Platform.isSiemens) {
            return (keyCode == -4);
        }
        if (Platform.isMotorola) {
            return (keyCode == -22 || keyCode == 22);
        }

        String keyName = AppCanvas.instance.getKeyName(keyCode).toLowerCase();

		return keyName.indexOf("soft") != -1 &&
			(keyName.indexOf("2") != -1 || keyName.indexOf("right") != -1);
    }
}