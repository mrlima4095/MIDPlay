package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import midplay.store.NotesManager;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;

/**
 * Password gate for the notes vault. Prompts for the vault password when one is
 * set, or asks to create one the first time the vault is used. The prompt is
 * shown as a transient dialog so closing it always returns to the screen that
 * opened it.
 */
public final class VaultAuth {
  public static void authenticate(final Navigator navigator, final Runnable onSuccess) {
    if (NotesManager.getInstance().hasVaultPassword()) {
      promptPassword(navigator, onSuccess);
    } else {
      passwordForm(navigator, false, onSuccess);
    }
  }

  public static void changePassword(final Navigator navigator) {
    passwordForm(navigator, true, null);
  }

  private static void promptPassword(final Navigator navigator, final Runnable onSuccess) {
    final TextBox box = new TextBox(Lang.tr("vault.enter_password"), "", 64, TextField.PASSWORD);
    box.addCommand(Commands.ok());
    box.addCommand(Commands.cancel());
    box.setCommandListener(
        new CommandListener() {
          public void commandAction(Command command, Displayable displayable) {
            if (command == Commands.cancel()) {
              navigator.closeDialog();
            } else if (command == Commands.ok()) {
              if (NotesManager.getInstance().verifyVaultPassword(box.getString())) {
                navigator.restoreDialogAndThen(onSuccess);
              } else {
                box.setString("");
                navigator.showAlert(Lang.tr("vault.wrong_password"), AlertType.ERROR, box);
              }
            }
          }
        });
    navigator.showDialog(box);
  }

  private static void passwordForm(
      final Navigator navigator, final boolean requireCurrent, final Runnable onSuccess) {
    final Form form =
        new Form(Lang.tr(requireCurrent ? "vault.change_password" : "vault.create_title"));
    final TextField currentField =
        requireCurrent
            ? new TextField(Lang.tr("vault.current_password"), "", 64, TextField.PASSWORD)
            : null;
    final TextField newField =
        new TextField(Lang.tr("vault.new_password"), "", 64, TextField.PASSWORD);
    final TextField confirmField =
        new TextField(Lang.tr("vault.confirm_password"), "", 64, TextField.PASSWORD);
    if (currentField != null) {
      form.append(currentField);
    }
    form.append(newField);
    form.append(confirmField);
    final Command saveCommand = Commands.ok();
    form.addCommand(saveCommand);
    form.addCommand(Commands.cancel());
    form.setCommandListener(
        new CommandListener() {
          public void commandAction(Command command, Displayable displayable) {
            if (command == Commands.cancel()) {
              navigator.closeDialog();
            } else if (command == saveCommand) {
              save(navigator, form, requireCurrent, currentField, newField, confirmField, onSuccess);
            }
          }
        });
    navigator.showDialog(form);
  }

  private static void save(
      final Navigator navigator,
      Form form,
      boolean requireCurrent,
      TextField currentField,
      TextField newField,
      TextField confirmField,
      final Runnable onSuccess) {
    NotesManager manager = NotesManager.getInstance();
    if (requireCurrent && !manager.verifyVaultPassword(currentField.getString())) {
      navigator.showAlert(Lang.tr("vault.wrong_password"), AlertType.ERROR, form);
      return;
    }
    String password = newField.getString();
    if (password.length() == 0) {
      navigator.showAlert(Lang.tr("vault.password_empty"), AlertType.ERROR, form);
      return;
    }
    if (!password.equals(confirmField.getString())) {
      navigator.showAlert(Lang.tr("vault.password_mismatch"), AlertType.ERROR, form);
      return;
    }
    try {
      manager.setVaultPassword(password);
    } catch (Exception e) {
      navigator.showAlert(e.toString(), AlertType.ERROR, form);
      return;
    }
    final Displayable returnTo = navigator.getDialogReturn();
    navigator.restoreDialogAndThen(
        new Runnable() {
          public void run() {
            if (onSuccess != null) {
              onSuccess.run();
            } else {
              navigator.showAlert(
                  Lang.tr("vault.password_changed"), AlertType.CONFIRMATION, returnTo);
            }
          }
        });
  }

  private VaultAuth() {}
}
