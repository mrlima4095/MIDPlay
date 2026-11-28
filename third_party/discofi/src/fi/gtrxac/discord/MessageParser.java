package fi.gtrxac.discord;

import fi.gtrxac.discord.model.*;
import cc.nnproject.json.*;

public class MessageParser {
    private JSONObject inputContentObject;
    private String inputContent;
    private int readOffset;
    private StringBuffer outputContent; 

    public MessageParser(JSONObject contentObject) {
        inputContentObject = contentObject;
        inputContent = contentObject.getString("content", null);
        outputContent = new StringBuffer(); 

        if (inputContent == null) {
            inputContent = "";
        }
    }

    public String parse() {
        while (readOffset < inputContent.length()) {
            char currentChar = inputContent.charAt(readOffset);

            if (currentChar == '<' && parseMentionAndMoveCursor()) {
                continue;
            }
            else {
                outputContent.append(currentChar);
                readOffset++;
            }
        }

        return outputContent.toString();
    }

    private boolean parseMentionAndMoveCursor() {
        if (parseMention()) {
            int tagEndIndex = inputContent.indexOf(">", readOffset);
            readOffset = tagEndIndex + 1;
            return true;
        }
        return false;
    }

    /**
     * Try to parse a mention tag for channels, users, etc. that is inside < >.
     * Return true on success.
     */
    private boolean parseMention() {
        int tagEndIndex = inputContent.indexOf(">", readOffset);
        if (tagEndIndex == -1) return false;

        // Everything that's inside the < >
        String tagContent = inputContent.substring(readOffset + 1, tagEndIndex);
        if (tagContent.length() == 0) return false;

        if (tagContent.charAt(0) == '@') {
            // User mention
            String userID = tagContent.substring(1);
            return parseUserMention(userID);
        }
        else if (tagContent.charAt(0) == '#') {
            String channelID = tagContent.substring(1);
            return parseChannelMention(channelID);
        }
        else if (tagContent.charAt(0) == ':') {
            // Basic emoji
            return parseEmojiMention(tagContent.substring(1));
        }
        else if (tagContent.charAt(0) == 'a' && tagContent.length() > 1 && tagContent.charAt(1) == ':') {
            // Animated emoji
            return parseEmojiMention(tagContent.substring(2));
        }

        return false;
    }

    /**
     * Try to parse user mention.
     * On success, appends the human-readable username form of the mention to
     * the output, and returns true.
     * On failure, return false.
     */
    private boolean parseUserMention(String userID) {
        // From the mentions object, find the user that this ID belongs to
        try {
            JSONArray mentions = inputContentObject.getArray("mentions");

            for (int i = 0; i < mentions.size(); i++) {
                JSONObject mention = mentions.getObject(i);
                String mentionID = mention.getString("id", null);

                if (userID.equals(mentionID)) {
                    String mentionName = mention.getString("username", "unknown");

                    outputContent.append("@").append(mentionName);
                    return true;
                }
            }
        }
        catch (JSONException e) {}

        if (!isValidID(userID)) return false;
        
        outputContent.append("@unknown");
        return true;
    }

    private boolean parseChannelMention(String channelID) {
        Account acc = Accounts.getActive();

        try {
            long id = Long.parseLong(channelID);
            Channel channel = acc.channelCache.getChannelByID(0, id);

            if (channel != null) {
                outputContent.append(channel.getNameForMention());
                return true;
            }
        }
        catch (NumberFormatException e) {
            return false;
        }

        if (!isValidID(channelID)) return false;

        outputContent.append("#unknown");
        return true;
    }

    /**
     * Try to parse a custom emoji mention. emojiContent is the tag content
     * without the leading < > or the "a:" prefix, i.e. "name:id".
     * On success, appends the :name: form of the emoji to the output, and
     * returns true. On failure, return false.
     */
    private boolean parseEmojiMention(String emojiContent) {
        int separatorIndex = emojiContent.indexOf(':');
        if (separatorIndex == -1) return false;

        String emojiName = emojiContent.substring(0, separatorIndex);
        String emojiID = emojiContent.substring(separatorIndex + 1);

        if (!isValidEmojiName(emojiName)) return false;
        if (!isValidID(emojiID)) return false;

        outputContent.append(':').append(emojiName).append(':');
        return true;
    }

    private boolean isValidEmojiName(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!isValidEmojiCharacter(c)) return false;
        }

        return true;
    }

    private boolean isValidEmojiCharacter(char c) {
        return (c >= 'a' && c <= 'z') ||
            (c >= 'A' && c <= 'Z') ||
            (c >= '0' && c <= '9') ||
            c == '_';
    }

    private boolean isValidID(String id) {
        if (id.length() < 17) return false;

        try {
            Long.parseLong(id);
        }
        catch (NumberFormatException e) {
            return false;
        }

        return true;
    }
}