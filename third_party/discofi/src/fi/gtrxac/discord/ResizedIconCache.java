package fi.gtrxac.discord;

import java.util.*;
import java.io.*;
import cc.nnproject.json.*;
import fi.gtrxac.discord.model.*;
import javax.microedition.lcdui.Image;
import fi.gtrxac.discord.util.*;

/**
 * LRU Cache for resized user avatars.
 * 
 * Unlike the IconCache, which is for 16px avatars,
 * these resized avatars are not saved to persistent storage.
 */
public class ResizedIconCache extends LRUCache {
    private static final int CACHE_SIZE = 16;

    public ResizedIconCache() {
        super(CACHE_SIZE);
    }

    public Image get(String iconHash, int size) {
        String key = iconHash + size;
        return (Image) super.get(key);
    }

    public void set(String iconHash, int size, Image icon) {
        String key = iconHash + size;
        super.set(key, icon);
    }
}