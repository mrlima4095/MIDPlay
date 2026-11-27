package fi.gtrxac.discord;

import fi.gtrxac.bluewap.ui.*;
import fi.gtrxac.bluewap.*;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextField;

public class AuthCodeScreen extends ListScreen implements CommandListener {
    public static final int CMD_BACK = 1;
    public static final int CMD_CONTINUE = 2;

    private TextFieldItem authCodeField;
    private ButtonItem continueButton;

    public AuthCodeScreen() {
        super();
        setTitle("Authentication");

        addItem("Enter the 6-digit code from your authenticator app (e.g. Google Authenticator).");

        addItem(new BlankItem(0));

        addItem(new LabelItem("Authentication code"));
        addItem(authCodeField = new TextFieldItem("Authentication code", "", 6, TextField.NUMERIC));
        setHighlightedItem(authCodeField);

        setCommandListener(this);
        addCommand(new Command("Back", Command.BACK, CMD_BACK));
        addCommand(new Command("Next", Command.OK, CMD_CONTINUE));
    }

    public void commandAction(Command c, Displayable d) {
        switch (c.getPriority()) {
            case CMD_BACK: {
                App.popScreen();
                break;
            }
            case CMD_CONTINUE: {
                submitAuthCode();
                break;
            }
        }
    }

    public void itemSelected(Item i) {
        if (i == continueButton) {
            submitAuthCode();
        }
    }

    private void submitAuthCode() {
        String authCode = authCodeField.getValue();

        if (!authCodeIsValid(authCode)) {
            new DiscordException(
                "Please enter a valid authentication code",
                "The multi-factor authentication code (TOTP) from your authenticator app should be a 6-digit number.",
                null)
                .show();

            return;
        }

        HTTPThread h = new HTTPThread(HTTPThread.ACTION_SUBMIT_TOTP);
        h.totp = authCode;
        h.start();
    }

    private boolean authCodeIsValid(String totp) {
        // totp code format: 123456
        if (totp.length() == 6) {
            try {
                Integer.parseInt(totp);
                return true;
            }
            catch (Exception e) {}
        }
        return false;
    }
}