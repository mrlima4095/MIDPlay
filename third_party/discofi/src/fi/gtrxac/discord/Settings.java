package fi.gtrxac.discord;

import java.io.*;
import java.util.*;
import javax.microedition.io.*;
import javax.microedition.rms.*;
import javax.microedition.lcdui.Font;
import fi.gtrxac.bluewap.*;
import fi.gtrxac.bluewap.ui.*;
import fi.gtrxac.discord.util.*;

public class Settings implements RecordIOImpl {
    /**
     * Global font size preset used by the UI framework.
     */
    public static int fontSize = Font.SIZE_SMALL;
    
    /**
     * Whether to fetch paired BT device names. On some devices this is instant,
     * while on some this may freeze the app for e.g. up to 10 seconds per device.
     * This gets automatically disabled if fetching takes too long.
     */
    public static boolean getPairedDeviceNames = true;

    /**
     * MAC address - name mappings of Bluetooth devices
     */
    public static Hashtable deviceNameCache = new Hashtable();

    /**
     * Device vendor ID used in Discord X-Super-Properties header (persistent random UUID for each device)
     */
    public static String deviceVendorID = DiscordUtil.generateRandomUUID();

    /**
     * Use light UI theme instead of dark theme.
     * This may be reworked later if/when more themes are added.
     */
    public static boolean lightTheme = false;

    /**
     * Show square avatars/profile pictures in chats instead of circular ones.
     */
    public static boolean squareAvatars = false;

    /**
     * Use 12-hour AM/PM time format in message timestamps instead of 24-hour.
     */
    public static boolean use12hTime = false;

    /**
     * How many messages to load per page.
     */
    public static int messageLoadCount = 20;

    /**
     * When going to a message sending screen, send the typing indicator to show others that you are typing. 
     */
    public static boolean sendTypingIndicator = true;
    
    public void readData(DataInputStream dis) throws IOException {
        int numAccounts = dis.readInt();

        if (numAccounts > 0) {
            Accounts.list = new Vector();

            for (int i = 0; i < numAccounts; i++) {
                Accounts.list.addElement(new Account(dis));
            }
        }

        int activeAccount = dis.readInt();

        if (activeAccount >= 0 && activeAccount < numAccounts) {
            Accounts.setActive(activeAccount);
        }

        fontSize = dis.readUnsignedByte();
        getPairedDeviceNames = dis.readBoolean();

        int deviceNameCount = dis.readInt();

        if (deviceNameCount > 0) {
            deviceNameCache = new Hashtable(deviceNameCount);

            for (int i = 0; i < deviceNameCount; i++) {
                deviceNameCache.put(dis.readUTF(), dis.readUTF());
            }
        }

        deviceVendorID = dis.readUTF();
        lightTheme = dis.readBoolean();
        squareAvatars = dis.readBoolean();
        use12hTime = dis.readBoolean();
        messageLoadCount = dis.readUnsignedByte();
        sendTypingIndicator = dis.readBoolean();
    }

    public void writeData(DataOutputStream dos) throws IOException {
        dos.writeInt(Accounts.list.size());

        for (int i = 0; i < Accounts.list.size(); i++) {
            Account acc = (Account) Accounts.list.elementAt(i);
            acc.writeSaveData(dos);
        }

        dos.writeInt(Accounts.getActiveIndex());

        dos.writeByte(fontSize);
        dos.writeBoolean(getPairedDeviceNames);

        dos.writeInt(deviceNameCache.size());

        for (Enumeration e = deviceNameCache.keys(); e.hasMoreElements(); ) {
            String key = (String) e.nextElement();
            String value = (String) deviceNameCache.get(key);
            dos.writeUTF(key);
            dos.writeUTF(value);
        }

        dos.writeUTF(deviceVendorID);
        dos.writeBoolean(lightTheme);
        dos.writeBoolean(squareAvatars);
        dos.writeBoolean(use12hTime);
        dos.writeByte(messageLoadCount);
        dos.writeBoolean(sendTypingIndicator);
    }

    private Settings() {}
    private static Settings instance = new Settings();

    public static void save() {
        RecordIO.save("a", instance);
        refresh();
    }

    public static void load() {
        RecordIO.load("a", instance);
        refresh();
    }

    /**
     * Reset variables that are dependent on any settings.
     */
    public static void refresh() {
        Fonts.loadFonts(fontSize);
        Theme.load();
        AppCanvas.setTimeFormat(use12hTime);
        IconManager.reset();
    }

    static {
        load();
    }
}