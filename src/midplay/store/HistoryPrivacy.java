package midplay.store;

/**
 * Central check for anonymous mode, used by every history store so the setting is enforced no
 * matter which screen records or reads history.
 */
public final class HistoryPrivacy {
  public static boolean isAnonymous() {
    try {
      return SettingsManager.getInstance().isAnonymousModeEnabled();
    } catch (Throwable t) {
      return false;
    }
  }

  private HistoryPrivacy() {}
}
