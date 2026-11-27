package fi.gtrxac.discord;

import java.util.Vector;
import fi.gtrxac.bluewap.http.HTTP;

/**
 * Created by gtrx on 29.8.2026.
 */

public class Accounts {
    public static Vector list = new Vector();
    private static int activeIndex = -1;

    public static void setActive(int index) {
        activeIndex = index;

        Account acc = getActive();

//#ifndef NO_BLUETOOTH
        if (acc != null) {
            HTTP.CONNECTION_TYPE =
                (acc.connectionMode == Account.CONNECTION_MODE_BLUETOOTH) ?
                HTTP.CONNECTION_TYPE_BLUETOOTH :
                HTTP.CONNECTION_TYPE_STANDARD;
        }
//#endif
    }

    public static int getActiveIndex() {
        return activeIndex;
    }

    public static Account getActive() {
        if (activeIndex == -1 || activeIndex >= list.size()) {
            return null;
        }
        return (Account) list.elementAt(activeIndex);
    }
}
