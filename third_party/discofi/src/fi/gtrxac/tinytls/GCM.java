package fi.gtrxac.tinytls;

public final class GCM {

    private final AES128 aes = new AES128();

    private final byte[] H = new byte[16];
    private final byte[] S = new byte[16];
    private final byte[] counter = new byte[16];
    private final byte[] stream = new byte[16];
    private final byte[] block = new byte[16];
    private final byte[] j0 = new byte[16];

    public void setKey(byte[] key, int off) {
        aes.setKey(key, off);

        int i;

        for (i = 0; i < 16; i++) {
            block[i] = 0;
        }

        aes.encryptBlock(block, 0, H, 0);
    }

    public void encrypt(
            byte[] nonce,
            int nonceOff,
            byte[] aad,
            int aadOff,
            int aadLen,
            byte[] data,
            int dataOff,
            int dataLen,
            byte[] tag,
            int tagOff) {

        makeJ0(nonce, nonceOff);

        /*
         * First encryption counter is inc32(J0).
         */
        inc32(counter);

        int p = dataOff;
        int remaining = dataLen;

        while (remaining > 0) {
            aes.encryptBlock(
                counter, 0,
                stream, 0
            );

            int n = remaining;

            if (n > 16) {
                n = 16;
            }

            int i;

            for (i = 0; i < n; i++) {
                data[p + i] ^=
                    stream[i];
            }

            p += n;
            remaining -= n;

            inc32(counter);
        }

        calculateGHash(
            aad, aadOff, aadLen,
            data, dataOff, dataLen
        );

        aes.encryptBlock(
            j0, 0,
            stream, 0
        );

        int i;

        for (i = 0; i < 16; i++) {
            tag[tagOff + i] =
                (byte)(stream[i] ^ S[i]);
        }
    }

    public boolean decrypt(
            byte[] nonce,
            int nonceOff,
            byte[] aad,
            int aadOff,
            int aadLen,
            byte[] data,
            int dataOff,
            int dataLen,
            byte[] tag,
            int tagOff) {

        makeJ0(nonce, nonceOff);

        /*
         * GHASH authenticates ciphertext, so this must
         * happen BEFORE decrypting data in place.
         */
        calculateGHash(
            aad, aadOff, aadLen,
            data, dataOff, dataLen
        );
        
        aes.encryptBlock(
            j0, 0,
            stream, 0
        );

        int diff = 0;
        int i;

        for (i = 0; i < 16; i++) {
            diff |=
                (stream[i] ^ S[i] ^ tag[tagOff + i])
                & 255;
        }

        if (diff != 0) {
            return false;
        }

        inc32(counter);

        int p = dataOff;
        int remaining = dataLen;

        while (remaining > 0) {
            aes.encryptBlock(
                counter, 0,
                stream, 0
            );

            int n = remaining;

            if (n > 16) {
                n = 16;
            }

            for (i = 0; i < n; i++) {
                data[p + i] ^=
                    stream[i];
            }

            p += n;
            remaining -= n;

            inc32(counter);
        }

        return true;
    }

    /*
     * For a 96-bit nonce:
     *
     * J0 = nonce || 0x00000001
     */
    private void makeJ0(
            byte[] nonce,
            int off) {

        int i;

        for (i = 0; i < 12; i++) {
            j0[i] = nonce[off + i];
        }

        j0[12] = 0;
        j0[13] = 0;
        j0[14] = 0;
        j0[15] = 1;

        System.arraycopy(
            j0,
            0,
            counter,
            0,
            16
        );
    }

    private void calculateGHash(
            byte[] aad,
            int aadOff,
            int aadLen,
            byte[] cipher,
            int cipherOff,
            int cipherLen) {

        int i;

        for (i = 0; i < 16; i++) {
            S[i] = 0;
        }

        ghashData(
            aad,
            aadOff,
            aadLen
        );

        ghashData(
            cipher,
            cipherOff,
            cipherLen
        );

        /*
         * Final block:
         *
         * [64-bit AAD bit length]
         * [64-bit ciphertext bit length]
         */
        for (i = 0; i < 16; i++) {
            block[i] = 0;
        }

        putLength(
            block,
            0,
            ((long)aadLen) << 3
        );

        putLength(
            block,
            8,
            ((long)cipherLen) << 3
        );

        xorBlock(S, block);
        multiplyH(S);
    }

    private void ghashData(
            byte[] data,
            int off,
            int len) {

        while (len > 0) {
            int n = len;

            if (n > 16) {
                n = 16;
            }

            int i;

            for (i = 0; i < 16; i++) {
                block[i] = 0;
            }

            for (i = 0; i < n; i++) {
                block[i] = data[off + i];
            }

            xorBlock(S, block);
            multiplyH(S);

            off += n;
            len -= n;
        }
    }

    /*
     * GF(2^128) multiply:
     *
     * X = X * H
     *
     * Simple bit-at-a-time implementation.
     * Slow compared with lookup tables, but tiny and
     * suitable for getting TLS working first.
     */
    private void multiplyH(byte[] x) {
        byte[] z = new byte[16];
        byte[] v = new byte[16];

        System.arraycopy(
            H, 0,
            v, 0,
            16
        );

        int i;

        for (i = 0; i < 128; i++) {
            int bit =
                (x[i >>> 3]
                    >>> (7 - (i & 7)))
                & 1;

            if (bit != 0) {
                int j;

                for (j = 0; j < 16; j++) {
                    z[j] ^= v[j];
                }
            }

            int lsb =
                v[15] & 1;

            shiftRight(v);

            if (lsb != 0) {
                v[0] ^= (byte)0xE1;
            }
        }

        System.arraycopy(
            z, 0,
            x, 0,
            16
        );
    }

    private static void shiftRight(byte[] x) {
        int carry = 0;
        int i;

        for (i = 0; i < 16; i++) {
            int v = x[i] & 255;
            int next = v & 1;

            x[i] =
                (byte)((v >>> 1) | (carry << 7));

            carry = next;
        }
    }

    private static void xorBlock(
            byte[] x,
            byte[] y) {

        int i;

        for (i = 0; i < 16; i++) {
            x[i] ^= y[i];
        }
    }

    private static void inc32(byte[] x) {
        int i;

        for (i = 15; i >= 12; i--) {
            x[i]++;

            if (x[i] != 0) {
                break;
            }
        }
    }

    private static void putLength(
            byte[] out,
            int off,
            long value) {

        int i;

        for (i = 7; i >= 0; i--) {
            out[off + i] =
                (byte)value;

            value >>>= 8;
        }
    }
}