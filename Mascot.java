import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

// The cartoon characters. They are drawn with code, so they use the colours of your theme.
// All three characters are original drawings made for this app.
public class Mascot {

    private static final Color OUTLINE = new Color(60, 36, 70);

    // Draws a character inside a square. (cx, cy) is the middle, size is the width of the square.
    public static void draw(Graphics2D g0, String kind, double cx, double cy, double size, long t, boolean blink) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.translate(cx - size / 2, cy - size / 2);
        g.scale(size / 200.0, size / 200.0);
        Theme th = Theme.current();
        if (kind.equals("Droney")) {
            droney(g, th, t, blink);
        } else if (kind.equals("Mochi")) {
            mochi(g, th, t, blink);
        } else if (kind.equals("Picture") && Theme.picture() != null) {
            picture(g, th, t);
        } else {
            mika(g, th, t, blink);
        }
        g.dispose();
    }

    private static Ellipse2D oval(double cx, double cy, double rx, double ry) {
        return new Ellipse2D.Double(cx - rx, cy - ry, rx * 2, ry * 2);
    }

    private static BasicStroke line(double w) {
        return new BasicStroke((float) w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    // ---------- Mika: a cheerful anime-style girl ----------

    private static void mika(Graphics2D g, Theme th, long t, boolean blink) {
        Color hair = Theme.darker(th.primary, 0.58);
        Color hairLight = Theme.mix(hair, Color.WHITE, 0.16);
        Color skin = new Color(255, 226, 208);
        Color skinShade = new Color(244, 196, 176);
        double sway = Math.sin(t / 520.0) * 4;

        // back hair
        g.setPaint(new GradientPaint(0, 14, hairLight, 0, 170, hair));
        g.fill(oval(100, 92, 74, 80));

        // twin tails
        for (int side = -1; side <= 1; side += 2) {
            AffineTransform keep = g.getTransform();
            double px = 100 + side * 66;
            g.rotate(Math.toRadians(side * (14 + sway)), px, 70);
            g.setPaint(new GradientPaint((float) px, 70, hairLight, (float) px, 180, hair));
            g.fill(oval(px + side * 6, 118, 15, 52));
            g.setTransform(keep);
        }

        // ribbons on the twin tails
        for (int side = -1; side <= 1; side += 2) {
            double rx = 100 + side * 68;
            g.setColor(th.accent);
            g.fill(oval(rx - side * 7, 76, 9, 6));
            g.fill(oval(rx + side * 7, 76, 9, 6));
            g.setColor(Theme.darker(th.accent, 0.15));
            g.fill(oval(rx, 76, 4.5, 4.5));
        }

        // shoulders and dress
        g.setPaint(new GradientPaint(0, 150, th.primary, 0, 210, Theme.darker(th.primary, 0.25)));
        g.fill(new RoundRectangle2D.Double(50, 154, 100, 44, 40, 40));
        // collar and bow
        Path2D collar = new Path2D.Double();
        collar.moveTo(78, 154);
        collar.lineTo(100, 178);
        collar.lineTo(122, 154);
        collar.lineTo(122, 164);
        collar.lineTo(100, 190);
        collar.lineTo(78, 164);
        collar.closePath();
        g.setColor(Color.WHITE);
        g.fill(collar);
        g.setColor(th.accent);
        g.fill(oval(93, 176, 8, 6));
        g.fill(oval(107, 176, 8, 6));
        g.setColor(Theme.darker(th.accent, 0.15));
        g.fill(oval(100, 177, 4.5, 4.5));

        // neck
        g.setColor(skinShade);
        g.fill(new RoundRectangle2D.Double(89, 142, 22, 20, 8, 8));

        // ears
        g.setColor(skin);
        g.fill(oval(45, 104, 8, 11));
        g.fill(oval(155, 104, 8, 11));

        // face
        g.setPaint(new GradientPaint(0, 50, new Color(255, 236, 222), 0, 160, skin));
        g.fill(oval(100, 104, 55, 55));

        // cheeks
        g.setColor(new Color(255, 120, 150, 110));
        g.fill(oval(66, 124, 13, 7));
        g.fill(oval(134, 124, 13, 7));

        // eyes
        animeEye(g, 76, 104, blink, th.primary);
        animeEye(g, 124, 104, blink, th.primary);

        // eyebrows
        g.setStroke(line(2.6));
        g.setColor(hair);
        g.draw(new Arc2D.Double(63, 76, 24, 14, 40, 100, Arc2D.OPEN));
        g.draw(new Arc2D.Double(113, 76, 24, 14, 40, 100, Arc2D.OPEN));

        // nose and mouth
        g.setStroke(line(1.8));
        g.setColor(new Color(214, 150, 130));
        g.draw(new Line2D.Double(100, 120, 99, 122.5));
        g.setStroke(line(2.8));
        g.setColor(new Color(204, 80, 100));
        g.draw(new Arc2D.Double(90, 124, 20, 13, 205, 130, Arc2D.OPEN));

        // front hair (the fringe)
        Path2D fringe = new Path2D.Double();
        fringe.moveTo(38, 112);
        fringe.curveTo(14, -4, 186, -4, 162, 112);
        fringe.lineTo(150, 82);
        fringe.quadTo(142, 74, 136, 88);
        fringe.quadTo(126, 66, 116, 84);
        fringe.quadTo(108, 62, 100, 82);
        fringe.quadTo(90, 62, 82, 84);
        fringe.quadTo(72, 66, 64, 88);
        fringe.quadTo(56, 72, 50, 82);
        fringe.closePath();
        g.setPaint(new GradientPaint(0, 30, hairLight, 0, 100, hair));
        g.fill(fringe);
        // shine on the hair
        g.setColor(new Color(255, 255, 255, 70));
        g.setStroke(line(5));
        g.draw(new Arc2D.Double(52, 24, 96, 56, 120, 60, Arc2D.OPEN));

        // side hair
        for (int side = -1; side <= 1; side += 2) {
            Path2D lock = new Path2D.Double();
            double x = 100 + side * 56;
            lock.moveTo(x, 80);
            lock.curveTo(x + side * 6, 104, x + side * 4, 130, x + side * -2, 150);
            lock.curveTo(x - side * 8, 130, x - side * 10, 104, x - side * 6, 80);
            lock.closePath();
            g.setPaint(new GradientPaint(0, 80, hairLight, 0, 150, hair));
            g.fill(lock);
        }

        // star hair clip
        g.setColor(new Color(255, 210, 80));
        g.fill(Icons.starShape(142, 58, 12, 5.2, 5));
        g.setColor(new Color(235, 150, 40));
        g.setStroke(line(1.4));
        g.draw(Icons.starShape(142, 58, 12, 5.2, 5));
        g.setColor(new Color(255, 255, 255, 220));
        g.fill(oval(139, 55, 2.2, 2.2));

        sparkles(g, th, t);
    }

    private static void animeEye(Graphics2D g, double cx, double cy, boolean blink, Color irisColor) {
        Color ink = new Color(52, 30, 66);
        if (blink) {
            g.setStroke(line(3.4));
            g.setColor(ink);
            g.draw(new Arc2D.Double(cx - 13, cy - 8, 26, 16, 200, 140, Arc2D.OPEN));
            return;
        }
        g.setColor(Color.WHITE);
        g.fill(oval(cx, cy, 15, 19));
        g.setPaint(new GradientPaint(0, (float) (cy - 17), Theme.darker(irisColor, 0.35),
                0, (float) (cy + 17), Theme.lighter(irisColor, 0.35)));
        g.fill(oval(cx, cy + 1, 12.5, 16.5));
        g.setColor(ink);
        g.fill(oval(cx, cy + 2, 6.5, 9.5));
        g.setColor(Color.WHITE);
        g.fill(oval(cx - 4.5, cy - 6, 4.6, 5.6));
        g.fill(oval(cx + 4.5, cy + 8, 2.2, 2.2));
        g.setColor(ink);
        g.setStroke(line(3.6));
        g.draw(new Arc2D.Double(cx - 16, cy - 22, 32, 32, 25, 130, Arc2D.OPEN));
    }

    // ---------- Droney: a friendly little drone-robot ----------

    private static void droney(Graphics2D g, Theme th, long t, boolean blink) {
        double hover = Math.sin(t / 380.0) * 2;
        Color shell = new Color(250, 248, 255);
        Color shellShade = Theme.mix(th.primary, Color.WHITE, 0.62);

        // shadow on the ground
        g.setColor(new Color(0, 0, 0, 34));
        g.fill(oval(100, 186, 46 - hover * 2, 7));

        g.translate(0, hover);

        // arms and spinning rotors
        for (int side = -1; side <= 1; side += 2) {
            g.setColor(Theme.darker(th.primary, 0.1));
            g.setStroke(line(9));
            g.draw(new Line2D.Double(100 + side * 36, 112, 100 + side * 66, 78));
            g.setColor(OUTLINE);
            g.fill(new RoundRectangle2D.Double(100 + side * 66 - 3, 56, 6, 24, 4, 4));
            double blur = 36 + Math.sin(t / 28.0 + side) * 6;
            g.setColor(Theme.alpha(th.accent, 120));
            g.fill(oval(100 + side * 66, 55, blur, 5.5));
            g.setColor(Theme.alpha(th.primary, 150));
            g.fill(oval(100 + side * 66, 55, blur * 0.6, 3));
        }

        // legs
        g.setColor(Theme.darker(th.primary, 0.1));
        g.setStroke(line(6));
        g.draw(new Line2D.Double(78, 150, 70, 168));
        g.draw(new Line2D.Double(122, 150, 130, 168));
        g.draw(new Line2D.Double(64, 168, 78, 168));
        g.draw(new Line2D.Double(122, 168, 136, 168));

        // body
        g.setPaint(new GradientPaint(0, 70, shell, 0, 156, shellShade));
        g.fill(new RoundRectangle2D.Double(46, 70, 108, 84, 44, 44));
        g.setColor(Theme.alpha(th.primary, 120));
        g.setStroke(line(2.5));
        g.draw(new RoundRectangle2D.Double(46, 70, 108, 84, 44, 44));

        // face screen
        g.setPaint(new GradientPaint(0, 80, new Color(40, 42, 78), 0, 132, new Color(22, 22, 48)));
        g.fill(new RoundRectangle2D.Double(58, 82, 84, 52, 28, 28));
        g.setColor(new Color(255, 255, 255, 40));
        g.fill(new Arc2D.Double(62, 84, 76, 40, 20, 140, Arc2D.CHORD));

        // eyes
        Color glow = Theme.lighter(th.accent, 0.1);
        for (int side = -1; side <= 1; side += 2) {
            double ex = 100 + side * 20;
            if (blink) {
                g.setStroke(line(3.4));
                g.setColor(glow);
                g.draw(new Arc2D.Double(ex - 8, 98, 16, 10, 200, 140, Arc2D.OPEN));
            } else {
                g.setColor(Theme.alpha(glow, 70));
                g.fill(oval(ex, 104, 13, 15));
                g.setPaint(new GradientPaint(0, 92, Theme.lighter(glow, 0.5), 0, 116, glow));
                g.fill(oval(ex, 104, 9, 12));
                g.setColor(Color.WHITE);
                g.fill(oval(ex - 3, 99, 3.2, 4));
            }
        }
        // smile and cheeks
        g.setColor(new Color(255, 120, 160, 150));
        g.fill(oval(75, 120, 6, 3.5));
        g.fill(oval(125, 120, 6, 3.5));
        g.setColor(glow);
        g.setStroke(line(2.6));
        g.draw(new Arc2D.Double(92, 114, 16, 11, 200, 140, Arc2D.OPEN));

        // antenna with a glowing bulb
        g.setColor(OUTLINE);
        g.setStroke(line(3));
        g.draw(new Line2D.Double(100, 70, 100, 50));
        double pulse = 0.5 + 0.5 * Math.sin(t / 300.0);
        g.setColor(Theme.alpha(th.accent, (int) (60 + 80 * pulse)));
        g.fill(oval(100, 44, 12 + 3 * pulse, 12 + 3 * pulse));
        g.setColor(th.primary);
        g.fill(oval(100, 44, 7, 7));
        g.setColor(new Color(255, 255, 255, 200));
        g.fill(oval(98, 42, 2, 2));

        // lock badge on the belly
        g.setColor(th.primary);
        g.fill(oval(100, 144, 8, 8));
        Icons.lock(10, Color.WHITE).paintIcon(null, g, 95, 139);

        g.translate(0, -hover);
        sparkles(g, th, t);
    }

    // ---------- Mochi: a soft round cat ----------

    private static void mochi(Graphics2D g, Theme th, long t, boolean blink) {
        Color fur = new Color(255, 250, 246);
        Color furShade = new Color(240, 226, 226);
        double squish = Math.sin(t / 450.0) * 2;

        g.setColor(new Color(0, 0, 0, 30));
        g.fill(oval(100, 184, 54, 7));

        // tail
        g.setColor(furShade);
        g.setStroke(line(14));
        Path2D tail = new Path2D.Double();
        tail.moveTo(150, 150);
        tail.curveTo(184, 150, 190, 108 + squish * 2, 170, 98);
        g.draw(tail);

        // ears
        for (int side = -1; side <= 1; side += 2) {
            Path2D ear = new Path2D.Double();
            double ex = 100 + side * 46;
            ear.moveTo(ex - side * 20, 62);
            ear.lineTo(ex + side * 4, 22);
            ear.lineTo(ex + side * 26, 70);
            ear.closePath();
            g.setPaint(new GradientPaint(0, 22, fur, 0, 70, furShade));
            g.fill(ear);
            Path2D inner = new Path2D.Double();
            inner.moveTo(ex - side * 8, 62);
            inner.lineTo(ex + side * 4, 36);
            inner.lineTo(ex + side * 16, 66);
            inner.closePath();
            g.setColor(new Color(255, 170, 190));
            g.fill(inner);
        }

        // body and head (one soft blob)
        g.setPaint(new GradientPaint(0, 50, fur, 0, 180, furShade));
        g.fill(oval(100, 112 + squish, 70 + squish, 62 - squish));

        // belly
        g.setColor(new Color(255, 255, 255, 190));
        g.fill(oval(100, 148, 34, 22));

        // little paws
        g.setColor(fur);
        g.fill(oval(76, 170, 14, 9));
        g.fill(oval(124, 170, 14, 9));
        g.setColor(new Color(255, 170, 190));
        g.fill(oval(76, 172, 4, 3));
        g.fill(oval(124, 172, 4, 3));

        // forehead stripes in theme colour
        g.setColor(Theme.alpha(th.primary, 190));
        g.setStroke(line(4));
        g.draw(new Line2D.Double(100, 56, 100, 68));
        g.draw(new Line2D.Double(88, 58, 90, 68));
        g.draw(new Line2D.Double(112, 58, 110, 68));

        // eyes
        for (int side = -1; side <= 1; side += 2) {
            double ex = 100 + side * 24;
            if (blink) {
                g.setColor(OUTLINE);
                g.setStroke(line(3.4));
                g.draw(new Arc2D.Double(ex - 8, 92, 16, 12, 200, 140, Arc2D.OPEN));
            } else {
                g.setColor(OUTLINE);
                g.fill(oval(ex, 98, 8.5, 11));
                g.setColor(Color.WHITE);
                g.fill(oval(ex - 2.8, 93.5, 3.4, 4));
                g.fill(oval(ex + 2.6, 102.5, 1.6, 1.6));
            }
        }
        // cheeks, nose, mouth, whiskers
        g.setColor(new Color(255, 150, 175, 130));
        g.fill(oval(66, 112, 11, 6.5));
        g.fill(oval(134, 112, 11, 6.5));
        g.setColor(new Color(255, 140, 165));
        Path2D nose = new Path2D.Double();
        nose.moveTo(95.5, 108);
        nose.lineTo(104.5, 108);
        nose.lineTo(100, 113.5);
        nose.closePath();
        g.fill(nose);
        g.setColor(OUTLINE);
        g.setStroke(line(2.2));
        g.draw(new Arc2D.Double(91, 112, 9, 8, 200, 150, Arc2D.OPEN));
        g.draw(new Arc2D.Double(100, 112, 9, 8, 190, 150, Arc2D.OPEN));
        g.setColor(new Color(150, 120, 140));
        g.setStroke(line(1.6));
        for (int side = -1; side <= 1; side += 2) {
            g.draw(new Line2D.Double(100 + side * 36, 108, 100 + side * 54, 103));
            g.draw(new Line2D.Double(100 + side * 36, 113, 100 + side * 55, 114));
        }

        // collar with a tiny gold lock
        g.setColor(th.primary);
        g.setStroke(line(6));
        g.draw(new Arc2D.Double(66, 120, 68, 36, 200, 140, Arc2D.OPEN));
        g.setColor(new Color(255, 210, 80));
        g.fill(oval(100, 150, 6.5, 6.5));
        Icons.lock(9, new Color(160, 100, 20)).paintIcon(null, g, 95, 145);

        sparkles(g, th, t);
    }

    // ---------- your own picture, shown as a round sticker ----------

    // The picture is cut into a smooth circle once, then reused
    private static BufferedImage roundPicture;
    private static BufferedImage roundSource;

    private static BufferedImage makeRound(BufferedImage img) {
        if (roundSource != img || roundPicture == null) {
            int px = 320;
            BufferedImage round = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
            Graphics2D rg = round.createGraphics();
            rg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            rg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            rg.fillOval(0, 0, px, px);
            rg.setComposite(java.awt.AlphaComposite.SrcIn);
            int side = Math.min(img.getWidth(), img.getHeight());
            int sx = (img.getWidth() - side) / 2;
            int sy = (img.getHeight() - side) / 2;
            rg.drawImage(img, 0, 0, px, px, sx, sy, sx + side, sy + side, null);
            rg.dispose();
            roundPicture = round;
            roundSource = img;
        }
        return roundPicture;
    }

    private static void picture(Graphics2D g, Theme th, long t) {
        BufferedImage round = makeRound(Theme.picture());
        double d = 150;
        double x = 100 - d / 2, y = 100 - d / 2;
        g.setPaint(new GradientPaint(0, 0, th.accent, 200, 200, th.primary));
        g.fill(oval(100, 100, d / 2 + 8, d / 2 + 8));
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(round, (int) x, (int) y, (int) d, (int) d, null);
        sparkles(g, th, t);
    }

    // ---------- twinkling stars around a character ----------

    private static void sparkles(Graphics2D g, Theme th, long t) {
        double[][] spots = {{22, 40, 9}, {178, 34, 7}, {188, 118, 6}, {14, 130, 6}};
        for (int i = 0; i < spots.length; i++) {
            double phase = 0.5 + 0.5 * Math.sin(t / 420.0 + i * 1.7);
            Color c = (i % 2 == 0) ? th.accent : th.primary;
            g.setColor(Theme.alpha(c, (int) (90 + 150 * phase)));
            g.fill(Icons.sparkleShape(spots[i][0], spots[i][1], spots[i][2] * (0.7 + 0.5 * phase)));
        }
    }

    // ---------- a screen part that shows (and animates) a character ----------

    public static class MascotPanel extends JComponent {
        private final boolean animated;
        private final String fixedKind;       // null means "use the one chosen in Theme"
        private Timer timer;

        public MascotPanel(int size, boolean animated, String fixedKind) {
            this.animated = animated;
            this.fixedKind = fixedKind;
            setPreferredSize(new Dimension(size, size));
            setOpaque(false);
        }

        @Override
        public void addNotify() {
            super.addNotify();
            if (animated && timer == null) {
                timer = new Timer(45, e -> {
                    if (isShowing()) {
                        repaint();
                    }
                });
                timer.start();
            }
        }

        @Override
        public void removeNotify() {
            if (timer != null) {
                timer.stop();
                timer = null;
            }
            super.removeNotify();
        }

        @Override
        protected void paintComponent(Graphics g) {
            long t = animated ? System.currentTimeMillis() : 0;
            boolean blink = animated && (t % 3600) < 170;
            double bob = animated ? Math.sin(t / 650.0) * getHeight() * 0.015 : 0;
            double size = Math.min(getWidth(), getHeight());
            String kind = fixedKind != null ? fixedKind : Theme.mascot();
            draw((Graphics2D) g, kind, getWidth() / 2.0, getHeight() / 2.0 + bob, size, t, blink);
        }
    }
}
