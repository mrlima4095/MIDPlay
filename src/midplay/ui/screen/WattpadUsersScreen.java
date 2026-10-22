package midplay.ui.screen;

import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import midplay.MIDPlay;
import midplay.net.HttpTextOperation;
import midplay.net.WattpadApi;
import midplay.ui.BaseList;
import midplay.ui.Navigator;
import midplay.util.Lang;

/** Lists Wattpad users found by a search; selecting one loads the stories they published. */
public final class WattpadUsersScreen extends BaseList {
  private final String query;
  private final Vector users;
  private final Command moreCommand;
  private final Hashtable userIds = new Hashtable();
  private boolean hasMore;
  private boolean loading;
  private int offset;

  public WattpadUsersScreen(Navigator navigator, String query, Vector users) {
    this(navigator, query, users, true, users.size());
  }

  public WattpadUsersScreen(Navigator navigator, String query, Vector users, boolean hasMore) {
    this(navigator, query, users, hasMore, users.size());
  }

  public WattpadUsersScreen(
      Navigator navigator, String query, Vector users, boolean hasMore, int offset) {
    super(Lang.tr("wattpad.users"), navigator);
    this.query = query;
    this.users = users;
    this.hasMore = hasMore;
    this.offset = offset;
    moreCommand = new Command(Lang.tr("wattpad.more"), Command.SCREEN, 1);
    for (int i = 0; i < users.size(); i++) {
      String username = ((String[]) users.elementAt(i))[WattpadApi.USER_NAME];
      userIds.put(username, username);
    }
    if (hasMore) {
      addCommand(moreCommand);
    }
    refresh();
  }

  protected void populateItems() {
    for (int i = 0; i < users.size(); i++) {
      append(labelFor((String[]) users.elementAt(i)), null);
    }
  }

  /** Row label: "username - Display name". */
  private static String labelFor(String[] user) {
    String username = user[WattpadApi.USER_NAME];
    String display = user[WattpadApi.USER_DISPLAY];
    if (display == null || display.length() == 0) {
      return username;
    }
    return username + " - " + display;
  }

  protected void handleSelection() {
    int index = getSelectedIndex();
    if (index < 0 || index >= users.size() || loading) {
      return;
    }
    navigator.forward(
        new WattpadAuthorScreen(
            navigator, ((String[]) users.elementAt(index))[WattpadApi.USER_NAME]));
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == moreCommand) {
      loadMore();
    }
  }

  private void loadMore() {
    if (loading || !hasMore) {
      return;
    }
    loading = true;
    navigator.showLoadingAlert(
        Lang.tr("status.loading"),
        new Runnable() {
          public void run() {
            loading = false;
          }
        });
    MIDPlay.startOperation(
        new HttpTextOperation(
            WattpadApi.usersUrl(query, offset),
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        appendMore(text);
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        navigator.showAlert(e.toString(), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private void appendMore(String text) {
    Vector more = WattpadApi.parseUsers(text);
    int rawCount = WattpadApi.countUsers(text);
    offset += rawCount;
    boolean end = rawCount < WattpadApi.getSearchLimit();
    int added = 0;
    for (int i = 0; i < more.size(); i++) {
      String[] user = (String[]) more.elementAt(i);
      String username = user[WattpadApi.USER_NAME];
      if (userIds.containsKey(username)) {
        continue;
      }
      userIds.put(username, username);
      users.addElement(user);
      added++;
    }
    if (end) {
      hasMore = false;
      removeCommand(moreCommand);
    }
    if (added > 0) {
      refresh();
    }
    if (end) {
      navigator.showAlert(Lang.tr("wattpad.end_of_results"), AlertType.INFO);
    }
  }
}
