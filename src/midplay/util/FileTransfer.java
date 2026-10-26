package midplay.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;

/**
 * Copies and moves files between folders on the memory card.
 *
 * <p>JSR-75's {@link FileConnection#rename} only changes a file name inside its current directory,
 * so moving across folders has to copy the bytes and then delete the source. The helpers here keep
 * that logic out of the file browser and multi-select screens.
 */
public final class FileTransfer {
  private static final int BUFFER_SIZE = 4096;

  public static final class Result {
    public int done;
    public int failed;
    public int skipped;
    public boolean cancelled;
  }

  /** Shared flag used to stop a running transfer when the user cancels the loading alert. */
  public static final class CancelSignal {
    private boolean cancelled;

    public synchronized void cancel() {
      cancelled = true;
    }

    public synchronized boolean isCancelled() {
      return cancelled;
    }
  }

  public static Result copyMany(String[] sources, String destinationDir, CancelSignal signal) {
    return transfer(sources, destinationDir, false, signal);
  }

  public static Result moveMany(String[] sources, String destinationDir, CancelSignal signal) {
    return transfer(sources, destinationDir, true, signal);
  }

  /** Builds the localized result message shared by the file browser and the multi-select screen. */
  public static String describe(Result result, boolean move) {
    if (result == null) {
      return "";
    }
    String message = Lang.tr(move ? "files.move_result" : "files.copy_result");
    message = Utils.replace(message, "{0}", Integer.toString(result.done));
    message = Utils.replace(message, "{1}", Integer.toString(result.failed));
    message = Utils.replace(message, "{2}", Integer.toString(result.skipped));
    if (result.cancelled) {
      message = Lang.tr("files.transfer_cancelled") + "\n" + message;
    }
    return message;
  }

  private static Result transfer(
      String[] sources, String destinationDir, boolean move, CancelSignal signal) {
    Result result = new Result();
    if (sources == null || sources.length == 0) {
      return result;
    }
    String destination = normalizeDirectory(destinationDir);
    for (int i = 0; i < sources.length; i++) {
      if (signal != null && signal.isCancelled()) {
        result.cancelled = true;
        break;
      }
      String source = sources[i];
      if (source == null || source.length() == 0) {
        result.failed++;
        continue;
      }
      if (sameDirectory(parentOf(source), destination)) {
        // Copying into the source folder would only create "name (1).ext"; moving would be a
        // no-op. Count it as skipped instead of producing a duplicate.
        result.skipped++;
        continue;
      }
      String target;
      try {
        target = uniqueTarget(source, destination);
      } catch (Exception e) {
        result.failed++;
        continue;
      }
      try {
        if (!copyFile(source, target, signal)) {
          result.cancelled = true;
          break;
        }
        if (move && !deleteFile(source)) {
          // The copy succeeded but the source could not be removed; report the move as failed
          // instead of silently leaving two files behind.
          result.failed++;
          continue;
        }
        result.done++;
      } catch (Exception e) {
        deleteQuietly(target);
        result.failed++;
      }
    }
    return result;
  }

  /**
   * Copies one file to {@code targetPath}. Returns false when the signal was cancelled; the
   * partially written destination is removed in that case.
   */
  private static boolean copyFile(String sourcePath, String targetPath, CancelSignal signal)
      throws Exception {
    FileConnection source = null;
    FileConnection target = null;
    InputStream in = null;
    OutputStream out = null;
    boolean completed = false;
    try {
      source = (FileConnection) Connector.open(sourcePath, Connector.READ);
      if (!source.exists() || source.isDirectory()) {
        throw new IOException("not a file: " + sourcePath);
      }

      target = (FileConnection) Connector.open(targetPath, Connector.READ_WRITE);
      if (target.exists()) {
        target.delete();
      }
      target.create();

      in = source.openInputStream();
      out = target.openOutputStream();
      byte[] buffer = new byte[BUFFER_SIZE];
      int read;
      while ((read = in.read(buffer)) != -1) {
        if (read > 0) {
          out.write(buffer, 0, read);
        }
        if (signal != null && signal.isCancelled()) {
          return false;
        }
      }
      out.flush();
      completed = true;
      return true;
    } finally {
      Utils.closeQuietly(in);
      Utils.closeQuietly(out);
      close(target);
      close(source);
      if (!completed) {
        deleteQuietly(targetPath);
      }
    }
  }

  private static String uniqueTarget(String sourcePath, String destinationDir) throws Exception {
    String name = fileName(sourcePath);
    int dot = name.lastIndexOf('.');
    String base = dot > 0 ? name.substring(0, dot) : name;
    String extension = dot > 0 ? name.substring(dot) : "";
    String target = destinationDir + name;
    int counter = 1;
    while (exists(target)) {
      target = destinationDir + base + " (" + counter + ")" + extension;
      counter++;
      if (counter > 999) {
        throw new IOException("too many copies");
      }
    }
    return target;
  }

  private static boolean exists(String path) {
    FileConnection file = null;
    try {
      file = (FileConnection) Connector.open(path, Connector.READ);
      return file.exists();
    } catch (Exception e) {
      return false;
    } finally {
      close(file);
    }
  }

  private static boolean deleteFile(String path) {
    FileConnection file = null;
    try {
      file = (FileConnection) Connector.open(path, Connector.READ_WRITE);
      if (file.exists()) {
        file.delete();
      }
      return true;
    } catch (Exception e) {
      return false;
    } finally {
      close(file);
    }
  }

  private static void deleteQuietly(String path) {
    deleteFile(path);
  }

  private static String parentOf(String path) {
    if (path == null) {
      return "";
    }
    String trimmed = path;
    while (trimmed.length() > 1 && trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    int slash = trimmed.lastIndexOf('/');
    return slash >= 0 ? trimmed.substring(0, slash + 1) : "/";
  }

  private static String fileName(String path) {
    if (path == null) {
      return "";
    }
    String trimmed = path;
    while (trimmed.length() > 1 && trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    int slash = trimmed.lastIndexOf('/');
    return slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
  }

  private static String normalizeDirectory(String dir) {
    if (dir == null || dir.length() == 0) {
      return "";
    }
    return dir.endsWith("/") ? dir : dir + "/";
  }

  private static boolean sameDirectory(String left, String right) {
    return normalizeDirectory(left).equals(normalizeDirectory(right));
  }

  private static void close(FileConnection file) {
    if (file != null) {
      try {
        file.close();
      } catch (Exception e) {
      }
    }
  }

  private FileTransfer() {}
}
