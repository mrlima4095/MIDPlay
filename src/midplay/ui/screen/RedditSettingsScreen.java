package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextField;
import javax.microedition.rms.RecordStoreException;
import midplay.store.Configuration;
import midplay.store.SettingsManager;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;

/** Reddit-only settings: proxy URLs, page size and the photo download folder. */
public final class RedditSettingsScreen extends BaseForm {
  private final SettingsManager settings = SettingsManager.getInstance();
  private final TextField apiUrlField;
  private final TextField imageProxyField;
  private final TextField postsField;
  private final TextField pathField;

  private final String savedApiUrl;
  private final String savedImageProxy;
  private final int savedPosts;
  private final String savedPath;

  public RedditSettingsScreen(Navigator navigator) {
    super(Lang.tr("reddit.settings_title"), navigator);
    savedApiUrl = settings.getRedditApiUrl();
    savedImageProxy = settings.getRedditImageProxy();
    savedPosts = settings.getRedditPostsPerPage();
    savedPath = settings.getRedditPath();

    apiUrlField =
        new TextField(Lang.tr("reddit.settings_api_url"), savedApiUrl, 256, TextField.URL);
    append(apiUrlField);

    imageProxyField =
        new TextField(
            Lang.tr("reddit.settings_image_proxy"), savedImageProxy, 256, TextField.URL);
    append(imageProxyField);

    postsField =
        new TextField(
            Lang.tr("reddit.settings_posts_per_page"),
            Integer.toString(savedPosts),
            2,
            TextField.NUMERIC);
    append(postsField);

    pathField =
        new TextField(Lang.tr("reddit.settings_path"), savedPath, 256, TextField.URL);
    append(pathField);

    addCommand(Commands.formSave());
    addCommand(Commands.playerNowPlaying());
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == Commands.formSave()) {
      save();
    } else if (c == Commands.playerNowPlaying()) {
      PlayerNavHelper.showNowPlaying(navigator);
    }
  }

  protected boolean onBackPressed() {
    if (!isDirty()) {
      return false;
    }
    navigator.showConfirmationAlert(
        Lang.tr("confirm.discard_changes"),
        new Runnable() {
          public void run() {
            navigator.back();
          }
        },
        AlertType.WARNING);
    return true;
  }

  private boolean isDirty() {
    return !savedApiUrl.equals(apiUrlField.getString().trim())
        || !savedImageProxy.equals(imageProxyField.getString().trim())
        || savedPosts != parsePosts(postsField.getString())
        || !savedPath.equals(pathField.getString().trim());
  }

  private void save() {
    String apiUrl = apiUrlField.getString().trim();
    String imageProxy = imageProxyField.getString().trim();
    int posts = parsePosts(postsField.getString());
    String path = pathField.getString().trim();
    try {
      if (!apiUrl.equals(savedApiUrl)) {
        settings.saveRedditApiUrl(apiUrl);
      }
      if (!imageProxy.equals(savedImageProxy)) {
        settings.saveRedditImageProxy(imageProxy);
      }
      if (posts != savedPosts) {
        settings.saveRedditPostsPerPage(posts);
      }
      if (!path.equals(savedPath)) {
        settings.saveRedditPath(path);
      }
      navigator.back();
    } catch (RecordStoreException e) {
      navigator.showAlert(Lang.tr("settings.error.save_failed"), AlertType.ERROR);
    }
  }

  private static int parsePosts(String value) {
    try {
      int parsed = Integer.parseInt(value.trim());
      if (parsed < Configuration.REDDIT_POSTS_PER_PAGE_MIN) {
        return Configuration.REDDIT_POSTS_PER_PAGE_MIN;
      }
      if (parsed > Configuration.REDDIT_POSTS_PER_PAGE_MAX) {
        return Configuration.REDDIT_POSTS_PER_PAGE_MAX;
      }
      return parsed;
    } catch (Exception e) {
      return Configuration.DEFAULT_REDDIT_POSTS_PER_PAGE;
    }
  }
}
