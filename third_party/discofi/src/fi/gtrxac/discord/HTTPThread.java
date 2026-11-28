package fi.gtrxac.discord;

import java.util.*;
import fi.gtrxac.bluewap.ui.*;
import fi.gtrxac.bluewap.http.*;
import fi.gtrxac.bluewap.*;
import fi.gtrxac.discord.model.*;
import cc.nnproject.json.*;
import javax.microedition.lcdui.Image;
import fi.gtrxac.discord.util.*;

public class HTTPThread extends Thread {
    public static final int ACTION_PASSWORD_LOGIN = 0;
    public static final int ACTION_SUBMIT_TOTP = 1;
    public static final int ACTION_FETCH_GUILDS = 2;
    public static final int ACTION_FETCH_DM_CHANNELS = 3;
    public static final int ACTION_FETCH_INBOX = 4;
    public static final int ACTION_FETCH_GUILD_CHANNELS = 5;
    public static final int ACTION_FETCH_MESSAGES = 6;
    public static final int ACTION_SEND_MESSAGE = 7;
    public static final int ACTION_FETCH_ICON = 8;
    public static final int ACTION_FETCH_ATTACHMENT = 9;
    public static final int ACTION_DELETE_MESSAGE = 10;
    public static final int ACTION_EDIT_MESSAGE = 11;
    public static final int ACTION_SEND_TYPING = 12;

    private int action;
    private Screen callerScreen;
    private Account acc;

    public String email;
    public String password;
    public String totp;
    public Guild guild;
    public Channel channel;
    public String message;
    public String paginationType;
    public long paginationMessageId;
    public int newPage;
	public long recipientMessageId;
	public boolean sendPing;
    public IconRequest iconRequest;
	public Attachment attachment;
	public int attachmentPreviewWidth;
	public int attachmentPreviewHeight;
	public int attachmentRequestId;
    public Message editOrDeleteMessage;

    public HTTPThread(int action) {
        this(action, App.getCurrentScreen());
    }

    public HTTPThread(int action, Screen callerScreen) {
        this.action = action;
        this.callerScreen = callerScreen;
        this.acc = Accounts.getActive();
    }

    public void run() {
        try {
            // Fetch account's username before making the first API request from this account.
            if (acc != null && acc.username == null) {
                acc.fetchUserProfile();
                Settings.save();
            }

            switch (action) {
                case ACTION_PASSWORD_LOGIN: runPasswordLogin(); break;
                case ACTION_SUBMIT_TOTP: runSubmitTotp(); break;
                case ACTION_FETCH_GUILDS: runFetchGuilds(); break;
                case ACTION_FETCH_DM_CHANNELS: runFetchDMChannels(); break;
                case ACTION_FETCH_INBOX: runFetchInbox(); break;
                case ACTION_FETCH_GUILD_CHANNELS: runFetchGuildChannels(); break;
                case ACTION_FETCH_MESSAGES: runFetchMessages(); break;
                case ACTION_SEND_MESSAGE: runSendMessage(); break;
                case ACTION_FETCH_ICON: runFetchIcon(); break;
                case ACTION_FETCH_ATTACHMENT: runFetchAttachment(); break;
                case ACTION_DELETE_MESSAGE: runDeleteMessage(); break;
                case ACTION_EDIT_MESSAGE: runEditMessage(); break;
                case ACTION_SEND_TYPING: runSendTyping(); break;
            }
        }
        catch (DiscordException e) {
            e.show();
        }
    }

    private void runPasswordLogin() throws DiscordException {
        ListScreen loadScreen = new ListScreen();
        loadScreen.addItem(new LoadingItem());
        App.pushScreen(loadScreen);

        try {
            // 1. fetch experiments which contains the fingerprint we need to use during the login flow
            // could also use /auth/fingerprint but it seems to be deprecated
            JSONObject experiments = (JSONObject)
                App.pendingAccount.fetchJSON("/experiments?with_guild_experiments=true");

            App.pendingFingerprint = experiments.getString("fingerprint", "");

            // 2. create a login request
            // assuming correct credientials and no connection error, there are generally 4 possible outcomes:
            // - login succeeds straight away and we have our token
            // - a TOTP code is required and we continue by asking for it
            // - 2FA is not enabled and we either get a captcha or an email to authorize the IP address
            //   - captcha is handled in Account
            //   - email is untested but should show a descriptive error
            JSONObject loginObject = new JSONObject();
            loginObject.put("login", email);
            loginObject.put("password", password);
            loginObject.put("undelete", false);
            loginObject.put("login_source", (Object) null);
            loginObject.put("gift_code_sku_id", (Object) null);

            Hashtable loginHeaders = new Hashtable();
            loginHeaders.put("X-Fingerprint", App.pendingFingerprint);

            JSONObject loginResponse = (JSONObject)
                App.pendingAccount.postJSON("/auth/login", loginObject, loginHeaders);

            String token = loginResponse.getString("token", null);

            // 3a: login might have already succeeded
            if (token != null) {
                App.loginCompleted(token);
                return;
            }

            // 3b: continue with asking for TOTP code
            if (!loginResponse.getBoolean("mfa", false)) {
                throw new DiscordException(
                    "Account does not have multi-factor authentication enabled");
            }
            if (!loginResponse.getBoolean("totp", false)) {
                throw new DiscordException(
                    "Account does not have multi-factor authentication via authenticator app enabled");
            }

            App.pendingTicket = loginResponse.getString("ticket");
            App.pendingLoginInstanceID = loginResponse.getString("login_instance_id");
        }
        finally {
            App.popScreen();
        }

        App.replaceScreen(new AuthCodeScreen());

        // Login is continued with another HTTPThread that runs runSubmitTotp
    }

    private void runSubmitTotp() throws DiscordException {
        ListScreen loadScreen = new ListScreen();
        loadScreen.addItem(new LoadingItem());
        App.pushScreen(loadScreen);

        String token = null;

        try {
            JSONObject totpObject = new JSONObject();
            totpObject.put("code", totp);
            totpObject.put("ticket", App.pendingTicket);
            totpObject.put("login_source", (Object) null);
            totpObject.put("gift_code_sku_id", (Object) null);
            totpObject.put("login_instance_id", App.pendingLoginInstanceID);

            Hashtable totpHeaders = new Hashtable();
            totpHeaders.put("X-Fingerprint", App.pendingFingerprint);

            JSONObject totpResponse = (JSONObject)
                App.pendingAccount.postJSON("/auth/mfa/totp", totpObject, totpHeaders);

            token = totpResponse.getString("token");
        }
        finally {
            App.popScreen();
        }
        
        App.loginCompleted(token);
    }

    private void runFetchGuilds() throws DiscordException {
        MainScreen mainScreen = (MainScreen) callerScreen;

        try {
            Vector guilds = acc.getGuilds(true);
            Settings.save();
            mainScreen.populateGuildsTab(guilds);
        }
        catch (DiscordException e) {
            mainScreen.showErrorOnTab(MainScreen.TAB_GUILDS);
            throw e;
        }
    }

    private void runFetchDMChannels() throws DiscordException {
        MainScreen mainScreen = (MainScreen) callerScreen;

        try {
            Vector dmChannels = acc.getDMChannels(true);
            Settings.save();
            mainScreen.populateDMsTab(dmChannels);
        }
        catch (DiscordException e) {
            mainScreen.showErrorOnTab(MainScreen.TAB_DMS);
            throw e;
        }
    }

    private void runFetchInbox() throws DiscordException {
        MainScreen mainScreen = (MainScreen) callerScreen;

        try {
            Vector inbox = acc.getInbox(true);
            mainScreen.populateInboxTab(inbox);
        }
        catch (DiscordException e) {
            mainScreen.showErrorOnTab(MainScreen.TAB_INBOX);
            throw e;
        }
    }

    private void runFetchGuildChannels() throws DiscordException {
        ChannelListScreen list = (ChannelListScreen) callerScreen;
        list.removeAllItems();
        list.addItem(new LoadingItem());

        try {
            JSONArray channelsJson = (JSONArray) acc.fetchJSON("/guilds/" + guild.id + "/channels");
            Vector channels = parseGuildChannels(channelsJson);

            acc.channelCache.set(guild.id, channels);

            // Save cache
            Settings.save();

            list.populate(channels);
        }
        catch (DiscordException e) {
            list.showError();
            throw e;
        }
    }

    /**
     * Parse fetched channel list into sorted categories and channels.
     * Returns vector of ChannelCategory.
     */
    private Vector parseGuildChannels(JSONArray channelsJson) {
        int numChannels = channelsJson.size();

        Vector sortedChannels = new Vector();
        Vector sortedPositions = new Vector();

        for (int i = 0; i < numChannels; i++) {
            JSONObject channelJson = channelsJson.getObject(i);
            int position = channelJson.getInt("position", i);
            int insertAt = sortedChannels.size();

            while (insertAt > 0) {
                Integer previousPosition = (Integer) sortedPositions.elementAt(insertAt - 1);
                if (previousPosition.intValue() <= position) break;

                insertAt--;
            }

            sortedChannels.insertElementAt(channelJson, insertAt);
            sortedPositions.insertElementAt(new Integer(position), insertAt);
        }

        Vector textChannelsJson = new Vector();
        Vector voiceChannelsJson = new Vector();
        Vector categories = new Vector();

        ChannelCategory defaultCategory = new ChannelCategory((String) null);
        categories.addElement(defaultCategory);

        // Divide all channels into text/voice channels and channel categories.
        // Voice channels are separate so that they can be added last.
        // If we were to go just by position numbers, VC's would show first.
        // Channels with unsupported types are discarded.
        for (int i = 0; i < sortedChannels.size(); i++) {
            JSONObject channelJson = (JSONObject) sortedChannels.elementAt(i);
            int type = channelJson.getInt("type", 0);

            switch (type) {
                case Channel.TYPE_GUILD_CATEGORY: {
                    categories.addElement(new ChannelCategory(channelJson));
                    break;
                }
                case Channel.TYPE_GUILD_TEXT:
                case Channel.TYPE_GUILD_NEWS: {
                    textChannelsJson.addElement(channelJson);
                    break;
                }
                case Channel.TYPE_GUILD_VOICE: {
                    voiceChannelsJson.addElement(channelJson);
                    break;
                }
                // Not supported yet:
                // case Channel.TYPE_GUILD_FORUM:
                // case Channel.TYPE_GUILD_MEDIA: {
                    
                // }
            }
        }

        groupChannelsIntoCategories(textChannelsJson, categories);
        groupChannelsIntoCategories(voiceChannelsJson, categories);

        if (defaultCategory.children.size() == 0) {
            categories.removeElementAt(0);
        }

        return categories;
    }

    private void groupChannelsIntoCategories(Vector channels, Vector categories) {
        for (int i = 0; i < channels.size(); i++) {
            JSONObject channelJson = (JSONObject) channels.elementAt(i);
            long parentID = Long.parseLong(channelJson.getString("parent_id", null));

            // Fallback to default (topmost) category
            ChannelCategory parentCategory = (ChannelCategory) categories.elementAt(0);

            // Find the parent category
            for (int j = 1; j < categories.size(); j++) {
                ChannelCategory category = (ChannelCategory) categories.elementAt(j);

                if (category.id == parentID) {
                    parentCategory = category;
                    break;
                }
            }

            Channel channel = new Channel(channelJson);
            parentCategory.children.addElement(channel);
        }
    }

    private void runFetchMessages() throws DiscordException {
        ChatScreen chatScreen = (ChatScreen) callerScreen;

        try {
            String url = createFetchMessagesUrl(channel.id, paginationType, paginationMessageId);
            JSONArray messagesJson = (JSONArray) acc.fetchJSON(url);

            chatScreen.page = newPage;
            chatScreen.populate(messagesJson, paginationType);

            if ("after".equals(paginationType)) {
                // If we loaded a non-full page of messages and the anchor
                // message is still visible, scroll to it, else scroll to top.
                if (!chatScreen.jumpToMessage(paginationMessageId)) {
                    chatScreen.setHighlightedIndex(0);
                }
            }
            else if ("around".equals(paginationType)) {
                // Scroll to the anchor message that we jumped to
                chatScreen.jumpToMessage(paginationMessageId);
            }
            else {
                // "before" or not specified -> Scroll to bottom
                chatScreen.setHighlightedIndex(chatScreen.items.size() - 1);
            }
        }
        catch (DiscordException e) {
            if (!chatScreen.hasLoaded) {
                App.popScreen();
            }
            throw e;
        }
        finally {
            chatScreen.setBannerText(null);
        }
    }

    private String createFetchMessagesUrl(long channelID, String paginationType, long paginationMessageId) {
        StringBuffer url = new StringBuffer()
            .append("/channels/")
            .append(channel.id)
            .append("/messages");

        boolean hasParameter = false;

        if (paginationType != null) {
            url.append('?').append(paginationType)
                .append('=').append(paginationMessageId);

            hasParameter = true;
        }

        url.append(hasParameter ? '&' : '?')
            .append("limit=")
            .append(Settings.messageLoadCount);

        return url.toString();
    }

    private void runSendMessage() throws DiscordException {
        if (message.trim().length() == 0) {
            throw new DiscordException("Cannot send an empty message");
        }
        
        ChatScreen chatScreen = (ChatScreen) callerScreen;

        try {
            chatScreen.setBannerText("Sending message");
            acc.sendMessage(channel, message, recipientMessageId, sendPing);

            chatScreen.messageDraft = "";
        }
        finally {
            chatScreen.setBannerText(null);
        }

        chatScreen.fetchMessages(null, 0, 0, false);
    }

    private void runFetchIcon() {
        try {
            String iconURL =
                acc.getMediaProxyUrl() + "/avatars/" + iconRequest.user.id + "/" +
                iconRequest.user.iconHash + ".jpg?size=16&quality=low";

            HTTP h = HTTP.createRequest(iconURL);
            Image icon = h.getResponseImage();

            IconManager.fetchRequestFulfilled(iconRequest, icon);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void runFetchAttachment() {
        AttachmentScreen screen = (AttachmentScreen) callerScreen;

        try {
            String imageURL = attachment.getPreviewUrl(
                acc.getMediaProxyUrl(), attachmentPreviewWidth, attachmentPreviewHeight);

            if (imageURL == null) imageURL = attachment.url;

            if (imageURL == null) {
                throw new Exception("Attachment has no image URL");
            }

            HTTP h = HTTP.createRequest(imageURL);
            Image image = h.getResponseImage();

            screen.imageLoaded(attachmentRequestId, image);
        }
        catch (Exception e) {
            e.printStackTrace();
            screen.imageLoadFailed(attachmentRequestId);
        }
    }

    private void runDeleteMessage() throws DiscordException {
        ChatScreen chatScreen = (ChatScreen) callerScreen;

        try {
            chatScreen.setBannerText("Deleting message");

            HTTP h = acc.createApiRequest("DELETE",
                "/channels/" + channel.id + "/messages/" + editOrDeleteMessage.id);

            int responseCode = h.getResponseCode();
            String responseText = h.getResponseString();
            acc.checkApiResponse(responseCode, responseText);

            editOrDeleteMessage.markDeleted();

            // Re-add the message items to the screen to reflect any changes
            // in the deleted message (e.g. deleted attachments).
            // Keep the same message item selected.
            int prevHighlighted = chatScreen.getHighlightedIndex();
            chatScreen.readdItems();
            chatScreen.setHighlightedIndex(prevHighlighted);
        }
        catch (Exception e) {
            if (e == StandardHTTP.requestMethodException) {
                throwUnsupportedMethodException(
                    "Deleting messages is not supported",
                    "Your device does not support the HTTP DELETE method");
            }

            throw new DiscordException(e);
        }
        finally {
            chatScreen.setBannerText(null);
        }
    }

    private void runEditMessage() throws DiscordException {
        if (message.trim().length() == 0) {
            throw new DiscordException("Cannot send an empty message");
        }

        ChatScreen chatScreen = (ChatScreen) callerScreen;

        try {
            chatScreen.setBannerText("Editing message");

            HTTP h = acc.createApiRequest("PATCH",
                "/channels/" + channel.id + "/messages/" + editOrDeleteMessage.id);

            JSONObject editData = new JSONObject();
            editData.put("content", message);

            h.setData(editData.build());

            int responseCode = h.getResponseCode();
            String responseText = h.getResponseString();
            acc.checkApiResponse(responseCode, responseText);

            chatScreen.editDraft = "";
            chatScreen.editDraftTarget = null;
        }
        catch (Exception e) {
            if (e == StandardHTTP.requestMethodException) {
                throwUnsupportedMethodException(
                    "Editing messages is not supported",
                    "Your device does not support the HTTP PATCH method");
            }

            throw new DiscordException(e);
        }
        finally {
            chatScreen.setBannerText(null);
        }

        chatScreen.fetchMessages(null, 0, 0, false);
    }

    private void throwUnsupportedMethodException(String title, String description) throws DiscordException {
        Vector tips = new Vector();

        // Java patch can enable all HTTP methods, supports 9.3 and up
        if (Platform.isJ9S60Version("")) {
            tips.addElement("install Java patches from nnproject.cc/hmidp");
        }
        tips.addElement("use a different connection mode");

        throw new DiscordException(title, description, tips);
    }

    private void runSendTyping() {
        ChatScreen chatScreen = (ChatScreen) callerScreen;

        acc.sendTyping(channel.id);
        chatScreen.lastSentTypingTime = System.currentTimeMillis();
    }
}