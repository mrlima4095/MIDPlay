package midplay;

import java.util.Vector;

/**
 * Global registry of embedded subapps (MPGram, GitHub, Discord, OpenTTY and Pinterest).
 *
 * <p>The hidden htop screen uses this registry to show which subapps still hold memory and to kill
 * them individually or all at once. A kill must release the subapp state without changing the
 * current screen, because the user is looking at htop when the button is pressed.
 */
public final class SubAppManager {
  public interface SubApp {
    /** Language key with the subapp display name. */
    String getNameKey();

    /** True while the subapp owns resources in this process. */
    boolean isRunning();

    /** Releases the subapp resources. Implementations must not navigate the host. */
    void kill();
  }

  private static final Vector apps = new Vector();

  private SubAppManager() {}

  public static void register(SubApp app) {
    if (app != null) {
      apps.addElement(app);
    }
  }

  public static SubApp[] getApps() {
    SubApp[] result = new SubApp[apps.size()];
    apps.copyInto(result);
    return result;
  }

  public static int killAll() {
    int killed = 0;
    for (int i = 0; i < apps.size(); i++) {
      SubApp app = (SubApp) apps.elementAt(i);
      if (!app.isRunning()) {
        continue;
      }
      try {
        app.kill();
        killed++;
      } catch (Throwable ignored) {
      }
    }
    return killed;
  }
}
