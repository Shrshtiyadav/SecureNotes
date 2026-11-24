import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

// The writing page. You can type on it, and you can also draw on top of it with a pen.
public class DrawPane extends JTextPane {

    public enum Tool { TYPE, PEN, HIGHLIGHTER, ERASER }

    // One pen line
    public static class Stroke {
        public final Color color;
        public final float width;
        public final boolean highlighter;
        public final List<Point> points = new ArrayList<>();

        public Stroke(Color color, float width, boolean highlighter) {
            this.color = color;
            this.width = width;
            this.highlighter = highlighter;
        }

        public String toRecord() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("STK|#%06X|%.1f|%s|", color.getRGB() & 0xFFFFFF, width, highlighter ? "H" : "P"));
            for (int i = 0; i < points.size(); i++) {
                if (i > 0) {
                    sb.append(";");
                }
                sb.append(points.get(i).x).append(",").append(points.get(i).y);
            }
            return sb.toString();
        }

        public static Stroke parse(String record) {
            try {
                String[] p = record.split("\\|", 5);
                Stroke s = new Stroke(Color.decode(p[1]), Float.parseFloat(p[2]), p[3].equals("H"));
                if (p.length > 4 && !p[4].isEmpty()) {
                    for (String pair : p[4].split(";")) {
                        String[] xy = pair.split(",");
                        s.points.add(new Point(Integer.parseInt(xy[0]), Integer.parseInt(xy[1])));
                    }
                }
                return s;
            } catch (Exception e) {
                return new Stroke(Color.BLACK, 2f, false);     // a damaged line is just skipped
            }
        }

        // How close a spot is to this line
        double distanceTo(Point q) {
            double best = Double.MAX_VALUE;
            if (points.size() == 1) {
                return points.get(0).distance(q);
            }
            for (int i = 0; i + 1 < points.size(); i++) {
                Point a = points.get(i);
                Point b = points.get(i + 1);
                double dx = b.x - a.x;
                double dy = b.y - a.y;
                double len2 = dx * dx + dy * dy;
                double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((q.x - a.x) * dx + (q.y - a.y) * dy) / len2));
                best = Math.min(best, q.distance(a.x + t * dx, a.y + t * dy));
            }
            return best;
        }
    }

    private Tool tool = Tool.TYPE;
    private Color penColor = new Color(30, 30, 30);
    private float penWidth = 3f;
    private final List<Stroke> strokes = new ArrayList<>();
    private Stroke active;
    private Runnable onChange;

    public DrawPane() {
        super();
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    private void changed() {
        if (onChange != null) {
            onChange.run();
        }
    }

    // ---------- tools ----------

    public Tool tool() {
        return tool;
    }

    public void setTool(Tool tool) {
        this.tool = tool;
        setEditable(tool == Tool.TYPE);
        setCursor(Cursor.getPredefinedCursor(tool == Tool.TYPE ? Cursor.TEXT_CURSOR : Cursor.CROSSHAIR_CURSOR));
    }

    public void setPenColor(Color color) {
        this.penColor = color;
    }

    public Color penColor() {
        return penColor;
    }

    public void setPenWidth(float width) {
        this.penWidth = width;
    }

    public float penWidth() {
        return penWidth;
    }

    // ---------- the drawing itself ----------

    public List<Stroke> strokes() {
        return strokes;
    }

    public void setStrokes(List<Stroke> list) {
        strokes.clear();
        strokes.addAll(list);
        active = null;
        revalidate();
        repaint();
    }

    public void undoStroke() {
        if (!strokes.isEmpty()) {
            strokes.remove(strokes.size() - 1);
            revalidate();
            repaint();
            changed();
        }
    }

    public void clearStrokes() {
        if (!strokes.isEmpty()) {
            strokes.clear();
            revalidate();
            repaint();
            changed();
        }
    }

    // ---------- the mouse ----------

    @Override
    protected void processMouseEvent(MouseEvent e) {
        if (tool == Tool.TYPE) {
            if (e.getID() == MouseEvent.MOUSE_PRESSED && e.getButton() == MouseEvent.BUTTON1
                    && toggleCheckboxAt(e.getPoint())) {
                e.consume();
                return;
            }
            super.processMouseEvent(e);
            return;
        }
        if (e.getButton() != MouseEvent.BUTTON1) {
            return;
        }
        if (e.getID() == MouseEvent.MOUSE_PRESSED) {
            if (tool == Tool.ERASER) {
                eraseAt(e.getPoint());
            } else {
                active = new Stroke(penColor, tool == Tool.HIGHLIGHTER ? Math.max(14f, penWidth * 3.5f) : penWidth,
                        tool == Tool.HIGHLIGHTER);
                active.points.add(e.getPoint());
                repaint();
            }
        } else if (e.getID() == MouseEvent.MOUSE_RELEASED) {
            finishStroke();
        }
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e) {
        if (tool == Tool.TYPE) {
            super.processMouseMotionEvent(e);
            return;
        }
        if (e.getID() == MouseEvent.MOUSE_DRAGGED) {
            if (tool == Tool.ERASER) {
                eraseAt(e.getPoint());
            } else if (active != null) {
                Point last = active.points.get(active.points.size() - 1);
                if (last.distance(e.getPoint()) >= 2.0) {      // skip points that are too close
                    active.points.add(e.getPoint());
                    repaint();
                }
            }
        }
    }

    private void finishStroke() {
        if (active != null) {
            strokes.add(active);
            active = null;
            revalidate();
            repaint();
            changed();
        }
    }

    private void eraseAt(Point p) {
        double radius = 9;
        boolean removed = strokes.removeIf(s -> s.distanceTo(p) <= radius + s.width / 2.0);
        if (removed) {
            revalidate();
            repaint();
            changed();
        }
    }

    // ---------- a tick box inside the text: a click flips it ----------

    private boolean toggleCheckboxAt(Point click) {
        try {
            int pos = viewToModel2D(click);
            StyledDocument doc = getStyledDocument();
            for (int p = pos - 1; p <= pos; p++) {
                if (p < 0 || p >= doc.getLength()) {
                    continue;
                }
                Element el = doc.getCharacterElement(p);
                AttributeSet a = el.getAttributes();
                if (!a.isDefined(RichText.CHECK_KEY)) {
                    continue;
                }
                Rectangle2D start = modelToView2D(p);
                Rectangle2D end = modelToView2D(p + 1);
                if (start == null || end == null) {
                    continue;
                }
                boolean sameLine = click.y >= start.getY() && click.y <= start.getY() + start.getHeight();
                if (sameLine && click.x >= start.getX() - 2 && click.x <= end.getX() + 2) {
                    boolean now = !Boolean.TRUE.equals(a.getAttribute(RichText.CHECK_KEY));
                    doc.setCharacterAttributes(p, 1, RichText.checkAttributes(now), false);
                    repaint();
                    return true;
                }
            }
        } catch (BadLocationException e) {
            // not on a tick box
        }
        return false;
    }

    // ---------- painting the pen lines on top of the text ----------

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (Stroke s : strokes) {
            paintStroke(g2, s);
        }
        if (active != null) {
            paintStroke(g2, active);
        }
        g2.dispose();
    }

    private static void paintStroke(Graphics2D g, Stroke s) {
        if (s.points.isEmpty()) {
            return;
        }
        Color c = s.highlighter ? new Color(s.color.getRed(), s.color.getGreen(), s.color.getBlue(), 95) : s.color;
        g.setColor(c);
        g.setStroke(new BasicStroke(s.width, s.highlighter ? BasicStroke.CAP_BUTT : BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g.draw(smooth(s.points));
    }

    private static Path2D smooth(List<Point> pts) {
        Path2D path = new Path2D.Float();
        Point first = pts.get(0);
        path.moveTo(first.x, first.y);
        if (pts.size() == 1) {
            path.lineTo(first.x + 0.01, first.y);
            return path;
        }
        for (int i = 1; i + 1 < pts.size(); i++) {
            Point a = pts.get(i);
            Point b = pts.get(i + 1);
            path.quadTo(a.x, a.y, (a.x + b.x) / 2.0, (a.y + b.y) / 2.0);
        }
        Point last = pts.get(pts.size() - 1);
        path.lineTo(last.x, last.y);
        return path;
    }

    // ---------- room to draw below the text ----------

    private int lowestPenPoint() {
        int lowest = 0;
        for (Stroke s : strokes) {
            for (Point p : s.points) {
                lowest = Math.max(lowest, p.y);
            }
        }
        return lowest;
    }

    // The height the text and the pen lines need (without asking the scroll area)
    private int contentHeight() {
        int textHeight = getUI() == null ? 0 : getUI().getPreferredSize(this).height;
        return Math.max(textHeight, lowestPenPoint() + 120);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        int lowest = lowestPenPoint();
        if (lowest + 120 > d.height) {
            d.height = lowest + 120;
        }
        return d;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        Container parent = getParent();
        if (parent instanceof JViewport) {
            return contentHeight() <= ((JViewport) parent).getHeight();
        }
        return super.getScrollableTracksViewportHeight();
    }

    // A fresh copy of the settings used to insert things like tick boxes
    public static SimpleAttributeSet blank() {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setBold(a, false);
        return a;
    }
}
