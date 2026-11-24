import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class Locker {

    // Turns the PIN into a 32-byte key. The PIN is mixed with random "salt"
    // and scrambled 10,000 times, which makes guessing PINs much slower.
    private static byte[] makeKeyFromPin(String pin, byte[] salt) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        byte[] key = digest.digest(pin.getBytes("UTF-8"));
        for (int i = 0; i < 10000; i++) {
            digest.update(key);
            digest.update(salt);
            key = digest.digest();
        }
        return key; // 32 bytes = 256 bits
    }

    // Locks a piece of text using the PIN and our own MyAES
    public static String lockText(String pin, String plainText) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        byte[] iv = new byte[16];
        random.nextBytes(salt);
        random.nextBytes(iv);

        byte[] key = makeKeyFromPin(pin, salt);
        byte[] locked = MyAES.encryptCBC(plainText.getBytes("UTF-8"), key, iv);

        Base64.Encoder enc = Base64.getEncoder();
        return enc.encodeToString(salt) + "::" + enc.encodeToString(iv) + "::" + enc.encodeToString(locked);
    }

    // Unlocks text that was locked using lockText
    public static String unlockText(String pin, String lockedText) throws Exception {
        String[] parts = lockedText.split("::");
        if (parts.length != 3) {
            throw new Exception("This note was locked with the old system");
        }
        Base64.Decoder dec = Base64.getDecoder();
        byte[] salt = dec.decode(parts[0]);
        byte[] iv = dec.decode(parts[1]);
        byte[] locked = dec.decode(parts[2]);

        byte[] key = makeKeyFromPin(pin, salt);
        byte[] plain = MyAES.decryptCBC(locked, key, iv);
        return new String(plain, "UTF-8");
    }
}
