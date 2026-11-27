package fi.gtrxac.discord;

import fi.gtrxac.bluewap.ui.*;
import javax.microedition.lcdui.Font;
import java.util.*;

public class DiscordException extends Exception {
    public String friendlyMessage;
    public String message;
    public Vector tips;

    public DiscordException(Exception exception) {
        this(exception.toString());
    }

    public DiscordException(String message) {
        this.message = message;
        this.tips = new Vector();

        if (message.equals("401: Unauthorized")) {
            this.friendlyMessage = "Invalid session";
            this.tips.addElement("log in again");
            this.tips.addElement("if you're using token login, make sure the token is written correctly");
            this.tips.addElement("use a different connection method");
            this.tips.addElement("use a different account");
        }
        else if (message.startsWith("java.io.IOException") || message.indexOf("UnknownHost") != -1 || message.indexOf("SocketException") != -1) {
            this.friendlyMessage = "Failed to load data";
            this.tips.addElement("try again");
            this.tips.addElement("check that your internet connection is working");
            this.tips.addElement("check that other online apps can connect");
            this.tips.addElement("if you're using mobile data, check that your APN settings are correct");
        }
        // Permission error for Connector
        else if (message.startsWith("java.lang.SecurityException: cannot open connection")) {
            this.friendlyMessage = "Permission denied";
            
            Account acc = Accounts.getActive();

            if (acc != null && acc.connectionMode == Account.CONNECTION_MODE_DIRECT) {
                this.tips.addElement("make sure you've installed the app using a signed JAD, if possible on your device (see gtrxac.fi/j2me/proxyless)");
            }

            this.tips.addElement("check your device's app permission settings");
            this.tips.addElement("use a different connection method");
            this.tips.addElement("try on another device");
        }
        else {
            this.friendlyMessage = "Error";
        }
    }

    public DiscordException(String friendlyMessage, String message, Vector tips) {
        this.friendlyMessage = friendlyMessage;
        this.message = message;
        this.tips = tips;
    }

    public void show() {
        printStackTrace();

        Dialog d = new Dialog();

        Font largerBold = Fonts.get(1, true, false, false);
        String title = (friendlyMessage != null) ? friendlyMessage : message;

        d.listView.addItem(new StringItem(title, largerBold));

        boolean haveTips = (tips != null && tips.size() > 0);

        if (haveTips) {
            d.listView.addItem("Please try the following:");

            String[] tipsArray = new String[tips.size()];
            tips.copyInto(tipsArray);
            d.listView.addItem(new StringListItem(tipsArray));
        }

        if (friendlyMessage != null) {
            String details = haveTips ? ("Details:\n" + message) : message;
            d.listView.addItem(details);
        }

        App.pushScreen(d);
    }

    public String toString() {
        return friendlyMessage + " (" + message + ")";
    }
}