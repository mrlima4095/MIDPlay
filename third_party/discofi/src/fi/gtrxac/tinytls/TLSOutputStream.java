package fi.gtrxac.tinytls;

import java.io.*;

public final class TLSOutputStream extends OutputStream {
    private TinyTLS tls;
    private boolean closed;

    private final byte[] one = new byte[1];

    public TLSOutputStream(TinyTLS tls) {
        if (tls == null) {
            throw new NullPointerException();
        }
        this.tls = tls;
    }

    public void write(int b) throws IOException {
        checkOpen();
        one[0] = (byte) b;
        tls.write(one, 0, 1);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        checkOpen();
        tls.write(b, off, len);
    }

    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    public void flush() throws IOException {
        checkOpen();
        tls.flush();
    }

    public void close() throws IOException {
        if (!closed) {
            closed = true;

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