package fi.gtrxac.discord.model;

import cc.nnproject.json.*;

public class User {
    public String id;
    public String name;
    public String iconHash;

    // For placeholder icon
    // public int iconColor;
    // public String initials;

    public User(JSONObject data) {
        id = data.getString("id");

        name = data.getString("global_name", null);
        if (name == null) {
            name = data.getString("username", "(no name)");
        }

        iconHash = data.getString("avatar", null);

        // StringBuffer initialsBuf = new StringBuffer();
        // initialsBuf.append(name.charAt(0));
        // if (name.length() > 1) {
        //     for (int i = 1; i < name.length(); i++) {
        //         char last = name.charAt(i - 1);
        //         char curr = name.charAt(i);

        //         if (last == ' ' || (Character.isLowerCase(last) && Character.isUpperCase(curr))) {
        //             initialsBuf.append(curr);
        //             break; // max 2 chars
        //         }
        //     }
        // }
        // initials = initialsBuf.toString();

        // iconColor = Util.hsvToRgb((int) Long.parseLong(id) % 360, 192, 208);
    }
}
