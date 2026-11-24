import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

// Small pictures drawn in code (so they look the same on every computer)
public class Icons {

    private interface Painter {
        void paint(Graphics2D g, double s, Color c);
    }

    private static class DrawnIcon implements Icon {
        private final int size;
        private final Color color;
        private final Painter painter;

        DrawnIcon(int size, Color color, Painter painter) {
            this.size = size;
            this.color = color;
            this.painter = painter;
        }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            g2.setColor(color);
            painter.paint(g2, size, color);
            g2.dispose();
        }

        public int getIconWidth() {
            return size;
        }

        public int getIconHeight() {
            return size;
        }
    }

    private static BasicStroke round(double w) {
        return new BasicStroke((float) w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    public static Icon lock(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.12));
            g.draw(new Arc2D.Double(s * 0.28, s * 0.10, s * 0.44, s * 0.56, 0, 180, Arc2D.OPEN));
            g.fill(new RoundRectangle2D.Double(s * 0.18, s * 0.42, s * 0.64, s * 0.48, s * 0.14, s * 0.14));
            g.setColor(new Color(255, 255, 255, 220));
            g.fill(new Ellipse2D.Double(s * 0.44, s * 0.54, s * 0.12, s * 0.12));
            g.setStroke(round(s * 0.07));
            g.draw(new Line2D.Double(s * 0.5, s * 0.62, s * 0.5, s * 0.76));
        });
    }

    public static Icon plus(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.18));
            g.draw(new Line2D.Double(s * 0.2, s * 0.5, s * 0.8, s * 0.5));
            g.draw(new Line2D.Double(s * 0.5, s * 0.2, s * 0.5, s * 0.8));
        });
    }

    public static Icon trash(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.10));
            g.draw(new Line2D.Double(s * 0.14, s * 0.26, s * 0.86, s * 0.26));
            g.draw(new Line2D.Double(s * 0.38, s * 0.14, s * 0.62, s * 0.14));
            Path2D body = new Path2D.Double();
            body.moveTo(s * 0.22, s * 0.36);
            body.lineTo(s * 0.78, s * 0.36);
            body.lineTo(s * 0.70, s * 0.88);
            body.lineTo(s * 0.30, s * 0.88);
            body.closePath();
            g.fill(body);
            g.setColor(new Color(255, 255, 255, 190));
            g.setStroke(round(s * 0.06));
            g.draw(new Line2D.Double(s * 0.42, s * 0.46, s * 0.43, s * 0.78));
            g.draw(new Line2D.Double(s * 0.58, s * 0.46, s * 0.57, s * 0.78));
        });
    }

    public static Icon pin(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.10));
            g.draw(new Line2D.Double(s * 0.5, s * 0.55, s * 0.5, s * 0.92));
            g.fill(new Ellipse2D.Double(s * 0.18, s * 0.06, s * 0.64, s * 0.64));
            g.setColor(new Color(255, 255, 255, 170));
            g.fill(new Ellipse2D.Double(s * 0.32, s * 0.2, s * 0.18, s * 0.18));
        });
    }

    public static Icon palette(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.fill(new Ellipse2D.Double(s * 0.06, s * 0.12, s * 0.88, s * 0.76));
            Color[] dots = {new Color(255, 214, 90), new Color(255, 255, 255), new Color(120, 220, 160), new Color(255, 120, 150)};
            double[][] spots = {{0.28, 0.42}, {0.46, 0.26}, {0.68, 0.30}, {0.74, 0.52}};
            for (int i = 0; i < dots.length; i++) {
                g.setColor(dots[i]);
                g.fill(new Ellipse2D.Double(s * spots[i][0] - s * 0.07, s * spots[i][1] - s * 0.07, s * 0.14, s * 0.14));
            }
        });
    }

    public static Icon mail(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.fill(new RoundRectangle2D.Double(s * 0.08, s * 0.2, s * 0.84, s * 0.6, s * 0.12, s * 0.12));
            g.setColor(new Color(255, 255, 255, 230));
            g.setStroke(round(s * 0.07));
            Path2D flap = new Path2D.Double();
            flap.moveTo(s * 0.14, s * 0.28);
            flap.lineTo(s * 0.5, s * 0.55);
            flap.lineTo(s * 0.86, s * 0.28);
            g.draw(flap);
        });
    }

    public static Icon star(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> g.fill(starShape(s * 0.5, s * 0.54, s * 0.48, s * 0.2, 5)));
    }

    public static Icon heart(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> g.fill(heartShape(s * 0.5, s * 0.52, s * 0.9)));
    }

    public static Icon sparkle(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> g.fill(sparkleShape(s * 0.5, s * 0.5, s * 0.48)));
    }

    public static Icon check(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.16));
            Path2D p = new Path2D.Double();
            p.moveTo(s * 0.2, s * 0.54);
            p.lineTo(s * 0.42, s * 0.74);
            p.lineTo(s * 0.8, s * 0.28);
            g.draw(p);
        });
    }

    // ---------- icons for the ribbon ----------

    private static Path2D line2(double... xy) {
        Path2D p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i + 1 < xy.length; i += 2) {
            p.lineTo(xy[i], xy[i + 1]);
        }
        return p;
    }

    private static Path2D scaled(double s, double... xy) {
        double[] v = new double[xy.length];
        for (int i = 0; i < xy.length; i++) {
            v[i] = xy[i] * s;
        }
        return line2(v);
    }

    public static Icon undo(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.12));
            Path2D tail = new Path2D.Double();
            tail.moveTo(s * 0.36, s * 0.4);
            tail.curveTo(s * 0.95, s * 0.28, s * 0.95, s * 0.86, s * 0.5, s * 0.86);
            g.draw(tail);
            Path2D head = scaled(s, 0.1, 0.4, 0.4, 0.14, 0.4, 0.66);
            head.closePath();
            g.fill(head);
        });
    }

    public static Icon redo(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.12));
            Path2D tail = new Path2D.Double();
            tail.moveTo(s * 0.64, s * 0.4);
            tail.curveTo(s * 0.05, s * 0.28, s * 0.05, s * 0.86, s * 0.5, s * 0.86);
            g.draw(tail);
            Path2D head = scaled(s, 0.9, 0.4, 0.6, 0.14, 0.6, 0.66);
            head.closePath();
            g.fill(head);
        });
    }

    public static Icon bullets(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            for (int i = 0; i < 3; i++) {
                double y = s * (0.24 + i * 0.26);
                g.fill(new Ellipse2D.Double(s * 0.13, y - s * 0.06, s * 0.12, s * 0.12));
                g.setStroke(round(s * 0.1));
                g.draw(new Line2D.Double(s * 0.4, y, s * 0.88, y));
            }
        });
    }

    public static Icon checkbox(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.1));
            g.draw(new RoundRectangle2D.Double(s * 0.12, s * 0.12, s * 0.76, s * 0.76, s * 0.2, s * 0.2));
            g.setStroke(round(s * 0.13));
            g.draw(scaled(s, 0.3, 0.52, 0.45, 0.67, 0.72, 0.34));
        });
    }

    private static Icon alignment(int size, Color color, int mode) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            double[] widths = {0.72, 0.46, 0.72, 0.46};
            g.setStroke(round(s * 0.1));
            for (int i = 0; i < 4; i++) {
                double y = s * (0.2 + i * 0.2);
                double w = s * widths[i];
                double x = mode == 0 ? s * 0.14 : (mode == 1 ? (s - w) / 2 : s * 0.86 - w);
                g.draw(new Line2D.Double(x, y, x + w, y));
            }
        });
    }

    public static Icon alignLeft(int size, Color color) {
        return alignment(size, color, 0);
    }

    public static Icon alignCenter(int size, Color color) {
        return alignment(size, color, 1);
    }

    public static Icon alignRight(int size, Color color) {
        return alignment(size, color, 2);
    }

    public static Icon picture(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.09));
            g.draw(new RoundRectangle2D.Double(s * 0.1, s * 0.16, s * 0.8, s * 0.68, s * 0.14, s * 0.14));
            g.fill(new Ellipse2D.Double(s * 0.58, s * 0.28, s * 0.14, s * 0.14));
            Path2D hills = scaled(s, 0.14, 0.78, 0.4, 0.46, 0.56, 0.64, 0.68, 0.54, 0.86, 0.78);
            hills.closePath();
            g.fill(hills);
        });
    }

    public static Icon pastePicture(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.09));
            g.draw(new RoundRectangle2D.Double(s * 0.16, s * 0.16, s * 0.68, s * 0.76, s * 0.12, s * 0.12));
            g.fill(new RoundRectangle2D.Double(s * 0.34, s * 0.06, s * 0.32, s * 0.16, s * 0.08, s * 0.08));
            Path2D hills = scaled(s, 0.26, 0.82, 0.44, 0.52, 0.56, 0.68, 0.64, 0.6, 0.74, 0.82);
            hills.closePath();
            g.fill(hills);
        });
    }

    public static Icon table(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.09));
            g.draw(new RoundRectangle2D.Double(s * 0.1, s * 0.16, s * 0.8, s * 0.68, s * 0.1, s * 0.1));
            g.setStroke(round(s * 0.07));
            g.draw(new Line2D.Double(s * 0.1, s * 0.5, s * 0.9, s * 0.5));
            g.draw(new Line2D.Double(s * 0.37, s * 0.16, s * 0.37, s * 0.84));
            g.draw(new Line2D.Double(s * 0.63, s * 0.16, s * 0.63, s * 0.84));
        });
    }

    public static Icon rule(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.12));
            g.draw(new Line2D.Double(s * 0.12, s * 0.5, s * 0.88, s * 0.5));
            g.fill(new Ellipse2D.Double(s * 0.06, s * 0.2, s * 0.1, s * 0.1));
            g.fill(new Ellipse2D.Double(s * 0.84, s * 0.7, s * 0.1, s * 0.1));
        });
    }

    public static Icon calendar(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.09));
            g.draw(new RoundRectangle2D.Double(s * 0.12, s * 0.2, s * 0.76, s * 0.68, s * 0.14, s * 0.14));
            g.fill(new RoundRectangle2D.Double(s * 0.12, s * 0.2, s * 0.76, s * 0.2, s * 0.1, s * 0.1));
            g.draw(new Line2D.Double(s * 0.32, s * 0.1, s * 0.32, s * 0.28));
            g.draw(new Line2D.Double(s * 0.68, s * 0.1, s * 0.68, s * 0.28));
            for (int r = 0; r < 2; r++) {
                for (int k = 0; k < 3; k++) {
                    g.fill(new Ellipse2D.Double(s * (0.26 + k * 0.2), s * (0.52 + r * 0.19), s * 0.1, s * 0.1));
                }
            }
        });
    }

    public static Icon symbol(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, (int) (s * 0.95)));
            java.awt.FontMetrics fm = g.getFontMetrics();
            String text = "\u03A9";
            g.drawString(text, (float) ((s - fm.stringWidth(text)) / 2.0), (float) ((s - fm.getHeight()) / 2.0 + fm.getAscent()));
        });
    }

    public static Icon subNote(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.fill(new RoundRectangle2D.Double(s * 0.1, s * 0.1, s * 0.36, s * 0.26, s * 0.08, s * 0.08));
            g.setStroke(round(s * 0.09));
            g.draw(scaled(s, 0.28, 0.36, 0.28, 0.74, 0.5, 0.74));
            g.draw(new RoundRectangle2D.Double(s * 0.5, s * 0.6, s * 0.4, s * 0.28, s * 0.08, s * 0.08));
        });
    }

    public static Icon pen(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.rotate(Math.toRadians(40), s * 0.5, s * 0.5);
            g.fill(new RoundRectangle2D.Double(s * 0.4, s * 0.04, s * 0.2, s * 0.56, s * 0.08, s * 0.08));
            g.fill(scaledClosed(s, 0.4, 0.66, 0.6, 0.66, 0.5, 0.94));
        });
    }

    private static Path2D scaledClosed(double s, double... xy) {
        Path2D p = scaled(s, xy);
        p.closePath();
        return p;
    }

    public static Icon highlighter(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.rotate(Math.toRadians(40), s * 0.5, s * 0.46);
            g.fill(new RoundRectangle2D.Double(s * 0.36, s * 0.02, s * 0.28, s * 0.44, s * 0.08, s * 0.08));
            g.fill(scaledClosed(s, 0.4, 0.5, 0.6, 0.5, 0.56, 0.7, 0.44, 0.7));
            g.setColor(new Color(255, 214, 0));
            g.fill(new RoundRectangle2D.Double(s * 0.28, s * 0.86, s * 0.44, s * 0.1, s * 0.06, s * 0.06));
        });
    }

    public static Icon eraser(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.rotate(Math.toRadians(-40), s * 0.5, s * 0.5);
            g.setStroke(round(s * 0.09));
            g.draw(new RoundRectangle2D.Double(s * 0.14, s * 0.3, s * 0.72, s * 0.4, s * 0.12, s * 0.12));
            g.fill(new RoundRectangle2D.Double(s * 0.14, s * 0.3, s * 0.34, s * 0.4, s * 0.12, s * 0.12));
        });
    }

    public static Icon ibeam(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.11));
            g.draw(new Line2D.Double(s * 0.5, s * 0.16, s * 0.5, s * 0.84));
            g.draw(new Line2D.Double(s * 0.3, s * 0.16, s * 0.7, s * 0.16));
            g.draw(new Line2D.Double(s * 0.3, s * 0.84, s * 0.7, s * 0.84));
        });
    }

    public static Icon clock(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.1));
            g.draw(new Ellipse2D.Double(s * 0.1, s * 0.1, s * 0.8, s * 0.8));
            g.draw(scaled(s, 0.5, 0.26, 0.5, 0.5, 0.68, 0.6));
        });
    }

    public static Icon search(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.11));
            g.draw(new Ellipse2D.Double(s * 0.12, s * 0.12, s * 0.52, s * 0.52));
            g.draw(new Line2D.Double(s * 0.56, s * 0.56, s * 0.88, s * 0.88));
        });
    }

    public static Icon chevronUp(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.13));
            g.draw(scaled(s, 0.2, 0.64, 0.5, 0.34, 0.8, 0.64));
        });
    }

    public static Icon chevronDown(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.13));
            g.draw(scaled(s, 0.2, 0.36, 0.5, 0.66, 0.8, 0.36));
        });
    }

    public static Icon share(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.1));
            g.draw(new Line2D.Double(s * 0.5, s * 0.62, s * 0.5, s * 0.12));
            g.draw(scaled(s, 0.3, 0.3, 0.5, 0.1, 0.7, 0.3));
            g.draw(scaled(s, 0.2, 0.46, 0.2, 0.88, 0.8, 0.88, 0.8, 0.46));
        });
    }

    public static Icon printer(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.fill(new RoundRectangle2D.Double(s * 0.08, s * 0.38, s * 0.84, s * 0.34, s * 0.12, s * 0.12));
            g.setStroke(round(s * 0.08));
            g.draw(new Rectangle2DDouble(s * 0.28, s * 0.1, s * 0.44, s * 0.28));
            g.setColor(Color.WHITE);
            g.fill(new Rectangle2DDouble(s * 0.28, s * 0.58, s * 0.44, s * 0.32));
            g.setColor(color);
            g.draw(new Rectangle2DDouble(s * 0.28, s * 0.58, s * 0.44, s * 0.32));
        });
    }

    public static Icon copy(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.09));
            g.draw(new RoundRectangle2D.Double(s * 0.1, s * 0.1, s * 0.52, s * 0.62, s * 0.1, s * 0.1));
            g.fill(new RoundRectangle2D.Double(s * 0.34, s * 0.3, s * 0.54, s * 0.62, s * 0.1, s * 0.1));
        });
    }

    public static Icon file(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.09));
            Path2D page = scaled(s, 0.2, 0.1, 0.56, 0.1, 0.8, 0.34, 0.8, 0.9, 0.2, 0.9);
            page.closePath();
            g.draw(page);
            g.draw(scaled(s, 0.56, 0.1, 0.56, 0.34, 0.8, 0.34));
            g.draw(new Line2D.Double(s * 0.34, s * 0.56, s * 0.66, s * 0.56));
            g.draw(new Line2D.Double(s * 0.34, s * 0.72, s * 0.66, s * 0.72));
        });
    }

    public static Icon html(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.11));
            g.draw(scaled(s, 0.32, 0.26, 0.1, 0.5, 0.32, 0.74));
            g.draw(scaled(s, 0.68, 0.26, 0.9, 0.5, 0.68, 0.74));
            g.draw(new Line2D.Double(s * 0.56, s * 0.2, s * 0.44, s * 0.8));
        });
    }

    public static Icon save(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.fill(new RoundRectangle2D.Double(s * 0.1, s * 0.1, s * 0.8, s * 0.8, s * 0.12, s * 0.12));
            g.setColor(Color.WHITE);
            g.fill(new Rectangle2DDouble(s * 0.26, s * 0.1, s * 0.44, s * 0.3));
            g.fill(new RoundRectangle2D.Double(s * 0.26, s * 0.56, s * 0.48, s * 0.34, s * 0.06, s * 0.06));
            g.setColor(color);
            g.fill(new Rectangle2DDouble(s * 0.56, s * 0.15, s * 0.08, s * 0.2));
        });
    }

    public static Icon fullscreen(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.1));
            g.draw(scaled(s, 0.1, 0.36, 0.1, 0.1, 0.36, 0.1));
            g.draw(scaled(s, 0.64, 0.1, 0.9, 0.1, 0.9, 0.36));
            g.draw(scaled(s, 0.9, 0.64, 0.9, 0.9, 0.64, 0.9));
            g.draw(scaled(s, 0.36, 0.9, 0.1, 0.9, 0.1, 0.64));
        });
    }

    public static Icon sidebar(int size, Color color) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * 0.09));
            g.draw(new RoundRectangle2D.Double(s * 0.1, s * 0.16, s * 0.8, s * 0.68, s * 0.12, s * 0.12));
            g.fill(new Rectangle2DDouble(s * 0.18, s * 0.24, s * 0.22, s * 0.52));
        });
    }

    public static Icon thickness(int size, Color color, double weight) {
        return new DrawnIcon(size, color, (g, s, c) -> {
            g.setStroke(round(s * weight));
            g.draw(new Line2D.Double(s * 0.14, s * 0.5, s * 0.86, s * 0.5));
        });
    }

    // a rectangle that works like the other shapes here
    private static class Rectangle2DDouble extends java.awt.geom.Rectangle2D.Double {
        Rectangle2DDouble(double x, double y, double w, double h) {
            super(x, y, w, h);
        }
    }

    // ---------- shapes that other classes use too ----------

    // A star with a number of points (5 for a normal star)
    public static Path2D starShape(double cx, double cy, double outer, double inner, int points) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < points * 2; i++) {
            double radius = (i % 2 == 0) ? outer : inner;
            double angle = -Math.PI / 2 + i * Math.PI / points;
            double x = cx + Math.cos(angle) * radius;
            double y = cy + Math.sin(angle) * radius;
            if (i == 0) {
                p.moveTo(x, y);
            } else {
                p.lineTo(x, y);
            }
        }
        p.closePath();
        return p;
    }

    // A little 4-pointed twinkle
    public static Path2D sparkleShape(double cx, double cy, double r) {
        Path2D p = new Path2D.Double();
        double in = r * 0.28;
        p.moveTo(cx, cy - r);
        p.quadTo(cx + in * 0.4, cy - in * 0.4, cx + r, cy);
        p.quadTo(cx + in * 0.4, cy + in * 0.4, cx, cy + r);
        p.quadTo(cx - in * 0.4, cy + in * 0.4, cx - r, cy);
        p.quadTo(cx - in * 0.4, cy - in * 0.4, cx, cy - r);
        p.closePath();
        return p;
    }

    public static Path2D heartShape(double cx, double cy, double size) {
        double w = size / 2;
        Path2D p = new Path2D.Double();
        p.moveTo(cx, cy + w * 0.9);
        p.curveTo(cx - w * 1.5, cy - w * 0.1, cx - w * 0.75, cy - w * 1.05, cx, cy - w * 0.35);
        p.curveTo(cx + w * 0.75, cy - w * 1.05, cx + w * 1.5, cy - w * 0.1, cx, cy + w * 0.9);
        p.closePath();
        return p;
    }
}