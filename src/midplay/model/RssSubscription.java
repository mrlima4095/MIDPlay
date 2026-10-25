package midplay.model;

/** One feed the user follows. Empty strings are used instead of nulls for optional fields. */
public final class RssSubscription {
  public String url;
  public String title;
  public String site;
  public String description;
  public String image;

  public RssSubscription(String url, String title, String site, String description, String image) {
    this.url = url == null ? "" : url;
    this.title = title == null ? "" : title;
    this.site = site == null ? "" : site;
    this.description = description == null ? "" : description;
    this.image = image == null ? "" : image;
  }

  public String displayTitle() {
    if (title != null && title.length() > 0) {
      return title;
    }
    String value = site != null && site.length() > 0 ? site : url;
    return value == null ? "" : value;
  }
}
