package midplay.store;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONException;
import cc.nnproject.json.JSONObject;
import javax.microedition.rms.RecordStoreException;

/** Persists the weather city chosen by the user. */
public class WeatherManager {
  private static final int RECORD_ID = 1;
  private static WeatherManager instance;

  public static synchronized WeatherManager getInstance() {
    if (instance == null) {
      instance = new WeatherManager();
    }
    return instance;
  }

  private final JsonRecordStore storage;
  private JSONObject settings;

  private WeatherManager() {
    storage =
        new JsonRecordStore(Configuration.STORAGE_WEATHER, RECORD_ID, defaults().toString());
    load();
  }

  private static JSONObject defaults() {
    JSONObject object = new JSONObject();
    object.put("city", Configuration.DEFAULT_WEATHER_CITY);
    return object;
  }

  private void load() {
    try {
      settings = JSON.getObject(storage.load());
    } catch (JSONException e) {
      settings = defaults();
    }
    if (settings == null) {
      settings = defaults();
    }
  }

  private JSONObject data() {
    if (settings == null) {
      load();
    }
    return settings;
  }

  public String getCity() {
    return data().getString("city", Configuration.DEFAULT_WEATHER_CITY);
  }

  public void setCity(String city) throws RecordStoreException {
    data().put("city", city);
    storage.save(data().toString());
  }

  public void close() {
    storage.close();
  }
}
