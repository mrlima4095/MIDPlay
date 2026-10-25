package midplay.store;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import java.util.Vector;
import midplay.model.RssSubscription;

/**
 * Feeds the user follows, most recently added first, persisted in a single record store. Only the
 * feed metadata is stored; entries and article text always come from the proxy, so a subscription
 * does not take space on the phone.
 */
public final class RssSubscriptionManager {
  public static final int SUCCESS = 0;
  public static final int ALREADY_EXISTS = 1;
  public static final int FAILED = 2;
  public static final int LIMIT_REACHED = 3;

  private static RssSubscriptionManager instance;

  public static synchronized RssSubscriptionManager getInstance() {
    if (instance == null) {
      instance = new RssSubscriptionManager();
    }
    return instance;
  }

  private final JsonRecordStore storage =
      new JsonRecordStore(Configuration.STORAGE_RSS_SUBSCRIPTIONS, 1, "[]");
  private Vector subscriptions;

  private RssSubscriptionManager() {}

  public Vector getSubscriptions() {
    if (subscriptions == null) {
      subscriptions = load();
    }
    return subscriptions;
  }

  public int count() {
    return getSubscriptions().size();
  }

  public RssSubscription findByUrl(String url) {
    if (url == null) {
      return null;
    }
    Vector items = getSubscriptions();
    for (int i = 0; i < items.size(); i++) {
      RssSubscription subscription = (RssSubscription) items.elementAt(i);
      if (url.equals(subscription.url)) {
        return subscription;
      }
    }
    return null;
  }

  public boolean isSubscribed(String url) {
    return findByUrl(url) != null;
  }

  public int add(RssSubscription subscription) {
    if (subscription == null || subscription.url.length() == 0) {
      return FAILED;
    }
    if (isSubscribed(subscription.url)) {
      return ALREADY_EXISTS;
    }
    if (count() >= Configuration.RSS_MAX_SUBSCRIPTIONS) {
      return LIMIT_REACHED;
    }
    Vector items = getSubscriptions();
    items.insertElementAt(subscription, 0);
    save(items);
    return SUCCESS;
  }

  public boolean remove(String url) {
    if (url == null) {
      return false;
    }
    Vector items = getSubscriptions();
    for (int i = 0; i < items.size(); i++) {
      RssSubscription subscription = (RssSubscription) items.elementAt(i);
      if (url.equals(subscription.url)) {
        items.removeElementAt(i);
        save(items);
        return true;
      }
    }
    return false;
  }

  private Vector load() {
    Vector result = new Vector();
    try {
      JSONArray items = JSON.getArray(storage.load());
      for (int i = 0; i < items.size(); i++) {
        if (items.get(i) == null) {
          continue;
        }
        JSONObject item = items.getObject(i);
        String url = item.getString("u", "");
        if (url == null || url.length() == 0) {
          continue;
        }
        result.addElement(
            new RssSubscription(
                url,
                item.getString("t", ""),
                item.getString("s", ""),
                item.getString("d", ""),
                item.getString("i", "")));
      }
    } catch (Exception e) {
    }
    return result;
  }

  private void save(Vector items) {
    JSONArray array = new JSONArray();
    for (int i = 0; i < items.size(); i++) {
      RssSubscription subscription = (RssSubscription) items.elementAt(i);
      JSONObject item = new JSONObject();
      item.put("u", subscription.url);
      item.put("t", subscription.title);
      item.put("s", subscription.site);
      item.put("d", subscription.description);
      item.put("i", subscription.image);
      array.add(item);
    }
    storage.saveAsync(array.toString());
  }
}
