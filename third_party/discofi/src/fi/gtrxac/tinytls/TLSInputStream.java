package fi.gtrxac.tinytls;

import java.io.IOException;
import java.io.InputStream;

public final class TLSInputStream extends InputStream {
    private TinyTLS tls;
    private boolean closed;

    public TLSInputStream(TinyTLS tls) {
        if (tls == null) {
            throw new NullPointerException();
        }

        this.tls = tls;
    }

    public int read() throws IOException {
        checkOpen();
        return tls.read();
    }

    public int read(byte[] b, int off, int len) throws IOException {
        checkOpen();
        return tls.read(b, off, len);
    }

    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    public long skip(long n) throws IOException {
        checkOpen();

        if (n <= 0) return 0;

        byte[] temp = new byte[128];
        long skipped = 0;

        while (skipped < n) {
            int want = (int) (n - skipped);

            if (want > temp.length) {
                want = temp.length;
            }

            int got = read(temp, 0, want);
            if (got < 0) break;

            skipped += got;
        }

        return skipped;
    }

    public int available() throws IOException {
        checkOpen();
        return tls.available();
    }

    public void close() throws IOException {
        if (!closed) {
            closed = true;

            /*
             * Closing either stream closes the
             * underlying TLS connection.
             */
            tls.close();
            tls = null;
        }
    }

    private void checkOpen() throws IOException {
        if (closed || tls == null) {
            throw new IOException("Stream closed");
        }
    }
}