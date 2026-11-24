import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.swing.JTextPane;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

// Saves a copy of a note as a web page, a text file, a picture or a PDF.
// NOTE: these copies are NOT locked.
public class Exporter {

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ---------- plain text ----------

    public static String toText(List<Note> notes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < notes.size(); i++) {
            Note n = notes.get(i);
            if (i > 0) {
                sb.append("\n\n----------------------------------------\n\n");
            }
            String title = n.title.trim().isEmpty() ? "Untitled note" : n.title.trim();
            sb.append(title).append("\n").append("=".repeat(Math.min(60, Math.max(3, title.length())))).append("\n\n");
            sb.append(RichText.plainText(n.body));
        }
        return sb.toString();
    }

    // ---------- web page ----------

    public static String toHtml(List<Note> notes) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"><title>")
                .append(esc(notes.isEmpty() ? "Note" : notes.get(0).title)).append("</title>\n")
                .append("<style>body{font-family:Segoe UI,Arial,sans-serif;max-width:820px;margin:30px auto;padding:0 16px;}")
                .append("table{border-collapse:collapse;margin:8px 0;}td{border:1px solid #999;padding:6px 10px;min-width:70px;}")
                .append("section{margin-bottom:40px;}img{max-width:100%;}</style></head><body>\n");
        for (Note n : notes) {
            String title = n.title.trim().isEmpty() ? "Untitled note" : n.title.trim();
            sb.append("<section>\n<h1>").append(esc(title)).append("</h1>\n<p>");
            for (String line : n.body.split("\n")) {
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    if (line.startsWith("CHK|")) {
                        sb.append(line.endsWith("1") ? "&#9745; " : "&#9744; ");
                    } else if (line.startsWith("HR|")) {
                        sb.append("</p><hr><p>");
                    } else if (line.startsWith("IMG|")) {
                        String data = n.assets.get(line.substring(4));
                        if (data != null) {
                            byte[] raw = Base64.getDecoder().decode(data);
                            String mime = (raw.length > 1 && raw[1] == 'P') ? "image/png" : "image/jpeg";
                            sb.append("</p><p><img alt=\"picture\" src=\"data:").append(mime).append(";base64,")
                                    .append(data).append("\"></p><p>");
                        }
                    } else if (line.startsWith("TBL|")) {
                        sb.append("</p><table>");
                        for (String[] row : TableBox.parseCells(line)) {
                            sb.append("<tr>");
                            for (String cell : row) {
                                sb.append("<td>").append(esc(cell)).append("</td>");
                            }
                            sb.append("</tr>");
                        }
                        sb.append("</table><p>");
                    } else if (line.startsWith("STK|") || line.startsWith("ALN|")) {
                        continue;
                    } else {
                        String[] p = line.split("\\|", 6);
                        if (p.length < 6) {
                            continue;
                        }
                        StringBuilder style = new StringBuilder();
                        style.append("font-family:'").append(p[1]).append("';font-size:").append(p[2]).append("pt;color:").append(p[3]).append(";");
                        if (!p[4].equals("-")) {
                            style.append("background:").append(p[4]).append(";");
                        }
                        if (p[0].charAt(0) == '1') {
                            style.append("font-weight:bold;");
                        }
                        if (p[0].charAt(1) == '1') {
                            style.append("font-style:italic;");
                        }
                        if (p[0].charAt(2) == '1') {
                            style.append("text-decoration:underline;");
                        }
                        String text = esc(RichText.decode(p[5])).replace("\n", "<br>\n");
                        sb.append("<span style=\"").append(style).append("\">").append(text).append("</span>");
                    }
                } catch (RuntimeException e) {
                    // skip a damaged line
                }
            }
            sb.append("</p>\n</section>\n");
        }
        sb.append("</body></html>\n");
        return sb.toString();
    }

    // ---------- a picture of the page ----------

    // Draws the whole page (not just the part you can see) into a picture
    public static BufferedImage renderPage(JTextPane editor, int width) {
        int oldWidth = editor.getWidth();
        int oldHeight = editor.getHeight();
        int selStart = editor.getSelectionStart();
        int selEnd = editor.getSelectionEnd();
        editor.select(selStart, selStart);               // hide the blue selection in the picture
        editor.setSize(width, 20000);
        Dimension wanted = editor.getPreferredSize();
        int height = Math.max(300, wanted.height);
        editor.setSize(width, height);
        // a first quick pass lets tables and other inserted parts find their place on the page
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D sg = scratch.createGraphics();
        editor.paint(sg);
        sg.dispose();
        layoutTree(editor);
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        editor.paint(g);
        if (!editor.isShowing()) {
            paintEmbedded(editor, g, 0, 0);       // when the window is not on screen, draw tables ourselves
        }
        g.dispose();
        editor.setSize(oldWidth, oldHeight);
        editor.select(selStart, selEnd);
        editor.revalidate();
        return img;
    }

    private static void paintEmbedded(java.awt.Container parent, Graphics2D g, int ox, int oy) {
        for (java.awt.Component c : parent.getComponents()) {
            if (!c.isVisible() || c.getWidth() <= 0 || c.getHeight() <= 0) {
                continue;
            }
            if (c instanceof javax.swing.JComponent) {
                Graphics2D cg = (Graphics2D) g.create(ox + c.getX(), oy + c.getY(), c.getWidth(), c.getHeight());
                c.paint(cg);
                cg.dispose();
            } else if (c instanceof java.awt.Container) {
                paintEmbedded((java.awt.Container) c, g, ox + c.getX(), oy + c.getY());
            }
        }
    }

    private static void layoutTree(java.awt.Container c) {
        c.doLayout();
        for (java.awt.Component k : c.getComponents()) {
            if (k instanceof java.awt.Container) {
                layoutTree((java.awt.Container) k);
            }
        }
    }

    public static void writePng(BufferedImage img, File file) throws IOException {
        ImageIO.write(img, "png", file);
    }

    // ---------- a PDF (each page of the PDF is a slice of the picture) ----------

    private static byte[] jpegBytes(BufferedImage img) throws IOException {
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(img, 0, 0, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.9f);
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    public static void writePdf(BufferedImage page, File file) throws IOException {
        final double pageW = 595;
        final double pageH = 842;
        final double margin = 40;
        double scale = (pageW - 2 * margin) / page.getWidth();
        int sliceHeight = (int) Math.floor((pageH - 2 * margin) / scale);
        int pages = Math.max(1, (int) Math.ceil(page.getHeight() / (double) sliceHeight));

        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(pdf, "%PDF-1.4\n");

        // 1 = catalog, 2 = list of pages, then (page, contents, picture) for each page
        offsets.add(pdf.size());
        write(pdf, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pages; i++) {
            kids.append(3 + 3 * i).append(" 0 R ");
        }
        offsets.add(pdf.size());
        write(pdf, "2 0 obj\n<< /Type /Pages /Kids [" + kids + "] /Count " + pages + " >>\nendobj\n");

        for (int i = 0; i < pages; i++) {
            int y = i * sliceHeight;
            int h = Math.min(sliceHeight, page.getHeight() - y);
            BufferedImage slice = page.getSubimage(0, y, page.getWidth(), h);
            byte[] jpeg = jpegBytes(slice);
            double drawW = page.getWidth() * scale;
            double drawH = h * scale;
            double drawY = pageH - margin - drawH;
            int pageObj = 3 + 3 * i;
            int contentObj = pageObj + 1;
            int imageObj = pageObj + 2;

            offsets.add(pdf.size());
            write(pdf, pageObj + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                    + "/Resources << /XObject << /Im0 " + imageObj + " 0 R >> >> /Contents " + contentObj + " 0 R >>\nendobj\n");

            String content = String.format(java.util.Locale.US, "q %.2f 0 0 %.2f %.2f %.2f cm /Im0 Do Q", drawW, drawH, margin, drawY);
            offsets.add(pdf.size());
            write(pdf, contentObj + " 0 obj\n<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream\nendobj\n");

            offsets.add(pdf.size());
            write(pdf, imageObj + " 0 obj\n<< /Type /XObject /Subtype /Image /Width " + slice.getWidth() + " /Height " + h
                    + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + jpeg.length + " >>\nstream\n");
            pdf.write(jpeg);
            write(pdf, "\nendstream\nendobj\n");
        }

        int xref = pdf.size();
        int count = offsets.size() + 1;
        StringBuilder table = new StringBuilder("xref\n0 " + count + "\n0000000000 65535 f \n");
        for (int off : offsets) {
            table.append(String.format("%010d 00000 n \n", off));
        }
        write(pdf, table.toString());
        write(pdf, "trailer\n<< /Size " + count + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        Files.write(file.toPath(), pdf.toByteArray());
    }

    private static void write(ByteArrayOutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    // Text for the body of an email (email programs cannot take very long text)
    public static String forEmail(List<Note> notes, int limit) {
        String text = toText(notes);
        return text.length() <= limit ? text : text.substring(0, limit) + "\n\n(The rest was cut off because it was too long for an email link.)";
    }

    public static Map<String, String> unused() {
        return null;
    }
}