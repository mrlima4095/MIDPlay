package fi.gtrxac.discord.model;

import java.util.*;
import java.io.*;
import cc.nnproject.json.*;

public class ChannelCategory {
    public long id;
    public String name;
    public Vector children;

    public ChannelCategory(JSONObject data) {
        this(data.getString("name"));
        this.id = Long.parseLong(data.getString("id"));
    }

    public ChannelCategory(String name) {
        this.name = name;
        this.children = new Vector();
    }

    public ChannelCategory(DataInputStream saveDataStream, int saveDataVersion) throws IOException {
        boolean isDefaultCategory = saveDataStream.readBoolean();

        if (!isDefaultCategory) {
            id = saveDataStream.readLong();
            name = saveDataStream.readUTF();
        }

        int numChildren = saveDataStream.readShort();
        children = new Vector(numChildren);

        for (int i = 0; i < numChildren; i++) {
            children.addElement(new Channel(saveDataStream, saveDataVersion));
        }
    }

    public void writeSaveData(DataOutputStream saveDataStream) throws IOException {
        if (id == 0 && name == null) {
            // default category without ID/name
            saveDataStream.writeBoolean(true);
        }
        else {
            saveDataStream.writeBoolean(false);
            saveDataStream.writeLong(id);
            saveDataStream.writeUTF(name);
        }

        saveDataStream.writeShort(children.size());

        for (int i = 0; i < children.size(); i++) {
            Channel ch = (Channel) children.elementAt(i);
            ch.writeSaveData(saveDataStream);
        }
    }
}