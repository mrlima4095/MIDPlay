package fi.gtrxac.tinytls;

import fi.gtrxac.bluewap.*;
import java.io.*;

/**
 * To run tests, add TLSTest.test() somewhere in your code
 */
public class TLSTest {
    public static void test() {
        // https://github.com/AliMoghimii/AES128-Encryptor-Decryptor-KeyGenerator/blob/master/src/Part1.java#L405

        AES128 aes128 = new AES128();

        aes128Test(aes128,
            "5468617473206D79204B756E67204675",
            "54776F204F6E65204E696E652054776F",
            "29C3505F571420F6402299B31A02D73A");

        aes128Test(aes128,
            "c0000000000000000000000000000000",
            "00000000000000000000000000000000",
            "4bc3f883450c113c64ca42e1112a9e87");

        aes128Test(aes128,
            "00000000000000000000000000000000",
            "f34481ec3cc627bacd5dc3fb08f273e6",
            "0336763e966d92595a567cc9ce537f5e");

        aes128Test(aes128,
            "b6364ac4e1de1e285eaf144a2415f7a0",
            "00000000000000000000000000000000",
            "5d9b05578fc944b3cf1ccf0e746cd581");

        // https://github.com/NovaCrypto/SHA256/blob/main/src/test/java/io/github/novacrypto/Sha256Tests.java
        // https://github.com/meyfa/java-sha256/blob/main/src/test/java/net/meyfa/sha256/Sha256Test.java

        SHA256 sha256 = new SHA256();

        sha256Test(sha256, "", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        sha256Test(sha256, "00", "6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d");
        sha256Test(sha256, "01", "4bf5122f344554c53bde2ebb8cd2b7e3d1600ad631c385a5d7cce23c7785459a");
        sha256Test(sha256, "0000", "96a296d224f285c67bee93c30f8a309157f0daa35dc5b87e410b78630a09cfc7");
        sha256Test(sha256, "ffff", "ca2fd00fa001190744c15c317643ab092e7048ce086a243e2be9437c898de1bb");

        sha256TestString(sha256, "Hello world!",
            "c0535e4be2b79ffd93291305436bf889314e4a3faec05ecffcbb7df31ad9e51a");

        String lorem = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. "
                + "Proin pulvinar turpis purus, sit amet dapibus magna commodo "
                + "quis metus.";

        sha256TestString(sha256, lorem,
            "60497604d2f6b4df42cea5efb8956f587f81a4ad66fa1b65d9e085224d255036");

        byte[] allBytes = new byte[256];

        for (int i = 0; i < 256; i++) {
            allBytes[i] = (byte) i;
        }

        sha256Test(sha256, Util.bytesToHex(allBytes),
            "40aff2e9d2d8922e47afd4648e6967497158785fbd1da870e7110266bf944880");

        byte[] a55 = new byte[55];

        for (int i = 0; i < 55; i++) {
            a55[i] = (byte) 'a';
        }

        sha256Test(sha256, Util.bytesToHex(a55),
            "9f4390f8d30c2dd92ec9f095b65e2b9ae9b0a925a5258e241c9f1e910f734318");

        // https://github.com/OskarKarpinski/libcrypto-java/blob/main/src/test/java/dev/medzik/libcrypto/Curve25519Tests.java

        X25519 x25519 = new X25519();

        x25519Test(x25519,
            "3845bead1f44408ee436c742291f1362489eeaaa9daebd480b1c3e4bc528cb48",
            "9d49b72cf49defc6748c67ab274a1c2f096362ef3b2d691793686589760b4e25",
            "2bebf3c397ab3c79db9aeeb2c1523ab4a32bd1ae335a19cd47e35983a5184d09");
    }

    private static void aes128Test(AES128 aes128, String key, String text, String expectedCipher) {
        byte[] keyBytes = Util.hexToBytes(key);
        byte[] textBytes = Util.hexToBytes(text);
        byte[] expectedCipherBytes = Util.hexToBytes(expectedCipher);
        byte[] gotCipherBytes = new byte[16];
        
        aes128.setKey(keyBytes, 0);
        aes128.encryptBlock(textBytes, 0, gotCipherBytes, 0);

        printResult(
            "AES128 test (" + key.substring(0, 4) + ", " + text.substring(0, 4) + ")",
            expectedCipherBytes, gotCipherBytes);
    }

    private static void sha256Test(SHA256 sha256, String input, String expectedOutput) {
        byte[] inputBytes = Util.hexToBytes(input);
        byte[] expectedOutputBytes = Util.hexToBytes(expectedOutput);
        byte[] gotOutputBytes = new byte[32];

        sha256.reset();
        sha256.update(inputBytes, 0, inputBytes.length);
        sha256.digest(gotOutputBytes, 0);

        printResult(
            "SHA256 test (" + Util.stringToLength(input, 8) + ")",
            expectedOutputBytes, gotOutputBytes);
    }

    private static void sha256TestBytes(SHA256 sha256, byte[] inputBytes, String expectedOutput) {
        String inputHex = Util.bytesToHex(inputBytes);

        sha256Test(sha256, inputHex, expectedOutput);
    }

    private static void sha256TestString(SHA256 sha256, String inputString, String expectedOutput) {
        try {
            byte[] inputBytes = inputString.getBytes("us-ascii");

            sha256TestBytes(sha256, inputBytes, expectedOutput);
        }
        catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private static void x25519Test(X25519 x25519, String priv, String pub, String expectedSharedSecret) {
        byte[] privBytes = Util.hexToBytes(priv);
        byte[] pubBytes = Util.hexToBytes(pub);
        byte[] expectedSharedSecretBytes = Util.hexToBytes(expectedSharedSecret);
        byte[] gotSharedSecretBytes = new byte[32];

        x25519.scalarMult(gotSharedSecretBytes, 0, privBytes, 0, pubBytes, 0);

        printResult(
            "X25519 test (" + priv.substring(0, 4) + ", " + pub.substring(0, 4) + ")",
            expectedSharedSecretBytes, gotSharedSecretBytes);
    }

    private static void printResult(String what, byte[] expected, byte[] got) {
        StringBuffer print = new StringBuffer();

        print.append(what)
            .append(": ");

        if (equal(expected, got)) {
            print.append("OK");
        }
        else {
            print.append("FAIL (expected '")
                .append(Util.bytesToHex(expected))
                .append("', got '")
                .append(Util.bytesToHex(got))
                .append("')");
        }

        System.out.println(print.toString());
    }

    private static boolean equal(byte[] a, byte[] b) {
        if (a.length != b.length) return false;

        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return false;
        }

        return true;
    }
}