package midplay.net;

public class CheckUpdateOperation extends NetworkOperation {
  private final Listener listener;

  public CheckUpdateOperation(Listener listener) {
    this.listener = listener;
  }

  protected void execute() {
    fetchText(URLProvider.checkForUpdate());
  }

  protected void processResponse(String response) {
    if (response == null || response.trim().length() == 0) {
      this.listener.onNoUpdateAvailable();
      return;
    }

    String body = response.trim();

    // Compatibilidade: respostas antigas eram apenas uma URL pura.
    if (body.indexOf("update=") < 0
        && body.indexOf("jad_url=") < 0
        && body.indexOf("jar_url=") < 0) {
      this.listener.onUpdateAvailable(body);
      return;
    }

    String updateFlag = getField(body, "update");
    String jadUrl = getField(body, "jad_url");
    String jarUrl = getField(body, "jar_url");
    String target = (jadUrl != null && jadUrl.length() > 0) ? jadUrl : jarUrl;

    if ("1".equals(updateFlag) && target != null && target.length() > 0) {
      this.listener.onUpdateAvailable(target);
    } else {
      this.listener.onNoUpdateAvailable();
    }
  }

  private static String getField(String body, String name) {
    String prefix = name + "=";
    int start = 0;
    while (start <= body.length()) {
      int end = body.indexOf('\n', start);
      if (end < 0) {
        end = body.length();
      }
      String line = body.substring(start, end).trim();
      if (line.startsWith(prefix)) {
        return line.substring(prefix.length()).trim();
      }
      start = end + 1;
    }
    return null;
  }

  protected void handleNoData() {
    this.listener.onNoUpdateAvailable();
  }

  protected void handleError(Exception e) {
    this.listener.onError(e);
  }

  public interface Listener {
    void onUpdateAvailable(String updateUrl);

    void onNoUpdateAvailable();

    void onError(Exception e);
  }
}
