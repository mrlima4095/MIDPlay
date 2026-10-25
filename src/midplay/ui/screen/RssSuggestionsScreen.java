package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import midplay.model.RssSubscription;
import midplay.store.RssSubscriptionManager;
import midplay.ui.BaseList;
import midplay.ui.Navigator;
import midplay.util.Lang;

/** A short list of feeds that work without any setup, picked for the current UI language. */
public final class RssSuggestionsScreen extends BaseList {
  private final RssScreen root;
  private final String[] titles;
  private final String[] urls;
  private final String[] sites;

  public RssSuggestionsScreen(Navigator navigator, RssScreen root) {
    super(Lang.tr("rss.suggestions_title"), navigator);
    this.root = root;
    String language = Lang.getCurrentLang();
    if (language == null || language.length() == 0) {
      language = "en";
    }
    if ("pt".equals(language)) {
      titles = new String[] {"G1", "BBC Brasil", "Tecnoblog", "Olhar Digital", "Wikipédia"};
      urls =
          new String[] {
            "https://g1.globo.com/rss/g1/",
            "https://feeds.bbci.co.uk/portuguese/rss.xml",
            "https://tecnoblog.net/feed/",
            "https://olhardigital.com.br/feed/",
            "https://pt.wikipedia.org/w/index.php?title=Special:RecentChanges&feed=atom"
          };
      sites =
          new String[] {
            "https://g1.globo.com/",
            "https://www.bbc.com/portuguese",
            "https://tecnoblog.net/",
            "https://olhardigital.com.br/",
            "https://pt.wikipedia.org/"
          };
    } else if ("he".equals(language)) {
      titles = new String[] {"ynet", "Google News", "ויקיפדיה"};
      urls =
          new String[] {
            "https://www.ynet.co.il/Integration/StoryRss2.xml",
            "https://news.google.com/rss?hl=he&gl=IL&ceid=IL:he",
            "https://he.wikipedia.org/w/index.php?title=Special:RecentChanges&feed=atom"
          };
      sites =
          new String[] {
            "https://www.ynet.co.il/",
            "https://news.google.com/?hl=he&gl=IL&ceid=IL:he",
            "https://he.wikipedia.org/"
          };
    } else if ("pl".equals(language)) {
      titles = new String[] {"RMF24", "Google News", "Wikipedia"};
      urls =
          new String[] {
            "https://www.rmf24.pl/feed",
            "https://news.google.com/rss?hl=pl&gl=PL&ceid=PL:pl",
            "https://pl.wikipedia.org/w/index.php?title=Special:RecentChanges&feed=atom"
          };
      sites =
          new String[] {
            "https://www.rmf24.pl/",
            "https://news.google.com/?hl=pl&gl=PL&ceid=PL:pl",
            "https://pl.wikipedia.org/"
          };
    } else if ("tr".equals(language)) {
      titles = new String[] {"Hürriyet", "Google News", "Vikipedi"};
      urls =
          new String[] {
            "https://www.hurriyet.com.tr/rss/anasayfa",
            "https://news.google.com/rss?hl=tr&gl=TR&ceid=TR:tr",
            "https://tr.wikipedia.org/w/index.php?title=Special:RecentChanges&feed=atom"
          };
      sites =
          new String[] {
            "https://www.hurriyet.com.tr/",
            "https://news.google.com/?hl=tr&gl=TR&ceid=TR:tr",
            "https://tr.wikipedia.org/"
          };
    } else if ("vi".equals(language)) {
      titles = new String[] {"Tuổi Trẻ", "Google News", "Wikipedia"};
      urls =
          new String[] {
            "https://tuoitre.vn/rss/tin-moi-nhat.rss",
            "https://news.google.com/rss?hl=vi&gl=VN&ceid=VN:vi",
            "https://vi.wikipedia.org/w/index.php?title=Special:RecentChanges&feed=atom"
          };
      sites =
          new String[] {
            "https://tuoitre.vn/",
            "https://news.google.com/?hl=vi&gl=VN&ceid=VN:vi",
            "https://vi.wikipedia.org/"
          };
    } else {
      titles =
          new String[] {"BBC News", "Hacker News", "The Verge", "Ars Technica", "Wikipedia"};
      urls =
          new String[] {
            "https://feeds.bbci.co.uk/news/rss.xml",
            "https://news.ycombinator.com/rss",
            "https://www.theverge.com/rss/index.xml",
            "https://feeds.arstechnica.com/arstechnica/index",
            "https://en.wikipedia.org/w/index.php?title=Special:RecentChanges&feed=atom"
          };
      sites =
          new String[] {
            "https://www.bbc.com/news",
            "https://news.ycombinator.com/",
            "https://www.theverge.com/",
            "https://arstechnica.com/",
            "https://en.wikipedia.org/"
          };
    }
    populateItems();
  }

  protected void populateItems() {
    for (int i = 0; i < titles.length; i++) {
      append(titles[i], null);
    }
  }

  protected void handleSelection() {
    int index = getSelectedIndex();
    if (!isValidSelection(index, urls.length)) {
      return;
    }
    RssSubscription subscription =
        new RssSubscription(urls[index], titles[index], sites[index], "", "");
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
