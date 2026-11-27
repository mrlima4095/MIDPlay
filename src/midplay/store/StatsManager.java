package midplay.store;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import javax.microedition.rms.RecordStoreException;
import midplay.model.ArtistStats;
import midplay.model.StatsEntry;
import midplay.model.Track;
import midplay.util.Utils;

/**
 * Listening statistics: one record per track with a play count and the last time it was played.
 *
 * <p>Anonymous mode suppresses both writes and reads, and a playback session is counted at most
 * once so resuming after a pause does not inflate the numbers.
 */
public class StatsManager {
  private static final int MAX_ENTRIES = 300;
  private static final int DEDUPE_ENTRIES = 32;
  private static final long DEDUPE_WINDOW_MS = 3000L;
  private static StatsManager instance;

  public static StatsManager getInstance() {
    if (instance == null) {
      instance = new StatsManager();
    }
    return instance;
  }

  private final JsonRecordStore storage;
  private JSONArray cached;
  private final String[] recentKeys = new String[DEDUPE_ENTRIES];
  private final long[] recentTimes = new long[DEDUPE_ENTRIES];
  private int recentNext;

  private StatsManager() {
    storage = new JsonRecordStore(Configuration.STORAGE_STATS, 1, "[]");
  }

  private JSONArray items() {
    if (cached == null) {
      try {
        cached = JSON.getArray(storage.load());
      } catch (Exception e) {
        cached = new JSONArray();
      }
    }
    return cached;
  }

  /**
   * Records one play. Called both when the user taps a track and when playback actually starts; a
   * short per-track dedupe window prevents those two events from counting twice.
   */
  public void recordTrack(Track track) {
    if (track == null || HistoryPrivacy.isAnonymous()) {
      return;
    }
    String key = identityKey(track);
    long now = System.currentTimeMillis();
    if (wasCountedRecently(key, now)) {
      return;
    }
    rememberCount(key, now);

    JSONArray arr = items();
    int existingIndex = -1;
    int nextCount = 1;
    for (int i = 0; i < arr.size(); i++) {
      try {
        JSONObject existing = arr.getObject(i);
        Track other = Track.fromJSON(existing.getObject("track"));
        if (track.isSame(other)) {
          existingIndex = i;
          nextCount = existing.getInt("count", 0) + 1;
          break;
        }
      } catch (Exception e) {
      }
    }

    JSONObject entry = new JSONObject();
    entry.put("track", track.toJSON());
    entry.put("count", nextCount);
    entry.put("last", System.currentTimeMillis());

    JSONArray next = new JSONArray();
    next.add(entry);
    int carried = 0;
    for (int i = 0; i < arr.size() && carried < MAX_ENTRIES - 1; i++) {
      if (i == existingIndex) {
        continue;
      }
      next.add(arr.getObject(i));
      carried++;
    }
    save(next);
  }

  public StatsEntry[] getMostPlayed() {
    StatsEntry[] entries = getEntries();
    for (int i = 1; i < entries.length; i++) {
      StatsEntry value = entries[i];
      int j = i - 1;
      while (j >= 0 && comesBefore(value, entries[j])) {
        entries[j + 1] = entries[j];
        j--;
      }
      entries[j + 1] = value;
    }
    return entries;
  }

  public StatsEntry[] getRecentlyPlayed() {
    StatsEntry[] entries = getEntries();
    for (int i = 1; i < entries.length; i++) {
      StatsEntry value = entries[i];
      int j = i - 1;
      while (j >= 0 && entries[j].getLastPlayed() < value.getLastPlayed()) {
        entries[j + 1] = entries[j];
        j--;
      }
      entries[j + 1] = value;
    }
    return entries;
  }

  public ArtistStats[] getTopArtists() {
    StatsEntry[] entries = getEntries();
    ArtistStats[] artists = new ArtistStats[0];
    String[] keys = new String[0];
    for (int i = 0; i < entries.length; i++) {
      Track track = entries[i].getTrack();
      String[] names = splitArtists(track != null ? track.getArtist() : null);
      for (int a = 0; a < names.length; a++) {
        String name = names[a];
        String key = name.toLowerCase();
        boolean repeated = false;
        for (int r = 0; r < a; r++) {
          if (names[r].toLowerCase().equals(key)) {
            repeated = true;
            break;
          }
        }
        if (repeated) {
          continue;
        }
        int target = -1;
        for (int j = 0; j < keys.length; j++) {
          if (keys[j].equals(key)) {
            target = j;
            break;
          }
        }
        if (target < 0) {
          ArtistStats[] grown = new ArtistStats[artists.length + 1];
          String[] grownKeys = new String[keys.length + 1];
          System.arraycopy(artists, 0, grown, 0, artists.length);
          System.arraycopy(keys, 0, grownKeys, 0, keys.length);
          artists = grown;
          keys = grownKeys;
          target = artists.length - 1;
          keys[target] = key;
          artists[target] = new ArtistStats(name, 0, 0, 0L);
        }
        ArtistStats current = artists[target];
        int count = current.getCount() + entries[i].getCount();
        int trackCount = current.getTrackCount() + 1;
        long lastPlayed = current.getLastPlayed();
        if (entries[i].getLastPlayed() > lastPlayed) {
          lastPlayed = entries[i].getLastPlayed();
        }
        artists[target] = new ArtistStats(current.getName(), count, trackCount, lastPlayed);
      }
    }

    for (int i = 1; i < artists.length; i++) {
      ArtistStats value = artists[i];
      int j = i - 1;
      while (j >= 0
          && (artists[j].getCount() < value.getCount()
              || (artists[j].getCount() == value.getCount()
                  && artists[j].getLastPlayed() < value.getLastPlayed()))) {
        artists[j + 1] = artists[j];
        j--;
      }
      artists[j + 1] = value;
    }
    return artists;
  }

  public Track[] getTracksByArtist(String artist) {
    if (artist == null) {
      return new Track[0];
    }
    String key = trimToEmpty(artist).toLowerCase();
    if (key.length() == 0) {
      return new Track[0];
    }
    StatsEntry[] entries = getMostPlayed();
    Track[] result = new Track[entries.length];
    int n = 0;
    for (int i = 0; i < entries.length; i++) {
      Track track = entries[i].getTrack();
      if (track == null) {
        continue;
      }
      String[] names = splitArtists(track.getArtist());
      for (int a = 0; a < names.length; a++) {
        if (key.equals(names[a].toLowerCase())) {
          result[n++] = track;
          break;
        }
      }
    }
    if (n == result.length) {
      return result;
    }
    Track[] trimmed = new Track[n];
    System.arraycopy(result, 0, trimmed, 0, n);
    return trimmed;
  }

  public StatsEntry[] getEntries() {
    if (HistoryPrivacy.isAnonymous()) {
      return new StatsEntry[0];
    }
    JSONArray arr = items();
    StatsEntry[] result = new StatsEntry[arr.size()];
    int n = 0;
    for (int i = 0; i < arr.size(); i++) {
      try {
        JSONObject entry = arr.getObject(i);
        Track track = Track.fromJSON(entry.getObject("track"));
        if (track != null) {
          result[n++] =
              new StatsEntry(
                  track, entry.getInt("count", 0), entry.getLong("last", System.currentTimeMillis()));
        }
      } catch (Exception e) {
      }
    }
    if (n != result.length) {
      StatsEntry[] trimmed = new StatsEntry[n];
      System.arraycopy(result, 0, trimmed, 0, n);
      return trimmed;
    }
    return result;
  }

  public void clear() {
    cached = new JSONArray();
    for (int i = 0; i < DEDUPE_ENTRIES; i++) {
      recentKeys[i] = null;
      recentTimes[i] = 0L;
    }
    recentNext = 0;
    save(new JSONArray());
  }

  public void close() {
    storage.close();
  }

  private boolean wasCountedRecently(String key, long now) {
    for (int i = 0; i < DEDUPE_ENTRIES; i++) {
      if (key.equals(recentKeys[i]) && now - recentTimes[i] < DEDUPE_WINDOW_MS) {
        return true;
      }
    }
    return false;
  }

  private void rememberCount(String key, long now) {
    for (int i = 0; i < DEDUPE_ENTRIES; i++) {
      if (key.equals(recentKeys[i])) {
        recentTimes[i] = now;
        return;
      }
    }
    recentKeys[recentNext] = key;
    recentTimes[recentNext] = now;
    recentNext = (recentNext + 1) % DEDUPE_ENTRIES;
  }

  /** Splits credits like "Artist A, Artist B" or "A feat. B" into separate artist names. */
  private static String[] splitArtists(String raw) {
    String value = normalizeSeparators(trimToEmpty(raw));
    int separators = 0;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == ',' || c == ';') {
        separators++;
      }
    }
    String[] result = new String[separators + 1];
    int n = 0;
    int start = 0;
    for (int i = 0; i <= value.length(); i++) {
      if (i == value.length() || value.charAt(i) == ',' || value.charAt(i) == ';') {
        String part = value.substring(start, i).trim();
        if (part.length() > 0) {
          result[n++] = part;
        }
        start = i + 1;
      }
    }
    if (n == result.length) {
      return result;
    }
    String[] trimmed = new String[n];
    System.arraycopy(result, 0, trimmed, 0, n);
    return trimmed;
  }

  private static String normalizeSeparators(String value) {
    String result = value;
    String[] separators = {
      " & ", " feat. ", " feat ", " ft. ", " ft ", " featuring ", " x ", " vs. ", " vs "
    };
    for (int i = 0; i < separators.length; i++) {
      int at;
      while ((at = Utils.indexOfIgnoreCase(result, separators[i])) >= 0) {
        result = result.substring(0, at) + "," + result.substring(at + separators[i].length());
      }
    }
    return result;
  }

  private static String identityKey(Track track) {
    String key = trimToEmpty(track.getKey());
    if (key.length() > 0) {
      return "k:" + key;
    }
    String url = trimToEmpty(track.getUrl());
    if (url.length() > 0) {
      return "u:" + url;
    }
    return "n:"
        + trimToEmpty(track.getName())
        + "|"
        + trimToEmpty(track.getArtist())
        + "|"
        + track.getDuration();
  }

  private static boolean comesBefore(StatsEntry candidate, StatsEntry current) {
    if (candidate.getCount() != current.getCount()) {
      return candidate.getCount() > current.getCount();
    }
    return candidate.getLastPlayed() > current.getLastPlayed();
  }

  private static String trimToEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  /** Stats are small and infrequent, so write them immediately instead of relying on the async writer. */
  private void save(JSONArray arr) {
    cached = arr;
    try {
      storage.save(arr.toString());
    } catch (RecordStoreException e) {
      // Keep the in-memory copy; the next play or clear retries the write.
    }
  }
}
