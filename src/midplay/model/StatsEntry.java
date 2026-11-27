package midplay.model;

/** One entry in the listening statistics store. */
public class StatsEntry {
  private final Track track;
  private final int count;
  private final long lastPlayed;

  public StatsEntry(Track track, int count, long lastPlayed) {
    this.track = track;
    this.count = count;
    this.lastPlayed = lastPlayed;
  }

  public Track getTrack() {
    return track;
  }

  public int getCount() {
    return count;
  }

  public long getLastPlayed() {
    return lastPlayed;
  }
}
