package fi.gtrxac.discord;

import fi.gtrxac.bluewap.ui.*;
import fi.gtrxac.bluewap.http.*;
import fi.gtrxac.bluewap.client.*;
import fi.gtrxac.bluewap.*;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;

public class ConnectionModeScreen extends ListScreen implements CommandListener {
    public static final int CMD_BACK = 1;
    public static final int CMD_CONTINUE = 2;

    private RadioButtonItem directOption;
    private RadioButtonItem btOption;
    private RadioButtonItem proxyOption;
    private ButtonItem continueButton;
    private int urlFieldsPosition;
    private TextFieldItem apiUrlField;
    private TextFieldItem cdnUrlField;
    private TextFieldItem mediaProxyUrlField;

    public ConnectionModeScreen() {
        super();
        setTitle("Connection setup");

        addItem(new StringItem("Select your connection method.", Fonts.get(0, true, false, false)));

        RadioButtonGroup group = new RadioButtonGroup();

        addItem(new BlankItem(0));

        String directDescription =
            Platform.isSymbian ?
            "Recommended - requires the nnproject TLS 1.2 patch." :
            "Recommended - requires TLS 1.2 (supported on some Nokia S40 and Sony Ericsson devices if the app was installed via signed JAD).";

        addItem(directOption = new RadioButtonItem(group, "Direct"));
        addItem(directDescription);

        addItem(new BlankItem(0));

//#ifndef NO_BLUETOOTH
        if (Platform.supportsBluetooth) {
            addItem(btOption = new RadioButtonItem(group, "BT tethering"));
            addItem("Tether to a device running BlueWAP Server. Recommended when TLS 1.2 is unavailable, or in regions without 2G/3G networks.");
        }
        else {
            addItem(new WarningItem(
                "Bluetooth tethering is unavailable. Your device does not support Java Bluetooth API."));
        }
//#else
        if (Platform.supportsBluetooth) {
            addItem(new WarningItem(
                "Bluetooth tethering is unavailable in Discofi Lite. To use tethering, install the standard version of Discofi."));
        }
//#endif

        addItem(new BlankItem(0));

        addItem(proxyOption = new RadioButtonItem(group, "Proxy"));
        addItem("Unsafe - may lead to account restrictions. Hosting your own proxy is recommended when using this option.");

        addItem(new BlankItem(0));

        group.setTickedIndex(App.pendingAccount.connectionMode);

        urlFieldsPosition = items.size();

        boolean usingProxy =
            (App.pendingAccount.connectionMode == Account.CONNECTION_MODE_PROXY);

        setUrlFieldsVisibility(usingProxy);

        setCommandListener(this);
        addCommand(new Command("Back", Command.BACK, CMD_BACK));
        addCommand(new Command("Next", Command.OK, CMD_CONTINUE));
    }

    private void setUrlFieldsVisibility(boolean newVisibility) {
        boolean currentVisibility = (apiUrlField != null);

        if (newVisibility && !currentVisibility) {
            // Show if needed
            apiUrlField = new TextFieldItem("API URL", App.pendingAccount.apiUrl, 1000, 0);
            cdnUrlField = new TextFieldItem("CDN URL", App.pendingAccount.cdnUrl, 1000, 0);
            mediaProxyUrlField = new TextFieldItem("Media proxy URL", App.pendingAccount.mediaProxyUrl, 1000, 0);

            insertItem(new BlankItem(0), urlFieldsPosition);
            insertItem(mediaProxyUrlField, urlFieldsPosition);
            insertItem(new LabelItem("Media proxy URL"), urlFieldsPosition);
            
            insertItem(new BlankItem(0), urlFieldsPosition);
            insertItem(cdnUrlField, urlFieldsPosition);
            insertItem(new LabelItem("CDN URL"), urlFieldsPosition);

            insertItem(new BlankItem(0), urlFieldsPosition);
            insertItem(apiUrlField, urlFieldsPosition);
            insertItem(new LabelItem("API URL"), urlFieldsPosition);
        }
        else if (!newVisibility && currentVisibility) {
            // Hide if needed - save values first
            App.pendingAccount.apiUrl = apiUrlField.getValue();
            App.pendingAccount.cdnUrl = cdnUrlField.getValue();
            App.pendingAccount.mediaProxyUrl = mediaProxyUrlField.getValue();

            for (int itemIndex = 0; itemIndex < 9; itemIndex++) {
                removeItem(urlFieldsPosition);
            }

            apiUrlField = null;
            cdnUrlField = null;
            mediaProxyUrlField = null;
        }
    }

    public void commandAction(Command c, Displayable d) {
        switch (c.getPriority()) {
            case CMD_BACK: {
                App.popScreen();
                break;
            }
            case CMD_CONTINUE: {
                continueLogin();
                break;
            }
        }
    }

    public void itemSelected(Item i) {
//#ifndef NO_BLUETOOTH
        if (i == directOption || (i == btOption && i != null)) {
            App.pendingAccount.connectionMode = (i == directOption) ?
                Account.CONNECTION_MODE_DIRECT :
                Account.CONNECTION_MODE_BLUETOOTH;

            setUrlFieldsVisibility(false);
        }
//#else
        if (i == directOption) {
            App.pendingAccount.connectionMode = Account.CONNECTION_MODE_DIRECT;

            setUrlFieldsVisibility(false);
        }
//#endif
        else if (i == proxyOption) {
            App.pendingAccount.connectionMode = Account.CONNECTION_MODE_PROXY;

            setUrlFieldsVisibility(true);

            // Scroll so the URL fields are visible
            setHighlightedItem(mediaProxyUrlField);
            setHighlightedItem(proxyOption);
        }
        else if (i == continueButton) {
            continueLogin();
        }
    }

    private void continueLogin() {
        if (apiUrlField != null) {
            String apiUrl = apiUrlField.getValue();

            if (apiUrl.trim().length() == 0) {
                new DiscordException(
                    "API URL required",
                    "Please enter a proxy API URL or use a different connection mode.", null)
                    .show();

                return;
            }

            String cdnUrl = cdnUrlField.getValue();

            if (cdnUrl.trim().length() == 0) {
                new DiscordException(
                    "CDN URL required",
                    "Please enter a proxy CDN URL or use a different connection mode.", null)
                    .show();

                return;
            }

            String mediaProxyUrl = mediaProxyUrlField.getValue();

            if (mediaProxyUrl.trim().length() == 0) {
                new DiscordException(
                    "Media proxy URL required",
                    "Please enter a media proxy URL or use a different connection mode.", null)
                    .show();

                return;
            }

            App.pendingAccount.apiUrl = apiUrl;
            App.pendingAccount.cdnUrl = cdnUrl;
            App.pendingAccount.mediaProxyUrl = mediaProxyUrl;
        }

//#ifndef NO_BLUETOOTH
        if (App.pendingAccount.needsTetherDevice()) {
            App.pushScreen(new BluetoothDeviceScreen());
        }
        else {
            App.pushScreen(new LoginScreen());
        }
//#else
        App.pushScreen(new LoginScreen());
//#endif
    }
}