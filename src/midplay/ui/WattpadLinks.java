package midplay.ui;

import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import midplay.MIDPlay;
import midplay.net.HttpTextOperation;
import midplay.net.WattpadApi;
import midplay.store.Configuration;
import midplay.store.SettingsManager;
import midplay.store.WattpadDownloadManager;
import midplay.ui.screen.WattpadAuthorScreen;
import midplay.ui.screen.WattpadReaderScreen;
import midplay.ui.screen.WattpadResultsScreen;
import midplay.ui.screen.WattpadScreen;
import midplay.ui.screen.WattpadStoryScreen;
import midplay.util.Lang;

/**
 * Opens Wattpad links inside the app instead of sending them to the system browser. Handles shared
 * stories, single chapters, user profiles and reading lists, and is used both by the "Open..." screen
 * and by links clicked in MPGram.
 */
public final class WattpadLinks {
  private static final int MAX_STORY_BYTES = 400 * 1024;

  private WattpadLinks() {}

  /** True when the URL points at wattpad.com. */
  public static boolean isWattpad(String url) {
    return path(url) != null;
  }

  /** Opens the link in the matching Wattpad screen. */
  public static void open(final Navigator navigator, String url) {
    String path = path(url);
    if (path == null) {
      error(navigator);
      return;
    }
    String[] segment = segments(path);
    if (segment.length == 0) {
      // A bare wattpad.com link opens the Wattpad start screen.
      navigator.forward(new WattpadScreen(navigator));
      return;
    }
    int start = segment[0].equalsIgnoreCase("amp") && segment.length > 1 ? 1 : 0;
    String first = segment[start].toLowerCase();
    if (first.equals("user") || first.equals("users")) {
      if (segment.length > start + 1) {
        openUser(navigator, segment[start + 1]);
      } else {
        error(navigator);
      }
      return;
    }
    if (first.equals("story") || first.equals("stories")) {
      if (segment.length > start + 1 && leadingDigits(segment[start + 1]).length() > 0) {
        openStory(navigator, leadingDigits(segment[start + 1]));
      } else {
        error(navigator);
      }
      return;
    }
    if (first.equals("list") || first.equals("lists")) {
      if (segment.length > start + 1 && leadingDigits(segment[start + 1]).length() > 0) {
        openList(navigator, leadingDigits(segment[start + 1]));
      } else {
        error(navigator);
      }
      return;
    }
    // wattpad.com/<partId>-chapter-title
    String partId = leadingDigits(segment[start]);
    if (partId.length() > 0) {
      openPart(navigator, partId);
      return;
    }
    error(navigator);
  }

  /** Path of a wattpad.com URL ("/story/123-title"), or null for any other host. */
  private static String path(String url) {
    if (url == null) {
      return null;
    }
    String value = url.trim();
    int scheme = value.indexOf("://");
    if (scheme >= 0) {
      String protocol = value.substring(0, scheme).toLowerCase();
      if (!protocol.equals("http") && !protocol.equals("https")) {
        return null;
      }
      value = value.substring(scheme + 3);
    }
    int slash = value.indexOf('/');
    String host = slash < 0 ? value : value.substring(0, slash);
    String rest = slash < 0 ? "/" : value.substring(slash);
    String lowerHost = host.toLowerCase();
    if (lowerHost.startsWith("www.")) {
      lowerHost = lowerHost.substring(4);
    }
    if (!lowerHost.equals("wattpad.com") && !lowerHost.endsWith(".wattpad.com")) {
      return null;
    }
    int cut = rest.indexOf('?');
    if (cut >= 0) {
      rest = rest.substring(0, cut);
    }
    cut = rest.indexOf('#');
    if (cut >= 0) {
      rest = rest.substring(0, cut);
    }
    return rest;
  }

  private static String[] segments(String path) {
    Vector parts = new Vector();
    int start = 0;
    for (int i = 0; i <= path.length(); i++) {
      if (i == path.length() || path.charAt(i) == '/') {
        String part = path.substring(start, i);
        if (part.length() > 0) {
          parts.addElement(part);
        }
        start = i + 1;
      }
    }
    String[] result = new String[parts.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = (String) parts.elementAt(i);
    }
    return result;
  }

  private static String leadingDigits(String value) {
    int i = 0;
    while (i < value.length() && value.charAt(i) >= '0' && value.charAt(i) <= '9') {
      i++;
    }
    return value.substring(0, i);
  }

  private static void openStory(final Navigator navigator, final String storyId) {
    Vector local = WattpadDownloadManager.getInstance().getLocalParts(storyId);
    if (local != null && local.size() > 0) {
      navigator.forward(
          new WattpadStoryScreen(
              navigator,
              storyId,
              WattpadDownloadManager.getInstance().getLocalTitle(storyId),
              local));
      return;
    }
    navigator.showLoadingAlert(Lang.tr("wattpad.loading"));
    MIDPlay.startOperation(
        new HttpTextOperation(
            WattpadApi.storyUrl(storyId),
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        Vector parts = WattpadApi.parseParts(text);
                        if (parts.size() == 0) {
                          navigator.dismissAlert();
                          error(navigator);
                          return;
                        }
                        navigator.forward(new WattpadStoryScreen(navigator, storyId, text));
                      }
                    });
              }

              public void onError(Exception e) {
                fail(navigator);
              }
            },
            MAX_STORY_BYTES));
  }

  private static void openPart(final Navigator navigator, final String partId) {
    // A chapter that is already on the card can be opened offline; no story lookup is needed.
    String localStory = WattpadDownloadManager.getInstance().findStoryForPart(partId);
    if (localStory != null && localStory.length() > 0) {
      Vector localParts = WattpadDownloadManager.getInstance().getLocalParts(localStory);
      if (localParts != null && localParts.size() > 0) {
        showChapter(
            navigator,
            partId,
            localStory,
            WattpadDownloadManager.getInstance().getLocalTitle(localStory),
            localParts);
        return;
      }
    }
    navigator.showLoadingAlert(Lang.tr("wattpad.loading_chapter"));
    MIDPlay.startOperation(
        new HttpTextOperation(
            WattpadApi.partInfoUrl(partId),
            new HttpTextOperation.Listener() {
              public void onText(final String info) {
                final String storyId = WattpadApi.parsePartStoryId(info);
                if (storyId.length() == 0) {
                  fail(navigator);
                  return;
                }
                MIDPlay.startOperation(
                    new HttpTextOperation(
                        WattpadApi.storyUrl(storyId),
                        new HttpTextOperation.Listener() {
                          public void onText(final String story) {
                            navigator.callSerially(
                                new Runnable() {
                                  public void run() {
                                    showChapter(
                                        navigator,
                                        partId,
                                        storyId,
                                        WattpadApi.parseTitle(story),
                                        WattpadApi.parseParts(story));
                                  }
                                });
                          }

                          public void onError(Exception e) {
                            fail(navigator);
                          }
                        },
                        MAX_STORY_BYTES));
              }

              public void onError(Exception e) {
                fail(navigator);
              }
            }));
  }

  private static void showChapter(
      Navigator navigator, String partId, String storyId, String title, Vector parts) {
    if (parts.size() == 0) {
      navigator.dismissAlert();
      error(navigator);
      return;
    }
    int index = 0;
    for (int i = 0; i < parts.size(); i++) {
      if (partId.equals(((String[]) parts.elementAt(i))[WattpadApi.PART_ID])) {
        index = i;
        break;
      }
    }
    navigator.forward(new WattpadReaderScreen(navigator, storyId, title, parts, index));
  }

  private static void openUser(final Navigator navigator, final String username) {
    navigator.forward(new WattpadAuthorScreen(navigator, username));
  }

  private static void openList(final Navigator navigator, final String listId) {
    navigator.showLoadingAlert(Lang.tr("wattpad.loading"));
    MIDPlay.startOperation(
        new HttpTextOperation(
            WattpadApi.listInfoUrl(listId),
            new HttpTextOperation.Listener() {
              public void onText(final String info) {
                final String name = WattpadApi.parseListName(info);
                MIDPlay.startOperation(
                    new HttpTextOperation(
                        WattpadApi.listStoriesUrl(listId, 0),
                        new HttpTextOperation.Listener() {
                          public void onText(final String text) {
                            navigator.callSerially(
                                new Runnable() {
                                  public void run() {
                                    showList(navigator, listId, name, text);
                                  }
                                });
                          }

                          public void onError(Exception e) {
                            fail(navigator);
                          }
                        }));
              }

              public void onError(Exception e) {
                fail(navigator);
              }
            }));
  }

  private static void showList(
      Navigator navigator, String listId, String name, String text) {
    Vector stories = WattpadApi.parseStories(text, includeMature());
    if (stories.size() == 0) {
      navigator.dismissAlert();
      navigator.showAlert(Lang.tr("wattpad.empty"), AlertType.INFO);
      return;
    }
    String title = name.length() == 0 ? Lang.tr("wattpad.list") : name;
    int rawCount = WattpadApi.countStories(text);
    boolean hasMore = rawCount >= WattpadApi.getSearchLimit();
    navigator.forward(
        new WattpadResultsScreen(
            navigator,
            WattpadResultsScreen.SOURCE_LIST,
            listId,
            title,
            stories,
            hasMore,
            rawCount));
  }

  private static boolean includeMature() {
    return SettingsManager.getInstance().getCurrentWattpadMature() == Configuration.WATTPAD_MATURE_ON;
  }

  private static void fail(final Navigator navigator) {
    navigator.callSerially(
        new Runnable() {
          public void run() {
            navigator.dismissAlert();
            error(navigator);
          }
        });
  }

  private static void error(Navigator navigator) {
    navigator.showAlert(Lang.tr("wattpad.link_error"), AlertType.ERROR);
  }
}
