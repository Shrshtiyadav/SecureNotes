import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;

// Looks after the recovery email address and the 6-digit "Forgot PIN" code
public class RecoveryManager {

    public enum Result { OK, WRONG, EXPIRED, TOO_MANY, NONE }

    private static final String EMAIL_FILE = "recovery.txt";
    private static final long CODE_LIFETIME_MS = 10 * 60 * 1000;  // a code works for 10 minutes
    private static final long RESEND_WAIT_MS = 60 * 1000;         // wait 1 minute between emails
    private static final int MAX_TRIES = 5;                       // 5 wrong guesses and the code is dead

    // We keep only a scrambled copy of the code, never the code itself
    private static byte[] salt;
    private static byte[] codeHash;
    private static long expiresAt;
    private static int triesLeft;
    private static long lastSentAt;

    // ---------- the recovery email address ----------

    public static boolean looksLikeEmail(String text) {
        return text != null
                && text.length() <= 254
                && text.matches("[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}");
    }

    // The saved email, or null if none was saved
    public static String getEmail() {
        try {
            Path path = Paths.get(EMAIL_FILE);
            if (!Files.exists(path)) {
                return null;
            }
            String email = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
            return looksLikeEmail(email) ? email : null;
        } catch (IOException e) {
            return null;
        }
    }

    public static void saveEmail(String email) throws IOException {
        Files.write(Paths.get(EMAIL_FILE), email.trim().getBytes(StandardCharsets.UTF_8));
    }

    // shrshti@gmail.com  ->  s***@gmail.com
    public static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    // ---------- the 6-digit code ----------

    // Makes a new code and remembers a scrambled copy. Returns the code so it can be emailed.
    public static synchronized String newCode() throws Exception {
        SecureRandom random = new SecureRandom();
        String code = String.format("%06d", random.nextInt(1_000_000));
        salt = new byte[16];
        random.nextBytes(salt);
        codeHash = hash(salt, code);
        expiresAt = System.currentTimeMillis() + CODE_LIFETIME_MS;
        triesLeft = MAX_TRIES;
        return code;
    }

    // Call this after the email was really sent
    public static synchronized void markSent() {
        lastSentAt = System.currentTimeMillis();
    }

    public static synchronized int secondsUntilNextCode() {
        long left = lastSentAt + RESEND_WAIT_MS - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) ((left + 999) / 1000);
    }

    public static synchronized Result check(String entered) {
        if (codeHash == null) {
            return Result.NONE;
        }
        if (System.currentTimeMillis() > expiresAt) {
            clear();
            return Result.EXPIRED;
        }
        try {
            if (MessageDigest.isEqual(hash(salt, entered), codeHash)) {
                clear();                    // a code can only be used once
                return Result.OK;
            }
        } catch (Exception e) {
            return Result.NONE;
        }
        triesLeft--;
        if (triesLeft <= 0) {
            clear();
            return Result.TOO_MANY;
        }
        return Result.WRONG;
    }

    public static synchronized int triesLeft() {
        return triesLeft;
    }

    public static synchronized void clear() {
        codeHash = null;
        salt = null;
        expiresAt = 0;
        triesLeft = 0;
    }

    private static byte[] hash(byte[] salt, String code) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        return digest.digest(code.getBytes(StandardCharsets.UTF_8));
    }
}