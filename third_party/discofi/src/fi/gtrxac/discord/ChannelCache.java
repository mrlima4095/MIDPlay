package fi.gtrxac.discord;

import java.util.*;
import java.io.*;
import cc.nnproject.json.*;
import fi.gtrxac.discord.model.*;
import fi.gtrxac.discord.util.*;

/**
 * Maintains an LRU cache of up to CACHE_SIZE mappings of guild ID to
 * the guild's channel list (with category structure).
 */
public class ChannelCache extends LRUCache {
    private static final int CACHE_SIZE = 5;

    public ChannelCache() {
        super(CACHE_SIZE);
    }

    public void readSaveData(DataInputStream saveDataStream, int saveDataVersion) throws IOException {
        cache.clear();

        int numEntries = saveDataStream.readInt();

        for (int i = 0; i < numEntries; i++) {
            long guildID = saveDataStream.readLong();
            long lastUsed = (long) saveDataStream.readInt();
            
            int numCategories = saveDataStream.readShort();
            Vector categories = new Vector(numCategories);

            for (int j = 0; j < numCategories; j++) {
                ChannelCategory category = new ChannelCategory(saveDataStream, saveDataVersion);
                categories.addElement(category);
            }

            LRUCacheEntry entry = new LRUCacheEntry(categories, lastUsed);
            Long key = new Long(guildID);
            set(key, entry);
        }
    }

    public void writeSaveData(DataOutputStream saveDataStream) throws IOException {
        saveDataStream.writeInt(cache.size());

        for (Enumeration e = cache.keys(); e.hasMoreElements(); ) {
            Long guildID = (Long) e.nextElement();

            LRUCacheEntry entry = (LRUCacheEntry) cache.get(guildID);
            Vector categories = (Vector) entry.getObjectWithoutUpdate();

            // subtract current usage counter so the saved last-used values are
            // relative to the 0 usage counter of the next session
            long lastUsed = entry.getLastUsed() - LRUCacheEntry.getUsageCounter();

            saveDataStream.writeLong(guildID.longValue());
            saveDataStream.writeInt((int) lastUsed);
            saveDataStream.writeShort(categories.size());
            
            for (int i = 0; i < categories.size(); i++) {
                ChannelCategory category = (ChannelCategory) categories.elementAt(i);
                category.writeSaveData(saveDataStream);
            }
        }
    }

    public Vector get(long guildID) {
        Long key = new Long(guildID);
        return (Vector) super.get(key);
    }

    public void set(long guildID, Vector channels) {
        Long key = new Long(guildID);
        set(key, channels);
    }

    /**
     * Find a channel by its ID in all cached guilds, first searching the primary guild if specified.
     */
    public Channel getChannelByID(long primaryGuildID, long channelID) {
        if (channelID == 0) return null;

        if (primaryGuildID != 0) {
            Channel result = getChannelByIDInGuild(primaryGuildID, channelID);
            if (result != null) return result;
        }

        for (Enumeration e = cache.keys(); e.hasMoreElements(); ) {
            Long key = (Long) e.nextElement();
            long guildID = key.longValue();

            if (primaryGuildID != 0 && guildID == primaryGuildID) continue;

            Channel result = getChannelByIDInGuild(guildID, channelID);
            if (result != null) return result;
        }

        return null;
    }

    /**
     * Find a channel by ID in the specified guild.
     */
    public Channel getChannelByIDInGuild(long guildID, long channelID) {
        if (guildID == 0 || channelID == 0) return null;

        Vector categories = get(guildID);

        for (int i = 0; i < categories.size(); i++) {
            ChannelCategory category = (ChannelCategory) categories.elementAt(i);

            for (int j = 0; j < category.children.size(); j++) {
                Channel channel = (Channel) category.children.elementAt(j);

                if (channel.id == channelID) return channel;
            }
        }

        return null;
    }
}