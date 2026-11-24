public class MyAES {

    // Lookup tables, built once when the program starts
    private static final int[] SBOX = new int[256];
    private static final int[] INV_SBOX = new int[256];
    // "Multiply by 2, 3, 9, 11, 13, 14" answers, worked out in advance so locking is fast
    private static final int[] MUL2 = new int[256];
    private static final int[] MUL3 = new int[256];
    private static final int[] MUL9 = new int[256];
    private static final int[] MUL11 = new int[256];
    private static final int[] MUL13 = new int[256];
    private static final int[] MUL14 = new int[256];

    static {
        for (int a = 0; a < 256; a++) {
            // Find the number that multiplies with a to give 1 (0 stays 0)
            int inverse = 0;
            if (a != 0) {
                for (int b = 1; b < 256; b++) {
                    if (gmul(a, b) == 1) {
                        inverse = b;
                        break;
                    }
                }
            }
            // Mix the bits around a bit more, then add a fixed number
            int s = inverse ^ rotateLeft(inverse, 1) ^ rotateLeft(inverse, 2)
                    ^ rotateLeft(inverse, 3) ^ rotateLeft(inverse, 4) ^ 0x63;
            SBOX[a] = s & 0xFF;
            INV_SBOX[s & 0xFF] = a;
            MUL2[a] = gmul(a, 2);
            MUL3[a] = gmul(a, 3);
            MUL9[a] = gmul(a, 9);
            MUL11[a] = gmul(a, 11);
            MUL13[a] = gmul(a, 13);
            MUL14[a] = gmul(a, 14);
        }
    }

    private static int rotateLeft(int x, int n) {
        return ((x << n) | (x >> (8 - n))) & 0xFF;
    }

    // Special multiplication used by AES (numbers behave differently here)
    private static int gmul(int a, int b) {
        int result = 0;
        for (int i = 0; i < 8; i++) {
            if ((b & 1) != 0) {
                result ^= a;
            }
            boolean high = (a & 0x80) != 0;
            a = (a << 1) & 0xFF;
            if (high) {
                a ^= 0x1B;
            }
            b >>= 1;
        }
        return result;
    }

    // ---------- Making the 15 round keys from the 32-byte key ----------

    private static byte[] expandKey(byte[] key) {
        byte[] rk = new byte[240];
        System.arraycopy(key, 0, rk, 0, 32);
        int rcon = 1;
        for (int i = 8; i < 60; i++) {
            byte[] t = new byte[4];
            System.arraycopy(rk, 4 * (i - 1), t, 0, 4);
            if (i % 8 == 0) {
                byte first = t[0];
                t[0] = (byte) SBOX[t[1] & 0xFF];
                t[1] = (byte) SBOX[t[2] & 0xFF];
                t[2] = (byte) SBOX[t[3] & 0xFF];
                t[3] = (byte) SBOX[first & 0xFF];
                t[0] ^= rcon;
                rcon = gmul(rcon, 2);
            } else if (i % 8 == 4) {
                for (int j = 0; j < 4; j++) {
                    t[j] = (byte) SBOX[t[j] & 0xFF];
                }
            }
            for (int j = 0; j < 4; j++) {
                rk[4 * i + j] = (byte) (rk[4 * (i - 8) + j] ^ t[j]);
            }
        }
        return rk;
    }

    // ---------- The four small steps AES repeats ----------

    private static void addRoundKey(byte[] s, byte[] rk, int round) {
        for (int i = 0; i < 16; i++) {
            s[i] ^= rk[round * 16 + i];
        }
    }

    private static void subBytes(byte[] s) {
        for (int i = 0; i < 16; i++) {
            s[i] = (byte) SBOX[s[i] & 0xFF];
        }
    }

    private static void invSubBytes(byte[] s) {
        for (int i = 0; i < 16; i++) {
            s[i] = (byte) INV_SBOX[s[i] & 0xFF];
        }
    }

    // Row r of the 4x4 grid slides left by r places
    private static void shiftRows(byte[] s) {
        byte[] old = s.clone();
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                s[r + 4 * c] = old[r + 4 * ((c + r) % 4)];
            }
        }
    }

    private static void invShiftRows(byte[] s) {
        byte[] old = s.clone();
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                s[r + 4 * c] = old[r + 4 * ((c - r + 4) % 4)];
            }
        }
    }

    // Mixes the 4 numbers in each column together
    private static void mixColumns(byte[] s) {
        for (int c = 0; c < 4; c++) {
            int a0 = s[4 * c] & 0xFF;
            int a1 = s[4 * c + 1] & 0xFF;
            int a2 = s[4 * c + 2] & 0xFF;
            int a3 = s[4 * c + 3] & 0xFF;
            s[4 * c]     = (byte) (MUL2[a0] ^ MUL3[a1] ^ a2 ^ a3);
            s[4 * c + 1] = (byte) (a0 ^ MUL2[a1] ^ MUL3[a2] ^ a3);
            s[4 * c + 2] = (byte) (a0 ^ a1 ^ MUL2[a2] ^ MUL3[a3]);
            s[4 * c + 3] = (byte) (MUL3[a0] ^ a1 ^ a2 ^ MUL2[a3]);
        }
    }

    private static void invMixColumns(byte[] s) {
        for (int c = 0; c < 4; c++) {
            int a0 = s[4 * c] & 0xFF;
            int a1 = s[4 * c + 1] & 0xFF;
            int a2 = s[4 * c + 2] & 0xFF;
            int a3 = s[4 * c + 3] & 0xFF;
            s[4 * c]     = (byte) (MUL14[a0] ^ MUL11[a1] ^ MUL13[a2] ^ MUL9[a3]);
            s[4 * c + 1] = (byte) (MUL9[a0] ^ MUL14[a1] ^ MUL11[a2] ^ MUL13[a3]);
            s[4 * c + 2] = (byte) (MUL13[a0] ^ MUL9[a1] ^ MUL14[a2] ^ MUL11[a3]);
            s[4 * c + 3] = (byte) (MUL11[a0] ^ MUL13[a1] ^ MUL9[a2] ^ MUL14[a3]);
        }
    }

    // ---------- Locking and unlocking ONE block of 16 bytes ----------

    public static byte[] encryptBlock(byte[] block, byte[] key) {
        return encryptBlockWithRoundKeys(block, expandKey(key));
    }

    public static byte[] decryptBlock(byte[] block, byte[] key) {
        return decryptBlockWithRoundKeys(block, expandKey(key));
    }

    private static byte[] encryptBlockWithRoundKeys(byte[] block, byte[] rk) {
        byte[] s = block.clone();
        addRoundKey(s, rk, 0);
        for (int round = 1; round <= 13; round++) {
            subBytes(s);
            shiftRows(s);
            mixColumns(s);
            addRoundKey(s, rk, round);
        }
        subBytes(s);
        shiftRows(s);
        addRoundKey(s, rk, 14);
        return s;
    }

    private static byte[] decryptBlockWithRoundKeys(byte[] block, byte[] rk) {
        byte[] s = block.clone();
        addRoundKey(s, rk, 14);
        for (int round = 13; round >= 1; round--) {
            invShiftRows(s);
            invSubBytes(s);
            addRoundKey(s, rk, round);
            invMixColumns(s);
        }
        invShiftRows(s);
        invSubBytes(s);
        addRoundKey(s, rk, 0);
        return s;
    }

    // ---------- Locking and unlocking longer data (many blocks) ----------

    // Each block is mixed with the block before it, so repeated text
    // does not look repeated once locked. This style is called CBC.
    public static byte[] encryptCBC(byte[] data, byte[] key, byte[] iv) {
        byte[] rk = expandKey(key);

        // Pad the data so its length is a multiple of 16
        int padLen = 16 - (data.length % 16);
        byte[] padded = new byte[data.length + padLen];
        System.arraycopy(data, 0, padded, 0, data.length);
        for (int i = data.length; i < padded.length; i++) {
            padded[i] = (byte) padLen;
        }

        byte[] out = new byte[padded.length];
        byte[] previous = iv.clone();
        for (int i = 0; i < padded.length; i += 16) {
            byte[] block = new byte[16];
            for (int j = 0; j < 16; j++) {
                block[j] = (byte) (padded[i + j] ^ previous[j]);
            }
            byte[] locked = encryptBlockWithRoundKeys(block, rk);
            System.arraycopy(locked, 0, out, i, 16);
            previous = locked;
        }
        return out;
    }

    public static byte[] decryptCBC(byte[] data, byte[] key, byte[] iv) {
        if (data.length == 0 || data.length % 16 != 0) {
            throw new IllegalArgumentException("Locked data is damaged");
        }
        byte[] rk = expandKey(key);

        byte[] plain = new byte[data.length];
        byte[] previous = iv.clone();
        for (int i = 0; i < data.length; i += 16) {
            byte[] block = new byte[16];
            System.arraycopy(data, i, block, 0, 16);
            byte[] opened = decryptBlockWithRoundKeys(block, rk);
            for (int j = 0; j < 16; j++) {
                plain[i + j] = (byte) (opened[j] ^ previous[j]);
            }
            previous = block;
        }

        // Remove the padding added when locking
        int padLen = plain[plain.length - 1] & 0xFF;
        if (padLen < 1 || padLen > 16) {
            throw new IllegalArgumentException("Wrong PIN or damaged note");
        }
        for (int i = plain.length - padLen; i < plain.length; i++) {
            if ((plain[i] & 0xFF) != padLen) {
                throw new IllegalArgumentException("Wrong PIN or damaged note");
            }
        }
        byte[] result = new byte[plain.length - padLen];
        System.arraycopy(plain, 0, result, 0, result.length);
        return result;
    }
}