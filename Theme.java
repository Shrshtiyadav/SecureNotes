import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

// The colours of the app, which mascot is shown, and remembering your choice
public class Theme {
    public final String name;
    public final Color bg1, bg2;      // the two colours of the background
    public final Color primary;       // main colour (buttons, titles)
    public final Color accent;        // second colour (small highlights)
    public final Color dark;          // colour for title text

    private Theme(String name, Color bg1, Color bg2, Color primary, Color accent, Color dark) {
        this.name = name;
        this.bg1 = bg1;
        this.bg2 = bg2;
        this.primary = primary;
        this.accent = accent;
        this.dark = dark;
    }

    public static final Theme[] ALL = {
            new Theme("Sakura", new Color(255, 232, 242), new Color(228, 222, 255),
                    new Color(232, 76, 138), new Color(255, 160, 196), new Color(110, 30, 70)),
            new Theme("Ocean", new Color(220, 242, 255), new Color(210, 224, 255),
                    new Color(30, 118, 222), new Color(70, 200, 230), new Color(16, 52, 110)),
            new Theme("Mint", new Color(224, 250, 236), new Color(218, 240, 255),
                    new Color(24, 158, 118), new Color(255, 178, 90), new Color(14, 82, 64)),
            new Theme("Sunset", new Color(255, 238, 218), new Color(255, 222, 234),
                    new Color(238, 108, 46), new Color(255, 190, 60), new Color(120, 50, 20)),
            new Theme("Grape", new Color(238, 230, 255), new Color(255, 228, 246),
                    new Color(128, 78, 218), new Color(255, 118, 188), new Color(60, 30, 110))
    };

    public static final String[] MASCOTS = {"Mika", "Droney", "Mochi", "Picture"};

    private static final String FILE = "appearance.properties";
    private static Theme current = ALL[0];
    private static String mascot = "Mika";
    private static String picturePath = "";
    private static BufferedImage picture;

    public static Theme current() {
        return current;
    }

    public static void setCurrent(Theme t) {
        current = t;
    }

    public static String mascot() {
        return mascot;
    }

    public static void setMascot(String name) {
        mascot = name;
    }

    public static BufferedImage picture() {
        return picture;
    }

    public static String picturePath() {
        return picturePath;
    }

    // Loads a picture of your own to use as the mascot (any PNG, JPG or GIF file)
    public static void setPicture(String path) throws IOException {
        BufferedImage img = ImageIO.read(new File(path));
        if (img == null) {
            throw new IOException("That file is not a picture the app can open.\nUse a PNG, JPG or GIF file.");
        }
        int max = 512;                                  // keep it small so the app stays fast
        double scale = Math.min(1.0, (double) max / Math.max(img.getWidth(), img.getHeight()));
        int w = Math.max(1, (int) Math.round(img.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(img.getHeight() * scale));
        BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        picture = small;
        picturePath = path;
    }

    // ---------- remembering your choice ----------

    public static void load() {
        Path path = Paths.get(FILE);
        if (!Files.exists(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Properties p = new Properties();
            p.load(reader);
            String themeName = p.getProperty("theme", "Sakura");
            for (Theme t : ALL) {
                if (t.name.equals(themeName)) {
                    current = t;
                }
            }
            String m = p.getProperty("mascot", "Mika");
            String pic = p.getProperty("picture", "");
            if (!pic.isEmpty()) {
                try {
                    setPicture(pic);
                } catch (IOException e) {
                    picture = null;               // the file moved or was deleted
                    picturePath = "";
                }
            }
            boolean known = false;
            for (String name : MASCOTS) {
                if (name.equals(m)) {
                    known = true;
                }
            }
            mascot = (known && !(m.equals("Picture") && picture == null)) ? m : "Mika";
        } catch (IOException e) {
            // keep the default look
        }
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("theme", current.name);
        p.setProperty("mascot", mascot);
        p.setProperty("picture", picturePath);
        try (Writer writer = Files.newBufferedWriter(Paths.get(FILE), StandardCharsets.UTF_8)) {
            p.store(writer, "SecureNotes look");
        } catch (IOException e) {
            // not important: the look just will not be remembered
        }
    }

    // ---------- small colour helpers ----------

    public static Color mix(Color a, Color b, double t) {
        return new Color(
                clamp((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t)),
                clamp((int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t)),
                clamp((int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t)));
    }

    public static Color darker(Color c, double amount) {
        return mix(c, Color.BLACK, amount);
    }

    public static Color lighter(Color c, double amount) {
        return mix(c, Color.WHITE, amount);
    }

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), clamp(a));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
