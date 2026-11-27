package fi.gtrxac.discord.model;

import cc.nnproject.json.JSONObject;
import java.io.*;

/**
 * Created by gtrx on 30.8.2026.
 */

public class Guild {
    public String id;
    private String name;

    public Guild(JSONObject data) {
        id = data.getString("id");

        if (data.has("name")) {
            name = data.getString("name");
        }
        // else if (data.has("properties")) {
        //     name = data.getObject("properties").getString("name");
        // }
        else {
            name = "(unknown)";
        }
    }

    public Guild(DataInputStream saveDataStream) throws IOException {
        id = Long.toString(saveDataStream.readLong());
        name = saveDataStream.readUTF();
    }

    public void writeSaveData(DataOutputStream saveDataStream) throws IOException {
        saveDataStream.writeLong(Long.parseLong(id));
        saveDataStream.writeUTF(name);
    }

    public String getName() {
        if (name == null) return "(unknown)";
        return name;
    }
}
