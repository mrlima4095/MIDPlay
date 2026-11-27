package fi.gtrxac.discord;

import java.util.*;
import util.*;
import fi.gtrxac.bluewap.Util;

public class DiscordUtil {
    public static final Random random = new Random();

    private static Vector getInvalidTokenTips() {
        Vector result = new Vector();
        result.addElement("use password login instead of your token");
        result.addElement("if you want to use your token, make sure you have retrieved it correctly");
        result.addElement("to avoid typos, import your token from a text file or insert it into the JAR (see login screen for details)");
        return result;
    }

    public static String getUserIDFromToken(String token) throws DiscordException {
        int b64idLength = token.indexOf('.');

        if (b64idLength < 20) {
            throw new DiscordException(
                "Token is written incorrectly",
                "Token's ID segment is missing or too short",
                getInvalidTokenTips());
        }

        String b64id = token.substring(0, b64idLength);
        byte[] idBytes = Base64.decodeFast(b64id);
        String id = new String(idBytes);

        try {
            return Long.toString(Long.parseLong(id));
        }
        catch (Exception e) {
            throw new DiscordException(
                "Token is written incorrectly",
                "Failed to decode ID: " + e.toString(),
                getInvalidTokenTips());
        }
    }

    public static String generateRandomUUID() {
        byte[] bytes = new byte[16];

        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (random.nextInt() & 0xff);
        }

        bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x40);
        bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80);

        StringBuffer uuid = new StringBuffer(36);
        for (int i = 0; i < bytes.length; i++) {
            if (i == 4 || i == 6 || i == 8 || i == 10) {
                uuid.append('-');
            }

            int value = bytes[i] & 0xff;
            if (value < 0x10) {
                uuid.append('0');
            }
            uuid.append(Integer.toHexString(value));
        }
        return uuid.toString();
    }

    public static final long DISCORD_EPOCH = 1420070400000L;

    public static String formatTimestamp(String id, boolean use12hTime) {
        long timestamp = (Long.parseLong(id) >> 22) + DISCORD_EPOCH;
        return Util.formatTimestamp(timestamp, use12hTime);
    }
}