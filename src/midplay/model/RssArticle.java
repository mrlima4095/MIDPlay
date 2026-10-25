package midplay.model;

/** Full text of one entry, as returned by the proxy's {@code op=article}. */
public final class RssArticle {
  public String title;
  public String link;
  public String author;
  public long timestamp;
  public String content;
  public String image;

  public RssArticle(
      String title, String link, String author, long timestamp, String content, String image) {
    this.title = title == null ? "" : title;
    this.link = link == null ? "" : link;
    this.author = author == null ? "" : author;
    this.timestamp = timestamp;
    this.content = content == null ? "" : content;
    this.image = image == null ? "" : image;
  }
}
