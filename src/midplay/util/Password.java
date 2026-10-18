package midplay.util;

/**
 * Lightweight password hashing for the notes vault.
 *
 * CLDC 1.1 has no MessageDigest, so this uses an iterated FNV-1a / xorshift mix
 * over the UTF-8 bytes of the password with a fixed salt. It is not a
 * cryptographic KDF, but it hides the plain password in the settings store and
 * makes brute forcing noticeably slower than a single hash.
 */
public final class Password {
  private static final String SALT = "MIDPlayVault:v1:";
  private static final long FNV_OFFSET = 0xcbf29ce484222325L;
  private static final long FNV_PRIME = 0x100000001b3L;
  private static final int ROUNDS = 512;
  private static final String HEX = "0123456789abcdef";

  public static String hash(String password) {
    byte[] salt = Utils.utf8ToBytes(SALT);
    byte[] input = Utils.utf8ToBytes(password == null ? "" : password);

    long a = FNV_OFFSET;
    long b = FNV_OFFSET ^ input.length;
    for (int round = 0; round < ROUNDS; round++) {
      for (int i = 0; i < salt.length; i++) {
        a = (a ^ (salt[i] & 0xFF)) * FNV_PRIME;
        b = mix(b, salt[i] & 0xFF);
      }
      for (int i = 0; i < input.length; i++) {
        a = (a ^ (input[i] & 0xFF)) * FNV_PRIME;
        b = mix(b, input[i] & 0xFF);
      }
      b = (b ^ a) * FNV_PRIME;
    }
    return toHex(a) + toHex(b);
  }

  private static String toHex(long value) {
    StringBuffer sb = new StringBuffer(16);
    for (int i = 60; i >= 0; i -= 4) {
      sb.append(HEX.charAt((int) ((value >>> i) & 0xF)));
    }
    return sb.toString();
  }

  private static long mix(long value, int data) {
    value += data;
    value ^= (value << 13);
    value ^= (value >>> 7);
    value ^= (value << 17);
    return value;
  }

  public static boolean verify(String password, String hash) {
    if (hash == null || hash.length() == 0) {
      return false;
    }
    return hash.equals(hash(password));
  }

  private Password() {}
}
