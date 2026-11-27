package fi.gtrxac.discord;

import cc.nnproject.json.JSONObject;
import util.Base64;
import java.util.*;

/**
 * https://docs.discord.food/reference#client-properties
 * Created by gtrx on 1.9.2026.
 */

public class SuperProperties {
    private static final String clientLaunchID = DiscordUtil.generateRandomUUID();

    private static String heartbeatSessionID;
    private static long heartbeatSessionIDExpiry;

    /**
     * Random UUID that refreshes every 30 minutes
     */
    private static String getHeartbeatSessionID() {
        if (heartbeatSessionID == null || System.currentTimeMillis() >= heartbeatSessionIDExpiry) {
            heartbeatSessionID = DiscordUtil.generateRandomUUID();
            heartbeatSessionIDExpiry = System.currentTimeMillis() + 30*60*1000;
        }
        return heartbeatSessionID;
    }

    public static String get() {
        JSONObject propsJson = new JSONObject();

        propsJson.put("os", "Android");
        propsJson.put("browser", "Discord Android");
        propsJson.put("device", "a20e");
        propsJson.put("system_locale", "en-US");
        propsJson.put("has_client_mods", false);
        propsJson.put("client_version", "262.5 - rn");  // should match the user agent in HTTPConfig
        propsJson.put("release_channel", "alpha");
        propsJson.put("device_vendor_id", Settings.deviceVendorID);
        propsJson.put("design_id", 2);
        propsJson.put("browser_user_agent", "");
        propsJson.put("browser_version", "");
        propsJson.put("os_version", "34");
        propsJson.put("client_build_number", 3463);
        propsJson.put("client_event_source", (Object) null);
        propsJson.put("client_launch_id", clientLaunchID);
        propsJson.put("launch_signature", Long.toString(System.currentTimeMillis() * 1000000));
        propsJson.put("client_heartbeat_session_id", getHeartbeatSessionID());

        byte[] propsBytes = propsJson.build().getBytes();
        return Base64.encodeToString(propsBytes, false);
    }
}
