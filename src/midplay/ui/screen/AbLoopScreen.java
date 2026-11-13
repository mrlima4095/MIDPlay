package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextField;
import midplay.player.AbLoopController;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;

/** Form para definir os pontos A e B do loop em MM:SS. */
public final class AbLoopScreen extends BaseForm {
  private static final int MAX_FIELD_LEN = 8;
  private static final long MAX_SECONDS = 24L * 60L * 60L;

  private final TextField fieldA;
  private final TextField fieldB;
  private final AbLoopCallback callback;

  public AbLoopScreen(Navigator navigator, AbLoopCallback callback, long pointAMicros, long pointBMicros) {
    super(Lang.tr("loop.define"), navigator);
    this.callback = callback;
    this.fieldA = new TextField(Lang.tr("loop.point_a"), formatMicros(pointAMicros), MAX_FIELD_LEN, TextField.ANY);
    this.fieldB = new TextField(Lang.tr("loop.point_b"), formatMicros(pointBMicros), MAX_FIELD_LEN, TextField.ANY);
    append(fieldA);
    append(fieldB);
    addCommand(Commands.formSave());
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == Commands.formSave()) {
      long a = parseMicros(fieldA.getString());
      long b = parseMicros(fieldB.getString());
      if (a < 0 || b < 0) {
        navigator.showAlert(Lang.tr("loop.error.format"), AlertType.ERROR);
        return;
      }
      if (b <= a + AbLoopController.MIN_LOOP_SPAN_MICROS) {
        navigator.showAlert(Lang.tr("loop.error.span"), AlertType.ERROR);
        return;
      }
      if (callback != null) {
        callback.onPointsDefined(a, b);
      }
      navigator.back();
    }
  }

  private static String formatMicros(long micros) {
    if (micros < 0L) {
      return "";
    }
    long total = micros / 1000000L;
    long h = total / 3600L;
    long m = (total % 3600L) / 60L;
    long s = total % 60L;
    StringBuffer buf = new StringBuffer(8);
    if (h > 0) {
      buf.append(h).append(':');
      if (m < 10) buf.append('0');
    }
    buf.append(m).append(':');
    if (s < 10) buf.append('0');
    buf.append(s);
    return buf.toString();
  }

  private static long parseMicros(String raw) {
    if (raw == null) {
      return -1L;
    }
    String text = raw.trim();
    if (text.length() == 0) {
      return -1L;
    }
    long seconds = 0L;
    int parts = 0;
    long current = 0L;
    boolean hasDigit = false;
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (ch >= '0' && ch <= '9') {
        if (current > MAX_SECONDS) {
          return -1L;
        }
        current = current * 10L + (ch - '0');
        hasDigit = true;
      } else if (ch == ':') {
        if (!hasDigit) {
          return -1L;
        }
        seconds = seconds * 60L + current;
        current = 0L;
        hasDigit = false;
        parts++;
        if (parts > 2) {
          return -1L;
        }
      } else {
        return -1L;
      }
    }
    if (!hasDigit) {
      return -1L;
    }
    seconds = seconds * 60L + current;
    if (seconds > MAX_SECONDS) {
      return -1L;
    }
    return seconds * 1000000L;
  }

  public interface AbLoopCallback {
    void onPointsDefined(long pointAMicros, long pointBMicros);
  }
}
