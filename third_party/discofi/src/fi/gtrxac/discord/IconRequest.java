package fi.gtrxac.discord;

import fi.gtrxac.discord.model.*;
import java.util.*;

/**
 * Icon (avatar) request given to IconManager.
 * 
 * Used for both fetch requests and resize requests.
 * For fetch requests, target size is 0.
 */
public class IconRequest {
    public User user;
    public int targetSize;
    public Vector callbacks;
    public boolean isStarted;

    public IconRequest(User user, int targetSize, MessageItem item) {
        this.user = user;
        this.targetSize = targetSize;
        this.callbacks = new Vector();
        this.callbacks.addElement(item);
    }

    public void runCallbacks() {
        for (int i = 0; i < callbacks.size(); i++) {
            MessageItem item = (MessageItem) callbacks.elementAt(i);
            item.avatarLoaded();
        }
    }
}