package midplay;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import midplay.store.NotesManager;
import midplay.util.Lang;

/**
 * Connects the embedded Discord client to MIDPlay: notifications become MIDPlay
 * alerts, the copy-message screen can save text into the notepad, and Discord
 * screens can show the current MIDPlay track.
 */
public final class DiscordHostBridge implements discordj2me.HostBridge {
  private static boolean notificationShowing;

  public boolean showNotification(
      final String location,
      final String author,
      final String content,
      final String guildID,
      final String channelID) {
    final MIDPlay midlet = MIDPlay.getInstance();
    if (midlet == null) {
      return false;
    }
    final Display display = Display.getDisplay(midlet);
    final Displayable previous = display.getCurrent();
    if (previous instanceof DiscordNotificationForm || notificationShowing) {
      return true;
    }

    notificationShowing = true;
    try {
      discordj2me.GatewayThread.playNotificationSound();
    } catch (Throwable ignored) {
      // A missing sound backend must not hide the notification.
    }
    display.callSerially(
        new Runnable() {
          public void run() {
            if (display.getCurrent() instanceof DiscordNotificationForm) {
              notificationShowing = false;
              return;
            }
            display.setCurrent(
                new DiscordNotificationForm(
                    display, previous, location, author, content, guildID, channelID));
          }
        });
    return true;
  }

  public boolean saveNote(String text) {
    if (text == null || text.trim().length() == 0) {
      return false;
    }
    try {
      NotesManager.getInstance().create(text);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public String nowPlayingLabel() {
    return Lang.tr("menu.now_playing");
  }

  public void openNowPlaying() {
    MIDPlay midlet = MIDPlay.getInstance();
    if (midlet == null) {
      return;
    }
    // Leave Discord first so its screens stop owning the display and its
    // command does not fight with the host player.
    try {
      discordj2me.App.exit();
    } catch (Throwable ignored) {
      // The Discord app may not have been initialized yet.
    }
    midlet.openNowPlayingFromDiscord();
  }

  private static final class DiscordNotificationForm extends Form implements CommandListener {
    private final Display display;
    private final Displayable previous;
    private final String guildID;
    private final String channelID;
    private final Command openCommand;
    private final Command closeCommand;

    DiscordNotificationForm(
        Display display,
        Displayable previous,
        String location,
        String author,
        String content,
        String guildID,
        String channelID) {
      super(Lang.tr("discord.notification_title"));
      this.display = display;
      this.previous = previous;
      this.guildID = guildID;
      this.channelID = channelID;

      StringBuffer body = new StringBuffer();
      if (author != null && author.length() > 0) {
        body.append(author);
      }
      if (location != null && location.length() > 0) {
        if (body.length() > 0) {
          body.append(" · ");
        }
        body.append(location);
      }
      if (body.length() > 0) {
        body.append("\n\n");
      }
      body.append(content == null ? "" : content);
      append(body.toString());

      openCommand = new Command(Lang.tr("discord.open_in_discord"), Command.OK, 1);
      closeCommand = new Command(Lang.tr("discord.notification_close"), Command.BACK, 2);
      addCommand(openCommand);
      addCommand(closeCommand);
      setCommandListener(this);
    }

    public void commandAction(Command command, Displayable displayable) {
      notificationShowing = false;
      if (command == openCommand) {
        discordj2me.App.setHostBridge(DiscordHostBridge.getInstance());
        discordj2me.App.openNotification(
            MIDPlay.getInstance(),
            new Runnable() {
              public void run() {
                restore();
              }
            },
            guildID,
            channelID);
      } else if (command == closeCommand) {
        restore();
      }
    }

    private void restore() {
      if (previous != null) {
        display.setCurrent(previous);
      }
    }
  }

  private static final DiscordHostBridge INSTANCE = new DiscordHostBridge();

  /** Returns the shared bridge used by the embedded client. */
  public static DiscordHostBridge getInstance() {
    return INSTANCE;
  }
}
