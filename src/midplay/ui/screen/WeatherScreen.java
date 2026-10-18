package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;
import midplay.MIDPlay;
import midplay.net.HttpTextOperation;
import midplay.store.WeatherManager;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;
import midplay.util.Utils;
import midplay.util.WebText;

/** Shows the current weather (wttr.in) as a form. */
public final class WeatherScreen extends BaseForm {
  private final Command refreshCommand;
  private final Command cityCommand;
  private final StringItem content = new StringItem(null, Lang.tr("weather.loading"));
  private boolean loading;
  private boolean loaded;

  public WeatherScreen(Navigator navigator) {
    super(Lang.tr("weather.title"), navigator);
    refreshCommand = new Command(Lang.tr("weather.refresh"), Command.SCREEN, 1);
    cityCommand = new Command(Lang.tr("weather.set_city"), Command.SCREEN, 2);
    append(content);
    addCommand(refreshCommand);
    addCommand(cityCommand);
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == refreshCommand) {
      startRefresh();
    } else if (c == cityCommand) {
      promptCity();
    }
  }

  protected void showNotify() {
    if (!loaded && !loading) {
      startRefresh();
    }
  }

  private void startRefresh() {
    if (loading) {
      return;
    }
    loading = true;
    loaded = true;
    content.setText(Lang.tr("weather.loading"));
    String city = WeatherManager.getInstance().getCity();
    String url =
        "http://wttr.in/"
            + Utils.urlEncode(city)
            + "?format=%25C~%25t~%25f~%25w~%25h~%25p&m&lang="
            + Lang.getCurrentLang();
    MIDPlay.startOperation(
        new HttpTextOperation(
            url,
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        content.setText(formatWeather(text));
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        content.setText(Lang.tr("weather.error") + "\n\n" + e.toString());
                      }
                    });
              }
            }));
  }

  private String formatWeather(String text) {
    if (text == null) {
      return Lang.tr("weather.error");
    }
    String[] fields = split(text.trim(), '~');
    if (fields.length < 2) {
      return Lang.tr("weather.error") + "\n\n" + snippet(text, 200);
    }
    StringBuffer sb = new StringBuffer();
    sb.append(WebText.ascii(WeatherManager.getInstance().getCity())).append("\n\n");
    sb.append(Lang.tr("weather.condition")).append(": ").append(WebText.ascii(fields[0])).append('\n');
    sb.append(Lang.tr("weather.temp")).append(": ").append(WebText.ascii(fields[1])).append('\n');
    if (fields.length > 2) {
      sb.append(Lang.tr("weather.feels")).append(": ").append(WebText.ascii(fields[2])).append('\n');
    }
    if (fields.length > 3) {
      sb.append(Lang.tr("weather.wind")).append(": ").append(WebText.ascii(fields[3])).append('\n');
    }
    if (fields.length > 4) {
      sb.append(Lang.tr("weather.humidity")).append(": ").append(WebText.ascii(fields[4])).append('\n');
    }
    if (fields.length > 5) {
      sb.append(Lang.tr("weather.precip")).append(": ").append(WebText.ascii(fields[5])).append('\n');
    }
    return sb.toString();
  }

  private void promptCity() {
    final Form form = new Form(Lang.tr("weather.city_prompt"));
    final TextField field =
        new TextField(
            Lang.tr("weather.city_field"), WeatherManager.getInstance().getCity(), 60, TextField.ANY);
    form.append(field);
    form.addCommand(Commands.ok());
    form.addCommand(Commands.cancel());
    form.setCommandListener(
        new CommandListener() {
          public void commandAction(Command c, Displayable d) {
            if (c == Commands.ok()) {
              String value = trim(field.getString());
              if (value.length() == 0) {
                navigator.showAlert(Lang.tr("weather.city_field"), AlertType.ERROR);
                return;
              }
              try {
                WeatherManager.getInstance().setCity(value);
                navigator.back();
                loaded = false;
                startRefresh();
              } catch (Exception e) {
                navigator.showAlert(e.toString(), AlertType.ERROR);
              }
            } else if (c == Commands.cancel()) {
              navigator.back();
            }
          }
        });
    navigator.forward(form);
  }

  private static String trim(String s) {
    return s == null ? "" : s.trim();
  }

  private static String snippet(String s, int max) {
    if (s == null) {
      return "(null)";
    }
    String t = s.trim();
    return t.length() <= max ? t : t.substring(0, max) + "...";
  }

  private static String[] split(String s, char delimiter) {
    java.util.Vector parts = new java.util.Vector();
    int start = 0;
    for (int i = 0; i <= s.length(); i++) {
      if (i == s.length() || s.charAt(i) == delimiter) {
        parts.addElement(s.substring(start, i));
        start = i + 1;
      }
    }
    String[] result = new String[parts.size()];
    for (int i = 0; i < parts.size(); i++) {
      result[i] = (String) parts.elementAt(i);
    }
    return result;
  }
}
