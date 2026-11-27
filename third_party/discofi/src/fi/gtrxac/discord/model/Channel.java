package fi.gtrxac.discord.model;

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

    public String id;
    public String name;
    public int type;

    public Channel() {}

    public Channel(JSONObject data) {
        id = data.getString("id");
        type = data.getInt("type", 0);
        name = data.getString("name", null);

        if (nameHasHashtag()) name = "#" + name;
    }

    public Channel(DataInputStream saveDataStream) throws IOException {
        id = Long.toString(saveDataStream.readLong());
        type = saveDataStream.readUnsignedByte();
        name = saveDataStream.readUTF();
    }

    public void writeSaveData(DataOutputStream saveDataStream) throws IOException {
        saveDataStream.writeLong(Long.parseLong(id));
        saveDataStream.writeByte(type);
        saveDataStream.writeUTF(name);
    }

    protected boolean nameHasHashtag() {
        // Categories also wouldn't have a hashtag but we don't 
        // create Channel objects from those
        return type != TYPE_GUILD_VOICE;
    }
}