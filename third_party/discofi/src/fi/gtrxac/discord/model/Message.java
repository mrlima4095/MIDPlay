package fi.gtrxac.discord.model;

import fi.gtrxac.discord.*;
import fi.gtrxac.bluewap.Util;
import fi.gtrxac.discord.DiscordUtil;
import cc.nnproject.json.*;
import java.util.*;

public class Message {
    public static final int TYPE_ADDED = 1;  // user added another user to group DM
    public static final int TYPE_REMOVED = 2;  // user left (or removed someone) from group DM
    public static final int TYPE_CALL = 3;
    public static final int TYPE_CHANNEL_NAME_CHANGE = 4;  // changed name of group DM
    public static final int TYPE_CHANNEL_ICON_CHANGE = 5;
    public static final int TYPE_PINNED = 6;
    public static final int TYPE_JOINED = 7;  // user joined server
    public static final int TYPE_BOOSTED = 8;  // user boosted server
    public static final int TYPE_BOOSTED_LEVEL_1 = 9; 
    public static final int TYPE_BOOSTED_LEVEL_2 = 10;
    public static final int TYPE_BOOSTED_LEVEL_3 = 11;

    public long id;
    public User author;
    public String content;
    public boolean showAuthor;
    public String timestamp;
    public Vector attachments;
    public long channelID;

    public boolean isStatus;
    public boolean isForwarded;
    public boolean isReply;

    public long replyID;
    public User replyAuthor;
    public String replyContent;

    public Message(JSONObject data) {
        id = Long.parseLong(data.getString("id"));
        author = new User(data.getObject("author"));
        timestamp = DiscordUtil.formatTimestamp(id, Settings.use12hTime);
        channelID = Long.parseLong(data.getString("channel_id", null));

        JSONObject contentObject = getContentObject(data);

        int type = contentObject.getInt("type", 0);
        isStatus = (type >= TYPE_ADDED && type <= TYPE_BOOSTED_LEVEL_3);

        if (isStatus) {
            content = parseStatusMessage(contentObject, type);
        }
        else {
            content = parseContent(contentObject);
            parseReply(contentObject);
            parseAttachments(contentObject);
        }
    }

    /**
     * Get JSON object to use for filling out message content and related fields.
     * For forwarded messages, this is an inner object inside the message object.
     */
    private JSONObject getContentObject(JSONObject data) {
        JSONObject result = data;
        isForwarded = false;

        try {
            result = data.getArray("message_snapshots").getObject(0).getObject("message");
            isForwarded = true;
        }
        catch (Exception e) {}

        return result;
    }

    private String parseContent(JSONObject contentObject) {
        try {
            MessageParser parser = new MessageParser(contentObject);
            return parser.parse();
        }
        catch (Exception e) {
            e.printStackTrace();

            String rawContent = contentObject.getString("content", null);
            if (rawContent == null) return "";
            return rawContent;
        }
    }

    private void parseReply(JSONObject data) {
        try {
            data.getObject("message_reference");
            isReply = true;
        }
        catch (Exception e) {}

        try {
            Message replyMessage = new Message(data.getObject("referenced_message"));
            replyID = replyMessage.id;
            replyAuthor = replyMessage.author;

            StringBuffer replyBuf = new StringBuffer();
            replyBuf.append(replyMessage.content.replace('\n', ' '));

            if (replyMessage.attachments != null) {
                int attCount = replyMessage.attachments.size();

                if (replyBuf.length() != 0) {
                    replyBuf.append(" ");
                }

                replyBuf.append("(")
                    .append(attCount)
                    .append(" file");

                if (attCount != 1) {
                    replyBuf.append("s");
                }
                replyBuf.append(")");
            }

            replyContent = replyBuf.toString();
            replyContent = Util.stringToLength(replyContent, 90);
        }
        catch (Exception e) {}
    }

    private void parseAttachments(JSONObject data) {
        JSONArray attachArray = data.getArray("attachments", null);
        if (attachArray == null) return;

        for (int i = 0; i < attachArray.size(); i++) {
            try {
                JSONObject attachJson = attachArray.getObject(i);
                Attachment attachment = new Attachment(attachJson);

                if (attachments == null) {
                    attachments = new Vector();
                }
                attachments.addElement(attachment);
            }
            catch (Exception e) {}
        }
    }

    private String parseStatusMessage(JSONObject data, int type) {
        // Status message -> determine content by message type
        String target = "(unknown)";
        long targetID = 0;

        try {
            JSONObject targetData = data.getArray("mentions").getObject(0);
            User targetUser = new User(targetData);
            target = targetUser.name;
            targetID = targetUser.id;
        }
        catch (Exception e) {}

        switch (type) {
            case TYPE_ADDED: {
                return "added " + target + " to the group";
            }
            case TYPE_REMOVED: {
                if (author.id == targetID) {
                    return "left the group";
                }
                return "removed " + target + " from the group";
            }
            case TYPE_CALL: {
                return "started a call";
            }
            case TYPE_CHANNEL_NAME_CHANGE: {
                return "changed the group name";
            }
            case TYPE_CHANNEL_ICON_CHANGE: {
                return "changed the group icon";
            }
            case TYPE_PINNED: {
                return "pinned a message";
            }
            case TYPE_JOINED: {
                return "joined the server";
            }
            case TYPE_BOOSTED: {
                return "boosted the server";
            }
            default: {
                return "boosted the server to level " + (type - TYPE_BOOSTED);
            }
        }
    }

    /**
     * Check if no supported content was successfully parsed from this message.
     */
    public boolean isUnsupported() {
        return content.length() == 0 && attachments == null;
    }

    /**
     * Determine whether or not the author/timestamp row should be shown for
     * this message.
     *
     * @param above          The message shown above this message.
     * @param clusterStartId The ID of the top-most message in this message cluster.
     * @return true if author should be shown, false if messages are "merged"
     */
    public boolean shouldShowAuthor(Message above, long clusterStartId) {
        // Different authors -> true
        if (above.author.id != author.id) return true;

        // This message is a reply -> true
        if (isReply) return true;

        // This message only consists of an attachment -> true
        if (attachments != null && content.length() == 0) return true;

        // This message or above message is a status message -> true
        if (isStatus || above.isStatus) return true;

        // Finally, check if message was sent more than 7 minutes after the first message of the cluster
        long thisMsgTime = id >> 22;
        long firstMsgTime = clusterStartId >> 22;
        return (thisMsgTime - firstMsgTime > 7*60*1000);
    }

    /**
     * Check if this message was sent by the currently logged-in user.
     */
    public boolean isOwn() {
        Account acc = Accounts.getActive();
        if (acc == null) return false;

        try {
            return acc.getUserID() == author.id;
        }
        catch (DiscordException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Mark this message as deleted in the UI and remove its content.
     * This does not make the actual delete request. Use this after a delete request has gone through.
     */
    public void markDeleted() {
        content = "(deleted)";
        isStatus = true;
        isForwarded = false;
        attachments = null;
        // isEdited = false;
        // embeds = null;
    }
}