package midplay.ui;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;

public abstract class BaseForm extends Form implements CommandListener {
  protected final Navigator navigator;

  public BaseForm(String title, Navigator navigator) {
    super(title);
    this.navigator = navigator;
    setupCommands();
    setCommandListener(this);
  }

  private void setupCommands() {
    addCommand(Commands.back());
  }

  public void commandAction(Command c, Displayable d) {
    try {
      if (c == Commands.back()) {
        if (!onBackPressed()) {
          navigator.back();
        }
      } else {
        handleCommand(c, d);
      }
    } catch (Exception e) {
      navigator.showAlert(e.toString(), AlertType.ERROR);
    }
  }

  /**
   * Called by the runtime when the screen becomes visible. The MIDP stub used by the build does
   * not declare it, so it lives here (like BaseList does for lists) and subclasses can refresh
   * their state with {@code super.showNotify()}.
   */
  protected void showNotify() {}

  protected boolean onBackPressed() {
    return false;
  }

  protected abstract void handleCommand(Command c, Displayable d);
}
