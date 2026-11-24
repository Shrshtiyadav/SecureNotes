import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class NoteManager {
    private static final String NOTES_FILE = "notes.dat";
    private static final String PACKED = "z1:";
    private final String pin;
    private final List<Note> notes = new ArrayList<>();
    private final List<String> lockedLines = new ArrayList<>(); // the locked version of each note

    public NoteManager(String pin) {
        this.pin = pin;
    }

    // Makes the text smaller before it is locked (pictures and drawings can be big)
    private static String pack(String text) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        byte[] input = text.getBytes(StandardCharsets.UTF_8);
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return PACKED + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private static String unpack(String text) throws DataFormatException {
        if (!text.startsWith(PACKED)) {
            return text;                         // notes saved by older versions
        }
        byte[] packed = Base64.getDecoder().decode(text.substring(PACKED.length()));
        Inflater inflater = new Inflater();
        inflater.setInput(packed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!inflater.finished()) {
            int n = inflater.inflate(buffer);
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                break;
            }
            out.write(buffer, 0, n);
        }
        inflater.end();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    // Reads the file and unlocks every note
    public List<Note> loadNotes() throws Exception {
        notes.clear();
        lockedLines.clear();
        Path path = Paths.get(NOTES_FILE);
        if (Files.exists(path)) {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String text = unpack(Locker.unlockText(pin, line));
                notes.add(Note.fromStoredText(text));
                lockedLines.add(line);
            }
        }
        return new ArrayList<>(notes);
    }

    public void addNote(Note note) throws Exception {
        notes.add(note);
        lockedLines.add(Locker.lockText(pin, pack(note.toStoredText())));
        writeFile();
    }

    // Locks the changed note again and saves
    public void updateNote(Note note) throws Exception {
        int i = notes.indexOf(note);
        if (i < 0) {
            return;
        }
        lockedLines.set(i, Locker.lockText(pin, pack(note.toStoredText())));
        writeFile();
    }

    public void deleteNote(Note note) throws Exception {
        int i = notes.indexOf(note);
        if (i < 0) {
            return;
        }
        notes.remove(i);
        lockedLines.remove(i);
        writeFile();
    }

    // Removes notes that have been in the Recycle bin for too long. Returns how many were removed.
    public int purgeOldDeleted(long maxAgeMillis) throws Exception {
        long now = System.currentTimeMillis();
        int removed = 0;
        Iterator<Note> it = notes.iterator();
        int index = 0;
        while (it.hasNext()) {
            Note n = it.next();
            if (n.deleted && n.deletedAt > 0 && now - n.deletedAt > maxAgeMillis) {
                it.remove();
                lockedLines.remove(index);
                removed++;
            } else {
                index++;
            }
        }
        if (removed > 0) {
            writeFile();
        }
        return removed;
    }

    // Deletes every saved note for good (used when the PIN is reset)
    public static void eraseAllNotes() throws IOException {
        Files.deleteIfExists(Paths.get(NOTES_FILE));
        Files.deleteIfExists(Paths.get(NOTES_FILE + ".tmp"));
    }

    // Writes to a temporary file first, so a crash while saving can't damage your notes
    private void writeFile() throws IOException {
        Path target = Paths.get(NOTES_FILE);
        Path temp = Paths.get(NOTES_FILE + ".tmp");
        Files.write(temp, lockedLines, StandardCharsets.UTF_8);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }
}