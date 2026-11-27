package fi.gtrxac.tinytls;

public final class TLSCrypto {
    public static void hmacSha256(
            byte[] key,
            int keyOff,
            int keyLen,
            byte[] a,
            int aOff,
            int aLen,
            byte[] b,
            int bOff,
            int bLen,
            byte[] out,
            int outOff) {

        byte[] k = new byte[64];
        byte[] inner = new byte[32];

        int i;

        /*
         * Our TLS keys are all <= 64 bytes.
         *
         * Keep generic handling anyway.
         */
        if (keyLen > 64) {
            SHA256 s = new SHA256();

            s.update(key, keyOff, keyLen);
            s.digest(k, 0);

            keyLen = 32;
        } else {
            System.arraycopy(
                key,
                keyOff,
                k,
                0,
                keyLen
            );
        }

        SHA256 sha = new SHA256();

        /*
         * Convert k into ipad in place.
         */
        for (i = 0; i < 64; i++) {
            k[i] ^= 0x36;
        }

        sha.update(k, 0, 64);

        if (aLen != 0) {
            sha.update(a, aOff, aLen);
        }

        if (bLen != 0) {
            sha.update(b, bOff, bLen);
        }

        sha.digest(inner, 0);

        /*
         * ipad XOR opad:
         *
         * 0x36 XOR 0x5C = 0x6A
         */
        for (i = 0; i < 64; i++) {
            k[i] ^= 0x6A;
        }

        sha.update(k, 0, 64);
        sha.update(inner, 0, 32);
        sha.digest(out, outOff);
    }

    /*
     * TLS 1.2 PRF using SHA-256.
     */
    public static void prfSha256(
            byte[] secret,
            int secretOff,
            int secretLen,
            String label,
            byte[] seed,
            int seedOff,
            int seedLen,
            byte[] out,
            int outOff,
            int outLen) {

        byte[] labelBytes =
            TLSUtil.ascii(label);

        byte[] fullSeed =
            new byte[labelBytes.length + seedLen];

        System.arraycopy(
            labelBytes,
            0,
            fullSeed,
            0,
            labelBytes.length
        );

        System.arraycopy(
            seed,
            seedOff,
            fullSeed,
            labelBytes.length,
            seedLen
        );

        /*
         * A(1) = HMAC(secret, seed)
         */
        byte[] A = new byte[32];
        byte[] block = new byte[32];

        hmacSha256(
            secret,
            secretOff,
            secretLen,
            fullSeed,
            0,
            fullSeed.length,
            fullSeed,
            0,
            0,
            A,
            0
        );

        int remaining = outLen;
        int p = outOff;

        while (remaining > 0) {

            /*
             * HMAC(secret, A(i) || seed)
             */
            hmacSha256(
                secret,
                secretOff,
                secretLen,
                A,
                0,
                32,
                fullSeed,
                0,
                fullSeed.length,
                block,
                0
            );

            int n = remaining;

            if (n > 32) {
                n = 32;
            }

            System.arraycopy(
                block,
                0,
                out,
                p,
                n
            );

            p += n;
            remaining -= n;

            /*
             * A(i+1) = HMAC(secret, A(i))
             */
            hmacSha256(
                secret,
                secretOff,
                secretLen,
                A,
                0,
                32,
                A,
                0,
                0,
                block,
                0
            );

            System.arraycopy(
                block,
                0,
                A,
                0,
                32
            );
        }
    }
}