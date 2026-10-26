package midplay.store;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import java.util.Vector;
import javax.microedition.rms.RecordStoreException;
import midplay.model.Playlist;
import midplay.model.Playlists;
import midplay.model.Track;
import midplay.model.Tracks;
import midplay.util.Utils;

public class FavoritesManager {
  private static FavoritesManager instance;

  public static final int SUCCESS = 0;
  public static final int ALREADY_EXISTS = 1;
  public static final int FAILED = 2;

  /** Sentinel returned by {@link #findPlaylistRecord} when the store could not be read. */
  private static final int READ_FAILED = -2;

  // A custom playlist can grow large, so its tracks are stored across several RMS records
  // (chunks) instead of a single one. The cap below only bounds how many tracks a single playlist
  // parses into memory at once.
  private static final int MAX_TRACKS_PER_PLAYLIST = 1000;

  // Chunk budget measured in actual UTF-8 bytes. Keeping each record small avoids devices that
  // reject larger RMS records, and leaves room for the key/index fields written next to the
  // tracks.
  private static final int MAX_CHUNK_BYTES = 6 * 1024;

  public static FavoritesManager getInstance() {
    if (instance == null) {
      instance = new FavoritesManager();
    }
    return instance;
  }

  private final RecordStoreManager storage;
  private final RecordStoreManager tracksStorage;

  private FavoritesManager() {
    storage = new RecordStoreManager(Configuration.STORAGE_FAVORITES);
    tracksStorage = new RecordStoreManager(Configuration.STORAGE_TRACKS);
  }

  public int addPlaylist(Playlist playlist) {
    if (playlist == null) {
      return FAILED;
    }
    try {
      int existingRecord = findPlaylistRecord(playlist);
      if (existingRecord == READ_FAILED) {
        return FAILED;
      }
      if (existingRecord != -1) {
        return ALREADY_EXISTS;
      }
      if (playlist.getId() == 0) {
        playlist.setId(System.currentTimeMillis());
      }
      String jsonString = playlist.toJSON().toString();
      storage.addRecord(jsonString);
      return SUCCESS;
    } catch (Exception e) {
      storage.closeRecordStore();
      return FAILED;
    }
  }

  public boolean removePlaylistAndTracks(Playlist playlist) {
    if (playlist == null) {
      return false;
    }
    int recordId = findPlaylistRecord(playlist);
    if (recordId < 0) {
      return false;
    }
    try {
      storage.deleteRecord(recordId);
      if (playlist.isCustom()) {
        removeCustomPlaylistTracks(playlist.getKey());
      }
      return true;
    } catch (Exception e) {
      storage.closeRecordStore();
      tracksStorage.closeRecordStore();
      return false;
    }
  }

  public int removeAllPlaylists() {
    Playlist[] playlists = getPlaylists().getPlaylists();
    int removed = 0;
    if (playlists == null) {
      return removed;
    }
    for (int i = 0; i < playlists.length; i++) {
      if (removePlaylistAndTracks(playlists[i])) {
        removed++;
      }
    }
    return removed;
  }

  public boolean updatePlaylist(Playlist playlist) {
    if (playlist == null) {
      return false;
    }
    int recordId = findPlaylistRecord(playlist);
    if (recordId < 0) {
      return false;
    }
    try {
      storage.setRecord(recordId, playlist.toJSON().toString());
      return true;
    } catch (Exception e) {
      storage.closeRecordStore();
      return false;
    }
  }

  public Playlists getPlaylists() {
    Vector playlistVector = readPlaylists();
    if (playlistVector == null) {
      // The first read closed the store; a fresh instance usually recovers immediately.
      playlistVector = readPlaylists();
    }
    if (playlistVector == null) {
      playlistVector = new Vector();
    }
    sortPlaylistsById(playlistVector);
    Playlist[] playlistArray = new Playlist[playlistVector.size()];
    for (int i = 0; i < playlistVector.size(); i++) {
      playlistArray[i] = (Playlist) playlistVector.elementAt(i);
    }
    Playlists result = new Playlists();
    result.setPlaylists(playlistArray);
    return result;
  }

  /** Returns null when the playlist store could not be read. */
  private Vector readPlaylists() {
    final Vector playlistVector = new Vector();
    boolean read =
        storage.forEachRecord(
            new RecordStoreManager.RecordHandler() {
              public boolean handle(int recordId, String data) {
                Playlist playlist = Playlist.fromJSON(data);
                if (playlist != null) {
                  playlistVector.addElement(playlist);
                }
                return true;
              }
            });
    return read ? playlistVector : null;
  }

  private static void sortPlaylistsById(Vector vector) {
    Utils.bubbleSort(
        vector,
        new Utils.Comparator() {
          public boolean shouldSwap(Object a, Object b) {
            return ((Playlist) a).getId() < ((Playlist) b).getId();
          }
        });
  }

  public void close() {
    storage.closeRecordStore();
    tracksStorage.closeRecordStore();
  }

  public int addTrackToPlaylist(Playlist playlist, Track track) {
    if (playlist == null || track == null || !playlist.isCustom()) {
      return FAILED;
    }
    try {
      TracksRecord record = loadTracksRecord(playlist.getKey());
      if (record == null) {
        return FAILED;
      }
      for (int i = 0; i < record.tracks.size(); i++) {
        Track existingTrack = (Track) record.tracks.elementAt(i);
        if (existingTrack.isSame(track)) {
          return ALREADY_EXISTS;
        }
      }
      if (record.tracks.size() >= MAX_TRACKS_PER_PLAYLIST) {
        return FAILED;
      }
      record.tracks.addElement(track);
      return savePlaylistTracks(playlist.getKey(), record.tracks) ? SUCCESS : FAILED;
    } catch (Exception e) {
      tracksStorage.closeRecordStore();
      return FAILED;
    }
  }

  /**
   * Adds several tracks to a custom playlist in one pass, loading and saving the track list only
   * once so a bulk add of hundreds of files does not rewrite the record for every track.
   *
   * @return a three element array {@code {added, alreadyPresent, failed}}.
   */
  public int[] addTracksToPlaylist(Playlist playlist, Track[] tracks) {
    int total = tracks == null ? 0 : tracks.length;
    if (playlist == null || !playlist.isCustom() || tracks == null) {
      return new int[] {0, 0, total};
    }
    int added = 0;
    int exists = 0;
    int failed = 0;
    try {
      TracksRecord record = loadTracksRecord(playlist.getKey());
      if (record == null) {
        return new int[] {0, 0, total};
      }
      for (int i = 0; i < tracks.length; i++) {
        Track track = tracks[i];
        if (track == null) {
          failed++;
          continue;
        }
        boolean duplicate = false;
        for (int j = 0; j < record.tracks.size(); j++) {
          if (((Track) record.tracks.elementAt(j)).isSame(track)) {
            duplicate = true;
            break;
          }
        }
        if (duplicate) {
          exists++;
          continue;
        }
        if (record.tracks.size() >= MAX_TRACKS_PER_PLAYLIST) {
          failed++;
          continue;
        }
        record.tracks.addElement(track);
        added++;
      }
      if (added > 0 && !savePlaylistTracks(playlist.getKey(), record.tracks)) {
        return new int[] {0, exists, total - exists};
      }
      return new int[] {added, exists, failed};
    } catch (Exception e) {
      tracksStorage.closeRecordStore();
      return new int[] {0, 0, total};
    }
  }

  public boolean removeTrackFromPlaylist(Playlist playlist, Track track) {
    if (playlist == null || track == null || !playlist.isCustom()) {
      return false;
    }
    try {
      TracksRecord record = loadTracksRecord(playlist.getKey());
      if (record == null) {
        return false;
      }
      for (int i = 0; i < record.tracks.size(); i++) {
        Track existingTrack = (Track) record.tracks.elementAt(i);
        if (existingTrack.isSame(track)) {
          record.tracks.removeElementAt(i);
          return savePlaylistTracks(playlist.getKey(), record.tracks);
        }
      }
      return false;
    } catch (Exception e) {
      tracksStorage.closeRecordStore();
      return false;
    }
  }

  public Tracks getCustomPlaylistTracks(Playlist playlist) {
    if (playlist == null || !playlist.isCustom()) {
      return new Tracks();
    }
    try {
      TracksRecord record = loadTracksRecord(playlist.getKey());
      if (record == null) {
        return new Tracks();
      }
      Vector trackList = record.tracks;
      Track[] trackArray = new Track[trackList.size()];
      for (int i = 0; i < trackList.size(); i++) {
        trackArray[i] = (Track) trackList.elementAt(i);
      }
      Tracks result = new Tracks();
      result.setTracks(trackArray);
      result.setHasMore(false);
      return result;
    } catch (Exception e) {
      tracksStorage.closeRecordStore();
      return new Tracks();
    }
  }

  public boolean reorderCustomPlaylistTracks(Playlist playlist, Track[] reordered) {
    if (playlist == null || !playlist.isCustom() || reordered == null) {
      return false;
    }
    try {
      Vector tracks = new Vector();
      for (int i = 0; i < reordered.length; i++) {
        if (reordered[i] != null) {
          tracks.addElement(reordered[i]);
        }
      }
      return savePlaylistTracks(playlist.getKey(), tracks);
    } catch (Exception e) {
      tracksStorage.closeRecordStore();
      return false;
    }
  }

  public int getCustomPlaylistTracksCount(Playlist playlist) {
    if (playlist == null || !playlist.isCustom()) {
      return 0;
    }
    return countTracks(playlist.getKey());
  }

  private static final class TracksRecord {
    final Vector tracks = new Vector();
  }

  private static final class TracksRecordEntry {
    final int recordId;
    final int chunk;
    final JSONObject json;
    final String raw;

    TracksRecordEntry(int recordId, int chunk, JSONObject json, String raw) {
      this.recordId = recordId;
      this.chunk = chunk;
      this.json = json;
      this.raw = raw;
    }
  }

  /**
   * All records that hold one playlist's tracks, ordered by chunk index. Returns {@code null} when
   * the record store could not be read, so callers can tell a failed read from an empty playlist.
   */
  private Vector findTracksRecordsByKey(final String playlistKey) {
    if (playlistKey == null || playlistKey.length() == 0) {
      return new Vector();
    }
    Vector result = collectTracksRecords(playlistKey);
    if (result == null) {
      // The failed read closed the store; retry on a fresh instance before giving up.
      result = collectTracksRecords(playlistKey);
    }
    return result;
  }

  private Vector collectTracksRecords(final String playlistKey) {
    final Vector result = new Vector();
    boolean read = tracksStorage.forEachRecord(
        new RecordStoreManager.RecordHandler() {
          public boolean handle(int recordId, String data) {
            if (data.indexOf(playlistKey) == -1) {
              return true;
            }
            try {
              JSONObject json = JSON.getObject(data);
              if (playlistKey.equals(json.getString("playlistKey", ""))) {
                result.addElement(
                    new TracksRecordEntry(recordId, json.getInt("chunk", 0), json, data));
              }
            } catch (Exception e) {
            }
            return true;
          }
        });
    if (!read) {
      return null;
    }
    Utils.bubbleSort(
        result,
        new Utils.Comparator() {
          public boolean shouldSwap(Object a, Object b) {
            return ((TracksRecordEntry) a).chunk > ((TracksRecordEntry) b).chunk;
          }
        });
    return result;
  }

  /** Returns null when the stored track list could not be read. */
  private TracksRecord loadTracksRecord(final String playlistKey) {
    Vector entries = findTracksRecordsByKey(playlistKey);
    if (entries == null) {
      return null;
    }
    TracksRecord record = new TracksRecord();
    for (int e = 0; e < entries.size(); e++) {
      JSONArray tracksArray = ((TracksRecordEntry) entries.elementAt(e)).json.getArray("tracks");
      if (tracksArray == null) {
        continue;
      }
      for (int i = 0; i < tracksArray.size(); i++) {
        Track track = Track.fromJSON(tracksArray.getObject(i));
        if (track != null) {
          record.tracks.addElement(track);
        }
      }
    }
    return record;
  }

  private int countTracks(final String playlistKey) {
    int count = 0;
    Vector entries = findTracksRecordsByKey(playlistKey);
    if (entries == null) {
      return 0;
    }
    for (int e = 0; e < entries.size(); e++) {
      JSONArray tracksArray = ((TracksRecordEntry) entries.elementAt(e)).json.getArray("tracks");
      if (tracksArray != null) {
        count += tracksArray.size();
      }
    }
    return count;
  }

  /**
   * Rewrites a playlist's tracks in place. Existing chunk records are updated with {@code
   * setRecord}, and only new chunks are added; old chunks are removed after every write succeeds.
   * A failed write restores the previous payloads, so a device that rejects a record can never
   * leave the playlist empty (the bug that made every playlist report "no data" until restart).
   */
  private boolean savePlaylistTracks(String playlistKey, Vector trackList) {
    if (playlistKey == null || playlistKey.length() == 0) {
      return false;
    }
    boolean saved = writePlaylistTracks(playlistKey, trackList);
    if (!saved) {
      // A RecordStore that failed a write may stay unusable on some runtimes. Close it so the
      // retry (and every later access) opens a fresh instance instead of requiring an app restart.
      tracksStorage.closeRecordStore();
      saved = writePlaylistTracks(playlistKey, trackList);
    }
    return saved;
  }

  private boolean writePlaylistTracks(String playlistKey, Vector trackList) {
    Vector payloads;
    try {
      payloads = buildChunkPayloads(playlistKey, trackList);
    } catch (Exception e) {
      return false;
    }
    Vector existing = findTracksRecordsByKey(playlistKey);
    if (existing == null) {
      return false;
    }

    int[] addedIds = new int[payloads.size()];
    int written = 0;
    boolean failed = false;
    try {
      for (int i = 0; i < payloads.size(); i++) {
        String payload = (String) payloads.elementAt(i);
        if (i < existing.size()) {
          tracksStorage.setRecord(((TracksRecordEntry) existing.elementAt(i)).recordId, payload);
        } else {
          addedIds[i] = tracksStorage.addRecord(payload);
        }
        written++;
      }
    } catch (Exception e) {
      failed = true;
    }

    if (failed) {
      // Put the previous chunks back so the playlist keeps its last good contents.
      for (int i = 0; i < written && i < existing.size(); i++) {
        try {
          tracksStorage.setRecord(
              ((TracksRecordEntry) existing.elementAt(i)).recordId,
              ((TracksRecordEntry) existing.elementAt(i)).raw);
        } catch (Exception e) {
        }
      }
      for (int i = existing.size(); i < written && i < addedIds.length; i++) {
        if (addedIds[i] > 0) {
          try {
            tracksStorage.deleteRecord(addedIds[i]);
          } catch (Exception e) {
          }
        }
      }
      return false;
    }

    // The playlist shrank: drop chunks that are no longer used.
    for (int i = payloads.size(); i < existing.size(); i++) {
      try {
        tracksStorage.deleteRecord(((TracksRecordEntry) existing.elementAt(i)).recordId);
      } catch (Exception e) {
        tracksStorage.closeRecordStore();
        return false;
      }
    }
    return true;
  }

  /**
   * Builds the JSON payload of every chunk. Each track is measured by its real UTF-8 size (the
   * serializer escapes some characters to six bytes), so a chunk always stays under the record
   * budget while keeping non-ASCII titles and long paths intact.
   */
  private static Vector buildChunkPayloads(String playlistKey, Vector trackList) {
    Vector payloads = new Vector();
    JSONArray chunk = new JSONArray();
    int chunkBytes = 0;
    int chunkIndex = 0;
    for (int i = 0; i < trackList.size(); i++) {
      Track track = (Track) trackList.elementAt(i);
      if (track == null) {
        continue;
      }
      JSONObject trackJson = track.toJSON();
      String encoded = trackJson.toString();
      // Measure the real UTF-8 payload: the JSON serializer escapes some characters to six
      // bytes, so a character count is not a safe upper bound for the record size.
      int size = Utils.utf8ToBytes(encoded).length + 1;
      if (chunk.size() > 0 && chunkBytes + size > MAX_CHUNK_BYTES) {
        payloads.addElement(buildChunkPayload(playlistKey, chunkIndex++, chunk));
        chunk = new JSONArray();
        chunkBytes = 0;
      }
      chunk.add(trackJson);
      chunkBytes += size;
    }
    if (chunk.size() > 0 || payloads.size() == 0) {
      payloads.addElement(buildChunkPayload(playlistKey, chunkIndex++, chunk));
    }
    return payloads;
  }

  private static String buildChunkPayload(String playlistKey, int chunkIndex, JSONArray tracks) {
    JSONObject data = new JSONObject();
    data.put("playlistKey", playlistKey);
    data.put("chunk", chunkIndex);
    data.put("tracks", tracks);
    return data.toString();
  }

  private void removeCustomPlaylistTracks(final String playlistKey) {
    try {
      Vector entries = findTracksRecordsByKey(playlistKey);
      if (entries == null) {
        return;
      }
      for (int i = 0; i < entries.size(); i++) {
        try {
          tracksStorage.deleteRecord(((TracksRecordEntry) entries.elementAt(i)).recordId);
        } catch (Exception e) {
          tracksStorage.closeRecordStore();
        }
      }
    } catch (Exception e) {
      tracksStorage.closeRecordStore();
    }
  }

  private int findPlaylistRecord(final Playlist playlist) {
    final int[] found = {-1};
    boolean read =
        storage.forEachRecord(
            new RecordStoreManager.RecordHandler() {
              public boolean handle(int recordId, String data) {
                Playlist storedPlaylist = Playlist.fromJSON(data);
                if (playlist.isSame(storedPlaylist)) {
                  found[0] = recordId;
                  return false;
                }
                return true;
              }
            });
    return read ? found[0] : READ_FAILED;
  }
}
