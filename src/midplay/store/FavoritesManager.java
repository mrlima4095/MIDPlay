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

  // A custom playlist can grow large, so its tracks are stored across several RMS records (chunks)
  // instead of a single one, keeping every write well under the per-record size limit some devices
  // impose. The cap below only bounds how many tracks a single playlist parses into memory at once.
  private static final int MAX_TRACKS_PER_PLAYLIST = 1000;
  private static final int MAX_CHUNK_BYTES = 16 * 1024;

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
      if (findPlaylistRecord(playlist) != -1) {
        return ALREADY_EXISTS;
      }
      if (playlist.getId() == 0) {
        playlist.setId(System.currentTimeMillis());
      }
      String jsonString = playlist.toJSON().toString();
      storage.addRecord(jsonString);
      return SUCCESS;
    } catch (RecordStoreException e) {
      return FAILED;
    }
  }

  public boolean removePlaylistAndTracks(Playlist playlist) {
    if (playlist == null) {
      return false;
    }
    int recordId = findPlaylistRecord(playlist);
    if (recordId == -1) {
      return false;
    }
    try {
      storage.deleteRecord(recordId);
      if (playlist.isCustom()) {
        removeCustomPlaylistTracks(playlist.getKey());
      }
      return true;
    } catch (RecordStoreException e) {
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
    if (recordId == -1) {
      return false;
    }
    try {
      storage.setRecord(recordId, playlist.toJSON().toString());
      return true;
    } catch (RecordStoreException e) {
      return false;
    }
  }

  public Playlists getPlaylists() {
    final Vector playlistVector = new Vector();
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
    sortPlaylistsById(playlistVector);
    Playlist[] playlistArray = new Playlist[playlistVector.size()];
    for (int i = 0; i < playlistVector.size(); i++) {
      playlistArray[i] = (Playlist) playlistVector.elementAt(i);
    }
    Playlists result = new Playlists();
    result.setPlaylists(playlistArray);
    return result;
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
      return new int[] {0, 0, total};
    }
  }

  public boolean removeTrackFromPlaylist(Playlist playlist, Track track) {
    if (playlist == null || track == null || !playlist.isCustom()) {
      return false;
    }
    try {
      TracksRecord record = loadTracksRecord(playlist.getKey());
      for (int i = 0; i < record.tracks.size(); i++) {
        Track existingTrack = (Track) record.tracks.elementAt(i);
        if (existingTrack.isSame(track)) {
          record.tracks.removeElementAt(i);
          return savePlaylistTracks(playlist.getKey(), record.tracks);
        }
      }
      return false;
    } catch (Exception e) {
      return false;
    }
  }

  public Tracks getCustomPlaylistTracks(Playlist playlist) {
    if (playlist == null || !playlist.isCustom()) {
      return new Tracks();
    }
    try {
      Vector trackList = loadTracksRecord(playlist.getKey()).tracks;
      Track[] trackArray = new Track[trackList.size()];
      for (int i = 0; i < trackList.size(); i++) {
        trackArray[i] = (Track) trackList.elementAt(i);
      }
      Tracks result = new Tracks();
      result.setTracks(trackArray);
      result.setHasMore(false);
      return result;
    } catch (Exception e) {
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

    TracksRecordEntry(int recordId, int chunk, JSONObject json) {
      this.recordId = recordId;
      this.chunk = chunk;
      this.json = json;
    }
  }

  /** All records that hold one playlist's tracks, ordered by chunk index. */
  private Vector findTracksRecordsByKey(final String playlistKey) {
    final Vector result = new Vector();
    if (playlistKey == null || playlistKey.length() == 0) {
      return result;
    }
    tracksStorage.forEachRecord(
        new RecordStoreManager.RecordHandler() {
          public boolean handle(int recordId, String data) {
            if (data.indexOf(playlistKey) == -1) {
              return true;
            }
            try {
              JSONObject json = JSON.getObject(data);
              if (playlistKey.equals(json.getString("playlistKey", ""))) {
                result.addElement(new TracksRecordEntry(recordId, json.getInt("chunk", 0), json));
              }
            } catch (Exception e) {
            }
            return true;
          }
        });
    Utils.bubbleSort(
        result,
        new Utils.Comparator() {
          public boolean shouldSwap(Object a, Object b) {
            return ((TracksRecordEntry) a).chunk > ((TracksRecordEntry) b).chunk;
          }
        });
    return result;
  }

  private TracksRecord loadTracksRecord(final String playlistKey) {
    TracksRecord record = new TracksRecord();
    Vector entries = findTracksRecordsByKey(playlistKey);
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
    for (int e = 0; e < entries.size(); e++) {
      JSONArray tracksArray = ((TracksRecordEntry) entries.elementAt(e)).json.getArray("tracks");
      if (tracksArray != null) {
        count += tracksArray.size();
      }
    }
    return count;
  }

  /**
   * Rewrites a playlist's tracks as one or more chunked records. The old records are removed first,
   * so a shrinking playlist does not leave stale chunks behind.
   */
  private boolean savePlaylistTracks(String playlistKey, Vector trackList) {
    try {
      removeCustomPlaylistTracks(playlistKey);
      JSONArray chunk = new JSONArray();
      int chunkBytes = 0;
      int chunkIndex = 0;
      boolean wrote = false;
      for (int i = 0; i < trackList.size(); i++) {
        Track track = (Track) trackList.elementAt(i);
        if (track == null) {
          continue;
        }
        JSONObject trackJson = track.toJSON();
        String encoded = trackJson.toString();
        if (chunk.size() > 0 && chunkBytes + encoded.length() > MAX_CHUNK_BYTES) {
          writeChunk(playlistKey, chunkIndex++, chunk);
          wrote = true;
          chunk = new JSONArray();
          chunkBytes = 0;
        }
        chunk.add(trackJson);
        chunkBytes += encoded.length() + 1;
      }
      if (chunk.size() > 0 || !wrote) {
        writeChunk(playlistKey, chunkIndex++, chunk);
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private void writeChunk(String playlistKey, int chunkIndex, JSONArray tracks)
      throws RecordStoreException {
    JSONObject data = new JSONObject();
    data.put("playlistKey", playlistKey);
    data.put("chunk", chunkIndex);
    data.put("tracks", tracks);
    tracksStorage.addRecord(data.toString());
  }

  private void removeCustomPlaylistTracks(final String playlistKey) {
    Vector entries = findTracksRecordsByKey(playlistKey);
    for (int i = 0; i < entries.size(); i++) {
      try {
        tracksStorage.deleteRecord(((TracksRecordEntry) entries.elementAt(i)).recordId);
      } catch (RecordStoreException e) {
      }
    }
  }

  private int findPlaylistRecord(final Playlist playlist) {
    final int[] found = {-1};
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
    return found[0];
  }
}
