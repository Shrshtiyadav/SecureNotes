import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import java.awt.CardLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// The tabs and buttons at the top of the notes screen (Home, Insert, Draw, History, Search, Share, View)
class Ribbon {

    // A little coloured square shown inside a colour button
    static class Swatch implements Icon {
        Color color;

        Swatch(Color color) {
            this.color = color;
        }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fill(new RoundRectangle2D.Double(x, y, 16, 16, 6, 6));
            g2.setColor(new Color(0, 0, 0, 90));
            g2.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, 15, 15, 6, 6));
            g2.dispose();
        }

        public int getIconWidth() {
            return 16;
        }

        public int getIconHeight() {
            return 16;
        }
    }

    static final Color[] TEXT_COLORS = {
            new Color(0, 0, 0), new Color(89, 89, 89), new Color(192, 0, 0), new Color(237, 125, 49),
            new Color(0, 128, 0), new Color(0, 112, 192), new Color(33, 64, 120), new Color(112, 48, 160)
    };
    static final Color[] HIGHLIGHT_COLORS = {
            new Color(255, 255, 0), new Color(144, 238, 144), new Color(127, 255, 212),
            new Color(255, 182, 193), new Color(255, 200, 120), new Color(216, 191, 216)
    };
    static final Color[] PEN_COLORS = {
            new Color(30, 30, 30), new Color(220, 40, 40), new Color(245, 130, 30), new Color(255, 214, 0),
            new Color(30, 160, 80), new Color(30, 110, 220), new Color(130, 70, 200), new Color(240, 90, 160)
    };
    static final Integer[] FONT_SIZES = {8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 32, 36, 48, 72};

    private final SecureNotesApp app;
    final JPanel panel = new JPanel(new BorderLayout(0, 4));
    private final CardLayout cards = new CardLayout();
    private final Kit.RoundedPanel body = new Kit.RoundedPanel(cards, 22);
    private final List<Kit.TabChip> tabs = new ArrayList<>();
    private final List<JComponent> needNote = new ArrayList<>();
    private boolean collapsed = false;
    private boolean updating = false;

    // controls the app reads or updates
    JComboBox<String> fontBox;
    JComboBox<Integer> sizeBox;
    Kit.ColorButton bold;
    Kit.ColorButton italic;
    Kit.ColorButton underline;
    Kit.ColorButton textColorButton;
    Kit.ColorButton highlightButton;
    private final Swatch textSwatch = new Swatch(Color.BLACK);
    private final Swatch highlightSwatch = new Swatch(new Color(255, 255, 0));
    private Kit.RibbonButton typeTool;
    private Kit.RibbonButton penTool;
    private Kit.RibbonButton highlighterTool;
    private Kit.RibbonButton eraserTool;
    private final List<Kit.ColorDot> dots = new ArrayList<>();
    private final List<Kit.RibbonButton> widths = new ArrayList<>();
    JTextField findField;
    JLabel findCount;
    Kit.RibbonButton listToggle;
    Kit.RibbonButton fullScreenToggle;
    Kit.RibbonButton collapseToggle;

    Ribbon(SecureNotesApp app) {
        this.app = app;
        panel.setOpaque(false);

        JPanel tabRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tabRow.setOpaque(false);
        String[] names = {"Home", "Insert", "Draw", "History", "Search", "Share", "View"};
        body.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        body.add(homeTab(), "Home");
        body.add(insertTab(), "Insert");
        body.add(drawTab(), "Draw");
        body.add(historyTab(), "History");
        body.add(searchTab(), "Search");
        body.add(shareTab(), "Share");
        body.add(viewTab(), "View");
        for (String name : names) {
            Kit.TabChip chip = new Kit.TabChip(name);
            chip.addActionListener(e -> {
                if (collapsed) {
                    setCollapsed(false);
                }
                showTab(name);
            });
            tabs.add(chip);
            tabRow.add(chip);
        }
        panel.add(tabRow, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        showTab("Home");
        setTool(DrawPane.Tool.TYPE);
        selectPenColor(0);
        selectWidth(1);
    }

    // ---------- showing tabs ----------

    void showTab(String name) {
        cards.show(body, name);
        for (Kit.TabChip chip : tabs) {
            chip.setSelected(chip.getText().equals(name));
        }
    }

    void setCollapsed(boolean hide) {
        collapsed = hide;
        body.setVisible(!hide);
        if (collapseToggle != null) {
            collapseToggle.setSelected(hide);
        }
        panel.revalidate();
        panel.repaint();
    }

    // Controls that only make sense while a note is open
    void setNoteControlsEnabled(boolean on) {
        for (JComponent c : needNote) {
            c.setEnabled(on);
        }
    }

    boolean updating() {
        return updating;
    }

    // Makes the buttons show the look of the text where the cursor is
    void sync(AttributeSet a) {
        updating = true;
        bold.setSelected(StyleConstants.isBold(a));
        italic.setSelected(StyleConstants.isItalic(a));
        underline.setSelected(StyleConstants.isUnderline(a));
        fontBox.setSelectedItem(StyleConstants.getFontFamily(a));
        sizeBox.setSelectedItem(StyleConstants.getFontSize(a));
        textSwatch.color = StyleConstants.getForeground(a);
        textColorButton.repaint();
        updating = false;
    }

    void setTool(DrawPane.Tool tool) {
        typeTool.setSelected(tool == DrawPane.Tool.TYPE);
        penTool.setSelected(tool == DrawPane.Tool.PEN);
        highlighterTool.setSelected(tool == DrawPane.Tool.HIGHLIGHTER);
        eraserTool.setSelected(tool == DrawPane.Tool.ERASER);
    }

    private void selectPenColor(int index) {
        for (int i = 0; i < dots.size(); i++) {
            dots.get(i).setSelected(i == index);
        }
        app.setPenColor(PEN_COLORS[index]);
    }

    private void selectWidth(int index) {
        float[] sizes = {2.5f, 4.5f, 8f};
        for (int i = 0; i < widths.size(); i++) {
            widths.get(i).setSelected(i == index);
        }
        app.setPenWidth(sizes[index]);
    }

    // ---------- small building helpers ----------

    private Kit.RibbonButton button(String text, Kit.IconMaker icon, Runnable action, boolean needsNote) {
        Kit.RibbonButton b = new Kit.RibbonButton(text, icon);
        b.addActionListener(e -> action.run());
        if (needsNote) {
            needNote.add(b);
        }
        return b;
    }

    private JPanel tabPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        return p;
    }

    private JPanel group(String label, JComponent... items) {
        JPanel g = new JPanel(new BorderLayout(0, 2));
        g.setOpaque(false);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 2));
        row.setOpaque(false);
        for (JComponent c : items) {
            row.add(c);
        }
        JLabel l = new JLabel(label, JLabel.CENTER);
        l.setFont(new Font("SansSerif", Font.PLAIN, 10));
        l.setForeground(new Color(110, 118, 130));
        g.add(row, BorderLayout.CENTER);
        g.add(l, BorderLayout.SOUTH);
        return g;
    }

    private JComponent divider() {
        JPanel line = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.alpha(Theme.current().primary, 70));
                g.fillRect(getWidth() / 2, 6, 1, getHeight() - 12);
            }
        };
        line.setOpaque(false);
        line.setPreferredSize(new Dimension(9, 66));
        return line;
    }

    private static String[] availableFonts() {
        String[] wanted = {"SansSerif", "Serif", "Monospaced", "Arial", "Calibri", "Cambria",
                "Comic Sans MS", "Consolas", "Georgia", "Segoe UI", "Times New Roman",
                "Trebuchet MS", "Verdana"};
        Set<String> installed = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        List<String> result = new ArrayList<>();
        for (String name : wanted) {
            boolean builtIn = name.equals("SansSerif") || name.equals("Serif") || name.equals("Monospaced");
            if (builtIn || installed.contains(name)) {
                result.add(name);
            }
        }
        return result.toArray(new String[0]);
    }

    // ---------- Home ----------

    private JPanel homeTab() {
        JPanel tab = tabPanel();

        Kit.RibbonButton undo = button("Undo", Icons::undo, app::undoEdit, true);
        Kit.RibbonButton redo = button("Redo", Icons::redo, app::redoEdit, true);

        fontBox = new JComboBox<>(availableFonts());
        fontBox.setPreferredSize(new Dimension(140, 28));
        fontBox.setFocusable(false);
        fontBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setFont(new Font((String) value, Font.PLAIN, 14));
                return this;
            }
        });
        fontBox.addActionListener(e -> {
            if (updating || app.isLoading()) {
                return;
            }
            SimpleAttributeSet a = new SimpleAttributeSet();
            StyleConstants.setFontFamily(a, (String) fontBox.getSelectedItem());
            app.applyAttributes(a);
        });
        sizeBox = new JComboBox<>(FONT_SIZES);
        sizeBox.setPreferredSize(new Dimension(62, 28));
        sizeBox.setFocusable(false);
        sizeBox.addActionListener(e -> {
            if (updating || app.isLoading()) {
                return;
            }
            SimpleAttributeSet a = new SimpleAttributeSet();
            StyleConstants.setFontSize(a, (Integer) sizeBox.getSelectedItem());
            app.applyAttributes(a);
        });
        needNote.add(fontBox);
        needNote.add(sizeBox);

        bold = new Kit.ColorButton("B", null, Kit.Style.SOFT).asToggle();
        bold.setFont(new Font("SansSerif", Font.BOLD, 15));
        bold.setToolTipText("Bold (Ctrl+B)");
        bold.addActionListener(e -> {
            SimpleAttributeSet a = new SimpleAttributeSet();
            StyleConstants.setBold(a, bold.isSelected());
            app.applyAttributes(a);
        });
        italic = new Kit.ColorButton("I", null, Kit.Style.SOFT).asToggle();
        italic.setFont(new Font("SansSerif", Font.BOLD | Font.ITALIC, 15));
        italic.setToolTipText("Italic (Ctrl+I)");
        italic.addActionListener(e -> {
            SimpleAttributeSet a = new SimpleAttributeSet();
            StyleConstants.setItalic(a, italic.isSelected());
            app.applyAttributes(a);
        });
        underline = new Kit.ColorButton("U", null, Kit.Style.SOFT).asToggle();
        java.util.Map<java.awt.font.TextAttribute, Object> ul = new java.util.HashMap<>();
        ul.put(java.awt.font.TextAttribute.UNDERLINE, java.awt.font.TextAttribute.UNDERLINE_ON);
        underline.setFont(new Font("SansSerif", Font.BOLD, 15).deriveFont(ul));
        underline.setToolTipText("Underline (Ctrl+U)");
        underline.addActionListener(e -> {
            SimpleAttributeSet a = new SimpleAttributeSet();
            StyleConstants.setUnderline(a, underline.isSelected());
            app.applyAttributes(a);
        });
        for (Kit.ColorButton b : new Kit.ColorButton[]{bold, italic, underline}) {
            b.setPreferredSize(new Dimension(36, 28));
            b.setFocusable(false);
            needNote.add(b);
        }

        textColorButton = new Kit.ColorButton("Text colour", textSwatch, Kit.Style.SOFT);
        textColorButton.setFocusable(false);
        textColorButton.setPreferredSize(new Dimension(118, 28));
        JPopupMenu colorMenu = app.paletteMenu(TEXT_COLORS, false, color -> {
            textSwatch.color = color;
            textColorButton.repaint();
            SimpleAttributeSet a = new SimpleAttributeSet();
            StyleConstants.setForeground(a, color);
            app.applyAttributes(a);
        });
        textColorButton.addActionListener(e -> colorMenu.show(textColorButton, 0, textColorButton.getHeight()));

        highlightButton = new Kit.ColorButton("Highlight", highlightSwatch, Kit.Style.SOFT);
        highlightButton.setFocusable(false);
        highlightButton.setPreferredSize(new Dimension(104, 28));
        JPopupMenu highlightMenu = app.paletteMenu(HIGHLIGHT_COLORS, true, color -> {
            SimpleAttributeSet a = new SimpleAttributeSet();
            if (color == null) {
                StyleConstants.setBackground(a, RichText.PAPER);
            } else {
                highlightSwatch.color = color;
                highlightButton.repaint();
                StyleConstants.setBackground(a, color);
            }
            app.applyAttributes(a);
        });
        highlightButton.addActionListener(e -> highlightMenu.show(highlightButton, 0, highlightButton.getHeight()));
        needNote.add(textColorButton);
        needNote.add(highlightButton);

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row1.setOpaque(false);
        row1.add(fontBox);
        row1.add(sizeBox);
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row2.setOpaque(false);
        row2.add(bold);
        row2.add(italic);
        row2.add(underline);
        row2.add(textColorButton);
        row2.add(highlightButton);
        JPanel fontBlock = new JPanel();
        fontBlock.setOpaque(false);
        fontBlock.setLayout(new BoxLayout(fontBlock, BoxLayout.Y_AXIS));
        fontBlock.add(row1);
        fontBlock.add(row2);

        tab.add(group("Undo", undo, redo));
        tab.add(divider());
        tab.add(group("Font", fontBlock));
        tab.add(divider());
        tab.add(group("Paragraph",
                button("Bullets", Icons::bullets, app::toggleBullets, true),
                button("To-do", Icons::checkbox, app::insertCheckbox, true),
                button("Left", Icons::alignLeft, () -> app.setAlignment(StyleConstants.ALIGN_LEFT), true),
                button("Centre", Icons::alignCenter, () -> app.setAlignment(StyleConstants.ALIGN_CENTER), true),
                button("Right", Icons::alignRight, () -> app.setAlignment(StyleConstants.ALIGN_RIGHT), true)));
        return tab;
    }

    // ---------- Insert ----------

    private JPanel insertTab() {
        JPanel tab = tabPanel();
        tab.add(group("Pictures",
                button("Picture", Icons::picture, app::insertPicture, true),
                button("Paste picture", Icons::pastePicture, app::pastePicture, true)));
        tab.add(divider());
        tab.add(group("Insert",
                button("Table", Icons::table, app::insertTable, true),
                button("Line", Icons::rule, app::insertLine, true),
                button("Date & time", Icons::calendar, app::insertDateTime, true),
                button("Symbol", Icons::symbol, () -> app.showSymbolMenu(panel), true),
                button("To-do", Icons::checkbox, app::insertCheckbox, true)));
        tab.add(divider());
        tab.add(group("Pages",
                button("Sub note", Icons::subNote, app::newSubNote, true)));
        return tab;
    }

    // ---------- Draw ----------

    private JPanel drawTab() {
        JPanel tab = tabPanel();
        typeTool = button("Type", Icons::ibeam, () -> app.setDrawTool(DrawPane.Tool.TYPE), true);
        penTool = button("Pen", Icons::pen, () -> app.setDrawTool(DrawPane.Tool.PEN), true);
        highlighterTool = button("Highlighter", Icons::highlighter, () -> app.setDrawTool(DrawPane.Tool.HIGHLIGHTER), true);
        eraserTool = button("Eraser", Icons::eraser, () -> app.setDrawTool(DrawPane.Tool.ERASER), true);
        tab.add(group("Tools", typeTool, penTool, highlighterTool, eraserTool));
        tab.add(divider());

        JPanel dotRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 14));
        dotRow.setOpaque(false);
        for (int i = 0; i < PEN_COLORS.length; i++) {
            Kit.ColorDot dot = new Kit.ColorDot(PEN_COLORS[i]);
            final int index = i;
            dot.addActionListener(e -> selectPenColor(index));
            dots.add(dot);
            needNote.add(dot);
            dotRow.add(dot);
        }
        dotRow.setPreferredSize(new Dimension(8 * 30 + 10, 50));
        tab.add(group("Colour", dotRow));
        tab.add(divider());

        String[] widthNames = {"Thin", "Medium", "Thick"};
        double[] weights = {0.06, 0.14, 0.24};
        Kit.RibbonButton[] wb = new Kit.RibbonButton[3];
        for (int i = 0; i < 3; i++) {
            final int index = i;
            final double weight = weights[i];
            wb[i] = button(widthNames[i], (size, color) -> Icons.thickness(size, color, weight), () -> selectWidth(index), true);
            widths.add(wb[i]);
        }
        tab.add(group("Thickness", wb));
        tab.add(divider());
        tab.add(group("Edit",
                button("Undo line", Icons::undo, app::undoStroke, true),
                button("Clear all", Icons::trash, app::clearDrawing, true)));
        return tab;
    }

    // ---------- History ----------

    private JPanel historyTab() {
        JPanel tab = tabPanel();
        tab.add(group("This note",
                button("Page versions", Icons::clock, app::showVersions, true),
                button("Save version", Icons::save, app::saveVersionNow, true)));
        tab.add(divider());
        tab.add(group("All notes",
                button("Recent edits", Icons::calendar, app::showRecent, false),
                button("Recycle bin", Icons::trash, app::showRecycleBin, false)));
        return tab;
    }

    // ---------- Search ----------

    private JPanel searchTab() {
        JPanel tab = tabPanel();
        findField = new JTextField(16);
        findField.setFont(new Font("SansSerif", Font.PLAIN, 15));
        findField.setToolTipText("Type a word to find it on this page");
        findField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { changed(); }
            public void removeUpdate(DocumentEvent e) { changed(); }
            public void changedUpdate(DocumentEvent e) { changed(); }

            private void changed() {
                app.findTextChanged(findField.getText());
            }
        });
        findField.addActionListener(e -> app.findNext());
        needNote.add(findField);
        findCount = new JLabel(" ");
        findCount.setFont(new Font("SansSerif", Font.BOLD, 12));
        findCount.setPreferredSize(new Dimension(86, 20));
        JPanel fieldRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 14));
        fieldRow.setOpaque(false);
        fieldRow.add(findField);
        fieldRow.add(findCount);
        tab.add(group("Find on this page", fieldRow,
                button("Previous", Icons::chevronUp, app::findPrevious, true),
                button("Next", Icons::chevronDown, app::findNext, true),
                button("Clear", Icons::trash, app::clearFind, true)));
        tab.add(divider());
        tab.add(group("Everywhere",
                button("Search all notes", Icons::search, app::searchAllNotes, false)));
        return tab;
    }

    // ---------- Share ----------

    private JPanel shareTab() {
        JPanel tab = tabPanel();
        tab.add(group("Save a copy",
                button("PDF", Icons::file, app::exportPdf, true),
                button("Web page", Icons::html, app::exportHtml, true),
                button("Text file", Icons::file, app::exportText, true),
                button("Picture", Icons::picture, app::exportPicture, true)));
        tab.add(divider());
        tab.add(group("Send",
                button("Print", Icons::printer, app::printNote, true),
                button("Email", Icons::mail, app::emailNote, true),
                button("Copy text", Icons::copy, app::copyText, true)));
        tab.add(divider());
        JLabel warning = new JLabel("<html><div style='width:170px'>Copies you save are <b>not locked</b>. "
                + "Anyone who opens them can read them.</div></html>");
        warning.setFont(new Font("SansSerif", Font.PLAIN, 11));
        warning.setForeground(new Color(170, 60, 60));
        JPanel note = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 8));
        note.setOpaque(false);
        note.add(warning);
        tab.add(note);
        return tab;
    }

    // ---------- View ----------

    private JPanel viewTab() {
        JPanel tab = tabPanel();
        listToggle = new Kit.RibbonButton("Notes list", Icons::sidebar).asToggle();
        listToggle.setSelected(true);
        listToggle.addActionListener(e -> app.showNotesList(listToggle.isSelected()));
        fullScreenToggle = new Kit.RibbonButton("Full screen", Icons::fullscreen).asToggle();
        fullScreenToggle.addActionListener(e -> app.setFullScreen(fullScreenToggle.isSelected()));
        collapseToggle = new Kit.RibbonButton("Hide ribbon", Icons::chevronUp).asToggle();
        collapseToggle.addActionListener(e -> setCollapsed(collapseToggle.isSelected()));
        tab.add(group("Window", listToggle, fullScreenToggle, collapseToggle));
        return tab;
    }
}
