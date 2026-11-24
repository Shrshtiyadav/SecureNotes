import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class PinManager {
    private static final String PIN_FILE = "pin.txt";
    private static final int MAX_ATTEMPTS = 3;

    // Checks if a PIN has already been set up before
    public static boolean pinExists() {
        return Files.exists(Paths.get(PIN_FILE));
    }

    // Saves a new PIN (used the very first time the app runs)
    public static void setupPin(String pin) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = hashPin(pin, salt);

        String saltText = Base64.getEncoder().encodeToString(salt);
        String hashText = Base64.getEncoder().encodeToString(hash);

        try (FileWriter writer = new FileWriter(PIN_FILE)) {
            writer.write(saltText + "::" + hashText);
        }
    }

    // Checks if the entered PIN matches the saved one
    public static boolean checkPin(String enteredPin) throws Exception {
        String content = new String(Files.readAllBytes(Paths.get(PIN_FILE)));
        String[] parts = content.split("::");
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] savedHash = Base64.getDecoder().decode(parts[1]);

        byte[] enteredHash = hashPin(enteredPin, salt);
        return java.util.Arrays.equals(savedHash, enteredHash);
    }

    // Turns a PIN + a bit of random data into a safe, scrambled code
    private static byte[] hashPin(String pin, byte[] salt) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        return digest.digest(pin.getBytes("UTF-8"));
    }

    public static int getMaxAttempts() {
        return MAX_ATTEMPTS;
    }
}
