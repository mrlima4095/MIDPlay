package fi.gtrxac.tinytls;

import fi.gtrxac.bluewap.*;
import java.io.IOException;
import java.io.InputStream;

public final class TLSUtil {
    public static void log(StringBuffer log, String s) {
//#ifdef DEBUG
        if (log == null) {
            return;
        }

        /*
         * Prevent debugging from consuming unlimited heap.
         */
        if (log.length() > 4096) {
            return;
        }

        log.append(s);
        log.append('\n');
//#endif
    }

    public static void logHex(
            StringBuffer log,
            String name,
            byte[] b,
            int off,
            int len) {

//#ifdef DEBUG
        if (log == null || log.length() > 4096) {
            return;
        }

        log.append(name);
        log.append(": ");

        int i;

        for (i = 0; i < len; i++) {
            int x = b[off + i] & 0xFF;

            log.append(Util.intToHex((x >>> 4) & 15));
            log.append(Util.intToHex(x & 15));
        }

        log.append('\n');
//#endif
    }

    public static String hex16(int x) {
        char[] c = new char[4];

        c[0] = Util.intToHex((x >>> 12) & 15);
        c[1] = Util.intToHex((x >>> 8) & 15);
        c[2] = Util.intToHex((x >>> 4) & 15);
        c[3] = Util.intToHex(x & 15);

        return new String(c);
    }

    public static void writeU16(
            byte[] b,
            int off,
            int value) {

        b[off] = (byte) (value >>> 8);
        b[off + 1] = (byte) value;
    }

    public static void writeU24(
            byte[] b,
            int off,
            int value) {

        b[off] = (byte) (value >>> 16);
        b[off + 1] = (byte) (value >>> 8);
        b[off + 2] = (byte) value;
    }

    public static int readU8(InputStream in)
            throws IOException {

        int x = in.read();

        if (x < 0) {
            throw new IOException("Unexpected EOF");
        }

        return x;
    }

    public static int readU16(InputStream in)
            throws IOException {

        int a = readU8(in);
        int b = readU8(in);

        return (a << 8) | b;
    }

    public static void readFully(
            InputStream in,
            byte[] b,
            int off,
            int len) throws IOException {

        while (len > 0) {
            int n = in.read(b, off, len);

            if (n < 0) {
                throw new IOException("Unexpected EOF");
            }

            if (n == 0) {
                continue;
            }

            off += n;
            len -= n;
        }
    }

    public static byte[] ascii(String s) {
        int len = s.length();
        byte[] b = new byte[len];

        int i;

        for (i = 0; i < len; i++) {
            b[i] = (byte) s.charAt(i);
        }

        return b;
    }

    /*
     * NOT cryptographically secure.
     */
    public static void random(
            byte[] out,
            int off,
            int len) {

        java.util.Random r =
            new java.util.Random(
                System.currentTimeMillis()
                ^ Runtime.getRuntime().freeMemory()
            );

        int i;
        int x = 0;

        for (i = 0; i < len; i++) {
            if ((i & 3) == 0) {
                x = r.nextInt();
            }

            out[off + i] = (byte) x;
            x >>>= 8;
        }
    }

    public static boolean isAllZero(
            byte[] b,
            int off,
            int len) {

        int x = 0;
        int i;

        for (i = 0; i < len; i++) {
            x |= b[off + i] & 0xFF;
        }

        return x == 0;
    }

    public static void putLong(
            byte[] b,
            int off,
            long x) {

        int i;

        for (i = 7; i >= 0; i--) {
            b[off + i] = (byte)x;
            x >>>= 8;
        }
    }
}