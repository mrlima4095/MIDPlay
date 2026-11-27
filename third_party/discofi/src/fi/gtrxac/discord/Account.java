package fi.gtrxac.discord;

import fi.gtrxac.discord.model.*;
import java.util.Vector;
import cc.nnproject.json.*;
import fi.gtrxac.bluewap.http.*;
import java.io.*;
import java.util.*;
import fi.gtrxac.bluewap.Platform;

/**
 * Created by gtrx on 29.8.2026.
 */

public class Account {
    // HTTP connection methods (direct over system HTTP, direct over BlueWAP, proxy over system HTTP)
    public static final int CONNECTION_MODE_DIRECT = 0;
    public static final int CONNECTION_MODE_PROXY = 2;
    
//#ifndef NO_BLUETOOTH
    public static final int CONNECTION_MODE_BLUETOOTH = 1;
//#endif

    // Version numbers for account save data.
    public static final int VERSION_BASE = 1;
    public static final int VERSION_ADDED_CHANNEL_CACHE = 2;
    public static final int VERSION_ADDED_CDN_URL = 3;
    public static final int VERSION_ADDED_MEDIA_PROXY_URL = 4;
    public static final int VERSION_CURRENT = 4;

    public static final String DEFAULT_CDN_PROXY_URL = "http://cdn.gtrxac.fi";
    public static final String DEFAULT_MEDIA_PROXY_URL = "http://media.discordapp.net";

    public String token = "";
    public String username;
    public int connectionMode;
    public String apiUrl = "";
    public String cdnUrl = "";
    public String mediaProxyUrl = "";
    public String email = "";
    public String password = "";

    private final Vector guildsUnsorted = new Vector();
    private final Vector guilds = new Vector();
    private final Vector dmChannels = new Vector();
    private final Vector inbox = new Vector();

    public final ChannelCache channelCache = new ChannelCache();

    public Account() {
        cdnUrl = DEFAULT_CDN_PROXY_URL;
        mediaProxyUrl = DEFAULT_MEDIA_PROXY_URL;
    }

    public Account(DataInputStream saveDataStream) throws IOException {
        this();
        int version = saveDataStream.readUnsignedByte();
        token = saveDataStream.readUTF();
        username = saveDataStream.readUTF();
        connectionMode = saveDataStream.readUnsignedByte();
        apiUrl = saveDataStream.readUTF();
        email = saveDataStream.readUTF();
        password = saveDataStream.readUTF();

        if ("".equals(username)) username = null;

        int numGuilds = saveDataStream.readInt();

        for (int i = 0; i < numGuilds; i++) {
            guilds.addElement(new Guild(saveDataStream));
        }

        int numDMChannels = saveDataStream.readInt();

        for (int i = 0; i < numDMChannels; i++) {
            dmChannels.addElement(new DMChannel(saveDataStream));
        }

        if (version >= VERSION_ADDED_CHANNEL_CACHE) {
            channelCache.readSaveData(saveDataStream);
        }

        if (version >= VERSION_ADDED_CDN_URL) {
            cdnUrl = saveDataStream.readUTF();
        }

        if (version >= VERSION_ADDED_MEDIA_PROXY_URL) {
            mediaProxyUrl = saveDataStream.readUTF();
        }
    }

    public void writeSaveData(DataOutputStream saveDataStream) throws IOException {
        saveDataStream.writeByte(VERSION_CURRENT);
        saveDataStream.writeUTF(token);
        saveDataStream.writeUTF((username != null) ? username : "");
        saveDataStream.writeByte(connectionMode);
        saveDataStream.writeUTF(apiUrl);
        saveDataStream.writeUTF(email);
        saveDataStream.writeUTF(password);

        saveDataStream.writeInt(guilds.size());

        for (int i = 0; i < guilds.size(); i++) {
            Guild g = (Guild) guilds.elementAt(i);
            g.writeSaveData(saveDataStream);
        }

        saveDataStream.writeInt(dmChannels.size());

        for (int i = 0; i < dmChannels.size(); i++) {
            DMChannel c = (DMChannel) dmChannels.elementAt(i);
            c.writeSaveData(saveDataStream);
        }

        channelCache.writeSaveData(saveDataStream);

        saveDataStream.writeUTF(cdnUrl);
        saveDataStream.writeUTF(mediaProxyUrl);
    }

    public String getDisplayTitle() throws DiscordException {
        if (username != null) return username;
        return getUserID();
    }

    public String getUserID() throws DiscordException {
        return DiscordUtil.getUserIDFromToken(token);
    }

    public String getApiUrl() {
        return (connectionMode == CONNECTION_MODE_PROXY) ?
            apiUrl : "https://discord.com";
    }

    /**
     * Get CDN URL that can be used in the system browser to download a file.
     * 
     * On devices that support system-level TLS,
     * this can be official Discord CDN URL when no proxy is in use.
     * 
     * On other devices, a proxy is always needed for system browser,
     * and this currently defaults to cdn.gtrxac.fi.
     */
    public String getCdnUrl() {
        boolean supportsSystemTls =
            (Platform.isSymbian || Platform.isJ2MELoader || Platform.isKEmulator);

        String defaultCdnUrl = supportsSystemTls ? "https://cdn.discordapp.com" : DEFAULT_CDN_PROXY_URL;

        return (connectionMode == CONNECTION_MODE_PROXY) ? cdnUrl : defaultCdnUrl;
    }

    /**
     * Get media proxy URL that can be used to download a preview image
     * of a Discord CDN asset (attachment, pfp, etc.)
     * 
     * This defaults to Discord's own media proxy (does not connect to any third-party proxy)
     * but can be overridden if using a API/CDN proxy.
     * 
     * As of writing, Discord media proxy works over plain HTTP, but there have
     * been concerns with it. At one point, it started being slow over
     * plain HTTP while working fine over HTTPS.
     * 
     * As such, the media proxy is reconfigurable mainly for future-proofing
     * in case they ever drop plain HTTP support.
     */
    public String getMediaProxyUrl() {
        return (connectionMode == CONNECTION_MODE_PROXY) ?
            mediaProxyUrl : "http://media.discordapp.net";
    }

    public HTTP createApiRequest(String method, String route) throws Exception {
        HTTP result = HTTP.createRequest(method, getApiUrl() + "/api/v9" + route);
        
        if (token != null && token.length() > 0) {
            result.setHeader("Authorization", token);
        }
        result.setHeader("X-Super-Properties", SuperProperties.get());
        result.setHeader("X-Discord-Locale", "en-US");
        return result;
    }

    public String parseAPIError(JSONObject response) {
        StringBuffer result = new StringBuffer();
        result.append(response.getString("message"));

        try {
            JSONObject errors = response.getObject("errors");
            String firstErrorKey = (String) errors.keys().nextElement();

            String firstError = errors.getObject(firstErrorKey)
                .getArray("_errors")
                .getObject(0)
                .getString("message");

            result.append(" (")
                .append(firstError)
                .append(')');
        }
        catch (JSONException e) {}

        return result.toString();
    }

    public String getApiErrorMessage(int responseCode, String responseText) {
        try {
            JSONObject json = JSON.getObject(responseText);
            return parseAPIError(json);
        }
        catch (JSONException e) {}

        return "HTTP error " + responseCode;
    }

    public boolean guildsAvailable() {
        return guilds.size() != 0;
    }

    public boolean dmChannelsAvailable() {
        return dmChannels.size() != 0;
    }

    public boolean inboxAvailable() {
        return inbox.size() != 0;
    }

    public AbstractJSON fetchJSON(String route) throws DiscordException {
        int responseCode;
        String responseText;

        try {
            HTTP h = createApiRequest("GET", route);
            responseCode = h.getResponseCode();
            responseText = h.getResponseString();
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new DiscordException(e);
        }
        
        return handleJSONResponse(responseCode, responseText);
    }

    /**
     * Perform an authenticated POST request with a JSON body and parse the response as JSON.
     */
    public AbstractJSON postJSON(String route, JSONObject body, Hashtable headers) throws DiscordException {
        int responseCode;
        String responseText;

        try {
            HTTP h = createApiRequest("POST", route);
            
            if (headers != null) {
                for (Enumeration keys = headers.keys(); keys.hasMoreElements(); ) {
                    String key = (String) keys.nextElement();
                    String value = (String) headers.get(key);

                    h.setHeader(key, value);
                }
            }

            h.setData(body.build());
            responseCode = h.getResponseCode();
            responseText = h.getResponseString();
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new DiscordException(e);
        }

        return handleJSONResponse(responseCode, responseText);
    }

    public AbstractJSON postJSON(String route, JSONObject body) throws DiscordException {
        return postJSON(route, body, null);
    }

    /**
     * Throw exception if API response is an error, otherwise do nothing
     */
    public void checkApiResponse(int responseCode, String responseText) throws DiscordException {
        if (responseText == null) responseText = "";

        if (responseCode == 400 && responseText.indexOf("captcha") != -1) {
            throw new DiscordException("A CAPTCHA was received");
        }
        if (responseCode < 200 || responseCode >= 300) {
            throw new DiscordException(getApiErrorMessage(responseCode, responseText));
        }
    }

    public AbstractJSON handleJSONResponse(int responseCode, String responseText) throws DiscordException {
        checkApiResponse(responseCode, responseText);
        return JSON.get(responseText);
    }

    /**
     * Get user's guild (server) list. Returns vector of Guilds.
     * As the list is sorted by user-defined order, this makes up to two HTTP requests; the second one is for the user's settings.
     */
    public Vector getGuilds(boolean refresh) throws DiscordException {
        if (!refresh && guildsAvailable()) return guilds;

        if (refresh || guildsUnsorted.size() == 0) {
            JSONArray guildsJson = (JSONArray) fetchJSON("/users/@me/guilds");
            guildsUnsorted.setSize(0);

            for (int i = 0; i < guildsJson.size(); i++) {
                JSONObject guildJson = guildsJson.getObject(i);
                guildsUnsorted.addElement(new Guild(guildJson));
            }
        }

        // fetch user's guild folders which we can use to sort guilds by
        // user-defined order like in official clients
        JSONObject userSettings = (JSONObject) fetchJSON("/users/@me/settings");
        JSONArray guildFolders = userSettings.getArray("guild_folders");

        guilds.setSize(0);

        for (int i = 0; i < guildFolders.size(); i++) {
            JSONArray guildIds = guildFolders.getObject(i).getArray("guild_ids");

            for (int j = 0; j < guildIds.size(); j++) {
                String guildId = guildIds.getString(j);

                for (int k = 0; k < guildsUnsorted.size(); k++) {
                    Guild guild = (Guild) guildsUnsorted.elementAt(k);

                    if (guild.id.equals(guildId)) {
                        guilds.addElement(guild);
                        guildsUnsorted.removeElementAt(k);
                        break;
                    }
                }
            }
        }

        // If the user doesn't have guild folders, some or all of the guilds may have been
        // missed by the above sort, so add them at the end of the list
        for (int i = 0; i < guildsUnsorted.size(); i++) {
            guilds.addElement(guildsUnsorted.elementAt(i));
        }
        guildsUnsorted.setSize(0);

        return guilds;
    }

    public Vector getDMChannels(boolean refresh) throws DiscordException {
        if (!refresh && dmChannelsAvailable()) return dmChannels;

        JSONArray dmChannelsJson = (JSONArray) fetchJSON("/users/@me/channels");
        Vector dmChannelsUnsorted = new Vector();

        for (int i = 0; i < dmChannelsJson.size(); i++) {
            JSONObject dmChannelJson = dmChannelsJson.getObject(i);
            dmChannelsUnsorted.addElement(new DMChannel(dmChannelJson));
        }

        dmChannels.setSize(0);
        sortDMChannels(dmChannelsUnsorted);
        return dmChannels;
    }

    private void sortDMChannels(Vector dmChannelsUnsorted) {
        // Get the 20 latest DMs (add into another vector sorted by highest to lowest last message ID)
        int count = Math.min(20, dmChannelsUnsorted.size());
        
        for (int i = 0; i < count; i++) {
            long highestID = 0;
            int highestIndex = 0;

            for (int u = 0; u < dmChannelsUnsorted.size(); u++) {
                DMChannel ch = (DMChannel) dmChannelsUnsorted.elementAt(u);

                if (ch.lastMessageID <= highestID) continue;

                // Don't repeat ones that we've already added
                boolean alreadyHave = false;

                for (int l = 0; l < dmChannels.size(); l++) {
                    DMChannel compare = (DMChannel) dmChannels.elementAt(l);

                    if (compare.lastMessageID == ch.lastMessageID) {
                        alreadyHave = true;
                        break;
                    }
                }
                if (alreadyHave) continue;

                highestID = ch.lastMessageID;
                highestIndex = u;
            }
            dmChannels.addElement(dmChannelsUnsorted.elementAt(highestIndex));
        }
    }

    public Vector getInbox(boolean refresh) throws DiscordException {
        if (!refresh && inboxAvailable()) return inbox;

        JSONArray inboxJson = (JSONArray) fetchJSON(
            "/users/@me/mentions?limit=25&roles=true&everyone=true");

        for (int i = 0; i < inboxJson.size(); i++) {
            JSONObject mentionJson = inboxJson.getObject(i);
            inbox.addElement(new Message(mentionJson));
        }

        return inbox;
    }

    /**
     * Send a message to a channel. Returns the created message.
     */
    public Message sendMessage(Channel channel, String content, String recipientMessageId, boolean mention) throws DiscordException {
        boolean isDM = (channel instanceof DMChannel);

        JSONObject body = new JSONObject();
        body.put("content", content);
        body.put("flags", 0);
        body.put("mobile_network_type", "unknown");
        body.put("tts", false);

        // Reply
        if (recipientMessageId != null) {
            JSONObject ref = new JSONObject();
            ref.put("message_id", recipientMessageId);
            body.put("message_reference", ref);

            if (!mention && !isDM) {
                JSONObject ping = new JSONObject();
                ping.put("replied_user", false);
                body.put("allowed_mentions", ping);
            }
        }

        JSONObject created = (JSONObject) postJSON("/channels/" + channel.id + "/messages", body);
        return new Message(created);
    }

    /**
     * Tell the API that the user is typing in a channel. Discord clears the
     * indicator automatically after about ten seconds.
     */
    public void sendTyping(String channelId) {
        try {
            HTTP h = createApiRequest("POST", "/channels/" + channelId + "/typing");
            h.setData("");
            h.getResponseCode();
            h.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Fetch the logged in user's profile and cache the username on this account.
     */
    public void fetchUserProfile() throws DiscordException {
        JSONObject me = (JSONObject) fetchJSON("/users/@me");

        String globalName = me.getString("global_name", null);
        username = globalName != null ? globalName : me.getString("username", null);
    }

//#ifndef NO_BLUETOOTH
    /**
     * Check if the account requires a Bluetooth tethering device which hasn't been defined yet.
     */
    public boolean needsTetherDevice() {
        return connectionMode == Account.CONNECTION_MODE_BLUETOOTH &&
            BluetoothHTTP.selectedConnectionUrl == null;
    }
//#endif
}
