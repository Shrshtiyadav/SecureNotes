import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.font.TextAttribute;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.Map;

// Colourful building blocks: background, cards and buttons
public class Kit {

    private static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    // ---------- the colourful background ----------

    public static class GradientPanel extends JPanel {
        public GradientPanel(LayoutManager layout) {
            super(layout);
            setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);
            Theme th = Theme.current();
            int w = getWidth();
            int h = getHeight();
            g.setPaint(new GradientPaint(0, 0, th.bg1, w, h, th.bg2));
            g.fillRect(0, 0, w, h);

            // soft round blobs
            g.setColor(Theme.alpha(th.accent, 60));
            g.fill(new Ellipse2D.Double(-w * 0.06, h * 0.70, w * 0.26, w * 0.26));
            g.fill(new Ellipse2D.Double(w * 0.80, -h * 0.20, w * 0.30, w * 0.30));
            g.setColor(Theme.alpha(th.primary, 38));
            g.fill(new Ellipse2D.Double(w * 0.62, h * 0.82, w * 0.20, w * 0.20));
            g.fill(new Ellipse2D.Double(w * 0.10, -h * 0.12, w * 0.16, w * 0.16));

            // small stars, hearts and twinkles
            g.setColor(Theme.alpha(th.primary, 90));
            g.fill(Icons.sparkleShape(w * 0.07, h * 0.20, 9));
            g.fill(Icons.sparkleShape(w * 0.93, h * 0.46, 8));
            g.fill(Icons.sparkleShape(w * 0.56, h * 0.07, 7));
            g.setColor(Theme.alpha(th.accent, 150));
            g.fill(Icons.starShape(w * 0.05, h * 0.58, 8, 3.4, 5));
            g.fill(Icons.starShape(w * 0.79, h * 0.13, 7, 3, 5));
            g.fill(Icons.starShape(w * 0.47, h * 0.94, 7, 3, 5));
            g.setColor(Theme.alpha(th.primary, 80));
            g.fill(Icons.heartShape(w * 0.95, h * 0.80, 15));
            g.fill(Icons.heartShape(w * 0.03, h * 0.40, 12));
            g.fill(Icons.heartShape(w * 0.30, h * 0.95, 11));
            g.dispose();
        }
    }

    // ---------- a soft white card with rounded corners ----------

    public static class RoundedPanel extends JPanel {
        private final int radius;
        private final int cardAlpha;

        public RoundedPanel(LayoutManager layout, int radius, int cardAlpha) {
            super(layout);
            this.radius = radius;
            this.cardAlpha = cardAlpha;
            setOpaque(false);
        }

        public RoundedPanel(LayoutManager layout, int radius) {
            this(layout, radius, 225);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);
            Theme th = Theme.current();
            int w = getWidth() - 4;
            int h = getHeight() - 4;
            g.setColor(Theme.alpha(Theme.darker(th.primary, 0.5), 28));
            g.fill(new RoundRectangle2D.Double(2, 3, w, h, radius, radius));
            g.setColor(new Color(255, 255, 255, cardAlpha));
            g.fill(new RoundRectangle2D.Double(0, 0, w, h, radius, radius));
            g.setColor(Theme.alpha(th.primary, 95));
            g.setStroke(new BasicStroke(1.6f));
            g.draw(new RoundRectangle2D.Double(0.8, 0.8, w - 1.6, h - 1.6, radius, radius));
            g.dispose();
        }
    }

    // ---------- a colourful rounded button (can also work as an on/off switch) ----------

    public enum Style { PRIMARY, ACCENT, SOFT, DANGER }

    public static class ColorButton extends JButton {
        private final Style style;
        private boolean toggle = false;
        private boolean selected = false;
        private Icon picture;

        public ColorButton(String text, Icon icon, Style style) {
            super(text);
            this.style = style;
            this.picture = icon;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(new Font("SansSerif", Font.BOLD, 13));
        }

        public ColorButton asToggle() {
            toggle = true;
            return this;
        }

        public void setPicture(Icon icon) {
            this.picture = icon;
            repaint();
        }

        @Override
        public boolean isSelected() {
            return selected;
        }

        @Override
        public void setSelected(boolean b) {
            if (selected != b) {
                selected = b;
                repaint();
            }
        }

        // For an on/off button, the state flips before anyone is told about the click
        @Override
        protected void fireActionPerformed(ActionEvent e) {
            if (toggle) {
                selected = !selected;
            }
            super.fireActionPerformed(e);
        }

        @Override
        public Dimension getPreferredSize() {
            if (isPreferredSizeSet()) {
                return super.getPreferredSize();
            }
            FontMetrics fm = getFontMetrics(getFont());
            String text = getText() == null ? "" : getText();
            int w = fm.stringWidth(text) + 28;
            if (picture != null) {
                w += picture.getIconWidth() + (text.isEmpty() ? -8 : 6);
            }
            int h = Math.max(fm.getHeight() + 14, picture != null ? picture.getIconHeight() + 14 : 0);
            return new Dimension(w, h);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);
            Theme th = Theme.current();
            int w = getWidth();
            int h = getHeight();
            boolean on = isEnabled();
            boolean hover = on && getModel().isRollover();
            boolean pressed = on && getModel().isPressed();
            boolean lit = toggle && selected;

            Color top;
            Color bottom;
            Color textColor;
            Color border = null;
            if (!on) {
                top = new Color(232, 232, 236);
                bottom = new Color(220, 220, 226);
                textColor = new Color(150, 150, 160);
            } else if (style == Style.SOFT && !lit) {
                top = Color.WHITE;
                bottom = Theme.lighter(th.primary, 0.88);
                textColor = th.dark;
                border = Theme.alpha(th.primary, 140);
            } else {
                Color base;
                if (style == Style.ACCENT) {
                    base = Theme.darker(th.accent, 0.08);
                } else if (style == Style.DANGER) {
                    base = new Color(232, 84, 96);
                } else {
                    base = th.primary;
                }
                top = Theme.lighter(base, 0.22);
                bottom = base;
                textColor = (style == Style.ACCENT && th.accent.getRed() + th.accent.getGreen() > 380)
                        ? Theme.darker(th.dark, 0.1) : Color.WHITE;
            }
            if (hover) {
                top = Theme.lighter(top, 0.18);
                bottom = Theme.lighter(bottom, 0.12);
            }
            if (pressed) {
                top = Theme.darker(top, 0.10);
                bottom = Theme.darker(bottom, 0.10);
                g.translate(0, 1);
            }
            RoundRectangle2D shape = new RoundRectangle2D.Double(0.5, 0.5, w - 2, h - 3, h - 4, h - 4);
            if (on) {
                g.setColor(Theme.alpha(Theme.darker(th.primary, 0.5), 38));
                g.fill(new RoundRectangle2D.Double(0.5, 2, w - 2, h - 3, h - 4, h - 4));
            }
            g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
            g.fill(shape);
            if (border != null) {
                g.setColor(border);
                g.setStroke(new BasicStroke(1.5f));
                g.draw(shape);
            }

            // picture and text in the middle
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            String text = getText() == null ? "" : getText();
            int textW = fm.stringWidth(text);
            int iconW = picture != null ? picture.getIconWidth() : 0;
            int gap = (picture != null && !text.isEmpty()) ? 6 : 0;
            int total = iconW + gap + textW;
            int x = (w - total) / 2;
            if (picture != null) {
                picture.paintIcon(this, g, x, (h - 3 - picture.getIconHeight()) / 2 + 1);
                x += iconW + gap;
            }
            g.setColor(textColor);
            g.drawString(text, x, (h - 3 - fm.getHeight()) / 2 + fm.getAscent() + 1);
            g.dispose();
        }
    }

    // ---------- a text link (like "Forgot PIN?") ----------

    public static class LinkButton extends JButton {
        public LinkButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            Map<TextAttribute, Object> attrs = new HashMap<>();
            attrs.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
            setFont(new Font("SansSerif", Font.PLAIN, 14).deriveFont(attrs));
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(getText()) + 20, fm.getHeight() + 10);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);
            Theme th = Theme.current();
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            g.setColor(!isEnabled() ? Color.GRAY : (getModel().isRollover() ? th.accent.darker() : th.primary));
            g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    // ---------- ribbon parts (the tabs and buttons at the top, like OneNote) ----------

    public interface IconMaker {
        Icon make(int size, Color color);
    }

    // A tall button: picture on top, name underneath (can also work as an on/off switch)
    public static class RibbonButton extends JButton {
        private final IconMaker maker;
        private boolean toggle = false;
        private boolean selected = false;

        public RibbonButton(String text, IconMaker maker) {
            super(text);
            this.maker = maker;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(new Font("SansSerif", Font.BOLD, 11));
        }

        public RibbonButton asToggle() {
            toggle = true;
            return this;
        }

        @Override
        public boolean isSelected() {
            return selected;
        }

        @Override
        public void setSelected(boolean b) {
            if (selected != b) {
                selected = b;
                repaint();
            }
        }

        @Override
        protected void fireActionPerformed(ActionEvent e) {
            if (toggle) {
                selected = !selected;
            }
            super.fireActionPerformed(e);
        }

        @Override
        public Dimension getPreferredSize() {
            if (isPreferredSizeSet()) {
                return super.getPreferredSize();
            }
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(Math.max(58, fm.stringWidth(getText()) + 18), 62);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);
            Theme th = Theme.current();
            int w = getWidth();
            int h = getHeight();
            boolean on = isEnabled();
            if (on && (selected || getModel().isPressed())) {
                g.setColor(Theme.alpha(th.primary, 70));
                g.fill(new RoundRectangle2D.Double(1, 1, w - 3, h - 3, 16, 16));
                g.setColor(Theme.alpha(th.primary, 150));
                g.setStroke(new BasicStroke(1.4f));
                g.draw(new RoundRectangle2D.Double(1.5, 1.5, w - 4, h - 4, 16, 16));
            } else if (on && getModel().isRollover()) {
                g.setColor(Theme.alpha(th.primary, 32));
                g.fill(new RoundRectangle2D.Double(1, 1, w - 3, h - 3, 16, 16));
            }
            Icon icon = maker == null ? null : maker.make(24, on ? th.primary : new Color(170, 170, 180));
            if (icon != null) {
                icon.paintIcon(this, g, (w - 24) / 2, 7);
            }
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            g.setColor(on ? th.dark : new Color(160, 160, 170));
            String text = getText() == null ? "" : getText();
            g.drawString(text, (w - fm.stringWidth(text)) / 2, h - 10);
            g.dispose();
        }
    }

    // The tab names (Home, Insert, Draw...)
    public static class TabChip extends JButton {
        private boolean selected;

        public TabChip(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(new Font("SansSerif", Font.BOLD, 13));
        }

        @Override
        public boolean isSelected() {
            return selected;
        }

        @Override
        public void setSelected(boolean b) {
            selected = b;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(getText()) + 28, 30);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);
            Theme th = Theme.current();
            int w = getWidth();
            int h = getHeight();
            if (selected) {
                g.setPaint(new GradientPaint(0, 0, Theme.lighter(th.primary, 0.2), 0, h, th.primary));
                g.fill(new RoundRectangle2D.Double(1, 2, w - 2, h - 2, 18, 18));
            } else if (getModel().isRollover()) {
                g.setColor(Theme.alpha(th.primary, 40));
                g.fill(new RoundRectangle2D.Double(1, 2, w - 2, h - 2, 18, 18));
            }
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            g.setColor(selected ? Color.WHITE : th.dark);
            g.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h - fm.getHeight()) / 2 + fm.getAscent() + 2);
            g.dispose();
        }
    }

    // A round colour dot for choosing a pen colour
    public static class ColorDot extends JButton {
        private final Color dot;
        private boolean selected;

        public ColorDot(Color dot) {
            this.dot = dot;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(26, 26));
        }

        public Color dotColor() {
            return dot;
        }

        @Override
        public boolean isSelected() {
            return selected;
        }

        @Override
        public void setSelected(boolean b) {
            selected = b;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            quality(g);
            int w = getWidth();
            int h = getHeight();
            if (selected) {
                g.setColor(Theme.current().primary);
                g.setStroke(new BasicStroke(2.4f));
                g.draw(new Ellipse2D.Double(1.5, 1.5, w - 3, h - 3));
            }
            g.setColor(dot);
            g.fill(new Ellipse2D.Double(5, 5, w - 10, h - 10));
            g.setColor(new Color(0, 0, 0, 70));
            g.setStroke(new BasicStroke(1f));
            g.draw(new Ellipse2D.Double(5, 5, w - 10, h - 10));
            g.dispose();
        }
    }
}