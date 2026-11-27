package fi.gtrxac.discord;

import java.util.*;
import cc.nnproject.json.*;
import fi.gtrxac.discord.model.*;
import fi.gtrxac.bluewap.ui.*;
import fi.gtrxac.bluewap.*;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;

public class ChannelListScreen extends ListScreen implements CommandListener {
    public static final int CMD_BACK = 1;
    public static final int CMD_REFRESH = 2;

    private Guild guild;

    public ChannelListScreen(Guild guild) {
        super();
        setTitle(guild.getName());

        this.guild = guild;

        setCommandListener(this);
        addCommand(new Command("Back", Command.BACK, CMD_BACK));
        addCommand(new Command("Refresh", Command.SCREEN, CMD_REFRESH));
        
        loadOrFetchData();
    }

    private void loadOrFetchData() {
        Account acc = Accounts.getActive();
        Vector cachedChannels = acc.channelCache.get(guild.id);

        if (cachedChannels != null) {
            populate(cachedChannels);
        }
        else {
            fetchData();
        }
    }

    private void fetchData() {
        HTTPThread h = new HTTPThread(HTTPThread.ACTION_FETCH_GUILD_CHANNELS, this);
        h.guild = guild;
        h.start();
    }

    public void commandAction(Command c, Displayable d) {
        switch (c.getPriority()) {
            case CMD_BACK: {
                App.popScreen();
                break;
            }
            case CMD_REFRESH: {
                fetchData();
                break;
            }
        }
    }

    public void itemSelected(Item i) {
        if (i instanceof ListItem) {
            ListItem li = (ListItem) i;
            Channel channel = (Channel) li.object;
            App.pushScreen(new ChatScreen(channel));
        }
    }

    public void populate(Vector categories) {
        removeAllItems();

        for (int i = 0; i < categories.size(); i++) {
            ChannelCategory category = (ChannelCategory) categories.elementAt(i);

            if (category.name != null) {
                addItem(new ChannelCategoryItem(category.name));
                addItem(new BlankItem(0));
            }
            
            for (int j = 0; j < category.children.size(); j++) {
                Channel channel = (Channel) category.children.elementAt(j);

                addItem(new ListItem(channel.name, channel));
            }
        }
    }

    public void showError() {
        removeAllItems();
        addItem(new FailedToLoadItem());
    }
}