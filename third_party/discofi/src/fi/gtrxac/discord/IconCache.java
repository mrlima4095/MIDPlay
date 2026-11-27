package fi.gtrxac.discord;

import java.util.*;
import java.io.*;
import cc.nnproject.json.*;
import fi.gtrxac.discord.model.*;
import javax.microedition.lcdui.Image;
import fi.gtrxac.bluewap.*;
import javax.microedition.rms.*;
import fi.gtrxac.discord.util.*;

public class IconCache extends LRUCache implements RecordIOImpl {
    private static final int CACHE_SIZE = 32;

    public IconCache() {
        super(CACHE_SIZE);
        RecordIO.load("b", this);
    }

    public void readData(DataInputStream dis) throws IOException {
        cache.clear();

        byte[] hashBuf = new byte[16];
        int numEntries = dis.readInt();

        for (int i = 0; i < numEntries; i++) {
            dis.read(hashBuf);
            String iconHash = Util.bytesToHex(hashBuf);

            long lastUsed = (long) dis.readInt();
            
            byte[] iconData = new byte[160];
            dis.read(iconData, 0, iconData.length);
            
            LRUCacheEntry entry = new LRUCacheEntry(iconData, lastUsed);
            set(iconHash, entry);
        }
    }

    public void writeData(DataOutputStream dos) throws IOException {
        dos.writeInt(cache.size());

        for (Enumeration e = cache.keys(); e.hasMoreElements(); ) {
            String iconHash = (String) e.nextElement();
            LRUCacheEntry entry = (LRUCacheEntry) cache.get(iconHash);
            byte[] iconData = (byte[]) entry.getObjectWithoutUpdate();

            // subtract current usage counter so the saved last-used values are
            // relative to the 0 usage counter of the next session
            long lastUsed = entry.getLastUsed() - LRUCacheEntry.getUsageCounter();

            dos.write(Util.hexToBytes(iconHash));
            dos.writeInt((int) lastUsed);
            dos.write(iconData, 0, iconData.length);
        }
    }

    public Image get(String iconHash) {
        byte[] iconData = (byte[]) super.get(iconHash);
        if (iconData == null) return null;
        
        return AvatarCodec.decode(iconData);
    }

    public void set(String iconHash, Image icon) {
        byte[] iconData = AvatarCodec.encode(icon);
        super.set(iconHash, iconData);
    }

    public void save() {
        RecordIO.save("b", this);
    }
}