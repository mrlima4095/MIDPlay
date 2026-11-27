package fi.gtrxac.tinytls;

public final class SHA256 {

    private final int[] h = new int[8];
    private final int[] w = new int[64];
    private final byte[] block = new byte[64];

    private int blockPos;
    private long byteCount;

    public SHA256() {
        reset();
    }

    public SHA256(SHA256 other) {
        copyFrom(other);
    }

    public void copyFrom(SHA256 other) {
        int i;

        for (i = 0; i < 8; i++) {
            h[i] = other.h[i];
        }

        for (i = 0; i < 64; i++) {
            block[i] = other.block[i];
        }

        blockPos = other.blockPos;
        byteCount = other.byteCount;
    }

    public void reset() {
        h[0] = 0x6A09E667;
        h[1] = 0xBB67AE85;
        h[2] = 0x3C6EF372;
        h[3] = 0xA54FF53A;
        h[4] = 0x510E527F;
        h[5] = 0x9B05688C;
        h[6] = 0x1F83D9AB;
        h[7] = 0x5BE0CD19;

        blockPos = 0;
        byteCount = 0;
    }

    public void update(byte b) {
        block[blockPos++] = b;
        byteCount++;

        if (blockPos == 64) {
            transform();
            blockPos = 0;
        }
    }

    public void update(
            byte[] data,
            int off,
            int len) {

        while (len > 0) {
            int n = 64 - blockPos;

            if (n > len) {
                n = len;
            }

            System.arraycopy(
                data,
                off,
                block,
                blockPos,
                n
            );

            blockPos += n;
            byteCount += n;

            off += n;
            len -= n;

            if (blockPos == 64) {
                transform();
                blockPos = 0;
            }
        }
    }

    public void digest(
            byte[] out,
            int off) {

        long bitLength = byteCount << 3;

        /*
         * Append 0x80.
         */
        block[blockPos++] = (byte) 0x80;

        /*
         * Need 8 bytes at end for length.
         */
        if (blockPos > 56) {
            while (blockPos < 64) {
                block[blockPos++] = 0;
            }

            transform();
            blockPos = 0;
        }

        while (blockPos < 56) {
            block[blockPos++] = 0;
        }

        int i;

        for (i = 7; i >= 0; i--) {
            block[blockPos++] =
                (byte) (bitLength >>> (i * 8));
        }

        transform();

        for (i = 0; i < 8; i++) {
            int v = h[i];

            out[off++] = (byte) (v >>> 24);
            out[off++] = (byte) (v >>> 16);
            out[off++] = (byte) (v >>> 8);
            out[off++] = (byte) v;
        }

        reset();
    }

    private void transform() {
        int i;

        for (i = 0; i < 16; i++) {
            int p = i << 2;

            w[i] =
                ((block[p] & 0xFF) << 24)
                | ((block[p + 1] & 0xFF) << 16)
                | ((block[p + 2] & 0xFF) << 8)
                | (block[p + 3] & 0xFF);
        }

        for (i = 16; i < 64; i++) {
            int x = w[i - 15];
            int y = w[i - 2];

            int s0 =
                rotr(x, 7)
                ^ rotr(x, 18)
                ^ (x >>> 3);

            int s1 =
                rotr(y, 17)
                ^ rotr(y, 19)
                ^ (y >>> 10);

            w[i] =
                w[i - 16]
                + s0
                + w[i - 7]
                + s1;
        }

        int a = h[0];
        int b = h[1];
        int c = h[2];
        int d = h[3];
        int e = h[4];
        int f = h[5];
        int g = h[6];
        int hh = h[7];

        for (i = 0; i < 64; i++) {
            int s1 =
                rotr(e, 6)
                ^ rotr(e, 11)
                ^ rotr(e, 25);

            int ch =
                (e & f)
                ^ ((~e) & g);

            int t1 =
                hh
                + s1
                + ch
                + K[i]
                + w[i];

            int s0 =
                rotr(a, 2)
                ^ rotr(a, 13)
                ^ rotr(a, 22);

            int maj =
                (a & b)
                ^ (a & c)
                ^ (b & c);

            int t2 = s0 + maj;

            hh = g;
            g = f;
            f = e;
            e = d + t1;
            d = c;
            c = b;
            b = a;
            a = t1 + t2;
        }

        h[0] += a;
        h[1] += b;
        h[2] += c;
        h[3] += d;
        h[4] += e;
        h[5] += f;
        h[6] += g;
        h[7] += hh;
    }

    private static int rotr(int x, int n) {
        return (x >>> n) | (x << (32 - n));
    }

    private static final int[] K = {
        0x428A2F98, 0x71374491, 0xB5C0FBCF, 0xE9B5DBA5,
        0x3956C25B, 0x59F111F1, 0x923F82A4, 0xAB1C5ED5,
        0xD807AA98, 0x12835B01, 0x243185BE, 0x550C7DC3,
        0x72BE5D74, 0x80DEB1FE, 0x9BDC06A7, 0xC19BF174,
        0xE49B69C1, 0xEFBE4786, 0x0FC19DC6, 0x240CA1CC,
        0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA,
        0x983E5152, 0xA831C66D, 0xB00327C8, 0xBF597FC7,
        0xC6E00BF3, 0xD5A79147, 0x06CA6351, 0x14292967,
        0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13,
        0x650A7354, 0x766A0ABB, 0x81C2C92E, 0x92722C85,
        0xA2BFE8A1, 0xA81A664B, 0xC24B8B70, 0xC76C51A3,
        0xD192E819, 0xD6990624, 0xF40E3585, 0x106AA070,
        0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5,
        0x391C0CB3, 0x4ED8AA4A, 0x5B9CCA4F, 0x682E6FF3,
        0x748F82EE, 0x78A5636F, 0x84C87814, 0x8CC70208,
        0x90BEFFFA, 0xA4506CEB, 0xBEF9A3F7, 0xC67178F2
    };
}