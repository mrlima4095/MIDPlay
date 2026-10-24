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
import midplay.net.RedditImageDownloadOperation;
import midplay.store.RedditFavoritesManager;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.ui.Sharing;
import midplay.util.Lang;
import midplay.util.Utils;

/** Details of one Reddit post, with its image, links to the author/subreddit and its comments. */
public final class RedditPostScreen extends BaseForm {
  private final String[] post;
  private final ImageItem imageItem = new ImageItem(null, null, ImageItem.LAYOUT_CENTER, null);
  private final StringItem content = new StringItem(null, "");
  private final Command imageCommand;
  private final Command commentsCommand;
  private final Command subredditCommand;
  private final Command authorCommand;
  private final Command saveCommand;
  private final Command removeSavedCommand;
  private final Command shareCommand;
  private final Command downloadCommand;
  private boolean loading;

  public RedditPostScreen(Navigator navigator, String[] post) {
    super(Lang.tr("reddit.post"), navigator);
    this.post = post == null ? new String[RedditApi.POST_SIZE] : post;
    for (int i = 0; i < this.post.length; i++) {
      if (this.post[i] == null) {
        this.post[i] = "";
      }
    }
    append(imageItem);
    content.setText(RedditApi.postDetails(this.post));
    append(content);
    imageCommand = new Command(Lang.tr("reddit.view_image"), Command.SCREEN, 1);
    commentsCommand = new Command(Lang.tr("reddit.comments"), Command.SCREEN, 2);
    subredditCommand = new Command(Lang.tr("reddit.subreddit"), Command.SCREEN, 3);
    authorCommand = new Command(Lang.tr("reddit.author"), Command.SCREEN, 4);
    saveCommand = new Command(Lang.tr("reddit.save"), Command.SCREEN, 5);
    removeSavedCommand = new Command(Lang.tr("reddit.remove_saved"), Command.SCREEN, 5);
    shareCommand = new Command(Lang.tr("reddit.share"), Command.SCREEN, 6);
    downloadCommand = new Command(Lang.tr("reddit.download"), Command.SCREEN, 7);
    if (imageUrl().length() > 0) {
      addCommand(imageCommand);
      addCommand(downloadCommand);
    }
    if (this.post[RedditApi.POST_ID].length() > 0) {
      addCommand(commentsCommand);
      addCommand(shareCommand);
      updateSaveCommands();
    }
    if (this.post[RedditApi.POST_SUBREDDIT].length() > 0) {
      addCommand(subredditCommand);
    }
    if (this.post[RedditApi.POST_AUTHOR].length() > 0) {
      addCommand(authorCommand);
    }
    addCommand(Commands.playerNowPlaying());
    loadImage();
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == Commands.playerNowPlaying()) {
      PlayerNavHelper.showNowPlaying(navigator);
    } else if (c == imageCommand) {
      navigator.forward(new PinterestImageViewer(navigator, Lang.tr("reddit.post"), imageUrl()));
    } else if (c == commentsCommand) {
      navigator.forward(
          new RedditCommentsScreen(
              navigator, post[RedditApi.POST_ID], post[RedditApi.POST_TITLE]));
    } else if (c == subredditCommand) {
      openSubreddit();
    } else if (c == authorCommand) {
      openUser();
    } else if (c == saveCommand) {
      save();
    } else if (c == removeSavedCommand) {
      RedditFavoritesManager.getInstance().removePost(post[RedditApi.POST_ID]);
      updateSaveCommands();
      navigator.showAlert(Lang.tr("reddit.removed_saved"), AlertType.CONFIRMATION);
    } else if (c == shareCommand) {
      Sharing.toMPGram(this, RedditApi.postLabel(post) + "\n" + RedditApi.postWebUrl(post));
    } else if (c == downloadCommand) {
      downloadPost();
    }
  }

  private void updateSaveCommands() {
    if (RedditFavoritesManager.getInstance().isSaved(post[RedditApi.POST_ID])) {
      removeCommand(saveCommand);
      addCommand(removeSavedCommand);
    } else {
      removeCommand(removeSavedCommand);
      addCommand(saveCommand);
    }
  }

  private void save() {
    int result = RedditFavoritesManager.getInstance().savePost(post);
    updateSaveCommands();
    if (result == RedditFavoritesManager.ALREADY_EXISTS) {
      navigator.showAlert(Lang.tr("reddit.already_saved"), AlertType.INFO);
    } else if (result == RedditFavoritesManager.SUCCESS) {
      navigator.showAlert(Lang.tr("reddit.saved_post"), AlertType.CONFIRMATION);
    } else {
      navigator.showAlert(Lang.tr("reddit.save_error"), AlertType.ERROR);
    }
  }

  private void openSubreddit() {
    final String subreddit = post[RedditApi.POST_SUBREDDIT];
    if (loading || subreddit.length() == 0) {
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
    MIDPlay.startOperation(
        new HttpTextOperation(
            RedditApi.subPostsUrl(subreddit, "new", ""),
            new HttpTextOperation.Listener() {
              public void onText(final String response) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        showPosts(
                            response,
                            RedditResultsScreen.SOURCE_SUBPOSTS,
                            subreddit,
                            "",
                            "new",
                            Lang.tr("reddit.results_posts", "r/" + subreddit));
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

  private void openUser() {
    final String author = post[RedditApi.POST_AUTHOR];
    if (loading || author.length() == 0) {
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
    MIDPlay.startOperation(
        new HttpTextOperation(
            RedditApi.userPostsUrl(author, ""),
            new HttpTextOperation.Listener() {
              public void onText(final String response) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        showPosts(
                            response,
                            RedditResultsScreen.SOURCE_USER_POSTS,
                            author,
                            "",
                            "",
                            Lang.tr("reddit.results_user_posts", author));
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

  private void showPosts(
      String response,
      int source,
      String id,
      String query,
      String sort,
      String title) {
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
            navigator, source, id, query, sort, title, posts, RedditApi.parseBookmark(response)));
  }

  private void downloadPost() {
    if (loading) {
      return;
    }
    final String source = imageUrl();
    if (source.length() == 0) {
      navigator.showAlert(Lang.tr("reddit.download_error"), AlertType.ERROR);
      return;
    }
    loading = true;
    navigator.showLoadingAlert(
        Lang.tr("reddit.downloading"),
        new Runnable() {
          public void run() {
            loading = false;
          }
        });
    MIDPlay.startOperation(
        new RedditImageDownloadOperation(
            source,
            "reddit_" + post[RedditApi.POST_ID] + ".jpg",
            new RedditImageDownloadOperation.Listener() {
              public void onDownloaded(final String path) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        navigator.showAlert(
                            Lang.tr("reddit.downloaded", path), AlertType.CONFIRMATION);
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        navigator.showAlert(Lang.tr("reddit.download_error"), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private String imageUrl() {
    String thumb = post[RedditApi.POST_THUMB];
    return thumb == null ? "" : thumb;
  }

  private void loadImage() {
    String url = imageUrl();
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
                // The text details are still useful when the image server is unavailable.
              }
            }));
  }
}
