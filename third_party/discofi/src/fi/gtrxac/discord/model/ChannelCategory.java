package fi.gtrxac.discord.model;

import java.util.*;
import java.io.*;
import cc.nnproject.json.*;

public class ChannelCategory {
    public String id;
    public String name;
    public Vector children;

    public ChannelCategory(JSONObject data) {
        this(data.getString("name"));
        this.id = data.getString("id");
    }

    public ChannelCategory(String name) {
        this.name = name;
        this.children = new Vector();
    }

    public ChannelCategory(DataInputStream saveDataStream) throws IOException {
        boolean isDefaultCategory = saveDataStream.readBoolean();

        if (!isDefaultCategory) {
            id = Long.toString(saveDataStream.readLong());
            name = saveDataStream.readUTF();
        }

        int numChildren = saveDataStream.readShort();
        children = new Vector(numChildren);

        for (int i = 0; i < numChildren; i++) {
            children.addElement(new Channel(saveDataStream));
        }
    }

    public void writeSaveData(DataOutputStream saveDataStream) throws IOException {
        if (id == null && name == null) {
            // default category without ID/name
            saveDataStream.writeBoolean(true);
        }
        else {
            saveDataStream.writeBoolean(false);
            saveDataStream.writeLong(Long.parseLong(id));
            saveDataStream.writeUTF(name);
        }

        saveDataStream.writeShort(children.size());

        for (int i = 0; i < children.size(); i++) {
            Channel ch = (Channel) children.elementAt(i);
            ch.writeSaveData(saveDataStream);
        }
    }
}