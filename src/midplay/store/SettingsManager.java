package midplay.store;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONException;
import cc.nnproject.json.JSONObject;
import javax.microedition.rms.RecordStoreException;
import midplay.ui.Theme;
import midplay.util.Lang;
import midplay.util.Utils;

public class SettingsManager {
  private static final int SETTINGS_ID = 1;
  private static SettingsManager instance;
  private static String currentLanguage;
  private static String currentService;
  private static String currentQuality;
  private static String currentSearchType;
  private static int currentAutoUpdate;
  private static String currentPlayerMethod;
  private static String currentDownloadPath;
  private static String detectedDefaultMethod;
  private static boolean detectedDefaultMethodResolved;
  private static int currentRepeatMode;
  private static int currentShuffleMode;
  private static int currentVolumeLevel;
  private static String currentThemeMode;
  private static int currentBlackberryWifi;
  private static int currentSaveLastSession;
  private static int currentMpgramAutoLogin;
  private static int currentThumbnails;
  private static boolean currentEqEnabled;
  private static int currentEqPreset; // -1 = manual/custom levels
  private static int[] currentEqLevels;
  private static String currentHttpProxy;
  private static String currentWattpadPath;
  private static int currentWattpadPageChars;
  private static int currentWattpadMature;
  private static int currentWattpadLanguage;
  private static String currentPinterestApiUrl;
  private static String currentPinterestImageProxy;
  private static int currentPinterestPinsPerPage;
  private static String currentPinterestPath;
  private static String currentRedditApiUrl;
  private static String currentRedditImageProxy;
  private static int currentRedditPostsPerPage;
  private static String currentRedditPath;
  private static int currentRedditAutoTranslate;
  private static String currentRssApiUrl;
  private static String currentRssImageProxy;
  private static String currentRssBrowserProxy;
  private static int currentRssBrowserProxyMode;
  private static int currentEconomyMode;

  public static SettingsManager getInstance() {
    if (instance == null) {
      instance = new SettingsManager();
    }
    return instance;
  }

  private final JsonRecordStore storage;
  private JSONObject cachedSettings;

  private JSONObject settings() {
    if (cachedSettings == null) {
      cachedSettings = getSettingsJSON();
    }
    return cachedSettings;
  }

  private SettingsManager() {
    storage =
        new JsonRecordStore(
            Configuration.STORAGE_SETTINGS, SETTINGS_ID, createDefaultSettings().toString());
  }

  public String getDefaultPlayerMethod() {
    if (!detectedDefaultMethodResolved) {
      detectedDefaultMethodResolved = true;
      detectedDefaultMethod = detectPlayerHttpMethod();
    }
    return detectedDefaultMethod;
  }

  // Detect the best MMAPI player method (URL vs InputStream) from device platform
  // quirks. reference https://github.com/shinovon/mpgram-client/blob/master/src/MP.java
  private static String detectPlayerHttpMethod() {
    String platform = System.getProperty("microedition.platform");

    boolean symbianJrt = platform != null && platform.indexOf("platform=S60") != -1;
    boolean symbian =
        symbianJrt
            || Utils.hasProperty("com.symbian.midp.serversocket.support")
            || Utils.hasProperty("com.symbian.default.to.suite.icon")
            || Utils.hasClass("com.symbian.midp.io.protocol.http.Protocol")
            || Utils.hasClass("com.symbian.lcdjava.io.File");

    String method = Configuration.PLAYER_METHOD_PASS_URL;

    if (Utils.hasClass("com.nokia.mid.impl.isa.jam.Jam")) {
      // S40v1 uses sun impl for media and i/o so it should work fine with URL
      // S40v2+ breaks http locator parsing so needs InputStream
      method =
          Utils.hasClass("com.sun.mmedia.protocol.CommonDS")
              ? Configuration.PLAYER_METHOD_PASS_URL
              : Configuration.PLAYER_METHOD_PASS_INPUTSTREAM;
    } else if (symbian) {
      if (symbianJrt
          && platform != null
          && (platform.indexOf("java_build_version=2.") != -1
              || platform.indexOf("java_build_version=1.4") != -1)) {
        // EMC (S60v5+) supports mp3 streaming - keep default URL method
      } else if (Utils.hasClass("com.symbian.mmapi.PlayerImpl")) {
        // UIQ - use InputStream
        method = Configuration.PLAYER_METHOD_PASS_INPUTSTREAM;
      } else {
        // MMF (S60v3.2-) - use InputStream
        method = Configuration.PLAYER_METHOD_PASS_INPUTSTREAM;
      }
    } else if (Utils.isJ2MELoader()) {
      method = Configuration.PLAYER_METHOD_PASS_INPUTSTREAM;
    }

    return method;
  }

  public void loadSettings() {
    JSONObject settings = settings();
    setCurrentLanguage(settings.getString("language", "en"));
    setCurrentThemeMode(settings.getString("themeMode", Configuration.THEME_LIGHT));
    currentService = settings.getString("service", Configuration.SERVICE_NCT);
    currentQuality = settings.getString("quality", Configuration.QUALITY_128);
    currentSearchType = settings.getString("searchType", Configuration.SEARCH_PLAYLIST);
    currentAutoUpdate = settings.getInt("autoUpdate", Configuration.AUTO_UPDATE_ENABLED);
    currentPlayerMethod = settings.getString("playerMethod", getDefaultPlayerMethod());
    currentDownloadPath =
        settings.getString("downloadPath", Configuration.DEFAULT_DOWNLOAD_PATH);
    currentRepeatMode = settings.getInt("repeatMode", Configuration.PLAYER_REPEAT_ALL);
    currentShuffleMode = settings.getInt("shuffleMode", Configuration.PLAYER_SHUFFLE_OFF);
    currentVolumeLevel = settings.getInt("volumeLevel", Configuration.PLAYER_MAX_VOLUME);
    currentBlackberryWifi = settings.getInt("blackberryWifi", Configuration.BLACKBERRY_WIFI_ON);
    currentSaveLastSession =
        settings.getInt("saveLastSession", Configuration.SAVE_LAST_SESSION_OFF);
    currentMpgramAutoLogin =
        settings.getInt("mpgramAutoLogin", Configuration.MPGRAM_AUTO_LOGIN_OFF);
    currentThumbnails = settings.getInt("thumbnails", Configuration.THUMBNAILS_ON);
    currentEqEnabled = settings.getBoolean("eqEnabled", false);
    currentEqPreset = settings.getInt("eqPreset", -1);
    currentEqLevels = parseLevelsCsv(settings.getString("eqLevels", ""));
    currentHttpProxy = settings.getString("httpProxy", Configuration.DEFAULT_HTTP_PROXY);
    currentWattpadPath =
        settings.getString("wattpadPath", Configuration.DEFAULT_WATTPAD_PATH);
    currentWattpadPageChars =
        settings.getInt("wattpadPageChars", Configuration.DEFAULT_WATTPAD_PAGE_CHARS);
    currentWattpadMature = settings.getInt("wattpadMature", Configuration.WATTPAD_MATURE_OFF);
    currentWattpadLanguage = settings.getInt("wattpadLanguage", 0);
    currentPinterestApiUrl =
        settings.getString("pinterestApiUrl", Configuration.DEFAULT_PINTEREST_API_URL);
    currentPinterestImageProxy =
        settings.getString("pinterestImageProxy", Configuration.DEFAULT_PINTEREST_IMAGE_PROXY);
    currentPinterestPinsPerPage =
        settings.getInt(
            "pinterestPinsPerPage", Configuration.DEFAULT_PINTEREST_PINS_PER_PAGE);
    currentPinterestPath =
        settings.getString("pinterestPath", Configuration.DEFAULT_PINTEREST_PATH);
    currentRedditApiUrl =
        settings.getString("redditApiUrl", Configuration.DEFAULT_REDDIT_API_URL);
    currentRedditImageProxy =
        settings.getString("redditImageProxy", Configuration.DEFAULT_REDDIT_IMAGE_PROXY);
    currentRedditPostsPerPage =
        settings.getInt("redditPostsPerPage", Configuration.DEFAULT_REDDIT_POSTS_PER_PAGE);
    currentRedditPath =
        settings.getString("redditPath", Configuration.DEFAULT_REDDIT_PATH);
    currentRedditAutoTranslate =
        settings.getInt("redditAutoTranslate", Configuration.DEFAULT_REDDIT_AUTO_TRANSLATE);
    currentRssApiUrl = settings.getString("rssApiUrl", Configuration.DEFAULT_RSS_API_URL);
    currentRssImageProxy =
        settings.getString("rssImageProxy", Configuration.DEFAULT_RSS_IMAGE_PROXY);
    currentRssBrowserProxy =
        settings.getString("rssBrowserProxy", Configuration.DEFAULT_RSS_BROWSER_PROXY);
    currentRssBrowserProxyMode =
        settings.getInt("rssBrowserProxyMode", Configuration.DEFAULT_RSS_BROWSER_PROXY_MODE);
    currentEconomyMode = settings.getInt("economyMode", Configuration.ECONOMY_MODE_OFF);
  }

  private JSONObject getSettingsJSON() {
    try {
      return JSON.getObject(storage.load());
    } catch (JSONException e) {
    }
    return createDefaultSettings();
  }

  private JSONObject createDefaultSettings() {
    JSONObject settings = new JSONObject();
    settings.put("language", "en");
    settings.put("service", Configuration.SERVICE_NCT);
    settings.put("quality", Configuration.QUALITY_128);
    settings.put("repeatMode", Configuration.PLAYER_REPEAT_ALL);
    settings.put("shuffleMode", Configuration.PLAYER_SHUFFLE_OFF);
    settings.put("volumeLevel", Configuration.PLAYER_MAX_VOLUME);
    settings.put("searchType", Configuration.SEARCH_PLAYLIST);
    settings.put("autoUpdate", Configuration.AUTO_UPDATE_ENABLED);
    settings.put("playerMethod", getDefaultPlayerMethod());
    settings.put("downloadPath", Configuration.DEFAULT_DOWNLOAD_PATH);
    settings.put("themeMode", Configuration.THEME_LIGHT);
    settings.put("blackberryWifi", Configuration.BLACKBERRY_WIFI_ON);
    settings.put("saveLastSession", Configuration.SAVE_LAST_SESSION_OFF);
    settings.put("mpgramAutoLogin", Configuration.MPGRAM_AUTO_LOGIN_OFF);
    settings.put("thumbnails", Configuration.THUMBNAILS_ON);
    settings.put("eqEnabled", false);
    settings.put("eqPreset", -1);
    settings.put("eqLevels", "");
    settings.put("httpProxy", Configuration.DEFAULT_HTTP_PROXY);
    settings.put("wattpadPath", Configuration.DEFAULT_WATTPAD_PATH);
    settings.put("wattpadPageChars", Configuration.DEFAULT_WATTPAD_PAGE_CHARS);
    settings.put("wattpadMature", Configuration.WATTPAD_MATURE_OFF);
    settings.put("wattpadLanguage", 0);
    settings.put("pinterestApiUrl", Configuration.DEFAULT_PINTEREST_API_URL);
    settings.put("pinterestImageProxy", Configuration.DEFAULT_PINTEREST_IMAGE_PROXY);
    settings.put("pinterestPinsPerPage", Configuration.DEFAULT_PINTEREST_PINS_PER_PAGE);
    settings.put("pinterestPath", Configuration.DEFAULT_PINTEREST_PATH);
    settings.put("redditApiUrl", Configuration.DEFAULT_REDDIT_API_URL);
    settings.put("redditImageProxy", Configuration.DEFAULT_REDDIT_IMAGE_PROXY);
    settings.put("redditPostsPerPage", Configuration.DEFAULT_REDDIT_POSTS_PER_PAGE);
    settings.put("redditPath", Configuration.DEFAULT_REDDIT_PATH);
    settings.put("redditAutoTranslate", Configuration.DEFAULT_REDDIT_AUTO_TRANSLATE);
    settings.put("rssApiUrl", Configuration.DEFAULT_RSS_API_URL);
    settings.put("rssImageProxy", Configuration.DEFAULT_RSS_IMAGE_PROXY);
    settings.put("rssBrowserProxy", Configuration.DEFAULT_RSS_BROWSER_PROXY);
    settings.put("rssBrowserProxyMode", Configuration.DEFAULT_RSS_BROWSER_PROXY_MODE);
    settings.put("economyMode", Configuration.ECONOMY_MODE_OFF);
    settings.put("vaultPasswordHash", "");
    return settings;
  }

  private void saveJSON(JSONObject settings) throws RecordStoreException {
    storage.save(settings.toString());
  }

  private void saveSetting(String key, String value) throws RecordStoreException {
    JSONObject settings = settings();
    if (settings.has(key) && value != null && value.equals(settings.getString(key, ""))) {
      return;
    }
    settings.put(key, value);
    saveJSON(settings);
  }

  private void saveSetting(String key, int value) throws RecordStoreException {
    JSONObject settings = settings();
    if (settings.has(key) && value == settings.getInt(key, value)) {
      return;
    }
    settings.put(key, value);
    saveJSON(settings);
  }

  private void saveSetting(String key, JSONObject value) throws RecordStoreException {
    JSONObject settings = settings();
    if (settings.has(key) && value != null) {
      try {
        JSONObject existing = settings.getObject(key);
        if (existing != null && value.build().equals(existing.build())) {
          return;
        }
      } catch (Exception e) {
      }
    }
    settings.put(key, value);
    saveJSON(settings);
  }

  public void saveLanguage(String langCode) throws RecordStoreException {
    saveSetting("language", langCode);
    setCurrentLanguage(langCode);
  }

  public void saveTheme(String mode) throws RecordStoreException {
    saveSetting("themeMode", mode);
    setCurrentThemeMode(mode);
  }

  public void saveService(String serviceCode) throws RecordStoreException {
    saveSetting("service", serviceCode);
    currentService = serviceCode;
  }

  public void saveQuality(String qualityCode) throws RecordStoreException {
    saveSetting("quality", qualityCode);
    currentQuality = qualityCode;
  }

  public void saveSearchType(String searchType) throws RecordStoreException {
    saveSetting("searchType", searchType);
    currentSearchType = searchType;
  }

  public void saveAutoUpdate(int autoUpdate) throws RecordStoreException {
    saveSetting("autoUpdate", autoUpdate);
    currentAutoUpdate = autoUpdate;
  }

  public void saveSaveLastSession(int saveLastSession) throws RecordStoreException {
    saveSetting("saveLastSession", saveLastSession);
    currentSaveLastSession = saveLastSession;
  }

  public void saveMpgramAutoLogin(int mpgramAutoLogin) throws RecordStoreException {
    saveSetting("mpgramAutoLogin", mpgramAutoLogin);
    currentMpgramAutoLogin = mpgramAutoLogin;
  }

  public void savePlayerMethod(String playerMethod) throws RecordStoreException {
    saveSetting("playerMethod", playerMethod);
    currentPlayerMethod = playerMethod;
  }

  public void saveDownloadPath(String downloadPath) throws RecordStoreException {
    saveSetting("downloadPath", downloadPath);
    currentDownloadPath = downloadPath;
  }

  public String getVaultPasswordHash() {
    try {
      return settings().getString("vaultPasswordHash", "");
    } catch (Exception e) {
      return "";
    }
  }

  public void saveVaultPassword(String hash) throws RecordStoreException {
    saveSetting("vaultPasswordHash", hash == null ? "" : hash);
  }

  public String getCurrentDownloadPath() {
    if (currentDownloadPath == null || currentDownloadPath.length() == 0) {
      currentDownloadPath = Configuration.DEFAULT_DOWNLOAD_PATH;
    }
    return currentDownloadPath;
  }

  /** HTTP proxy that routes Wattpad's https requests; edited in the Wattpad settings screen. */
  public String getCurrentHttpProxy() {
    if (currentHttpProxy == null) {
      currentHttpProxy = Configuration.DEFAULT_HTTP_PROXY;
    }
    return currentHttpProxy;
  }

  public void saveHttpProxy(String proxy) throws RecordStoreException {
    String value = proxy == null ? "" : proxy.trim();
    saveSetting("httpProxy", value);
    currentHttpProxy = value;
  }

  /**
   * Routes https URLs through the configured http->https proxy. The proxy reads the raw query
   * string as the target URL, so the URL is appended verbatim. Plain http URLs pass through.
   */
  public String routeHttp(String url) {
    if (url == null) {
      return "";
    }
    String proxy = getCurrentHttpProxy();
    if (proxy != null && proxy.length() > 0 && url.startsWith("https://")) {
      return proxy + url;
    }
    return url;
  }

  /** Folder where downloaded Wattpad stories are stored on the memory card. */
  public String getWattpadPath() {
    if (currentWattpadPath == null || currentWattpadPath.length() == 0) {
      currentWattpadPath = Configuration.DEFAULT_WATTPAD_PATH;
    }
    return currentWattpadPath;
  }

  public void saveWattpadPath(String path) throws RecordStoreException {
    String value = path == null ? "" : path.trim();
    if (value.length() == 0) {
      value = Configuration.DEFAULT_WATTPAD_PATH;
    }
    saveSetting("wattpadPath", value);
    currentWattpadPath = value;
  }

  /** Maximum number of characters shown per Wattpad chapter page. */
  public int getWattpadPageChars() {
    if (currentWattpadPageChars < Configuration.WATTPAD_PAGE_CHARS_MIN
        || currentWattpadPageChars > Configuration.WATTPAD_PAGE_CHARS_MAX) {
      currentWattpadPageChars = Configuration.DEFAULT_WATTPAD_PAGE_CHARS;
    }
    return currentWattpadPageChars;
  }

  public void saveWattpadPageChars(int chars) throws RecordStoreException {
    int value = chars;
    if (value < Configuration.WATTPAD_PAGE_CHARS_MIN) {
      value = Configuration.WATTPAD_PAGE_CHARS_MIN;
    } else if (value > Configuration.WATTPAD_PAGE_CHARS_MAX) {
      value = Configuration.WATTPAD_PAGE_CHARS_MAX;
    }
    saveSetting("wattpadPageChars", value);
    currentWattpadPageChars = value;
  }

  public int getCurrentWattpadMature() {
    return currentWattpadMature;
  }

  public void saveWattpadMature(int mature) throws RecordStoreException {
    saveSetting("wattpadMature", mature);
    currentWattpadMature = mature;
  }

  /** Wattpad language id used to filter searches, or 0 for every language. */
  public int getWattpadLanguage() {
    return currentWattpadLanguage;
  }

  public void saveWattpadLanguage(int language) throws RecordStoreException {
    saveSetting("wattpadLanguage", language);
    currentWattpadLanguage = language;
  }

  /** Base URL of the Pinterest JSON proxy (defaults to {@code opentty.fun/pinterest.php}). */
  public String getPinterestApiUrl() {
    if (currentPinterestApiUrl == null || currentPinterestApiUrl.length() == 0) {
      currentPinterestApiUrl = Configuration.DEFAULT_PINTEREST_API_URL;
    }
    return currentPinterestApiUrl;
  }

  public void savePinterestApiUrl(String url) throws RecordStoreException {
    String value = url == null ? "" : url.trim();
    if (value.length() == 0) {
      value = Configuration.DEFAULT_PINTEREST_API_URL;
    }
    saveSetting("pinterestApiUrl", value);
    currentPinterestApiUrl = value;
  }

  /** Prefix used to fetch Pinterest images, ending with {@code url=} by default. */
  public String getPinterestImageProxy() {
    if (currentPinterestImageProxy == null || currentPinterestImageProxy.length() == 0) {
      currentPinterestImageProxy = Configuration.DEFAULT_PINTEREST_IMAGE_PROXY;
    }
    return currentPinterestImageProxy;
  }

  public void savePinterestImageProxy(String url) throws RecordStoreException {
    String value = url == null ? "" : url.trim();
    if (value.length() == 0) {
      value = Configuration.DEFAULT_PINTEREST_IMAGE_PROXY;
    }
    saveSetting("pinterestImageProxy", value);
    currentPinterestImageProxy = value;
  }

  /** Number of pins/boards requested per page (1..25). */
  public int getPinterestPinsPerPage() {
    if (currentPinterestPinsPerPage < Configuration.PINTEREST_PINS_PER_PAGE_MIN
        || currentPinterestPinsPerPage > Configuration.PINTEREST_PINS_PER_PAGE_MAX) {
      currentPinterestPinsPerPage = Configuration.DEFAULT_PINTEREST_PINS_PER_PAGE;
    }
    return currentPinterestPinsPerPage;
  }

  public void savePinterestPinsPerPage(int count) throws RecordStoreException {
    int value = count;
    if (value < Configuration.PINTEREST_PINS_PER_PAGE_MIN) {
      value = Configuration.PINTEREST_PINS_PER_PAGE_MIN;
    } else if (value > Configuration.PINTEREST_PINS_PER_PAGE_MAX) {
      value = Configuration.PINTEREST_PINS_PER_PAGE_MAX;
    }
    saveSetting("pinterestPinsPerPage", value);
    currentPinterestPinsPerPage = value;
  }

  /** Folder where downloaded Pinterest photos are stored on the memory card. */
  public String getPinterestPath() {
    if (currentPinterestPath == null || currentPinterestPath.length() == 0) {
      currentPinterestPath = Configuration.DEFAULT_PINTEREST_PATH;
    }
    return currentPinterestPath;
  }

  public void savePinterestPath(String path) throws RecordStoreException {
    String value = path == null ? "" : path.trim();
    if (value.length() == 0) {
      value = Configuration.DEFAULT_PINTEREST_PATH;
    }
    saveSetting("pinterestPath", value);
    currentPinterestPath = value;
  }

  /** Base URL of the Reddit JSON proxy (defaults to {@code opentty.fun/reddit.php}). */
  public String getRedditApiUrl() {
    if (currentRedditApiUrl == null || currentRedditApiUrl.length() == 0) {
      currentRedditApiUrl = Configuration.DEFAULT_REDDIT_API_URL;
    }
    return currentRedditApiUrl;
  }

  public void saveRedditApiUrl(String url) throws RecordStoreException {
    String value = url == null ? "" : url.trim();
    if (value.length() == 0) {
      value = Configuration.DEFAULT_REDDIT_API_URL;
    }
    saveSetting("redditApiUrl", value);
    currentRedditApiUrl = value;
  }

  /** Prefix used to fetch Reddit images, ending with {@code url=} by default. */
  public String getRedditImageProxy() {
    if (currentRedditImageProxy == null || currentRedditImageProxy.length() == 0) {
      currentRedditImageProxy = Configuration.DEFAULT_REDDIT_IMAGE_PROXY;
    }
    return currentRedditImageProxy;
  }

  public void saveRedditImageProxy(String url) throws RecordStoreException {
    String value = url == null ? "" : url.trim();
    if (value.length() == 0) {
      value = Configuration.DEFAULT_REDDIT_IMAGE_PROXY;
    }
    saveSetting("redditImageProxy", value);
    currentRedditImageProxy = value;
  }

  /** Number of posts requested per page (1..25). */
  public int getRedditPostsPerPage() {
    if (currentRedditPostsPerPage < Configuration.REDDIT_POSTS_PER_PAGE_MIN
        || currentRedditPostsPerPage > Configuration.REDDIT_POSTS_PER_PAGE_MAX) {
      currentRedditPostsPerPage = Configuration.DEFAULT_REDDIT_POSTS_PER_PAGE;
    }
    return currentRedditPostsPerPage;
  }

  public void saveRedditPostsPerPage(int count) throws RecordStoreException {
    int value = count;
    if (value < Configuration.REDDIT_POSTS_PER_PAGE_MIN) {
      value = Configuration.REDDIT_POSTS_PER_PAGE_MIN;
    } else if (value > Configuration.REDDIT_POSTS_PER_PAGE_MAX) {
      value = Configuration.REDDIT_POSTS_PER_PAGE_MAX;
    }
    saveSetting("redditPostsPerPage", value);
    currentRedditPostsPerPage = value;
  }

  /** Folder where downloaded Reddit photos are stored on the memory card. */
  public String getRedditPath() {
    if (currentRedditPath == null || currentRedditPath.length() == 0) {
      currentRedditPath = Configuration.DEFAULT_REDDIT_PATH;
    }
    return currentRedditPath;
  }

  public void saveRedditPath(String path) throws RecordStoreException {
    String value = path == null ? "" : path.trim();
    if (value.length() == 0) {
      value = Configuration.DEFAULT_REDDIT_PATH;
    }
    saveSetting("redditPath", value);
    currentRedditPath = value;
  }

  /** When on, Reddit post titles and bodies are translated automatically. */
  public int getRedditAutoTranslate() {
    return currentRedditAutoTranslate == Configuration.REDDIT_TRANSLATE_ON
        ? Configuration.REDDIT_TRANSLATE_ON
        : Configuration.REDDIT_TRANSLATE_OFF;
  }

  public void saveRedditAutoTranslate(int mode) throws RecordStoreException {
    int value =
        mode == Configuration.REDDIT_TRANSLATE_ON
            ? Configuration.REDDIT_TRANSLATE_ON
            : Configuration.REDDIT_TRANSLATE_OFF;
    saveSetting("redditAutoTranslate", value);
    currentRedditAutoTranslate = value;
  }

  /** Base URL of the RSS proxy (defaults to {@code opentty.fun/rss.php}). */
  public String getRssApiUrl() {
    if (currentRssApiUrl == null || currentRssApiUrl.length() == 0) {
      currentRssApiUrl = Configuration.DEFAULT_RSS_API_URL;
    }
    return currentRssApiUrl;
  }

  public void saveRssApiUrl(String url) throws RecordStoreException {
    String value = url == null ? "" : url.trim();
    if (value.length() == 0) {
      value = Configuration.DEFAULT_RSS_API_URL;
    }
    saveSetting("rssApiUrl", value);
    currentRssApiUrl = value;
  }

  /** Prefix used to fetch feed images, ending with {@code url=} by default. */
  public String getRssImageProxy() {
    if (currentRssImageProxy == null || currentRssImageProxy.length() == 0) {
      currentRssImageProxy = Configuration.DEFAULT_RSS_IMAGE_PROXY;
    }
    return currentRssImageProxy;
  }

  public void saveRssImageProxy(String url) throws RecordStoreException {
    String value = url == null ? "" : url.trim();
    if (value.length() == 0) {
      value = Configuration.DEFAULT_RSS_IMAGE_PROXY;
    }
    saveSetting("rssImageProxy", value);
    currentRssImageProxy = value;
  }

  /** Prefix used to open HTTPS article links through the browser proxy. */
  public String getRssBrowserProxy() {
    if (currentRssBrowserProxy == null || currentRssBrowserProxy.length() == 0) {
      currentRssBrowserProxy = Configuration.DEFAULT_RSS_BROWSER_PROXY;
    }
    return currentRssBrowserProxy;
  }

  public void saveRssBrowserProxy(String url) throws RecordStoreException {
    String value = url == null ? "" : url.trim();
    if (value.length() == 0) {
      value = Configuration.DEFAULT_RSS_BROWSER_PROXY;
    }
    saveSetting("rssBrowserProxy", value);
    currentRssBrowserProxy = value;
  }

  public int getRssBrowserProxyMode() {
    return currentRssBrowserProxyMode == Configuration.RSS_BROWSER_PROXY_ON
        ? Configuration.RSS_BROWSER_PROXY_ON
        : Configuration.RSS_BROWSER_PROXY_OFF;
  }

  public void saveRssBrowserProxyMode(int mode) throws RecordStoreException {
    int value =
        mode == Configuration.RSS_BROWSER_PROXY_ON
            ? Configuration.RSS_BROWSER_PROXY_ON
            : Configuration.RSS_BROWSER_PROXY_OFF;
    saveSetting("rssBrowserProxyMode", value);
    currentRssBrowserProxyMode = value;
  }

  public int getEconomyMode() {
    return currentEconomyMode;
  }

  public void saveEconomyMode(int mode) throws RecordStoreException {
    saveSetting("economyMode", mode);
    currentEconomyMode = mode;
  }

  public void saveRepeatMode(int repeatMode) throws RecordStoreException {
    saveSetting("repeatMode", repeatMode);
    currentRepeatMode = repeatMode;
  }

  public void saveShuffleMode(int shuffleMode) throws RecordStoreException {
    saveSetting("shuffleMode", shuffleMode);
    currentShuffleMode = shuffleMode;
  }

  public void saveVolumeLevel(int volumeLevel) throws RecordStoreException {
    saveSetting("volumeLevel", volumeLevel);
    currentVolumeLevel = volumeLevel;
  }

  public void saveBlackberryWifi(int blackberryWifi) throws RecordStoreException {
    saveSetting("blackberryWifi", blackberryWifi);
    currentBlackberryWifi = blackberryWifi;
  }

  public void saveThumbnails(int thumbnails) throws RecordStoreException {
    saveSetting("thumbnails", thumbnails);
    currentThumbnails = thumbnails;
  }

  public void saveThemeColors(JSONObject lightColors, JSONObject darkColors, int selected)
      throws RecordStoreException {
    JSONObject themeData = new JSONObject();
    themeData.put("light", lightColors);
    themeData.put("dark", darkColors);
    themeData.put("selected", selected);
    saveSetting("themeColors", themeData);
  }

  public void loadAndApplyThemeColors() {
    try {
      JSONObject settings = settings();
      if (settings.has("themeColors")) {
        JSONObject themeData = settings.getObject("themeColors");
        String colorType = Theme.isDark() ? "dark" : "light";
        if (themeData.has(colorType)) {
          JSONObject colors = themeData.getObject(colorType);
          Theme.applyColors(colors);
          return;
        }
      }
    } catch (Exception e) {
    }
    Theme.applyDefaults(Theme.isDark());
  }

  public int getSavedColorIndex() {
    try {
      JSONObject settings = settings();
      if (settings.has("themeColors")) {
        JSONObject themeData = settings.getObject("themeColors");
        return themeData.getInt("selected", 0);
      }
    } catch (Exception e) {
    }
    return 0;
  }

  public String getCurrentLanguage() {
    return currentLanguage;
  }

  public void setCurrentLanguage(String langCode) {
    currentLanguage = langCode;
    Lang.setLang(langCode);
  }

  public String getCurrentThemeMode() {
    return currentThemeMode;
  }

  public String getCurrentService() {
    return currentService;
  }

  public void setCurrentThemeMode(String mode) {
    currentThemeMode = mode;
    Theme.setDark(Configuration.THEME_DARK.equals(mode));
    loadAndApplyThemeColors();
  }

  public String getCurrentQuality() {
    return currentQuality;
  }

  public String getCurrentSearchType() {
    return currentSearchType;
  }

  public int getCurrentAutoUpdate() {
    return currentAutoUpdate;
  }

  public int getCurrentSaveLastSession() {
    return currentSaveLastSession;
  }

  public int getCurrentMpgramAutoLogin() {
    return currentMpgramAutoLogin;
  }

  public String getCurrentPlayerMethod() {
    return currentPlayerMethod;
  }

  public int getCurrentRepeatMode() {
    return currentRepeatMode;
  }

  public int getCurrentShuffleMode() {
    return currentShuffleMode;
  }

  public int getCurrentVolumeLevel() {
    return currentVolumeLevel;
  }

  public int getCurrentBlackberryWifi() {
    return currentBlackberryWifi;
  }

  public int getCurrentThumbnails() {
    return currentThumbnails;
  }

  public void setEqEnabledLive(boolean enabled) {
    currentEqEnabled = enabled;
  }

  public void setEqPresetLive(int preset) {
    currentEqPreset = preset;
  }

  public void setEqLevelsLive(int[] levels) {
    currentEqLevels = levels;
  }

  public boolean isEqEnabled() {
    return currentEqEnabled;
  }

  public int getEqPreset() {
    return currentEqPreset;
  }

  public int[] getEqLevels() {
    return currentEqLevels;
  }

  public void saveEqualizer(boolean enabled, int preset, int[] levels) throws RecordStoreException {
    JSONObject settings = settings();
    settings.put("eqEnabled", enabled);
    settings.put("eqPreset", preset);
    settings.put("eqLevels", toLevelsCsv(levels));
    saveJSON(settings);
    currentEqEnabled = enabled;
    currentEqPreset = preset;
    currentEqLevels = levels;
  }

  private static String toLevelsCsv(int[] levels) {
    if (levels == null || levels.length == 0) {
      return "";
    }
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < levels.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(levels[i]);
    }
    return sb.toString();
  }

  private static int[] parseLevelsCsv(String csv) {
    if (csv == null || csv.length() == 0) {
      return null;
    }
    int count = 1;
    for (int i = 0; i < csv.length(); i++) {
      if (csv.charAt(i) == ',') {
        count++;
      }
    }
    int[] result = new int[count];
    int idx = 0;
    int start = 0;
    for (int i = 0; i <= csv.length(); i++) {
      if (i == csv.length() || csv.charAt(i) == ',') {
        try {
          result[idx] = Integer.parseInt(csv.substring(start, i).trim());
        } catch (NumberFormatException e) {
          result[idx] = 0;
        }
        idx++;
        start = i + 1;
      }
    }
    return result;
  }

  public void cleanup() {
    storage.close();
  }
}
