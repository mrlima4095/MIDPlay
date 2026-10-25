package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import midplay.model.RssSubscription;
import midplay.store.RssSubscriptionManager;
import midplay.ui.BaseList;
import midplay.ui.Navigator;
import midplay.util.Lang;

/** A short list of feeds that work without any setup, offered on first use. */
public final class RssSuggestionsScreen extends BaseList {
  private static final String[] TITLES = {
    "G1",
    "BBC Brasil",
    "Tecnoblog",
    "Olhar Digital",
    "Hacker News",
    "The Verge",
    "Ars Technica",
    "Wikipedia"
  };

  private final RssScreen root;
  private final String[] urls;
  private final String[] sites;

  public RssSuggestionsScreen(Navigator navigator, RssScreen root) {
    super(Lang.tr("rss.suggestions_title"), navigator);
    this.root = root;
    String wikiLang = Lang.getCurrentLang();
    if (wikiLang == null || wikiLang.length() == 0) {
      wikiLang = "en";
    }
    urls = new String[] {
      "https://g1.globo.com/rss/g1/",
      "https://feeds.bbci.co.uk/portuguese/rss.xml",
      "https://tecnoblog.net/feed/",
      "https://olhardigital.com.br/feed/",
      "https://news.ycombinator.com/rss",
      "https://www.theverge.com/rss/index.xml",
      "https://feeds.arstechnica.com/arstechnica/index",
      "https://" + wikiLang + ".wikipedia.org/w/index.php?title=Special:RecentChanges&feed=atom"
    };
    sites = new String[] {
      "https://g1.globo.com/",
      "https://www.bbc.com/portuguese",
      "https://tecnoblog.net/",
      "https://olhardigital.com.br/",
      "https://news.ycombinator.com/",
      "https://www.theverge.com/",
      "https://arstechnica.com/",
      "https://" + wikiLang + ".wikipedia.org/"
    };
    populateItems();
  }

  protected void populateItems() {
    for (int i = 0; i < TITLES.length; i++) {
      String title = TITLES[i];
      if ("Wikipedia".equals(title)) {
        title = "Wikipedia (" + Lang.getCurrentLang() + ")";
      }
      append(title, null);
    }
  }

  protected void handleSelection() {
    int index = getSelectedIndex();
    if (!isValidSelection(index, urls.length)) {
      return;
    }
    RssSubscription subscription =
        new RssSubscription(urls[index], TITLES[index], sites[index], "", "");
    int result = RssSubscriptionManager.getInstance().add(subscription);
    if (result == RssSubscriptionManager.ALREADY_EXISTS) {
      navigator.showAlert(Lang.tr("rss.already_added"), AlertType.INFO);
    } else if (result == RssSubscriptionManager.LIMIT_REACHED) {
      navigator.showAlert(Lang.tr("rss.limit_reached"), AlertType.ERROR);
    } else if (result == RssSubscriptionManager.SUCCESS) {
      if (root != null) {
        root.refreshSubscriptions();
      }
      navigator.showAlert(Lang.tr("rss.saved"), AlertType.CONFIRMATION);
    } else {
      navigator.showAlert(Lang.tr("rss.error.generic"), AlertType.ERROR);
    }
  }

  protected void handleCommand(Command c, Displayable d) {}
}
