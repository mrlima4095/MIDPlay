package fi.gtrxac.discord;

import java.util.*;
import java.io.*;
import fi.gtrxac.bluewap.*;
import fi.gtrxac.discord.model.*;
import fi.gtrxac.discord.util.*;
import javax.microedition.lcdui.Image;
import tube42.lib.imagelib.ImageUtils;

public class IconManager implements Runnable {
    public static final IconCache iconCache = new IconCache();
    private static final ResizedIconCache resizedIconCache = new ResizedIconCache();

    private static final Vector requests = new Vector();

    public static Image get(User user, int size, MessageItem callback) {
        // User doesn't have an icon -> always null
        if (user.iconHash == null) return null;

        // Icon of the exact size directly available -> return it
        Image resizedIcon = resizedIconCache.get(user.iconHash, size);
        if (resizedIcon != null) return resizedIcon;

        // Icon available in cache -> queue a resize for it
        Image icon = iconCache.get(user.iconHash);
        if (icon != null) {
            addRequest(user, size, callback);
            return null;
        }

        // Icon not available -> queue a fetch for it
        addRequest(user, 0, callback);

        return null;
    }

    public static void reset() {
        resizedIconCache.clear();
    }

    private static void addRequest(User user, int size, MessageItem callback) {
        synchronized (requests) {
            // Find if there is an existing matching request
            for (int i = 0; i < requests.size(); i++) {
                IconRequest req = (IconRequest) requests.elementAt(i);
                
                if (req.user.iconHash.equals(user.iconHash) && req.targetSize == size) {
                    // matching request -> join it if we haven't already
                    if (req.callbacks.indexOf(callback) == -1) {
                        req.callbacks.addElement(callback);
                    }
                    return;
                }
            }

            // no matching request -> make a new one
            IconRequest req = new IconRequest(user, size, callback);
            requests.addElement(req);
        }
    }

    /**
     * Callback
     */
    public static void fetchRequestFulfilled(IconRequest req, Image icon) {
        iconCache.set(req.user.iconHash, icon);
        requestFulfilled(req);
    }

    private static void requestFulfilled(IconRequest req) {
        requests.removeElement(req);
        req.runCallbacks();
    }

    /**
     * Worker thread that handles icon requests.
     */
    public void run() {
        while (true) {
            try {
                IconRequest req = null;

                // Get the next pending request (fetch request that hasnt been started, or any resize request)
                synchronized (requests) {
                    for (int i = 0; i < requests.size(); i++) {
                        IconRequest checkReq = (IconRequest) requests.elementAt(i);

                        if (!checkReq.isStarted || checkReq.targetSize != 0) {
                            req = checkReq;
                            break;
                        }
                    }
                }
                // No pending request -> wait a bit and check again
                if (req == null) {
                    Util.sleep(250);
                    continue;
                }

                if (req.targetSize == 0) {
                    // Handle fetch request
                    req.isStarted = true;

                    HTTPThread h = new HTTPThread(HTTPThread.ACTION_FETCH_ICON);
                    h.iconRequest = req;
                    h.start();
                }
                else {
                    // Handle resize request
                    Image icon = iconCache.get(req.user.iconHash);
                    icon = ImageUtils.resize(icon, req.targetSize, req.targetSize, true, true);

                    if (!Settings.squareAvatars) {
                        boolean smooth = (App.disp.numAlphaLevels() > 2);
                        icon = CircleCutout.make(icon, smooth);
                    }

                    resizedIconCache.set(req.user.iconHash, req.targetSize, icon);
                    requestFulfilled(req);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                Util.sleep(250);
            }
        }
    }

    /**
     * Code required to start the worker thread.
     */
    private IconManager() {}

    static {
        new Thread(new IconManager()).start();
    }
}