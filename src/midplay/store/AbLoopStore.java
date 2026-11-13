package midplay.store;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import java.util.Enumeration;
import java.util.Hashtable;
import midplay.model.Track;

/**
 * Guarda os pontos A/B do loop por faixa, de forma persistente (RMS).
 *
 * <p>Cada faixa tem sua propria entrada ({@code id}, {@code a}, {@code b}, {@code on}), entao o
 * loop de uma musica nao interfere no de outra. A identidade da faixa e o {@code Key} do catalogo
 * quando existe; para arquivos locais usamos a URL/caminho.
 */
public final class AbLoopStore {
  private static final int STORE_ID = 1;
  /** Limite de faixas com loop salvo, para o RMS nao crescer sem controle. */
  private static final int MAX_ENTRIES = 200;

  private static AbLoopStore instance;

  public static synchronized AbLoopStore getInstance() {
    if (instance == null) {
      instance = new AbLoopStore();
    }
    return instance;
  }

  private final JsonRecordStore storage;
  private Hashtable entries;

  private AbLoopStore() {
    storage = new JsonRecordStore(Configuration.STORAGE_AB_LOOPS, STORE_ID, "");
  }

  /** Identidade estavel da faixa para indexar o loop salvo. */
  public static String trackId(Track track) {
    if (track == null) {
      return null;
    }
    String key = track.getKey();
    if (key != null) {
      key = key.trim();
      if (key.length() > 0) {
        return "k:" + key;
      }
    }
    String url = track.getUrl();
    if (url != null) {
      url = url.trim();
      if (url.length() > 0) {
        return "u:" + url;
      }
    }
    return null;
  }

  /** Retorna {@code long[]{a, b, on(0/1)}} ou null se a faixa nao tem loop salvo. */
  public synchronized long[] get(Track track) {
    String id = trackId(track);
    if (id == null) {
      return null;
    }
    long[] value = (long[]) entries().get(id);
    if (value == null) {
      return null;
    }
    return new long[] {value[0], value[1], value[2]};
  }

  public synchronized void save(Track track, long aMicros, long bMicros, boolean enabled) {
    String id = trackId(track);
    if (id == null || aMicros < 0L || bMicros <= aMicros) {
      return;
    }
    Hashtable map = entries();
    if (!map.containsKey(id) && map.size() >= MAX_ENTRIES) {
      return;
    }
    map.put(id, new long[] {aMicros, bMicros, enabled ? 1L : 0L});
    persist(map);
  }

  public synchronized void remove(Track track) {
    String id = trackId(track);
    if (id == null) {
      return;
    }
    Hashtable map = entries();
    if (map.remove(id) != null) {
      persist(map);
    }
  }

  private Hashtable entries() {
    if (entries == null) {
      entries = load();
    }
    return entries;
  }

  private Hashtable load() {
    Hashtable map = new Hashtable();
    String raw;
    try {
      raw = storage.load();
    } catch (Exception e) {
      return map;
    }
    if (raw == null || raw.trim().length() == 0) {
      return map;
    }
    try {
      JSONArray array = JSONArray.parseArray(raw);
      for (int i = 0; i < array.size(); i++) {
        JSONObject item;
        try {
          item = array.getObject(i);
        } catch (Exception e) {
          continue;
        }
        if (item == null) {
          continue;
        }
        String id = item.getString("id", "");
        if (id.length() == 0) {
          continue;
        }
        long a = item.getLong("a", -1L);
        long b = item.getLong("b", -1L);
        boolean on = item.getBoolean("on", false);
        if (a < 0L || b <= a) {
          continue;
        }
        map.put(id, new long[] {a, b, on ? 1L : 0L});
      }
    } catch (Exception e) {
      return new Hashtable();
    }
    return map;
  }

  private void persist(Hashtable map) {
    JSONArray array = new JSONArray();
    Enumeration keys = map.keys();
    while (keys.hasMoreElements()) {
      String id = (String) keys.nextElement();
      long[] value = (long[]) map.get(id);
      if (value == null) {
        continue;
      }
      JSONObject item = new JSONObject();
      item.put("id", id);
      item.put("a", value[0]);
      item.put("b", value[1]);
      item.put("on", value[2] == 1L);
      array.add(item);
    }
    storage.saveAsync(array.toString());
  }
}
