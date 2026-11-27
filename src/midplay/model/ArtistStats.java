package midplay.model;

/** Aggregated listening statistics for one artist. */
public class ArtistStats {
  private final String name;
  private final int count;
  private final int trackCount;
  private final long lastPlayed;

  public ArtistStats(String name, int count, int trackCount, long lastPlayed) {
    this.name = name;
    this.count = count;
    this.trackCount = trackCount;
    this.lastPlayed = lastPlayed;
  }

  public String getName() {
    return name;
  }

  public int getCount() {
    return count;
  }

  public int getTrackCount() {
    return trackCount;
  }

  public long getLastPlayed() {
    return lastPlayed;
  }
}
