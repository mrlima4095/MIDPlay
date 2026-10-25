package midplay.ui.screen;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.StringItem;
import midplay.MIDPlay;
import midplay.net.HttpTextOperation;
import midplay.net.TranslateApi;
import midplay.store.NotesManager;
import midplay.store.NotesManager.Note;
import midplay.ui.BaseForm;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;

public final class NoteReaderScreen extends BaseForm {
  private final int noteId;
  private final Runnable onClose;
  private final StringItem content;
  private final Command editCommand;
  private final Command translateCommand;
  private final Command originalCommand;
  private boolean translating;
  private boolean translated;
  private boolean showOriginal;
  private String translatedText = "";

  public NoteReaderScreen(Note note, Navigator navigator, Runnable onClose) {
    super(note.title, navigator);
    noteId = note.id;
    this.onClose = onClose;
    content = new StringItem(null, note.text);
    editCommand = new Command(Lang.tr("note.edit"), Command.SCREEN, 1);
    translateCommand = new Command(Lang.tr("note.translate"), Command.SCREEN, 2);
    originalCommand = new Command(Lang.tr("note.show_original"), Command.SCREEN, 2);
    append(content);
    addCommand(editCommand);
    addCommand(Commands.playerNowPlaying());
    updateTranslateCommands();
  }

  protected void handleCommand(Command command, Displayable displayable) {
    if (command == editCommand) {
      Note note = NotesManager.getInstance().getNote(noteId);
      if (note != null) {
        navigator.forward(new NoteEditorScreen(note, navigator, refreshReader()));
      }
    } else if (command == translateCommand) {
      translate();
    } else if (command == originalCommand) {
      showOriginal = true;
      updateContent();
      updateTranslateCommands();
    } else if (command == Commands.playerNowPlaying()) {
      PlayerNavHelper.showNowPlaying(navigator);
    }
  }

  private void updateTranslateCommands() {
    if (translated && !showOriginal) {
      removeCommand(translateCommand);
      addCommand(originalCommand);
    } else {
      removeCommand(originalCommand);
      addCommand(translateCommand);
    }
  }

  private void translate() {
    if (translating) {
      return;
    }
    if (translatedText.length() > 0) {
      translated = true;
      showOriginal = false;
      updateContent();
      updateTranslateCommands();
      return;
    }
    Note note = NotesManager.getInstance().getNote(noteId);
    final String text = note == null ? "" : note.text;
    if (text.trim().length() == 0) {
      navigator.showAlert(Lang.tr("note.translate_error"), AlertType.ERROR);
      return;
    }
    translating = true;
    navigator.showLoadingAlert(
        Lang.tr("note.translating"),
        new Runnable() {
          public void run() {
            translating = false;
          }
        });
    MIDPlay.startOperation(
        new HttpTextOperation(
            TranslateApi.url(),
            TranslateApi.body(text),
            new HttpTextOperation.Listener() {
              public void onText(final String response) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        if (!translating) {
                          return;
                        }
                        translating = false;
                        String result = TranslateApi.parse(response);
                        if (result.length() == 0) {
                          navigator.dismissAlert();
                          navigator.showAlert(
                              Lang.tr("note.translate_error"), AlertType.ERROR);
                          return;
                        }
                        translatedText = result;
                        translated = true;
                        showOriginal = false;
                        navigator.dismissAlert();
                        updateContent();
                        updateTranslateCommands();
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        if (!translating) {
                          return;
                        }
                        translating = false;
                        navigator.dismissAlert();
                        navigator.showAlert(Lang.tr("note.translate_error"), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private void updateContent() {
    String original = "";
    Note current = NotesManager.getInstance().getNote(noteId);
    if (current != null) {
      original = current.text;
    }
    if (showOriginal || translatedText.length() == 0) {
      content.setText(original);
    } else {
      content.setText(translatedText);
    }
  }

  private Runnable refreshReader() {
    return new Runnable() {
      public void run() {
        Note current = NotesManager.getInstance().getNote(noteId);
        if (current != null) {
          content.setText(current.text);
          setTitle(current.title);
        }
        translated = false;
        showOriginal = false;
        translatedText = "";
        translating = false;
        updateTranslateCommands();
        if (onClose != null) {
          onClose.run();
        }
      }
    };
  }
}
