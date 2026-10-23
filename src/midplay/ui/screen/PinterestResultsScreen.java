package midplay.ui.screen;

import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import midplay.MIDPlay;
import midplay.net.HttpTextOperation;
import midplay.net.PinterestApi;
import midplay.net.PinterestImageDownloadOperation;
import midplay.net.PinterestThumbnailOperation;
import midplay.store.Configuration;
import midplay.store.PinterestFavoritesManager;
import midplay.store.SettingsManager;
import midplay.ui.BaseList;
import midplay.ui.Navigator;
import midplay.ui.Sharing;
import midplay.util.Lang;

/** Paginated list of Pinterest pins or boards. */
public final class PinterestResultsScreen extends BaseList {
  public static final int SOURCE_SEARCH_PINS = 0;
  public static final int SOURCE_SEARCH_BOARDS = 1;
  public static final int SOURCE_USER_PINS = 2;
  public static final int SOURCE_USER_BOARDS = 3;
  public static final int SOURCE_BOARD_PINS = 4;

  private final int source;
  private final String sourceId;
  private final String sourceSlug;
  private final String sourceBoardId;
  private final Vector items;
  private final Hashtable ids = new Hashtable();
  private final Hashtable thumbCache = new Hashtable();
  private final Command moreCommand;
  private final Command saveCommand;
  private final Command shareCommand;
  private final Command downloadCommand;
  private final Command selectCommand;
  private final Command saveAllCommand;
  private String bookmark;
  private boolean hasMore;
  private boolean loading;
  private boolean downloading;
  private boolean thumbsInFlight;
  private int thumbToken;
  private PinterestThumbnailOperation thumbOperation;

  public PinterestResultsScreen(
      Navigator navigator,
      int source,
      String sourceId,
      String sourceSlug,
      String title,
      Vector items,
      String bookmark) {
    this(navigator, source, sourceId, sourceSlug, "", title, items, bookmark);
  }

  public PinterestResultsScreen(
      Navigator navigator,
      int source,
      String sourceId,
      String sourceSlug,
      String sourceBoardId,
      String title,
      Vector items,
      String bookmark) {
    super(title, navigator);
    this.source = source;
    this.sourceId = sourceId == null ? "" : sourceId;
    this.sourceSlug = sourceSlug == null ? "" : sourceSlug;
    this.sourceBoardId = sourceBoardId == null ? "" : sourceBoardId;
    this.items = items == null ? new Vector() : items;
    this.bookmark = bookmark == null ? "" : bookmark;
    hasMore = this.bookmark.length() > 0;
    moreCommand = new Command(Lang.tr("pinterest.more"), Command.SCREEN, 1);
    saveCommand = new Command(Lang.tr("pinterest.save"), Command.SCREEN, 2);
    shareCommand = new Command(Lang.tr("pinterest.share"), Command.SCREEN, 3);
    downloadCommand = new Command(Lang.tr("pinterest.download"), Command.SCREEN, 4);
    selectCommand = new Command(Lang.tr("pinterest.select"), Command.SCREEN, 5);
    saveAllCommand = new Command(Lang.tr("pinterest.save_whole_board"), Command.SCREEN, 6);
    for (int i = 0; i < this.items.size(); i++) {
      String id = idOf((String[]) this.items.elementAt(i));
      ids.put(id, id);
    }
    if (this.items.size() > 0) {
      addCommand(saveCommand);
      addCommand(shareCommand);
      addCommand(downloadCommand);
      addCommand(selectCommand);
    }
    if (isBoards() || source == SOURCE_BOARD_PINS) {
      addCommand(saveAllCommand);
    }
    if (hasMore) {
      addCommand(moreCommand);
    }
    refresh();
    loadThumbnails();
  }

  protected void populateItems() {
    for (int i = 0; i < items.size(); i++) {
      String[] item = (String[]) items.elementAt(i);
      String thumbUrl = thumbnailUrl(item);
      Image image =
          thumbUrl == null || thumbUrl.length() == 0
              ? null
              : (Image) thumbCache.get(thumbUrl);
      append(isBoards() ? PinterestApi.boardLabel(item) : PinterestApi.pinLabel(item), image);
    }
  }

  protected void handleSelection() {
    int index = getSelectedIndex();
    if (index < 0 || index >= items.size() || loading) {
      return;
    }
    String[] item = (String[]) items.elementAt(index);
    if (isBoards()) {
      loadBoard(item);
    } else {
      navigator.forward(new PinterestPinScreen(navigator, item));
    }
  }

  protected void handleCommand(Command c, Displayable d) {
    if (c == moreCommand) {
      loadMore();
    } else if (c == saveCommand) {
      saveSelected();
    } else if (c == shareCommand) {
      shareSelected();
    } else if (c == downloadCommand) {
      downloadSelected();
    } else if (c == selectCommand) {
      selectMultiple();
    } else if (c == saveAllCommand) {
      saveWholeBoard();
    }
  }

  private void selectMultiple() {
    if (items.size() == 0) {
      return;
    }
    Vector rows = new Vector();
    String type = isBoards() ? PinterestMultiSelectScreen.TYPE_BOARD : PinterestMultiSelectScreen.TYPE_PIN;
    for (int i = 0; i < items.size(); i++) {
      rows.addElement(new Object[] {type, items.elementAt(i)});
    }
    navigator.forward(
        new PinterestMultiSelectScreen(
            navigator, PinterestMultiSelectScreen.MODE_RESULTS, Lang.tr("pinterest.select"), rows, null));
  }

  private void saveWholeBoard() {
    if (isBoards()) {
      String[] board = selectedItem();
      if (board == null) {
        return;
      }
      PinterestBoardImportHelper.saveWholeBoard(
          navigator,
          board[PinterestApi.BOARD_USER],
          board[PinterestApi.BOARD_SLUG],
          board[PinterestApi.BOARD_ID]);
    } else {
      PinterestBoardImportHelper.saveWholeBoard(
          navigator, sourceId, sourceSlug, sourceBoardId);
    }
  }

  private String[] selectedItem() {
    int index = getSelectedIndex();
    if (index < 0 || index >= items.size()) {
      return null;
    }
    return (String[]) items.elementAt(index);
  }

  private void saveSelected() {
    String[] item = selectedItem();
    if (item == null) {
      return;
    }
    int result;
    if (isBoards()) {
      result = PinterestFavoritesManager.getInstance().saveBoard(item);
    } else {
      result = PinterestFavoritesManager.getInstance().savePin(item);
    }
    if (result == PinterestFavoritesManager.ALREADY_EXISTS) {
      navigator.showAlert(Lang.tr("pinterest.already_saved"), AlertType.INFO);
    } else if (result == PinterestFavoritesManager.SUCCESS) {
      navigator.showAlert(
          Lang.tr(isBoards() ? "pinterest.saved_board" : "pinterest.saved_pin"),
          AlertType.CONFIRMATION);
    } else {
      navigator.showAlert(Lang.tr("pinterest.save_error"), AlertType.ERROR);
    }
  }

  private void shareSelected() {
    String[] item = selectedItem();
    if (item == null) {
      return;
    }
    String text;
    if (isBoards()) {
      text =
          PinterestApi.boardLabel(item)
              + "\n"
              + PinterestApi.boardWebUrl(
                  item[PinterestApi.BOARD_USER], item[PinterestApi.BOARD_SLUG]);
    } else {
      text = PinterestApi.pinLabel(item) + "\n" + PinterestApi.pinShareUrl(item);
    }
    Sharing.toMPGram(this, text);
  }

  private void downloadSelected() {
    final String[] item = selectedItem();
    if (item == null || downloading) {
      return;
    }
    final boolean board = isBoards();
    final String source;
    final String fileName;
    if (board) {
      source = item[PinterestApi.BOARD_COVER];
      fileName = "board_" + item[PinterestApi.BOARD_ID] + ".jpg";
    } else {
      source =
          item[PinterestApi.PIN_IMAGE_LARGE].length() > 0
              ? item[PinterestApi.PIN_IMAGE_LARGE]
              : item[PinterestApi.PIN_IMAGE];
      fileName = "pin_" + item[PinterestApi.PIN_ID] + ".jpg";
    }
    if (source == null || source.length() == 0) {
      navigator.showAlert(Lang.tr("pinterest.download_error"), AlertType.ERROR);
      return;
    }
    downloading = true;
    navigator.showLoadingAlert(
        Lang.tr("pinterest.downloading"),
        new Runnable() {
          public void run() {
            downloading = false;
          }
        });
    MIDPlay.startOperation(
        new PinterestImageDownloadOperation(
            source,
            fileName,
            new PinterestImageDownloadOperation.Listener() {
              public void onDownloaded(final String path) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        downloading = false;
                        navigator.dismissAlert();
                        navigator.showAlert(
                            Lang.tr("pinterest.downloaded", path), AlertType.CONFIRMATION);
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        downloading = false;
                        navigator.dismissAlert();
                        navigator.showAlert(
                            Lang.tr("pinterest.download_error"), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  protected void showNotify() {
    super.showNotify();
    loadThumbnails();
  }

  protected void hideNotify() {
    cancelThumbnails();
    super.hideNotify();
  }

  private boolean isBoards() {
    return source == SOURCE_SEARCH_BOARDS || source == SOURCE_USER_BOARDS;
  }

  private void loadBoard(final String[] board) {
    final String owner = board[PinterestApi.BOARD_USER];
    final String slug = board[PinterestApi.BOARD_SLUG];
    if (owner.length() == 0 || slug.length() == 0) {
      navigator.showAlert(Lang.tr("pinterest.empty"), AlertType.INFO);
      return;
    }
    loading = true;
    navigator.showLoadingAlert(
        Lang.tr("pinterest.loading"),
        new Runnable() {
          public void run() {
            loading = false;
          }
        });
    MIDPlay.startOperation(
        new HttpTextOperation(
            PinterestApi.boardPinsUrl(owner, slug, board[PinterestApi.BOARD_ID], ""),
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        String error = PinterestApi.parseError(text);
                        if (error.length() > 0) {
                          navigator.dismissAlert();
                          navigator.showAlert(error, AlertType.ERROR);
                          return;
                        }
                        Vector pins = PinterestApi.parsePins(text);
                        if (pins.size() == 0) {
                          navigator.dismissAlert();
                          navigator.showAlert(Lang.tr("pinterest.empty"), AlertType.INFO);
                          return;
                        }
                        navigator.forward(
                            new PinterestResultsScreen(
                                navigator,
                                PinterestResultsScreen.SOURCE_BOARD_PINS,
                                owner,
                                slug,
                                board[PinterestApi.BOARD_ID],
                                board[PinterestApi.BOARD_NAME],
                                pins,
                                PinterestApi.parseBookmark(text)));
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        navigator.showAlert(e.toString(), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private void loadMore() {
    if (loading || !hasMore) {
      return;
    }
    loading = true;
    navigator.showLoadingAlert(
        Lang.tr("status.loading"),
        new Runnable() {
          public void run() {
            loading = false;
          }
        });
    MIDPlay.startOperation(
        new HttpTextOperation(
            nextPageUrl(),
            new HttpTextOperation.Listener() {
              public void onText(final String text) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        appendMore(text);
                      }
                    });
              }

              public void onError(final Exception e) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        loading = false;
                        navigator.dismissAlert();
                        navigator.showAlert(e.toString(), AlertType.ERROR);
                      }
                    });
              }
            }));
  }

  private String nextPageUrl() {
    if (source == SOURCE_SEARCH_BOARDS) {
      return PinterestApi.searchBoardsUrl(sourceId, bookmark);
    }
    if (source == SOURCE_USER_PINS) {
      return PinterestApi.userPinsUrl(sourceId, bookmark);
    }
    if (source == SOURCE_USER_BOARDS) {
      return PinterestApi.userBoardsUrl(sourceId, bookmark);
    }
    if (source == SOURCE_BOARD_PINS) {
      return PinterestApi.boardPinsUrl(sourceId, sourceSlug, sourceBoardId, bookmark);
    }
    return PinterestApi.searchPinsUrl(sourceId, bookmark);
  }

  private void appendMore(String text) {
    String error = PinterestApi.parseError(text);
    if (error.length() > 0) {
      navigator.showAlert(error, AlertType.ERROR);
      return;
    }
    Vector more = isBoards() ? PinterestApi.parseBoards(text) : PinterestApi.parsePins(text);
    String newBookmark = PinterestApi.parseBookmark(text);
    boolean end = newBookmark.length() == 0 || newBookmark.equals(bookmark);
    int added = 0;
    for (int i = 0; i < more.size(); i++) {
      String[] item = (String[]) more.elementAt(i);
      String id = idOf(item);
      if (ids.containsKey(id)) {
        continue;
      }
      ids.put(id, id);
      items.addElement(item);
      added++;
    }
    bookmark = newBookmark;
    if (end) {
      hasMore = false;
      removeCommand(moreCommand);
    }
    if (added > 0) {
      refresh();
      loadThumbnails();
    }
    if (end) {
      navigator.showAlert(Lang.tr("pinterest.end"), AlertType.INFO);
    }
  }

  private static String idOf(String[] item) {
    return item[0];
  }

  private String thumbnailUrl(String[] item) {
    return isBoards() ? item[PinterestApi.BOARD_COVER] : item[PinterestApi.PIN_IMAGE];
  }

  private void loadThumbnails() {
    if (thumbsInFlight
        || SettingsManager.getInstance().getCurrentThumbnails() == Configuration.THUMBNAILS_OFF) {
      return;
    }
    String[] urls = new String[items.size()];
    int count = 0;
    for (int i = 0; i < items.size() && count < PinterestApi.MAX_THUMBNAILS; i++) {
      String[] item = (String[]) items.elementAt(i);
      String url = isBoards() ? item[PinterestApi.BOARD_COVER] : item[PinterestApi.PIN_IMAGE];
      if (url == null || url.length() == 0 || thumbCache.containsKey(url)) {
        continue;
      }
      urls[i] = url;
      count++;
    }
    if (count == 0) {
      return;
    }
    cancelThumbnails();
    final int token = ++thumbToken;
    thumbsInFlight = true;
    thumbOperation =
        new PinterestThumbnailOperation(
            urls,
            36,
            new PinterestThumbnailOperation.Listener() {
              public void onImage(final int index, final Image image) {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        if (token != thumbToken || index < 0 || index >= items.size()) {
                          return;
                        }
                        String[] item = (String[]) items.elementAt(index);
                        String url =
                            isBoards()
                                ? item[PinterestApi.BOARD_COVER]
                                : item[PinterestApi.PIN_IMAGE];
                        if (url != null && url.length() > 0) {
                          thumbCache.put(url, image);
                        }
                        if (index < size()) {
                          set(index, getString(index), image);
                        }
                      }
                    });
              }

              public void onDone() {
                navigator.callSerially(
                    new Runnable() {
                      public void run() {
                        if (token == thumbToken) {
                          thumbsInFlight = false;
                        }
                      }
                    });
              }

              public void onError(Exception e) {
                // A single failed thumbnail is ignored; the operation still continues with the
                // remaining images and reports completion through onDone().
              }
            });
    thumbOperation.start();
  }

  private void cancelThumbnails() {
    if (thumbOperation != null) {
      thumbOperation.stop();
      thumbOperation = null;
    }
    thumbsInFlight = false;
    thumbToken++;
  }
}
