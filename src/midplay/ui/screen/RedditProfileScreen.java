package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.ImageItem;
import javax.microedition.lcdui.StringItem;
import midplay.MIDPlay;
import midplay.net.BinaryImageLoadOperation;
import midplay.net.HttpTextOperation;
import midplay.net.RedditApi;
import midplay.store.RedditFavoritesManager;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.DateTime;
import midplay.util.Lang;
import midplay.util.Utils;

/**
 * Profile of a Reddit user or subreddit: picture, bio and stats, with a shortcut to the posts. User
 * profiles come from Reddit's {@code u_<name>} pseudo-subreddit, which carries the bio and avatar.
 */
public final class RedditProfileScreen extends BaseForm {
  private final boolean user;
  private final String name;
  private final String[] profile;
  private final ImageItem imageItem = new ImageItem(null, null, ImageItem.LAYOUT_CENTER, null);
  private final Command postsCommand;
  private final Command followCommand;
  private final Command unfollowCommand;
  private boolean loading;

  private RedditProfileScreen(
      Navigator navigator, boolean user, String name, String[] profile) {
    super((user ? "u/" : "r/") + name, navigator);
    this.user = user;
    this.name = name;
    this.profile = profile == null ? new String[RedditApi.PROFILE_SIZE] : profile;
    for (int i = 0; i < this.profile.length; i++) {
      if (this.profile[i] == null) {
        this.profile[i] = "";
      }
    }

    if (iconUrl().length() > 0) {
      append(imageItem);
    }
    String details = detailsText();
    if (details.length() > 0) {
      append(new StringItem(null, details));
    }

    postsCommand = new Command(Lang.tr("reddit.view_posts"), Command.SCREEN, 1);
    followCommand =
        new Command(
            Lang.tr(user ? "reddit.follow_user" : "reddit.follow_subreddit"),
            Command.SCREEN,
            2);
    unfollowCommand =
        new Command(
            Lang.tr(user ? "reddit.unfollow_user" : "reddit.unfollow_subreddit"),
            Command.SCREEN,
            2);
    addCommand(postsCommand);
    updateFollowCommands();
    addCommand(Commands.playerNowPlaying());
    loadImage();
  }

  /** Loads the profile of a user ({@code user = true}) or subreddit and opens it. */
  public static void open(final Navigator navigator, final boolean user, String rawName) {
    final String name = rawName == null ? "" : rawName.trim();
    if (name.length() == 0) {
      navigator.showAlert(Lang.tr("reddit.profile_error"), AlertType.ERROR);
      return;
    }
    navigator.showLoadingAlert(Lang.tr("reddit.profile_loading"));
    MIDPlay.startOperation(
        new HttpTextOperation(
            user ? RedditApi.userProfileUrl(name) : RedditApi.subredditProfileUrl(name),
            new HttpTextOperation.Listener() {
              public void onText(final String response) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        String[] data = RedditApi.parseProfile(response);
                        if (data == null) {
                          navigator.showAlert(Lang.tr("reddit.profile_error"), AlertType.ERROR);
                          return;
                        }
                        navigator.forward(new RedditProfileScreen(navigator, user, name, data));
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        navigator.showAlert(Lang.tr("reddit.profile_error"), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private String detailsText() {
    StringBuffer sb = new StringBuffer();
    String display = profile[RedditApi.PROFILE_DISPLAY_NAME].trim();
    if (display.length() > 0 && !display.equalsIgnoreCase(name)) {
      sb.append(display).append('\n');
    }
    String bio = profile[RedditApi.PROFILE_BIO].trim();
    if (bio.length() > 0) {
      sb.append(bio).append('\n');
    }
    if (!user) {
      appendLine(sb, Lang.tr("reddit.members"), profile[RedditApi.PROFILE_SUBSCRIBERS]);
      appendDate(sb, Lang.tr("reddit.profile_created"), profile[RedditApi.PROFILE_CREATED]);
    } else {
      appendLine(sb, Lang.tr("reddit.karma"), profile[RedditApi.PROFILE_KARMA]);
      appendLine(sb, Lang.tr("reddit.post_count"), profile[RedditApi.PROFILE_POSTS]);
      appendLine(sb, Lang.tr("reddit.comment_count"), profile[RedditApi.PROFILE_COMMENTS]);
      appendDate(
          sb, Lang.tr("reddit.profile_first_activity"), profile[RedditApi.PROFILE_FIRST_ACTIVITY]);
      appendDate(
          sb, Lang.tr("reddit.profile_last_activity"), profile[RedditApi.PROFILE_LAST_ACTIVITY]);
    }
    if (RedditApi.isNsfw(profile[RedditApi.PROFILE_NSFW])) {
      sb.append(Lang.tr("reddit.nsfw")).append(": 18+\n");
    }
    return sb.toString().trim();
  }

  private void appendDate(StringBuffer sb, String label, String seconds) {
    long value = seconds(seconds);
    if (value > 0) {
      appendLine(sb, label, DateTime.formatDate(value * 1000L));
    }
  }

  private void loadImage() {
    String url = iconUrl();
    if (url.length() == 0) {
      return;
    }
    MIDPlay.startOperation(
        new BinaryImageLoadOperation(
            RedditApi.imageUrl(url),
            new BinaryImageLoadOperation.Listener() {
              public void onImageLoaded(final Image image) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        imageItem.setImage(Utils.resizeImageToFitInside(image, 180, 220));
                      }
                    });
              }

              public void onImageLoadError(Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        removeImageItem();
                      }
                    });
              }
            }));
  }

  private void removeImageItem() {
    try {
      if (size() > 0 && get(0) == imageItem) {
        delete(0);
      }
    } catch (Exception e) {
    }
  }

  private String iconUrl() {
    String url = profile[RedditApi.PROFILE_ICON];
    return url == null ? "" : url.trim();
  }

  private void updateFollowCommands() {
    boolean saved =
        user
            ? RedditFavoritesManager.getInstance().isUserSaved(name)
            : RedditFavoritesManager.getInstance().isSubredditSaved(name);
    if (saved) {
      removeCommand(followCommand);
      addCommand(unfollowCommand);
    } else {
      removeCommand(unfollowCommand);
      addCommand(followCommand);
    }
  }

  private void toggleFollow(boolean follow) {
    if (user) {
      if (follow) {
        RedditFavoritesManager.getInstance().saveUser(name);
      } else {
        RedditFavoritesManager.getInstance().removeUser(name);
      }
    } else {
      if (follow) {
        RedditFavoritesManager.getInstance().saveSubreddit(name);
      } else {
        RedditFavoritesManager.getInstance().removeSubreddit(name);
      }
    }
    updateFollowCommands();
    navigator.showAlert(
        Lang.tr(follow ? "reddit.followed" : "reddit.unfollowed"), AlertType.CONFIRMATION);
  }

  private void openPosts() {
    if (loading) {
      return;
    }
    loading = true;
    navigator.showLoadingAlert(
        Lang.tr("reddit.loading"),
        new Runnable() {
          public void run() {
            loading = false;
          }
        });
    final String url =
        user
            ? RedditApi.userPostsUrl(name, "")
            : RedditApi.subPostsUrl(name, "new", "");
    final int source =
        user ? RedditResultsScreen.SOURCE_USER_POSTS : RedditResultsScreen.SOURCE_SUBPOSTS;
    final String sort = user ? "" : "new";
    final String title =
        user
            ? Lang.tr("reddit.results_user_posts", name)
            : Lang.tr("reddit.results_posts", "r/" + name);
    MIDPlay.startOperation(
        new HttpTextOperation(
            url,
            new HttpTextOperation.Listener() {
              public void onText(final String response) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        String error = RedditApi.parseError(response);
                        if (error.length() > 0) {
                          navigator.showAlert(error, AlertType.ERROR);
                          return;
                        }
                        java.util.Vector posts = RedditApi.parsePosts(response);
                        if (posts.size() == 0) {
                          navigator.showAlert(Lang.tr("reddit.empty"), AlertType.INFO);
                          return;
                        }
                        navigator.forward(
                            new RedditResultsScreen(
                                navigator,
                                source,
                                name,
                                "",
                                sort,
                                title,
                                posts,
                                RedditApi.parseBookmark(response)));
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.showAlert(e.toString(), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private static long seconds(String value) {
    try {
      return Long.parseLong(value);
    } catch (Exception e) {
      return 0L;
    }
  }

  private static void appendLine(StringBuffer sb, String label, String value) {
    if (value == null || value.length() == 0 || "0".equals(value)) {
      return;
    }
    sb.append(label).append(": ").append(value).append('\n');
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == Commands.playerNowPlaying()) {
      PlayerNavHelper.showNowPlaying(navigator);
    } else if (c == postsCommand) {
      openPosts();
    } else if (c == followCommand || c == unfollowCommand) {
      toggleFollow(c == followCommand);
    }
  }
}
