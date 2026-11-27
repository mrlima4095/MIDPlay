package fi.gtrxac.discord;

import fi.gtrxac.tinytls.*;
import fi.gtrxac.bluewap.*;
import fi.gtrxac.bluewap.http.*;
import fi.gtrxac.bluewap.client.*;
import fi.gtrxac.bluewap.ui.*;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Graphics;
import javax.microedition.io.*;
import java.io.*;

public class AccountListScreen extends ListScreen implements CommandListener {
    public static final int CMD_QUIT = 1;
    public static final int CMD_EDIT = 3;
    public static final int CMD_DELETE = 4;
    public static final int CMD_SETTINGS = 5;
    public static final int CMD_DELETE_PROMPT_YES = 100;
    public static final int CMD_DELETE_PROMPT_NO = 101;

    private SmallButtonItem addAccountButton;

    public AccountListScreen() {
        super();
        setTitle("Select account");

        int numAccounts = Accounts.list.size();

        if (numAccounts == 0) {
            int align = Graphics.HCENTER | Graphics.TOP;
            addItem(new StringItem("No accounts", Fonts.bold, align));
            addItem(new StringItem("Select 'Add account' to log in.", Fonts.plain, align));
        }
        else {
            addItem(new BlankItem(0));

            for (int i = 0; i < numAccounts; i++) {
                Account acc = (Account) Accounts.list.elementAt(i);

                try {
                    addItem(new ListItem(acc.getDisplayTitle()));
                }
                catch (DiscordException e) {
                    e.printStackTrace();
                }
            }
        }

        addItem(new BlankItem(0));
        addItem(addAccountButton = new SmallButtonItem("Add account"));

        if (numAccounts == 0) {
            setHighlightedItem(addAccountButton);
        }

        setCommandListener(this);
        addCommand(new Command("Quit", Command.EXIT, CMD_QUIT));
        addCommand(new Command("Edit", Command.ITEM, CMD_EDIT));
        addCommand(new Command("Delete", Command.ITEM, CMD_DELETE));
        addCommand(new Command("Settings", Command.SCREEN, CMD_SETTINGS));
    }

    public void commandAction(Command c, Displayable d) {
        switch (c.getPriority()) {
            case CMD_QUIT: {
                App.instance.notifyDestroyed();
                break;
            }
            case CMD_EDIT: {
                Account acc = getHighlightedAccount();
                if (acc == null) break;

                App.pendingAccount = acc;
                App.pushScreen(new ConnectionModeScreen());
                break;
            }
            case CMD_DELETE: {
                promptDelete();
                break;
            }
            case CMD_SETTINGS: {
                App.pushScreen(new SettingsScreen());
                break;
            }
            case CMD_DELETE_PROMPT_YES: {
                Account acc = getHighlightedAccount();
                if (acc == null) break;

                Accounts.list.removeElement(acc);
                Settings.save();

                App.popScreen();

                AccountListScreen newScreen = new AccountListScreen();
                newScreen.setHighlightedIndex(getHighlightedIndex() - 1);
                App.replaceScreen(newScreen);
                break;
            }
            case CMD_DELETE_PROMPT_NO: {
                App.popScreen();
                break;
            }
        }
    }

    public void itemSelected(Item i) {
        if (i == addAccountButton) {
            addAccount();
        }
        else {
            // -1 because of the blank item
            int accountIndex = getHighlightedIndex() - 1;
            Accounts.setActive(accountIndex);
            Account acc = Accounts.getActive();

//#ifndef NO_BLUETOOTH
            if (acc.needsTetherDevice()) {
                App.pushScreen(new BluetoothDeviceScreen());
            }
            else {
                App.replaceScreen(new MainScreen());
            }
//#else
            App.replaceScreen(new MainScreen());
//#endif
        }
    }

    private void addAccount() {
        App.pendingAccount = new Account();
        App.pushScreen(new ConnectionModeScreen());
    }

    private Account getHighlightedAccount() {
        int accountIndex = getHighlightedIndex() - 1;
        if (accountIndex < 0 || accountIndex >= Accounts.list.size()) return null;

        return (Account) Accounts.list.elementAt(accountIndex);
    }

    private void promptDelete() {
        Account acc = getHighlightedAccount();
        if (acc == null) return;
        
        try {
            Dialog prompt = new Dialog(
                "Are you sure you want to delete the account '" +
                acc.getDisplayTitle() +
                "' from the list?");

            prompt.addCommand(new Command("Yes", Command.OK, CMD_DELETE_PROMPT_YES));
            prompt.addCommand(new Command("No", Command.BACK, CMD_DELETE_PROMPT_NO));
            prompt.setCommandListener(this);

            App.pushScreen(prompt);
        }
        catch (DiscordException e) {
            e.show();
        }
    }
}