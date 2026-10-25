package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.ImageItem;
import javax.microedition.lcdui.StringItem;
import midplay.MIDPlay;
import midplay.model.RssArticle;
import midplay.model.RssItem;
import midplay.model.RssSubscription;
import midplay.net.BinaryImageLoadOperation;
import midplay.net.HttpTextOperation;
import midplay.net.RssApi;
import midplay.ui.BaseForm;
import midplay.ui.Navigator;
import midplay.ui.Sharing;
import midplay.util.Lang;
import midplay.util.Utils;

/**
 * Article body of one feed entry. The proxy supplies the text the feed itself published; when a
 * feed only carries a teaser the remaining text is one command away in the system browser.
 */
public final class RssArticleScreen extends BaseForm {
  private final RssSubscription subscription;
  private final RssItem item;
  private final StringItem titleItem;
  private final StringItem metaItem;
  private final StringItem contentItem;
  private final ImageItem imageItem = new ImageItem(null, null, ImageItem.LAYOUT_CENTER, null);
  private final Command browserCommand;
  private final Command shareCommand;
  private boolean loaded;
  private boolean loading;
  private boolean imageAppended;
  private RssArticle article;

  public RssArticleScreen(Navigator navigator, RssSubscription subscription, RssItem item) {
    super(subscription == null ? Lang.tr("rss.article") : subscription.displayTitle(), navigator);
    this.subscription = subscription;
    this.item = item;
    titleItem = new StringItem(null, item.title);
    metaItem = new StringItem(null, metaText());
    contentItem =
        new StringItem(
            null, item.summary.length() > 0 ? item.summary : Lang.tr("rss.loading_article"));
    append(titleItem);
    append(metaItem);
    if (item.image.length() > 0) {
      append(imageItem);
      imageAppended = true;
    }
    append(contentItem);
    browserCommand = new Command(Lang.tr("rss.open_browser"), Command.SCREEN, 1);
    shareCommand = new Command(Lang.tr("rss.share"), Command.SCREEN, 2);
    addCommand(browserCommand);
    addCommand(shareCommand);
    addCommand(midplay.ui.Commands.playerNowPlaying());
  }

  protected void showNotify() {
    if (!loaded && !loading) {
      loadArticle();
    }
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == browserCommand) {
      openInBrowser();
    } else if (c == shareCommand) {
      share();
    } else if (c == midplay.ui.Commands.playerNowPlaying()) {
      midplay.ui.PlayerNavHelper.showNowPlaying(navigator);
    }
  }

  private String metaText() {
    StringBuffer text = new StringBuffer();
    String date = RssApi.formatTimestamp(item.timestamp);
    if (date.length() > 0) {
      text.append(date);
    }
    if (item.author.length() > 0) {
      if (text.length() > 0) {
        text.append(" · ");
      }
      text.append(Lang.tr("rss.by", item.author));
    }
    return text.toString();
  }

  private void loadArticle() {
    if (subscription == null || item.id.length() == 0) {
      loaded = true;
      return;
    }
    loading = true;
    MIDPlay.startOperation(
        new HttpTextOperation(
            RssApi.articleUrl(subscription.url, item.id),
            new HttpTextOperation.Listener() {
              public void onText(final String response) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        finishArticle(response);
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        loaded = true;
                        if (item.summary.length() == 0) {
                          contentItem.setText(Lang.tr("rss.article_error"));
                        }
                        navigator.showAlert(Lang.tr("rss.article_error"), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private void finishArticle(String response) {
    loading = false;
    loaded = true;
    String error = RssApi.parseErrorCode(response);
    if (error.length() > 0) {
      if (item.summary.length() == 0) {
        contentItem.setText(RssApi.errorMessage(error));
      }
      navigator.showAlert(RssApi.errorMessage(error), AlertType.ERROR);
      return;
    }
    RssArticle parsed = RssApi.parseArticle(response);
    if (parsed == null) {
      if (item.summary.length() == 0) {
        contentItem.setText(Lang.tr("rss.article_error"));
      }
      return;
    }
    article = parsed;
    if (article.title.length() > 0) {
      titleItem.setText(article.title);
    }
    metaItem.setText(metaText(article.timestamp, article.author));
    if (article.content.length() > 0) {
      contentItem.setText(article.content);
    } else if (item.summary.length() > 0) {
      contentItem.setText(item.summary + "\n\n" + Lang.tr("rss.no_content"));
    } else {
      contentItem.setText(Lang.tr("rss.no_content"));
    }
    loadImage();
  }

  private String metaText(long timestamp, String author) {
    StringBuffer text = new StringBuffer();
    String date = RssApi.formatTimestamp(timestamp > 0 ? timestamp : item.timestamp);
    if (date.length() > 0) {
      text.append(date);
    }
    String name = author.length() > 0 ? author : item.author;
    if (name.length() > 0) {
      if (text.length() > 0) {
        text.append(" · ");
      }
      text.append(Lang.tr("rss.by", name));
    }
    return text.toString();
  }

  private void loadImage() {
    String url = article != null && article.image.length() > 0 ? article.image : item.image;
    if (url.length() == 0) {
      return;
    }
    if (!imageAppended) {
      insert(2, imageItem);
      imageAppended = true;
    }
    MIDPlay.startOperation(
        new BinaryImageLoadOperation(
            RssApi.imageUrl(url),
            new BinaryImageLoadOperation.Listener() {
              public void onImageLoaded(final Image image) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        imageItem.setImage(Utils.resizeImageToFitInside(image, 190, 220));
                      }
                    });
              }

              public void onImageLoadError(Exception e) {}
            }));
  }

  private void openInBrowser() {
    String link = article != null && article.link.length() > 0 ? article.link : item.link;
    if (link.length() == 0) {
      navigator.showAlert(Lang.tr("rss.no_link"), AlertType.INFO);
      return;
    }
    try {
      MIDPlay.getInstance().openUri(link);
    } catch (Exception e) {
      navigator.showAlert(Lang.tr("rss.open_failed"), AlertType.ERROR);
    }
  }

  private void share() {
    String title = article != null && article.title.length() > 0 ? article.title : item.title;
    String link = article != null && article.link.length() > 0 ? article.link : item.link;
    String text = link.length() > 0 ? title + "\n" + link : title;
    Sharing.toMPGram(this, text);
  }
}
