package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.TextField;
import midplay.store.NotesManager;
import midplay.ui.BaseList;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.util.Lang;

/**
 * Lets the user save a block of text (a Wattpad chapter) as a note, or export
 * it to a file (.txt / .vnt). For files, the destination folder is picked in the file explorer and
 * the file name is chosen by the user.
 */
public final class TextSaveScreen extends BaseList {
  private final Displayable returnTo;
  private final String title;
  private final String text;

  public TextSaveScreen(Navigator navigator, Displayable returnTo, String title, String text) {
    super(Lang.tr("wattpad.save_chapter_page"), navigator);
    this.returnTo = returnTo;
    this.title = title == null ? "" : title;
    this.text = text == null ? "" : text;
    refresh();
  }

  protected void populateItems() {
    append(Lang.tr("wattpad.save_notes"), null);
    append(Lang.tr("wattpad.save_file"), null);
  }

  protected void handleSelection() {
    switch (getSelectedIndex()) {
      case 0:
        saveToNotes();
        break;
      case 1:
        pickDirectory(false);
        break;
      default:
        break;
    }
  }

  private String text() {
    return title.length() == 0 ? text : title + "\n\n" + text;
  }

  private void saveToNotes() {
    try {
      NotesManager.getInstance().create(text());
      navigator.popTo(returnTo);
      navigator.showAlert(Lang.tr("wattpad.saved_note"), AlertType.CONFIRMATION);
    } catch (Exception e) {
      navigator.showAlert(e.toString(), AlertType.ERROR);
    }
  }

  private void pickDirectory(final boolean vnote) {
    navigator.forward(
        new FileBrowserScreen(
            navigator,
            new FileBrowserScreen.DirectorySelectionListener() {
              public void onDirectorySelected(final String directory) {
                navigator.back();
                promptFileName(directory, vnote);
              }
            }));
  }

  private void promptFileName(final String directory, final boolean vnote) {
    final Form form = new Form(Lang.tr("wattpad.save_file"));
    final TextField name =
        new TextField(Lang.tr("note.file_name"), defaultFileName(vnote), 100, TextField.ANY);
    form.append(name);
    form.addCommand(Commands.ok());
    form.addCommand(Commands.cancel());
    form.setCommandListener(
        new CommandListener() {
          public void commandAction(Command command, Displayable displayable) {
            if (command == Commands.cancel()) {
              navigator.back();
            } else if (command == Commands.ok()) {
              try {
                String path =
                    NotesManager.getInstance()
                        .exportTextToDevice(directory, name.getString(), text(), vnote);
                navigator.popTo(returnTo);
                navigator.showAlert(Lang.tr("note.exported", path), AlertType.CONFIRMATION);
              } catch (Exception e) {
                navigator.showAlert(e.toString(), AlertType.ERROR);
              }
            }
          }
        });
    navigator.forward(form);
  }

  protected void handleCommand(Command c, Displayable d) {}

  /** Default file name is the title (sanitized), with the matching extension. */
  private String defaultFileName(boolean vnote) {
    return safeName(title) + (vnote ? ".vnt" : ".txt");
  }

  private static String safeName(String name) {
    if (name == null) {
      return "text";
    }
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < name.length() && sb.length() < 60; i++) {
      char c = name.charAt(i);
      if ((c >= 'a' && c <= 'z')
          || (c >= 'A' && c <= 'Z')
          || (c >= '0' && c <= '9')
          || c == '-'
          || c == '_') {
        sb.append(c);
      } else if (c == ' ') {
        sb.append('_');
      }
    }
    return sb.length() == 0 ? "text" : sb.toString();
  }
}