import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

// A small table you can type in, placed inside a note
public class TableBox extends JPanel {

    // The app sets this so it knows when you type in a table (to save the note)
    public static Runnable changeListener;

    private final JTable table;
    private final DefaultTableModel model;

    public TableBox(String[][] cells) {
        super(new BorderLayout());
        int rows = Math.max(1, cells.length);
        int cols = Math.max(1, cells[0].length);
        model = new DefaultTableModel(rows, cols);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                model.setValueAt(c < cells[r].length ? cells[r][c] : "", r, c);
            }
        }
        table = new JTable(model);
        table.setRowHeight(30);
        table.setFont(new Font("SansSerif", Font.PLAIN, 15));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setShowGrid(true);
        table.setFillsViewportHeight(false);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        JTextField editorField = new JTextField();
        editorField.setFont(table.getFont());
        DefaultCellEditor cellEditor = new DefaultCellEditor(editorField);
        cellEditor.setClickCountToStart(1);
        table.setDefaultEditor(Object.class, cellEditor);
        restyle();
        setWidths();
        setOpaque(false);
        add(table, BorderLayout.CENTER);

        model.addTableModelListener(e -> {
            if (changeListener != null) {
                changeListener.run();
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowMenu(e);
            }
        });
    }

    public static TableBox empty(int rows, int cols) {
        String[][] cells = new String[rows][cols];
        for (String[] row : cells) {
            java.util.Arrays.fill(row, "");
        }
        return new TableBox(cells);
    }

    private void restyle() {
        Theme th = Theme.current();
        table.setGridColor(Theme.alpha(th.primary, 150));
        table.setSelectionBackground(Theme.lighter(th.primary, 0.78));
        table.setSelectionForeground(Color.BLACK);
        setBorder(BorderFactory.createLineBorder(Theme.alpha(th.primary, 190), 2));
    }

    private void setWidths() {
        for (int c = 0; c < table.getColumnCount(); c++) {
            table.getColumnModel().getColumn(c).setPreferredWidth(130);
        }
    }

    private void maybeShowMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int row = table.rowAtPoint(e.getPoint());
        int col = table.columnAtPoint(e.getPoint());
        if (row < 0 || col < 0) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        JMenuItem addRow = new JMenuItem("Add a row below");
        addRow.addActionListener(a -> addRow(row + 1));
        JMenuItem addCol = new JMenuItem("Add a column on the right");
        addCol.addActionListener(a -> addColumn(col + 1));
        JMenuItem delRow = new JMenuItem("Delete this row");
        delRow.addActionListener(a -> deleteRow(row));
        JMenuItem delCol = new JMenuItem("Delete this column");
        delCol.addActionListener(a -> deleteColumn(col));
        menu.add(addRow);
        menu.add(addCol);
        menu.addSeparator();
        menu.add(delRow);
        menu.add(delCol);
        menu.show(table, e.getX(), e.getY());
    }

    private void resized() {
        setWidths();
        invalidate();
        revalidate();
        repaint();
        if (changeListener != null) {
            changeListener.run();
        }
    }

    public void addRow(int at) {
        Object[] blank = new Object[model.getColumnCount()];
        java.util.Arrays.fill(blank, "");
        model.insertRow(Math.min(at, model.getRowCount()), blank);
        resized();
    }

    public void addColumn(int at) {
        String[] column = new String[model.getRowCount()];
        java.util.Arrays.fill(column, "");
        model.addColumn("", column);
        // move the new last column into place
        int last = model.getColumnCount() - 1;
        if (at < last) {
            table.moveColumn(last, at);
        }
        resized();
    }

    public void deleteRow(int row) {
        if (model.getRowCount() > 1 && row >= 0 && row < model.getRowCount()) {
            if (table.isEditing()) {
                table.getCellEditor().cancelCellEditing();
            }
            model.removeRow(row);
            resized();
        }
    }

    public void deleteColumn(int viewColumn) {
        if (model.getColumnCount() > 1 && viewColumn >= 0 && viewColumn < table.getColumnCount()) {
            if (table.isEditing()) {
                table.getCellEditor().cancelCellEditing();
            }
            int modelColumn = table.convertColumnIndexToModel(viewColumn);
            // rebuild the model without that column
            int rows = model.getRowCount();
            int cols = model.getColumnCount();
            String[][] keep = new String[rows][cols - 1];
            for (int r = 0; r < rows; r++) {
                int k = 0;
                for (int c = 0; c < cols; c++) {
                    if (c != modelColumn) {
                        keep[r][k++] = valueAt(r, c);
                    }
                }
            }
            model.setColumnCount(cols - 1);
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols - 1; c++) {
                    model.setValueAt(keep[r][c], r, c);
                }
            }
            resized();
        }
    }

    private String valueAt(int r, int c) {
        Object v = model.getValueAt(r, c);
        return v == null ? "" : v.toString();
    }

    // What is in the table, row by row (in the order you see on screen)
    public String[][] data() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        int rows = model.getRowCount();
        int cols = table.getColumnCount();
        String[][] cells = new String[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                cells[r][c] = valueAt(r, table.convertColumnIndexToModel(c));
            }
        }
        return cells;
    }

    // ---------- saving and loading ----------

    public String toRecord() {
        String[][] cells = data();
        StringBuilder sb = new StringBuilder("TBL|").append(cells.length).append("|").append(cells[0].length).append("|");
        for (int r = 0; r < cells.length; r++) {
            if (r > 0) {
                sb.append(";");
            }
            for (int c = 0; c < cells[r].length; c++) {
                if (c > 0) {
                    sb.append(",");
                }
                sb.append(Base64.getEncoder().encodeToString(cells[r][c].getBytes(StandardCharsets.UTF_8)));
            }
        }
        return sb.toString();
    }

    // Reads the cells from a saved line (also used to search inside tables)
    public static String[][] parseCells(String record) {
        String[] parts = record.split("\\|", 4);
        if (parts.length < 4) {
            return new String[][]{{""}};
        }
        String[] rows = parts[3].split(";", -1);
        String[][] cells = new String[rows.length][];
        for (int r = 0; r < rows.length; r++) {
            String[] cols = rows[r].split(",", -1);
            cells[r] = new String[cols.length];
            for (int c = 0; c < cols.length; c++) {
                cells[r][c] = new String(Base64.getDecoder().decode(cols[c]), StandardCharsets.UTF_8);
            }
        }
        return cells;
    }

    public static TableBox fromRecord(String record) {
        return new TableBox(parseCells(record));
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }
}
