public class AesTest {
    public static void main(String[] args) {
        // The official test from the AES standard
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) {
            key[i] = (byte) i;
        }
        byte[] text = new byte[16];
        for (int i = 0; i < 16; i++) {
            text[i] = (byte) (i * 0x11);
        }

        byte[] locked = MyAES.encryptBlock(text, key);

        StringBuilder hex = new StringBuilder();
        for (byte b : locked) {
            hex.append(String.format("%02x", b));
        }
        System.out.println("Our result : " + hex);
        System.out.println("Official   : 8ea2b7ca516745bfeafc49904b496089");

        byte[] back = MyAES.decryptBlock(locked, key);
        System.out.println("Unlocks back correctly: " + java.util.Arrays.equals(back, text));
    }
}
