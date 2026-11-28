package fi.gtrxac.discord.model;

import fi.gtrxac.discord.Account;
import cc.nnproject.json.*;
import java.io.*;

public class Channel {
    // https://docs.discord.food/resources/channel#channel-type
    public static final int TYPE_GUILD_TEXT = 0;
    public static final int TYPE_DM = 1;
    public static final int TYPE_GUILD_VOICE = 2;
    public static final int TYPE_GROUP_DM = 3;
    public static final int TYPE_GUILD_CATEGORY = 4;
    public static final int TYPE_GUILD_NEWS = 5;
    public static final int TYPE_GUILD_FORUM = 15;
    public static final int TYPE_GUILD_MEDIA = 16;

    public long id;
    protected String name;
    public int type;

    public Channel() {}

    public Channel(JSONObject data) {
        id = Long.parseLong(data.getString("id"));
        type = data.getInt("type", 0);
        setName(data.getString("name", null));
    }

    public Channel(DataInputStream saveDataStream, int saveDataVersion) throws IOException {
        id = saveDataStream.readLong();
        type = saveDataStream.readUnsignedByte();
        setName(saveDataStream.readUTF());

        // Remove hashtag from old save data. Hashtag is now dynamically added
        // rather than contained in the name field directly.
        if (saveDataVersion < Account.VERSION_REMOVED_HASHTAG_FROM_CHANNELS &&
            name != null && name.startsWith("#")) {

            setName(name.substring(1));
        }
    }

    public void writeSaveData(DataOutputStream saveDataStream) throws IOException {
        saveDataStream.writeLong(id);
        saveDataStream.writeByte(type);
        saveDataStream.writeUTF(name);
    }

    public void setName(String newName) {
        name = newName;

        // For the purposes of our client, a blank name (like an unnamed group chat)
        // will have the same behavior as no name, so we can easily replace with "(unknown)"
        if (name != null && name.trim().length() == 0) {
            name = null;
        }
    }

    /**
     * Get raw unformatted name of the channel that is used for loading/saving data.
     * The raw name may be null, but the other getName methods will always return a valid string.
     */
    public String getRawName() {
        return name;
    }

    /**
     * Get display name of the channel that is shown in title bar.
     */
    public String getNameForTitle() {
        if (name == null) return "(unknown)";
        return getNamePrefix() + name;
    }

    /**
     * Get display name of the channel that is shown in messages mentioning this channel.
     */
    public String getNameForMention() {
        if (name == null) return "(unknown)";
        // Always with a #, even for voice channels
        return "#" + name;
    }

    /**
     * Get display name of the channel that is shown in notifications.
     */
    public String getNameForNotification() {
        return getNameForTitle();
    }

    /**
     * Get display name of the channel that is shown in lists.
     */
    public String getNameForList() {
        return getNameForTitle();
    }

    protected String getNamePrefix() {
        // Voice channels don't have a #. Category channels also wouldn't have a #,
        // but we don't create Channel objects from those, so they are not checked here.
        return (type != TYPE_GUILD_VOICE) ? "#" : "";
    }
}