package fi.gtrxac.discord;

import fi.gtrxac.bluewap.ui.*;
import fi.gtrxac.bluewap.*;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.Font;

/**
 * Screen used for entering email/password or token when adding a new account or editing an existing account.
 */
public class LoginScreen extends ListScreen implements CommandListener {
    public static final int CMD_BACK = 1;

    private TextFieldItem emailField;
    private TextFieldItem passwordField;
    private TextFieldItem tokenField;
    private LinkItem loginTipsLink;
    private ButtonItem continueButton;
    private ButtonItem loginButton;
    private ButtonItem tokenLoginButton;
    private ButtonItem tokenJarImportButton;

//#ifndef NO_FILECONNECTION
    private ButtonItem tokenFileImportButton;
//#endif

    public LoginScreen() {
        super();
        setTitle("Log in");

        boolean hasToken = (App.pendingAccount.token.trim().length() != 0);

        if (hasToken) {
            addItem("To keep using your previous session, select 'Continue'.");
            addItem(continueButton = new ButtonItem("Continue"));
            addItem(new BlankItem(Fonts.height/4));
            
            setHighlightedItem(continueButton);
        }

        addItem("To use password login, you may need to enable 2FA and use a Wi-Fi network that you've previously used Discord on.");

        addItem(loginTipsLink = new LinkItem("Need help logging in?"));

        addItem(new LabelItem("Email"));
        addItem(emailField = new TextFieldItem("Email", App.pendingAccount.email, 100, TextField.EMAILADDR));

        addItem(new LabelItem("Password"));
        addItem(passwordField = new TextFieldItem("Password", "", 100, TextField.PASSWORD));

        if (App.pendingAccount.password.trim().length() != 0) {
            addItem(new LabelItem("Leave blank to use existing password"));
        }

        addItem(new BlankItem(0));

        addItem(loginButton = new ButtonItem(hasToken ? "Log in again" : "Log in"));

        addItem(new BlankItem(Fonts.height/4));

        addItem("Token login is only intended for advanced users and may be unsafe.");

        if (hasToken) {
            addItem(new StringItem("Don't share your token with others!", Fonts.bold));
        }

        addItem(new LabelItem("Token"));
        addItem(tokenField = new TextFieldItem("Token", App.pendingAccount.token, 80, 0));

        addItem(new BlankItem(0));

        addItem(tokenLoginButton = new ButtonItem("Use token"));
        addItem(tokenJarImportButton = new ButtonItem("Import from JAR"));

//#ifndef NO_FILECONNECTION
        if (Platform.supportsFileConnection) {
            addItem(tokenFileImportButton = new ButtonItem("Import from file"));
        }
        else {
            addItem(new WarningItem(
                "Token file import is unavailable as your device does not support FileConnection API. Use JAR import instead."));
        }
//#else
        if (Platform.supportsFileConnection) {
            addItem(new WarningItem(
                "Token file import is unavailable in Discofi Lite. Use JAR import or install the standard version of Discofi to use file import."));
        }
//#endif

        setCommandListener(this);
        addCommand(new Command("Back", Command.BACK, CMD_BACK));
    }

    public void commandAction(Command c, Displayable d) {
        switch (c.getPriority()) {
            case CMD_BACK: {
                App.popScreen();
                break;
            }
        }
    }

    public void itemSelected(Item i) {
        if (i == continueButton) {
            App.loginCompleted(App.pendingAccount.token);
        }
        else if (i == loginButton) {
            passwordLogin();
        }
        else if (i == tokenLoginButton) {
            tokenLogin();
        }
        else if (i == tokenJarImportButton) {
            String jarToken = App.instance.getAppProperty("Token");

            if (jarToken == null) {
                new DiscordException(
                    "Token not found",
                    "To use this option, add your token to the app's JAR manifest.\nOn your PC or smartphone, open the Discofi JAR as a ZIP, edit the META-INF/MANIFEST.MF file, and add a line containing:\n\"Token: (your token)\"",
                    null)
                    .show();

                return;
            }
            jarToken = jarToken.replace('"', ' ').trim();
            verifyAndCompleteLogin(jarToken);
        }
        else if (i == loginTipsLink) {
            Dialog d = new Dialog();
            d.listView.addItem("If you get CAPTCHAs, enable multi-factor authentication (authenticator app) on your account and connect to a Wi-Fi network that you've previously used Discord on.");

            if (App.pendingAccount.connectionMode == Account.CONNECTION_MODE_PROXY) {
                d.listView.addItem(new StringItem(
                    "You may also need to connect without a proxy.", Fonts.bold));
            }

            d.listView.addItem("If you need to use Wi-Fi and your device doesn't support it:");
            d.listView.addItem("1. Log in to Discofi on a device that supports Wi-Fi.");
            d.listView.addItem("2. Get the token by editing the account and going to the login step.");
            d.listView.addItem("3. Send the token to this device.");
            d.listView.addItem("4. Import the token or paste it into the token field.");
            d.listView.addItem("5. Avoid using Discofi on both devices simultaneously.");

            App.pushScreen(d);
        }
//#ifndef NO_FILECONNECTION
        else if (i == tokenFileImportButton && i != null) {
            App.pushScreen(new TokenImportScreen());
        }
//#endif
    }

    public void passwordLogin() {
        String email = emailField.getValue();

        if (email.trim().length() == 0 || email.indexOf("@") == -1) {
            new DiscordException(
                "Please enter an email address",
                "A valid email address is required when using password login. You may also try token login (not recommended).",
                null)
                .show();

            return;
        }

        String password = passwordField.getValue();

        if (password.trim().length() == 0) {
            if (App.pendingAccount.password.trim().length() != 0) {
                password = App.pendingAccount.password;
            }
            else {
                new DiscordException(
                    "Please enter a password",
                    "A password is required when using password login. You may also try token login (not recommended).",
                    null)
                    .show();

                return;
            }
        }

        App.pendingAccount.email = email;
        App.pendingAccount.password = password;

        HTTPThread h = new HTTPThread(HTTPThread.ACTION_PASSWORD_LOGIN);
        h.email = email;
        h.password = password;
        h.start();
    }

    public void tokenLogin() {
        String token = tokenField.getValue();

        if (token.trim().length() == 0) {
            new DiscordException(
                "Please enter your token",
                "A token is required when using token login. You may also try password login.",
                null)
                .show();

            return;
        }
        verifyAndCompleteLogin(token);
    }

    public void tokenFileLogin(String token) {
        if (token.length() == 0) {
            new DiscordException(
                "Empty file",
                "The selected token file is empty. Please try another file.",
                null)
                .show();

            return;
        }
        verifyAndCompleteLogin(token);
    }

    private void verifyAndCompleteLogin(String token) {
        try {
            DiscordUtil.getUserIDFromToken(token);
            App.loginCompleted(token);
        }
        catch (DiscordException e) {
            e.show();
        }
    }
}