import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

// Turns a formatted page (text, colours, pictures, tick boxes, tables, drawings)
// into plain saveable text and back
public class RichText {
    public static final Color PAPER = Color.WHITE;      // the editor's background colour
    public static final String DEFAULT_FONT = "SansSerif";
    public static final int DEFAULT_SIZE = 16;
    public static final String IMAGE_KEY = "imgdata";   // marks a picture inside the text
    public static final String CHECK_KEY = "chk";       // marks a tick box inside the text
    public static final String LINE_KEY = "hr";         // marks a divider line inside the text

    public static String encode(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String s) {
        return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }

    // The plain starting look of the editor
    public static void setupDefaultStyle(StyledDocument doc) {
        Style base = doc.getStyle(StyleContext.DEFAULT_STYLE);
        StyleConstants.setFontFamily(base, DEFAULT_FONT);
        StyleConstants.setFontSize(base, DEFAULT_SIZE);
        StyleConstants.setForeground(base, Color.BLACK);
    }

    private static String colorToHex(Color c) {
        return String.format("#%06X", c.getRGB() & 0xFFFFFF);
    }

    // ---------- the little tick box ----------

    public static class CheckIcon implements Icon {
        public static final CheckIcon UNCHECKED = new CheckIcon(false);
        public static final CheckIcon CHECKED = new CheckIcon(true);
        private final boolean checked;

        private CheckIcon(boolean checked) {
            this.checked = checked;
        }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Theme th = Theme.current();
            RoundRectangle2D box = new RoundRectangle2D.Double(x + 1.5, y + 2.5, 16, 16, 6, 6);
            g2.setColor(checked ? th.primary : Color.WHITE);
            g2.fill(box);
            g2.setColor(th.primary);
            g2.setStroke(new BasicStroke(2f));
            g2.draw(box);
            if (checked) {
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D tick = new Path2D.Double();
                tick.moveTo(x + 5.5, y + 10.5);
                tick.lineTo(x + 8.5, y + 13.5);
                tick.lineTo(x + 14, y + 7);
                g2.draw(tick);
            }
            g2.dispose();
        }

        public int getIconWidth() {
            return 20;
        }

        public int getIconHeight() {
            return 20;
        }
    }

    // ---------- a divider line across the page ----------

    public static class LineIcon implements Icon {
        public static final LineIcon INSTANCE = new LineIcon();

        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Theme th = Theme.current();
            g2.setPaint(new java.awt.GradientPaint(x, 0, th.primary, x + getIconWidth(), 0, th.accent));
            g2.fill(new RoundRectangle2D.Double(x, y + 5, getIconWidth(), 4, 4, 4));
            g2.dispose();
        }

        public int getIconWidth() {
            return 520;
        }

        public int getIconHeight() {
            return 14;
        }
    }

    public static AttributeSet lineAttributes() {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setIcon(a, LineIcon.INSTANCE);
        a.addAttribute(LINE_KEY, Boolean.TRUE);
        return a;
    }

    // ---------- things you can put inside a note ----------

    // A picture: scaled down if it is wide, saved as JPEG (or PNG when it has see-through parts)
    public static String encodePicture(BufferedImage source, int maxWidth) throws java.io.IOException {
        BufferedImage img = source;
        if (img.getWidth() > maxWidth) {
            int h = Math.max(1, (int) Math.round(img.getHeight() * (maxWidth / (double) img.getWidth())));
            BufferedImage small = new BufferedImage(maxWidth, h,
                    img.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D g = small.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(img, 0, 0, maxWidth, h, null);
            g.dispose();
            img = small;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (img.getColorModel().hasAlpha()) {
            ImageIO.write(img, "png", out);
        } else {
            BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
                writer.setOutput(stream);
                writer.write(null, new IIOImage(rgb, null, null), param);
            } finally {
                writer.dispose();
            }
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    // The look of a picture placed in the text (null if the data is not a picture)
    public static AttributeSet imageAttributes(String base64) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
            if (img == null) {
                return null;
            }
            SimpleAttributeSet a = new SimpleAttributeSet();
            StyleConstants.setIcon(a, new ImageIcon(img));
            a.addAttribute(IMAGE_KEY, base64);
            return a;
        } catch (Exception e) {
            return null;
        }
    }

    public static AttributeSet checkAttributes(boolean checked) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setIcon(a, checked ? CheckIcon.CHECKED : CheckIcon.UNCHECKED);
        a.addAttribute(CHECK_KEY, checked);
        return a;
    }

    public static AttributeSet tableAttributes(TableBox table) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setComponent(a, table);
        return a;
    }

    private static String pictureId(String data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(data.hashCode());
        }
    }

    // ---------- saving the page ----------

    // Writes the page as lines of text. Pictures go into "assets" (each kept only once).
    public static String toBody(StyledDocument doc, Map<String, String> assets, List<DrawPane.Stroke> strokes) {
        StringBuilder sb = new StringBuilder();
        try {
            int length = doc.getLength();
            int pos = 0;
            while (pos < length) {
                Element piece = doc.getCharacterElement(pos);
                int end = Math.min(piece.getEndOffset(), length);
                if (end <= pos) {
                    break;
                }
                AttributeSet a = piece.getAttributes();
                Object component = a.getAttribute(StyleConstants.ComponentAttribute);
                if (a.isDefined(CHECK_KEY)) {
                    for (int i = pos; i < end; i++) {
                        sb.append("CHK|").append(Boolean.TRUE.equals(a.getAttribute(CHECK_KEY)) ? "1" : "0").append("\n");
                    }
                } else if (a.isDefined(LINE_KEY)) {
                    for (int i = pos; i < end; i++) {
                        sb.append("HR|\n");
                    }
                } else if (a.isDefined(IMAGE_KEY)) {
                    String data = (String) a.getAttribute(IMAGE_KEY);
                    String id = pictureId(data);
                    assets.putIfAbsent(id, data);
                    for (int i = pos; i < end; i++) {
                        sb.append("IMG|").append(id).append("\n");
                    }
                } else if (component instanceof TableBox) {
                    for (int i = pos; i < end; i++) {
                        sb.append(((TableBox) component).toRecord()).append("\n");
                    }
                } else {
                    String text = doc.getText(pos, end - pos);
                    sb.append(StyleConstants.isBold(a) ? "1" : "0");
                    sb.append(StyleConstants.isItalic(a) ? "1" : "0");
                    sb.append(StyleConstants.isUnderline(a) ? "1" : "0");
                    sb.append("|").append(StyleConstants.getFontFamily(a));
                    sb.append("|").append(StyleConstants.getFontSize(a));
                    sb.append("|").append(colorToHex(StyleConstants.getForeground(a)));

                    String highlight = "-";
                    if (a.isDefined(StyleConstants.Background)) {
                        Color bg = StyleConstants.getBackground(a);
                        if (!bg.equals(PAPER)) {
                            highlight = colorToHex(bg);
                        }
                    }
                    sb.append("|").append(highlight);
                    sb.append("|").append(encode(text)).append("\n");
                }
                pos = end;
            }
            // left / centre / right of each paragraph
            Element root = doc.getDefaultRootElement();
            for (int i = 0; i < root.getElementCount(); i++) {
                int alignment = StyleConstants.getAlignment(root.getElement(i).getAttributes());
                if (alignment == StyleConstants.ALIGN_CENTER || alignment == StyleConstants.ALIGN_RIGHT) {
                    sb.append("ALN|").append(i).append("|").append(alignment).append("\n");
                }
            }
        } catch (BadLocationException e) {
            // nothing to do
        }
        for (DrawPane.Stroke s : strokes) {
            sb.append(s.toRecord()).append("\n");
        }
        return sb.toString();
    }

    // The simple version (no pictures, no drawings)
    public static String toBody(StyledDocument doc) {
        return toBody(doc, new java.util.HashMap<>(), new ArrayList<>());
    }

    // ---------- opening a saved page ----------

    // Puts a saved page back into the editor. Pen lines are handed back in strokesOut.
    public static void loadBody(StyledDocument doc, String body, Map<String, String> assets,
                                List<DrawPane.Stroke> strokesOut) {
        List<int[]> alignments = new ArrayList<>();
        try {
            doc.remove(0, doc.getLength());
            for (String line : body.split("\n")) {
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    if (line.startsWith("IMG|")) {
                        String data = assets.get(line.substring(4));
                        AttributeSet a = data == null ? null : imageAttributes(data);
                        if (a != null) {
                            doc.insertString(doc.getLength(), " ", a);
                        } else {
                            doc.insertString(doc.getLength(), "[picture missing]", null);
                        }
                    } else if (line.startsWith("HR|")) {
                        doc.insertString(doc.getLength(), " ", lineAttributes());
                    } else if (line.startsWith("CHK|")) {
                        doc.insertString(doc.getLength(), " ", checkAttributes(line.endsWith("1")));
                    } else if (line.startsWith("TBL|")) {
                        doc.insertString(doc.getLength(), " ", tableAttributes(TableBox.fromRecord(line)));
                    } else if (line.startsWith("STK|")) {
                        if (strokesOut != null) {
                            strokesOut.add(DrawPane.Stroke.parse(line));
                        }
                    } else if (line.startsWith("ALN|")) {
                        String[] p = line.split("\\|");
                        alignments.add(new int[]{Integer.parseInt(p[1]), Integer.parseInt(p[2])});
                    } else {
                        String[] p = line.split("\\|", 6);
                        if (p.length < 6) {
                            continue;
                        }
                        SimpleAttributeSet a = new SimpleAttributeSet();
                        StyleConstants.setBold(a, p[0].charAt(0) == '1');
                        StyleConstants.setItalic(a, p[0].charAt(1) == '1');
                        StyleConstants.setUnderline(a, p[0].charAt(2) == '1');
                        StyleConstants.setFontFamily(a, p[1]);
                        StyleConstants.setFontSize(a, Integer.parseInt(p[2]));
                        StyleConstants.setForeground(a, Color.decode(p[3]));
                        if (!p[4].equals("-")) {
                            StyleConstants.setBackground(a, Color.decode(p[4]));
                        }
                        doc.insertString(doc.getLength(), decode(p[5]), a);
                    }
                } catch (RuntimeException e) {
                    // one damaged line is skipped; the rest still loads
                }
            }
            Element root = doc.getDefaultRootElement();
            for (int[] al : alignments) {
                if (al[0] >= 0 && al[0] < root.getElementCount()) {
                    Element paragraph = root.getElement(al[0]);
                    SimpleAttributeSet a = new SimpleAttributeSet();
                    StyleConstants.setAlignment(a, al[1]);
                    doc.setParagraphAttributes(paragraph.getStartOffset(),
                            paragraph.getEndOffset() - paragraph.getStartOffset(), a, false);
                }
            }
        } catch (BadLocationException e) {
            // keep what was loaded so far
        }
    }

    public static void loadBody(StyledDocument doc, String body) {
        loadBody(doc, body, new java.util.HashMap<>(), null);
    }

    // Used for notes from the first version of the app (plain text only)
    public static String plainToBody(String text) {
        if (text.isEmpty()) {
            return "";
        }
        return "000|" + DEFAULT_FONT + "|" + DEFAULT_SIZE + "|#000000|-|" + encode(text) + "\n";
    }

    // ---------- the words of a note, for searching ----------

    public static String plainText(String body) {
        StringBuilder sb = new StringBuilder();
        for (String line : body.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            try {
                if (line.startsWith("CHK|")) {
                    sb.append(line.endsWith("1") ? "[x] " : "[ ] ");
                } else if (line.startsWith("TBL|")) {
                    for (String[] row : TableBox.parseCells(line)) {
                        sb.append(String.join(" | ", row)).append("\n");
                    }
                } else if (line.startsWith("IMG|")) {
                    sb.append("[picture] ");
                } else if (line.startsWith("STK|") || line.startsWith("ALN|") || line.startsWith("HR|")) {
                    continue;
                } else {
                    String[] p = line.split("\\|", 6);
                    if (p.length >= 6) {
                        sb.append(decode(p[5]));
                    }
                }
            } catch (RuntimeException e) {
                // skip a damaged line
            }
        }
        return sb.toString();
    }
}