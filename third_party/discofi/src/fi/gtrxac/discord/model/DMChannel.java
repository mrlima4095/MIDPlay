package fi.gtrxac.discord.model;

import cc.nnproject.json.*;
import java.io.*;

/**
 * Created by gtrx on 3.9.2026.
 */

public class DMChannel extends Channel {
    public String username;
    public boolean isGroup;
    public long lastMessageID;

    public DMChannel(JSONObject data) {
        super(data);
        isGroup = data.getInt("type", TYPE_DM) == TYPE_GROUP_DM;

        String msgIdStr = data.getString("last_message_id", null);

        if (msgIdStr != null) {
            lastMessageID = Long.parseLong(msgIdStr);
        } else {
            lastMessageID = id;
        }

        if (isGroup) populateGroupInfo(data);
        else populateRecipientInfo(data);
    }

    private void populateGroupInfo(JSONObject data) {
        // No group name -> list the names of up to 3 participants
        if (name == null) {
            JSONArray recipients = data.getArray("recipients");
            StringBuffer nameBuf = new StringBuffer();

            int i = 0;
            while (i < recipients.size()) {
                JSONObject recipient = recipients.getObject(i);
                String name = recipient.getString("global_name", null);
                if (name == null) {
                    name = recipient.getString("username");
                }
                nameBuf.append(name);
                i++;
                if (i >= 3 || i >= recipients.size()) break;
                nameBuf.append(", ");
            }
            name = nameBuf.toString();
        }
    }

    private void populateRecipientInfo(JSONObject data) {
        try {
            JSONObject recipient = data.getArray("recipients").getObject(0);

            name = recipient.getString("global_name", null);
            if (name == null) {
                name = recipient.getString("username");
            } else {
                username = recipient.getString("username", null);
            }
        }
        catch (Exception e) {}
    }

    public DMChannel(DataInputStream saveDataStream) throws IOException {
        id = saveDataStream.readLong();
        name = saveDataStream.readUTF();
        username = saveDataStream.readUTF();
        isGroup = saveDataStream.readBoolean();
        lastMessageID = saveDataStream.readLong();

        if ("".equals(username)) username = null;
    }

    public void writeSaveData(DataOutputStream saveDataStream) throws IOException {
        saveDataStream.writeLong(id);
        saveDataStream.writeUTF(name);
        saveDataStream.writeUTF((username != null) ? username : "");
        saveDataStream.writeBoolean(isGroup);
        saveDataStream.writeLong(lastMessageID);
    }

    public String getNameForMention() {
        if (name == null) return "(unknown)";
        return "@" + name;
    }

    public String getNameForNotification() {
        return (name != null) ? name : "(unknown)";
    }

    public String getNameForList() {
        return getNameForNotification();
    }

    protected String getNamePrefix() {
        return !isGroup ? "@" : "";
    }
}
