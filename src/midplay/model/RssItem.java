package midplay.model;

/** One entry of a feed, using only the fields the compact proxy response carries. */
public final class RssItem {
  public String id;
  public String title;
  public String link;
  public long timestamp;
  public String author;
  public String summary;
  public String image;

  public RssItem(
      String id,
      String title,
      String link,
      long timestamp,
      String author,
      String summary,
      String image) {
    this.id = id == null ? "" : id;
    this.title = title == null ? "" : title;
    this.link = link == null ? "" : link;
    this.timestamp = timestamp;
    this.author = author == null ? "" : author;
    this.summary = summary == null ? "" : summary;
    this.image = image == null ? "" : image;
  }
}
