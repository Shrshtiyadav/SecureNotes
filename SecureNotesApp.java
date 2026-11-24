import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

public class SecureNotesApp {
    private JFrame frame;
    private CardLayout cards;
    private JPanel mainPanel;

    // Login screen parts
    private JLabel messageLabel;
    private JPasswordField pinField;
    private JButton loginButton;
    private int attempts = 0;
    private JLabel confirmLabel;
    private JPasswordField confirmField;
    private JLabel emailLabel;
    private JTextField emailField;
    private JButton forgotButton;

    // Notes screen parts
    private DefaultListModel<Note> listModel;
    private JList<Note> notesList;
    private JTextField titleField;
    private DrawPane editor;
    private Ribbon ribbon;
    private JButton newButton;
    private JButton subButton;
    private JButton pinButton;
    private JButton deleteButton;
    private JButton makeSubButton;
    private JButton makeMainButton;
    private JLabel statusLabel;
    private JLabel appNameLabel;
    private JLabel loginTitle;
    private JPanel rightCards;
    private CardLayout rightLayout;
    private JScrollPane editorScroll;
    private JPanel leftPanel;
    private JPanel crumbPanel;
    private JTextField listFilter;

    // Things that change colour when you pick another theme
    private final List<JLabel> darkLabels = new ArrayList<>();
    private final List<JLabel> primaryLabels = new ArrayList<>();
    private final List<JTextComponent> styledFields = new ArrayList<>();
    private final List<Runnable> iconRefreshers = new ArrayList<>();

    // Notes and saving
    private NoteManager manager;
    private List<Note> allNotes = new ArrayList<>();
    private Note current;               // the note being edited right now
    private boolean dirty = false;      // true when there are changes not saved yet
    private boolean loading = false;    // true while a note is being put on the screen
    private boolean selectionLock = false;
    private Timer saveTimer;            // saves a moment after you stop typing
    private final UndoManager undo = new UndoManager();
    private final Set<String> collapsed = new HashSet<>();                 // notes whose sub notes are hidden
    private final Map<String, NoteTree.Row> rowInfo = new HashMap<>();     // how each note sits in the list
    private final List<int[]> matches = new ArrayList<>();                 // words found on the page
    private final List<Object> findTags = new ArrayList<>();
    private int matchIndex = -1;
    private boolean exportWarned = false;

    // Auto-lock
    private static final int AUTO_LOCK_SECONDS = 120; // 2 minutes of no activity
    private Timer idleTimer;

    // Colours used across the app
    private static final Color DARK_BLUE = new Color(33, 64, 120);
    private static final Color SOFT_BACKGROUND = new Color(244, 247, 251);
    private static final Color GREY_TEXT = new Color(110, 118, 130);

    public static void main(String[] args) {
        Theme.load();   // the colours and character you picked last time
        // Use the modern "Nimbus" look if this computer has it
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
            // If it fails, the normal look is used
        }
        SwingUtilities.invokeLater(() -> new SecureNotesApp().start());
    }

    // Creates the window and adds both screens to it
    private void start() {
        frame = new JFrame("SecureNotes");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveCurrentNow(); // do not lose the last bit of typing
            }
        });
        cards = new CardLayout();
        mainPanel = new JPanel(cards);
        mainPanel.add(buildLoginPanel(), "login");
        mainPanel.add(buildNotesPanel(), "notes");
        frame.add(mainPanel);
        // The inside of the window is 1200 x 675, which is a 16:9 shape (good for screenshots)
        frame.getContentPane().setPreferredSize(new Dimension(1200, 675));
        frame.pack();
        frame.setMinimumSize(new Dimension(1000, 600));
        frame.setLocationRelativeTo(null);
        applyTheme();
        setupAutoLock();
        frame.setVisible(true);
    }

    // Watches for mouse or keyboard use. If nothing happens for a while, the app locks itself.
    private void setupAutoLock() {
        idleTimer = new Timer(AUTO_LOCK_SECONDS * 1000, e -> {
            if (manager != null) {
                lockApp();
                messageLabel.setText("Locked because you were away. Enter your PIN:");
            }
        });
        idleTimer.setRepeats(false);

        // Every mouse move, click or key press restarts the countdown
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (manager != null) {
                idleTimer.restart();
            }
        }, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    // ---------- LOGIN SCREEN ----------

    private JPanel buildLoginPanel() {
        Kit.GradientPanel root = new Kit.GradientPanel(new GridBagLayout());
        GridBagConstraints rc = new GridBagConstraints();
        rc.insets = new Insets(0, 8, 0, 8);

        // left: the character
        rc.gridx = 0;
        root.add(new Mascot.MascotPanel(330, true, null), rc);

        // right: a card with the form
        Kit.RoundedPanel card = new Kit.RoundedPanel(new GridBagLayout(), 36);
        card.setBorder(BorderFactory.createEmptyBorder(20, 34, 22, 38));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.insets = new Insets(4, 0, 4, 0);

        loginTitle = new JLabel("SecureNotes", Icons.lock(34, Theme.current().primary), JLabel.LEFT);
        loginTitle.setIconTextGap(10);
        loginTitle.setFont(new Font("SansSerif", Font.BOLD, 34));
        darkLabels.add(loginTitle);
        card.add(loginTitle, c);

        JLabel subtitle = new JLabel("Your private notes, locked with your PIN");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitle.setForeground(GREY_TEXT);
        c.insets = new Insets(0, 0, 14, 0);
        card.add(subtitle, c);

        messageLabel = new JLabel();
        messageLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        primaryLabels.add(messageLabel);
        c.insets = new Insets(4, 0, 4, 0);
        card.add(messageLabel, c);

        pinField = new JPasswordField(13);
        pinField.setFont(new Font("SansSerif", Font.PLAIN, 22));
        pinField.setHorizontalAlignment(JTextField.CENTER);
        pinField.addActionListener(e -> handleLogin()); // pressing Enter works too
        styledFields.add(pinField);
        card.add(pinField, c);

        // These three only show the very first time, when you create your PIN
        confirmLabel = new JLabel("Type the PIN again:");
        confirmLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        card.add(confirmLabel, c);

        confirmField = new JPasswordField(13);
        confirmField.setFont(new Font("SansSerif", Font.PLAIN, 22));
        confirmField.setHorizontalAlignment(JTextField.CENTER);
        confirmField.addActionListener(e -> handleLogin());
        styledFields.add(confirmField);
        card.add(confirmField, c);

        emailLabel = new JLabel("Recovery email (used if you forget the PIN):");
        emailLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        card.add(emailLabel, c);

        emailField = new JTextField(20);
        emailField.setFont(new Font("SansSerif", Font.PLAIN, 15));
        emailField.setHorizontalAlignment(JTextField.CENTER);
        emailField.addActionListener(e -> handleLogin());
        styledFields.add(emailField);
        card.add(emailField, c);

        Kit.ColorButton login = new Kit.ColorButton("Unlock", Icons.lock(16, Color.WHITE), Kit.Style.PRIMARY);
        login.setFont(new Font("SansSerif", Font.BOLD, 15));
        login.setPreferredSize(new Dimension(190, 42));
        login.addActionListener(e -> handleLogin());
        loginButton = login;
        c.insets = new Insets(10, 0, 2, 0);
        card.add(loginButton, c);

        forgotButton = new Kit.LinkButton("Forgot PIN?");
        forgotButton.addActionListener(e -> startForgotPin());
        c.insets = new Insets(0, 0, 0, 0);
        card.add(forgotButton, c);

        Kit.LinkButton changeLook = new Kit.LinkButton("Change colours and character");
        changeLook.setFont(changeLook.getFont().deriveFont(12f));
        changeLook.addActionListener(e -> showThemeDialog());
        card.add(changeLook, c);

        for (JTextComponent field : styledFields) {
            styleField(field);
        }
        rc.gridx = 1;
        root.add(card, rc);

        updateLoginMessage();
        return root;
    }

    // Rounded, colourful edge for a text box
    private void styleField(JTextComponent field) {
        Theme th = Theme.current();
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.alpha(th.primary, 170), 2, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
    }

    // Shows the right fields: "create a PIN" the first time, "enter your PIN" after that
    private void updateLoginMessage() {
        boolean firstTime = !PinManager.pinExists();
        messageLabel.setText(firstTime ? "First time here! Create a PIN:" : "Enter your PIN:");
        confirmLabel.setVisible(firstTime);
        confirmField.setVisible(firstTime);
        emailLabel.setVisible(firstTime);
        emailField.setVisible(firstTime);
        forgotButton.setVisible(!firstTime);
        loginButton.setText(firstTime ? "Create PIN" : "Unlock");
        if (loginButton instanceof Kit.ColorButton) {
            ((Kit.ColorButton) loginButton).setPicture(firstTime ? Icons.star(16, Color.WHITE) : Icons.lock(16, Color.WHITE));
        }
    }

    private void handleLogin() {
        String pin = new String(pinField.getPassword());
        String again = new String(confirmField.getPassword());
        String email = emailField.getText().trim();
        pinField.setText("");
        confirmField.setText("");
        if (pin.isEmpty()) {
            messageLabel.setText("Please type a PIN.");
            return;
        }
        try {
            if (!PinManager.pinExists()) {
                if (pin.length() < 4) {
                    messageLabel.setText("Use at least 4 characters for your PIN.");
                    return;
                }
                if (!pin.equals(again)) {
                    messageLabel.setText("The two PINs do not match. Try again.");
                    return;
                }
                if (!email.isEmpty() && !RecoveryManager.looksLikeEmail(email)) {
                    messageLabel.setText("That email address does not look right.");
                    return;
                }
                PinManager.setupPin(pin);
                if (!email.isEmpty()) {
                    RecoveryManager.saveEmail(email);
                }
                emailField.setText("");
                openNotes(pin);
                return;
            }
            if (PinManager.checkPin(pin)) {
                attempts = 0;
                openNotes(pin);
            } else {
                attempts++;
                int left = PinManager.getMaxAttempts() - attempts;
                if (left <= 0) {
                    startLockout();
                } else {
                    messageLabel.setText("Wrong PIN. Attempts left: " + left);
                }
            }
        } catch (Exception ex) {
            messageLabel.setText("Something went wrong: " + ex.getMessage());
        }
    }

    // After too many wrong tries, the login is blocked for 30 seconds
    private void startLockout() {
        loginButton.setEnabled(false);
        pinField.setEnabled(false);
        messageLabel.setText("Too many wrong tries. Wait 30 seconds.");
        final int[] secondsLeft = {30};
        Timer timer = new Timer(1000, null);
        timer.addActionListener(e -> {
            secondsLeft[0]--;
            if (secondsLeft[0] <= 0) {
                timer.stop();
                attempts = 0;
                loginButton.setEnabled(true);
                pinField.setEnabled(true);
                updateLoginMessage();
            } else {
                messageLabel.setText("Too many wrong tries. Wait " + secondsLeft[0] + " seconds.");
            }
        });
        timer.start();
    }

    // ---------- FORGOT PIN ----------

    private void startForgotPin() {
        String email = RecoveryManager.getEmail();
        if (email == null) {
            JOptionPane.showMessageDialog(frame,
                    "No recovery email was saved for this app,\nso the PIN cannot be reset by email.\n\n"
                            + "(You can add one after logging in, with the \"Recovery email\" button.)",
                    "Forgot PIN", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (!EmailSender.isConfigured()) {
            showEmailSetupHelp(frame);
            return;
        }
        int wait = RecoveryManager.secondsUntilNextCode();
        if (wait > 0) {
            JOptionPane.showMessageDialog(frame, "Please wait " + wait + " more seconds before asking for another code.",
                    "Forgot PIN", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String masked = RecoveryManager.mask(email);
        int ok = JOptionPane.showConfirmDialog(frame,
                "A 6-digit code will be sent to " + masked + ".\n\n"
                        + "WARNING: your notes are locked with your PIN, so a forgotten PIN cannot be recovered.\n"
                        + "Resetting the PIN will PERMANENTLY DELETE all your notes.\n\nContinue?",
                "Forgot PIN", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }
        final String code;
        try {
            code = RecoveryManager.newCode();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Something went wrong: " + ex.getMessage());
            return;
        }
        forgotButton.setEnabled(false);
        messageLabel.setText("Sending the code...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                EmailSender.sendCode(email, code);   // runs in the background so the window does not freeze
                return null;
            }

            @Override
            protected void done() {
                forgotButton.setEnabled(true);
                updateLoginMessage();
                try {
                    get();
                } catch (Exception ex) {
                    RecoveryManager.clear();
                    JOptionPane.showMessageDialog(frame, "The code could not be sent.\n\n" + rootMessage(ex),
                            "Forgot PIN", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                RecoveryManager.markSent();
                askForCode(masked);
            }
        }.execute();
    }

    // Asks for the 6-digit code from the email
    private void askForCode(String masked) {
        while (true) {
            String entered = JOptionPane.showInputDialog(frame,
                    "Enter the 6-digit code sent to " + masked + ":", "Forgot PIN", JOptionPane.QUESTION_MESSAGE);
            if (entered == null) {              // Cancel
                RecoveryManager.clear();
                return;
            }
            RecoveryManager.Result result = RecoveryManager.check(entered.trim());
            if (result == RecoveryManager.Result.OK) {
                break;
            }
            if (result == RecoveryManager.Result.WRONG) {
                JOptionPane.showMessageDialog(frame,
                        "That code is not correct. Tries left: " + RecoveryManager.triesLeft(),
                        "Forgot PIN", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            String why = (result == RecoveryManager.Result.EXPIRED)
                    ? "This code has expired. Please start again."
                    : "Too many wrong codes. Please start again.";
            JOptionPane.showMessageDialog(frame, why, "Forgot PIN", JOptionPane.WARNING_MESSAGE);
            return;
        }
        chooseNewPin();
    }

    // The email is verified: choose a new PIN. The old notes are erased.
    private void chooseNewPin() {
        JPasswordField first = new JPasswordField(16);
        JPasswordField second = new JPasswordField(16);
        JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
        form.add(new JLabel("Email verified. Choose a new PIN (at least 4 characters):"));
        form.add(first);
        form.add(new JLabel("Type it again:"));
        form.add(second);
        while (true) {
            first.setText("");
            second.setText("");
            int choice = JOptionPane.showConfirmDialog(frame, form, "New PIN",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return;                          // cancelled: nothing was changed
            }
            String a = new String(first.getPassword());
            String b = new String(second.getPassword());
            if (a.length() < 4) {
                JOptionPane.showMessageDialog(frame, "Use at least 4 characters.");
                continue;
            }
            if (!a.equals(b)) {
                JOptionPane.showMessageDialog(frame, "The two PINs do not match.");
                continue;
            }
            try {
                NoteManager.eraseAllNotes();
                PinManager.setupPin(a);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Could not save the new PIN: " + ex.getMessage());
                return;
            }
            RecoveryManager.clear();
            attempts = 0;
            loginButton.setEnabled(true);
            pinField.setEnabled(true);
            updateLoginMessage();
            messageLabel.setText("New PIN saved. Old notes were erased. Enter your PIN:");
            return;
        }
    }

    private static String rootMessage(Exception ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }

    private void showEmailSetupHelp(Component parent) {
        try {
            EmailSender.createTemplateIfMissing();
        } catch (Exception ignored) {
            // the message below still explains what to do
        }
        JOptionPane.showMessageDialog(parent,
                "To send the code, the app needs a Gmail account that can send email for it.\n\n"
                        + "1. Open this file in IntelliJ (or Notepad):\n    " + EmailSender.configPath() + "\n"
                        + "2. After username= type your Gmail address.\n"
                        + "3. In your Google account, turn on 2-Step Verification, then make an\n"
                        + "    \"App password\" and type it after password=\n"
                        + "4. Save the file and try again.\n\n"
                        + "Keep that file private. Never upload it to GitHub.",
                "Email setup needed", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---------- RECOVERY EMAIL WINDOW (when you are logged in) ----------

    private void showRecoveryDialog() {
        if (manager == null) {
            return;
        }
        JDialog dialog = new JDialog(frame, "Recovery email", true);
        dialog.setContentPane(buildRecoveryForm(dialog));
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private JPanel buildRecoveryForm(JDialog dialog) {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        form.setBackground(Theme.lighter(Theme.current().bg1, 0.4));

        JLabel heading = new JLabel("Recovery email");
        heading.setFont(new Font("SansSerif", Font.BOLD, 18));
        heading.setForeground(Theme.current().dark);

        JLabel explain = new JLabel("<html>If you forget your PIN, a 6-digit code is sent to this address.<br>"
                + "Resetting a forgotten PIN erases all notes, because they are locked with the PIN.</html>");
        explain.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JLabel currentLabel = new JLabel();
        currentLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        JTextField emailInput = new JTextField(26);
        emailInput.setFont(new Font("SansSerif", Font.PLAIN, 15));
        emailInput.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JLabel result = new JLabel(" ");
        result.setFont(new Font("SansSerif", Font.PLAIN, 13));

        Runnable refresh = () -> {
            String email = RecoveryManager.getEmail();
            currentLabel.setText(email == null ? "No recovery email saved yet." : "Saved: " + email);
        };
        refresh.run();

        Kit.ColorButton save = new Kit.ColorButton("Save email", Icons.check(16, Color.WHITE), Kit.Style.PRIMARY);
        Kit.ColorButton test = new Kit.ColorButton("Send test email", Icons.mail(16, Theme.current().primary), Kit.Style.SOFT);
        Kit.ColorButton close = new Kit.ColorButton("Close", null, Kit.Style.SOFT);

        save.addActionListener(e -> {
            String email = emailInput.getText().trim();
            if (!RecoveryManager.looksLikeEmail(email)) {
                showResult(result, "That email address does not look right.", true);
                return;
            }
            try {
                RecoveryManager.saveEmail(email);
            } catch (Exception ex) {
                showResult(result, "Could not save: " + ex.getMessage(), true);
                return;
            }
            emailInput.setText("");
            refresh.run();
            showResult(result, "Saved.", false);
        });

        test.addActionListener(e -> {
            String email = RecoveryManager.getEmail();
            if (email == null) {
                showResult(result, "Save an email address first.", true);
                return;
            }
            if (!EmailSender.isConfigured()) {
                showEmailSetupHelp(dialog);
                return;
            }
            test.setEnabled(false);
            showResult(result, "Sending...", false);
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    EmailSender.sendTest(email);
                    return null;
                }

                @Override
                protected void done() {
                    test.setEnabled(true);
                    try {
                        get();
                        showResult(result, "Test email sent to " + email + ". Check your inbox (and spam).", false);
                    } catch (Exception ex) {
                        showResult(result, "Could not send: " + rootMessage(ex), true);
                    }
                }
            }.execute();
        });

        close.addActionListener(e -> {
            if (dialog != null) {
                dialog.dispose();
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(save);
        buttons.add(test);
        buttons.add(close);

        for (JComponent part : new JComponent[]{heading, explain, currentLabel, emailInput, buttons, result}) {
            part.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        form.add(heading);
        form.add(Box.createVerticalStrut(6));
        form.add(explain);
        form.add(Box.createVerticalStrut(12));
        form.add(currentLabel);
        form.add(Box.createVerticalStrut(6));
        form.add(emailInput);
        form.add(Box.createVerticalStrut(10));
        form.add(buttons);
        form.add(Box.createVerticalStrut(10));
        form.add(result);
        return form;
    }

    // Shows a message in green, or in red if it is a problem. Line breaks become real line breaks.
    private void showResult(JLabel label, String message, boolean problem) {
        String safe = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
        label.setForeground(problem ? new Color(180, 30, 30) : new Color(20, 120, 50));
        label.setText("<html><div style='width:360px'>" + safe + "</div></html>");
        Window window = SwingUtilities.getWindowAncestor(label);
        if (window != null) {
            window.pack();
        }
    }

    // ---------- NOTES SCREEN ----------

    private JPanel buildNotesPanel() {
        Kit.GradientPanel root = new Kit.GradientPanel(new BorderLayout(10, 6));
        root.setBorder(BorderFactory.createEmptyBorder(8, 14, 12, 14));

        createEditor();                                    // the writing page comes first

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setOpaque(false);
        top.add(buildHeader(), BorderLayout.NORTH);
        ribbon = new Ribbon(this);
        top.add(ribbon.panel, BorderLayout.CENTER);
        root.add(top, BorderLayout.NORTH);

        leftPanel = buildLeftPanel();
        root.add(leftPanel, BorderLayout.WEST);
        root.add(buildRightSide(), BorderLayout.CENTER);

        // Saves about one second after you stop typing
        saveTimer = new Timer(900, e -> saveCurrentNow());
        saveTimer.setRepeats(false);
        TableBox.changeListener = this::markDirty;

        applyTheme();
        setEditorEnabled(false);
        return root;
    }

    // ----- the top bar: character, name, status and buttons -----

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        brand.setOpaque(false);
        brand.add(new Mascot.MascotPanel(54, true, null));
        JPanel names = new JPanel();
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        appNameLabel = new JLabel("SecureNotes");
        appNameLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        darkLabels.add(appNameLabel);
        JLabel tagline = new JLabel("Your private, locked notebook");
        tagline.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tagline.setForeground(GREY_TEXT);
        names.add(Box.createVerticalStrut(6));
        names.add(appNameLabel);
        names.add(tagline);
        brand.add(names);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        statusLabel.setForeground(GREY_TEXT);
        Kit.ColorButton themeButton = new Kit.ColorButton("Look", null, Kit.Style.SOFT);
        iconRefreshers.add(() -> themeButton.setPicture(Icons.palette(16, Theme.current().primary)));
        themeButton.addActionListener(e -> showThemeDialog());
        Kit.ColorButton recoveryButton = new Kit.ColorButton("Recovery email", null, Kit.Style.SOFT);
        iconRefreshers.add(() -> recoveryButton.setPicture(Icons.mail(16, Theme.current().primary)));
        recoveryButton.addActionListener(e -> showRecoveryDialog());
        Kit.ColorButton lockButton = new Kit.ColorButton("Lock app", Icons.lock(16, Color.WHITE), Kit.Style.PRIMARY);
        lockButton.addActionListener(e -> lockApp());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        right.setOpaque(false);
        right.add(statusLabel);
        right.add(themeButton);
        right.add(recoveryButton);
        right.add(lockButton);
        header.add(brand, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    // ----- the left side: the list of notes (with sub notes under their note) -----

    private JPanel buildLeftPanel() {
        newButton = new Kit.ColorButton("New note", Icons.plus(16, Color.WHITE), Kit.Style.ACCENT);
        newButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        newButton.addActionListener(e -> newNote());
        subButton = new Kit.ColorButton("Sub note", null, Kit.Style.SOFT);
        iconRefreshers.add(() -> ((Kit.ColorButton) subButton).setPicture(Icons.subNote(16, Theme.current().primary)));
        subButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        subButton.addActionListener(e -> newSubNote());
        JPanel newRow = new JPanel(new GridLayout(1, 2, 8, 0));
        newRow.setOpaque(false);
        newRow.add(newButton);
        newRow.add(subButton);
        newRow.setPreferredSize(new Dimension(0, 40));

        listFilter = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(new Color(150, 150, 160));
                    g2.setFont(getFont().deriveFont(Font.ITALIC));
                    g2.drawString("Search my notes...", 12, (getHeight() + g2.getFontMetrics().getAscent()) / 2 - 2);
                    g2.dispose();
                }
            }
        };
        listFilter.setFont(new Font("SansSerif", Font.PLAIN, 14));
        listFilter.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refreshList(null); }
            public void removeUpdate(DocumentEvent e) { refreshList(null); }
            public void changedUpdate(DocumentEvent e) { refreshList(null); }
        });
        styledFields.add(listFilter);

        listModel = new DefaultListModel<>();
        notesList = new JList<>(listModel);
        notesList.setCellRenderer(new NoteCard(note -> rowInfo.get(note.id)));
        notesList.setOpaque(false);
        notesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        notesList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || selectionLock) {
                return;
            }
            Note target = notesList.getSelectedValue();
            saveCurrentNow();       // save the note we are leaving
            showNote(target);
        });
        notesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // a click on the little arrow next to a note opens or closes its sub notes
                int index = notesList.locationToIndex(e.getPoint());
                if (index < 0) {
                    return;
                }
                Rectangle cell = notesList.getCellBounds(index, index);
                if (cell == null || !cell.contains(e.getPoint())) {
                    return;
                }
                Note n = listModel.get(index);
                NoteTree.Row row = rowInfo.get(n.id);
                if (row != null && row.hasChildren) {
                    int arrowStart = cell.x + 3 + row.depth * NoteCard.INDENT;
                    if (e.getX() >= arrowStart && e.getX() <= arrowStart + 24) {
                        if (!collapsed.remove(n.id)) {
                            collapsed.add(n.id);
                        }
                        refreshList(null);
                        e.consume();
                    }
                }
            }
        });
        JScrollPane listScroll = new JScrollPane(notesList);
        listScroll.setOpaque(false);
        listScroll.getViewport().setOpaque(false);
        listScroll.setBorder(BorderFactory.createEmptyBorder());
        listScroll.setViewportBorder(null);

        JLabel listTitle = new JLabel("My notes");
        listTitle.setFont(new Font("SansSerif", Font.BOLD, 15));
        listTitle.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
        darkLabels.add(listTitle);
        JPanel listHead = new JPanel(new BorderLayout(0, 6));
        listHead.setOpaque(false);
        listHead.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        listHead.add(listTitle, BorderLayout.NORTH);
        listHead.add(listFilter, BorderLayout.CENTER);
        Kit.RoundedPanel listCard = new Kit.RoundedPanel(new BorderLayout(), 26);
        listCard.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));
        listCard.add(listHead, BorderLayout.NORTH);
        listCard.add(listScroll, BorderLayout.CENTER);

        pinButton = new Kit.ColorButton("Pin note", null, Kit.Style.SOFT);
        iconRefreshers.add(() -> ((Kit.ColorButton) pinButton).setPicture(Icons.pin(16, new Color(230, 90, 40))));
        deleteButton = new Kit.ColorButton("Delete", Icons.trash(16, Color.WHITE), Kit.Style.DANGER);
        pinButton.addActionListener(e -> togglePin());
        deleteButton.addActionListener(e -> deleteCurrent());
        JPanel row1 = new JPanel(new GridLayout(1, 2, 8, 0));
        row1.setOpaque(false);
        row1.add(pinButton);
        row1.add(deleteButton);

        makeSubButton = new Kit.ColorButton("Make sub note", null, Kit.Style.SOFT);
        makeMainButton = new Kit.ColorButton("Make main note", null, Kit.Style.SOFT);
        makeSubButton.setFont(new Font("SansSerif", Font.BOLD, 11));
        makeMainButton.setFont(new Font("SansSerif", Font.BOLD, 11));
        makeSubButton.setToolTipText("Puts this note under the note above it");
        makeMainButton.setToolTipText("Takes this note out of its main note");
        makeSubButton.addActionListener(e -> makeSubNote());
        makeMainButton.addActionListener(e -> makeMainNote());
        JPanel row2 = new JPanel(new GridLayout(1, 2, 8, 0));
        row2.setOpaque(false);
        row2.add(makeSubButton);
        row2.add(makeMainButton);

        JPanel bottom = new JPanel(new GridLayout(2, 1, 0, 6));
        bottom.setOpaque(false);
        bottom.add(row1);
        bottom.add(row2);

        JPanel left = new JPanel(new BorderLayout(0, 8));
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(296, 0));
        left.add(newRow, BorderLayout.NORTH);
        left.add(listCard, BorderLayout.CENTER);
        left.add(bottom, BorderLayout.SOUTH);
        return left;
    }

    // ----- the right side: the writing page, or a welcome page when no note is open -----

    private void createEditor() {
        editor = new DrawPane();
        RichText.setupDefaultStyle(editor.getStyledDocument());
        editor.setMargin(new Insets(10, 12, 10, 12));
        editor.getCaret().setSelectionVisible(true);
        editor.setSelectionColor(new Color(166, 200, 255));      // soft blue, so the text stays easy to read
        editor.setSelectedTextColor(Color.BLACK);
        editor.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { markDirty(); }
            public void removeUpdate(DocumentEvent e) { markDirty(); }
            public void changedUpdate(DocumentEvent e) { markDirty(); }
        });
        undo.setLimit(500);
        editor.getDocument().addUndoableEditListener(e -> {
            if (!loading) {
                undo.addEdit(e.getEdit());
            }
        });
        editor.setOnChange(this::markDirty);
        // Swing tells our code about cursor moves before it updates the "typing style",
        // so we wait a moment before reading it
        editor.addCaretListener(e -> SwingUtilities.invokeLater(this::syncToolbar));
        addShortcut("boldKey", KeyEvent.VK_B, () -> ribbon.bold.doClick());
        addShortcut("italicKey", KeyEvent.VK_I, () -> ribbon.italic.doClick());
        addShortcut("underlineKey", KeyEvent.VK_U, () -> ribbon.underline.doClick());
        addShortcut("undoKey", KeyEvent.VK_Z, this::undoEdit);
        addShortcut("redoKey", KeyEvent.VK_Y, this::redoEdit);
        addShortcut("findKey", KeyEvent.VK_F, () -> {
            ribbon.showTab("Search");
            ribbon.findField.requestFocusInWindow();
            ribbon.findField.selectAll();
        });
    }

    private JPanel buildRightSide() {
        titleField = new JTextField();
        titleField.setFont(new Font("SansSerif", Font.BOLD, 24));
        titleField.setToolTipText("Note title");
        titleField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { titleChanged(); }
            public void removeUpdate(DocumentEvent e) { titleChanged(); }
            public void changedUpdate(DocumentEvent e) { titleChanged(); }
        });
        styledFields.add(titleField);

        editorScroll = new JScrollPane(editor);
        crumbPanel = new JPanel();
        crumbPanel.setOpaque(false);
        crumbPanel.setLayout(new BoxLayout(crumbPanel, BoxLayout.Y_AXIS));

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setOpaque(false);
        top.add(crumbPanel, BorderLayout.NORTH);
        top.add(titleField, BorderLayout.CENTER);

        Kit.RoundedPanel editorCard = new Kit.RoundedPanel(new BorderLayout(0, 8), 26);
        editorCard.setBorder(BorderFactory.createEmptyBorder(10, 14, 14, 16));
        editorCard.add(top, BorderLayout.NORTH);
        editorCard.add(editorScroll, BorderLayout.CENTER);

        Kit.RoundedPanel welcome = new Kit.RoundedPanel(new GridBagLayout(), 26);
        GridBagConstraints wc = new GridBagConstraints();
        wc.gridx = 0;
        wc.insets = new Insets(2, 0, 2, 0);
        welcome.add(new Mascot.MascotPanel(220, true, null), wc);
        JLabel hello = new JLabel("Welcome to your notebook!");
        hello.setFont(new Font("SansSerif", Font.BOLD, 24));
        darkLabels.add(hello);
        welcome.add(hello, wc);
        JLabel hint = new JLabel("Press  \"New note\"  to start writing, or pick a note from the list.");
        hint.setFont(new Font("SansSerif", Font.PLAIN, 14));
        hint.setForeground(GREY_TEXT);
        welcome.add(hint, wc);

        rightLayout = new CardLayout();
        rightCards = new JPanel(rightLayout);
        rightCards.setOpaque(false);
        rightCards.add(welcome, "empty");
        rightCards.add(editorCard, "editor");
        return rightCards;
    }

    // The line above the title: where this note sits, and the sub notes inside it
    private void updateCrumbs() {
        crumbPanel.removeAll();
        if (current != null) {
            List<Note> live = NoteTree.live(allNotes);
            List<Note> chain = new ArrayList<>();
            Note p = NoteTree.parentOf(current, live);
            int guard = 0;
            while (p != null && guard++ < 20) {
                chain.add(0, p);
                p = NoteTree.parentOf(p, live);
            }
            if (!chain.isEmpty()) {
                JPanel crumbs = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                crumbs.setOpaque(false);
                JLabel in = new JLabel("In:");
                in.setFont(new Font("SansSerif", Font.PLAIN, 12));
                in.setForeground(GREY_TEXT);
                crumbs.add(in);
                for (Note ancestor : chain) {
                    Kit.LinkButton link = new Kit.LinkButton(shortTitle(ancestor, 26));
                    link.setFont(link.getFont().deriveFont(12f));
                    link.addActionListener(e -> openNote(ancestor));
                    crumbs.add(link);
                    JLabel sep = new JLabel(">");
                    sep.setForeground(GREY_TEXT);
                    crumbs.add(sep);
                }
                crumbs.setAlignmentX(Component.LEFT_ALIGNMENT);
                crumbPanel.add(crumbs);
            }
            JPanel subs = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            subs.setOpaque(false);
            List<Note> kids = NoteTree.childrenOf(current, live);
            JLabel label = new JLabel(kids.isEmpty() ? "No sub notes yet" : "Sub notes:");
            label.setFont(new Font("SansSerif", Font.BOLD, 12));
            label.setForeground(GREY_TEXT);
            subs.add(label);
            int shown = 0;
            for (Note kid : kids) {
                if (shown++ >= 6) {
                    JLabel more = new JLabel("+" + (kids.size() - 6) + " more (see the list)");
                    more.setFont(new Font("SansSerif", Font.PLAIN, 12));
                    more.setForeground(GREY_TEXT);
                    subs.add(more);
                    break;
                }
                Kit.ColorButton chip = new Kit.ColorButton(shortTitle(kid, 18), null, Kit.Style.SOFT);
                chip.setFont(new Font("SansSerif", Font.BOLD, 12));
                chip.setPreferredSize(new Dimension(chip.getPreferredSize().width, 26));
                chip.addActionListener(e -> openNote(kid));
                subs.add(chip);
            }
            Kit.ColorButton add = new Kit.ColorButton("Add sub note", Icons.plus(14, Color.WHITE), Kit.Style.ACCENT);
            add.setFont(new Font("SansSerif", Font.BOLD, 12));
            add.setPreferredSize(new Dimension(add.getPreferredSize().width, 26));
            add.addActionListener(e -> newSubNote());
            subs.add(add);
            subs.setAlignmentX(Component.LEFT_ALIGNMENT);
            crumbPanel.add(subs);
        }
        crumbPanel.revalidate();
        crumbPanel.repaint();
    }

    private static String shortTitle(Note n, int max) {
        String t = n.title.trim().isEmpty() ? "Untitled note" : n.title.trim();
        return t.length() > max ? t.substring(0, max - 1) + "..." : t;
    }

    // A small box of colour squares that opens under a button
    JPopupMenu paletteMenu(Color[] colors, boolean withNone, Consumer<Color> onPick) {
        JPopupMenu menu = new JPopupMenu();
        JPanel grid = new JPanel(new GridLayout(0, 4, 4, 4));
        grid.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        for (Color color : colors) {
            JButton square = new JButton(new SwatchIcon(color));
            square.setPreferredSize(new Dimension(34, 30));
            square.setFocusable(false);
            square.addActionListener(e -> {
                menu.setVisible(false);
                onPick.accept(color);
            });
            grid.add(square);
        }
        menu.add(grid);
        if (withNone) {
            JMenuItem none = new JMenuItem("No highlight");
            none.addActionListener(e -> onPick.accept(null));
            menu.add(none);
        }
        return menu;
    }

    private void addShortcut(String name, int key, Runnable action) {
        editor.getInputMap().put(KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK), name);
        editor.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    // ---------- FORMATTING ----------

    boolean isLoading() {
        return loading;
    }

    // The look of the selected text (or of the text you are about to type)
    AttributeSet currentAttributes() {
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start != end) {
            return editor.getStyledDocument().getCharacterElement(start).getAttributes();
        }
        return editor.getInputAttributes();
    }

    // Applies a look to the selected text, and to whatever you type next
    void applyAttributes(AttributeSet attrs) {
        if (current == null) {
            return;
        }
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start != end) {
            editor.getStyledDocument().setCharacterAttributes(start, end - start, attrs, false);
        }
        editor.getInputAttributes().addAttributes(attrs);
        editor.requestFocusInWindow();
    }

    private void syncToolbar() {
        if (ribbon != null && editor != null) {
            ribbon.sync(currentAttributes());
        }
    }

    private void setEditorEnabled(boolean on) {
        titleField.setEnabled(on);
        editor.setEnabled(on);
        ribbon.setNoteControlsEnabled(on);
        pinButton.setEnabled(on);
        deleteButton.setEnabled(on);
        subButton.setEnabled(on);
        makeSubButton.setEnabled(on);
        makeMainButton.setEnabled(on);
    }

    void undoEdit() {
        try {
            if (undo.canUndo()) {
                undo.undo();
            }
        } catch (javax.swing.undo.CannotUndoException ignored) {
            // nothing to undo
        }
    }

    void redoEdit() {
        try {
            if (undo.canRedo()) {
                undo.redo();
            }
        } catch (javax.swing.undo.CannotRedoException ignored) {
            // nothing to redo
        }
    }

    // Puts "• " at the start of each chosen line (or takes it away)
    void toggleBullets() {
        if (current == null) {
            return;
        }
        StyledDocument doc = editor.getStyledDocument();
        Element root = doc.getDefaultRootElement();
        int first = root.getElementIndex(editor.getSelectionStart());
        int last = root.getElementIndex(editor.getSelectionEnd());
        try {
            boolean allHave = true;
            for (int i = first; i <= last; i++) {
                int p = root.getElement(i).getStartOffset();
                int len = Math.min(2, doc.getLength() - p);
                if (len < 2 || !doc.getText(p, 2).equals("\u2022 ")) {
                    allHave = false;
                }
            }
            for (int i = last; i >= first; i--) {       // from the bottom, so the positions stay right
                int p = root.getElement(i).getStartOffset();
                if (allHave) {
                    doc.remove(p, 2);
                } else if (doc.getLength() - p < 2 || !doc.getText(p, 2).equals("\u2022 ")) {
                    doc.insertString(p, "\u2022 ", null);
                }
            }
        } catch (BadLocationException ignored) {
            // leave the text as it is
        }
        editor.requestFocusInWindow();
    }

    void setAlignment(int alignment) {
        if (current == null) {
            return;
        }
        StyledDocument doc = editor.getStyledDocument();
        Element root = doc.getDefaultRootElement();
        int start = root.getElement(root.getElementIndex(editor.getSelectionStart())).getStartOffset();
        int end = root.getElement(root.getElementIndex(editor.getSelectionEnd())).getEndOffset();
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setAlignment(a, alignment);
        doc.setParagraphAttributes(start, Math.max(1, end - start), a, false);
        editor.requestFocusInWindow();
    }

    // ---------- INSERT ----------

    // Puts one special item (picture, table, tick box...) at the cursor
    private void insertItem(AttributeSet item) {
        StyledDocument doc = editor.getStyledDocument();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        try {
            if (end > start) {
                doc.remove(start, end - start);
            }
            doc.insertString(start, " ", item);
            editor.setCaretPosition(start + 1);
        } catch (BadLocationException ignored) {
            // nothing inserted
        }
        editor.requestFocusInWindow();
    }

    void insertCheckbox() {
        if (current == null) {
            return;
        }
        StyledDocument doc = editor.getStyledDocument();
        Element root = doc.getDefaultRootElement();
        int p = root.getElement(root.getElementIndex(editor.getSelectionStart())).getStartOffset();
        try {
            doc.insertString(p, " ", RichText.checkAttributes(false));
            doc.insertString(p + 1, " ", null);
            editor.setCaretPosition(p + 2);
        } catch (BadLocationException ignored) {
            // nothing inserted
        }
        editor.requestFocusInWindow();
    }

    void insertPicture() {
        if (current == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a picture");
        chooser.setFileFilter(new FileNameExtensionFilter("Pictures (png, jpg, gif)", "png", "jpg", "jpeg", "gif", "bmp"));
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            BufferedImage img = ImageIO.read(chooser.getSelectedFile());
            if (img == null) {
                JOptionPane.showMessageDialog(frame, "That file is not a picture the app can open.", "Picture", JOptionPane.WARNING_MESSAGE);
                return;
            }
            insertPictureImage(img);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Could not open the picture: " + ex.getMessage(), "Picture", JOptionPane.WARNING_MESSAGE);
        }
    }

    // Shared by "Picture" and "Paste picture"
    void insertPictureImage(BufferedImage img) throws IOException {
        String data = RichText.encodePicture(img, 560);
        AttributeSet item = RichText.imageAttributes(data);
        if (item != null) {
            insertItem(item);
        }
    }

    void pastePicture() {
        if (current == null) {
            return;
        }
        try {
            Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t != null && t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image image = (Image) t.getTransferData(DataFlavor.imageFlavor);
                BufferedImage img = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                g.drawImage(image, 0, 0, null);
                g.dispose();
                insertPictureImage(img);
                return;
            }
        } catch (Exception ignored) {
            // falls through to the message below
        }
        JOptionPane.showMessageDialog(frame, "There is no picture on the clipboard.\n(Copy a picture first, then try again.)",
                "Paste picture", JOptionPane.INFORMATION_MESSAGE);
    }

    void insertTable() {
        if (current == null) {
            return;
        }
        JSpinner rows = new JSpinner(new SpinnerNumberModel(3, 1, 30, 1));
        JSpinner cols = new JSpinner(new SpinnerNumberModel(3, 1, 12, 1));
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Rows:"));
        form.add(rows);
        form.add(new JLabel("Columns:"));
        form.add(cols);
        int ok = JOptionPane.showConfirmDialog(frame, form, "Insert a table", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return;
        }
        insertItem(RichText.tableAttributes(TableBox.empty((Integer) rows.getValue(), (Integer) cols.getValue())));
        try {
            StyledDocument doc = editor.getStyledDocument();
            doc.insertString(editor.getCaretPosition(), "\n", null);
        } catch (BadLocationException ignored) {
            // fine
        }
    }

    void insertLine() {
        if (current == null) {
            return;
        }
        StyledDocument doc = editor.getStyledDocument();
        try {
            int pos = editor.getSelectionStart();
            Element root = doc.getDefaultRootElement();
            if (pos > root.getElement(root.getElementIndex(pos)).getStartOffset()) {
                doc.insertString(pos, "\n", null);
                pos++;
            }
            doc.insertString(pos, " ", RichText.lineAttributes());
            doc.insertString(pos + 1, "\n", null);
            editor.setCaretPosition(pos + 2);
        } catch (BadLocationException ignored) {
            // nothing inserted
        }
        editor.requestFocusInWindow();
    }

    void insertDateTime() {
        if (current == null) {
            return;
        }
        editor.replaceSelection(new SimpleDateFormat("d MMM yyyy, h:mm a").format(new Date()));
        editor.requestFocusInWindow();
    }

    void showSymbolMenu(Component invoker) {
        if (current == null) {
            return;
        }
        String[] symbols = {"\u2192", "\u2190", "\u2191", "\u2193", "\u2022", "\u00B0", "\u00B1", "\u00D7",
                "\u00F7", "\u2248", "\u2260", "\u2264", "\u2265", "\u221E", "\u03C0", "\u03A9",
                "\u00B5", "\u221A", "\u00B2", "\u00B3", "\u00A9", "\u00AE", "\u2122", "\u20AC",
                "\u20B9", "\u2605", "\u2665", "\u2713"};
        JPopupMenu menu = new JPopupMenu();
        JPanel grid = new JPanel(new GridLayout(0, 7, 3, 3));
        grid.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        for (String symbol : symbols) {
            JButton b = new JButton(symbol);
            b.setFont(new Font("SansSerif", Font.PLAIN, 17));
            b.setPreferredSize(new Dimension(40, 34));
            b.setFocusable(false);
            b.addActionListener(e -> {
                menu.setVisible(false);
                editor.replaceSelection(symbol);
                editor.requestFocusInWindow();
            });
            grid.add(b);
        }
        menu.add(grid);
        menu.show(invoker, 280, invoker.getHeight() / 2);
    }

    // ---------- DRAW ----------

    void setDrawTool(DrawPane.Tool tool) {
        editor.setTool(tool);
        ribbon.setTool(tool);
        if (tool != DrawPane.Tool.TYPE) {
            editor.requestFocusInWindow();
        }
    }

    void setPenColor(Color color) {
        editor.setPenColor(color);
    }

    void setPenWidth(float width) {
        editor.setPenWidth(width);
    }

    void undoStroke() {
        editor.undoStroke();
    }

    void clearDrawing() {
        if (current == null || editor.strokes().isEmpty()) {
            return;
        }
        int sure = JOptionPane.showConfirmDialog(frame, "Remove everything you drew on this note?", "Clear drawing",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (sure == JOptionPane.YES_OPTION) {
            editor.clearStrokes();
        }
    }

    // ---------- SEARCH ON THE PAGE ----------

    private void removeFindMarks() {
        for (Object tag : findTags) {
            editor.getHighlighter().removeHighlight(tag);
        }
        findTags.clear();
    }

    void findTextChanged(String text) {
        if (editor == null || ribbon == null) {
            return;
        }
        removeFindMarks();
        matches.clear();
        matchIndex = -1;
        if (current == null || text.isEmpty()) {
            ribbon.findCount.setText(" ");
            return;
        }
        try {
            StyledDocument doc = editor.getStyledDocument();
            String all = doc.getText(0, doc.getLength()).toLowerCase();
            String word = text.toLowerCase();
            int from = 0;
            int at;
            while ((at = all.indexOf(word, from)) >= 0) {
                matches.add(new int[]{at, at + word.length()});
                from = at + word.length();
            }
            DefaultHighlighter.DefaultHighlightPainter painter =
                    new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 226, 120));
            for (int[] m : matches) {
                findTags.add(editor.getHighlighter().addHighlight(m[0], m[1], painter));
            }
        } catch (BadLocationException ignored) {
            // no marks
        }
        if (matches.isEmpty()) {
            ribbon.findCount.setText("No matches");
        } else {
            matchIndex = 0;
            goToMatch();
        }
    }

    private void goToMatch() {
        if (matchIndex < 0 || matchIndex >= matches.size()) {
            return;
        }
        int[] m = matches.get(matchIndex);
        editor.select(m[0], m[1]);
        try {
            java.awt.geom.Rectangle2D r = editor.modelToView2D(m[0]);
            if (r != null) {
                editor.scrollRectToVisible(r.getBounds());
            }
        } catch (BadLocationException ignored) {
            // stays where it is
        }
        ribbon.findCount.setText((matchIndex + 1) + " of " + matches.size());
    }

    void findNext() {
        if (!matches.isEmpty()) {
            matchIndex = (matchIndex + 1) % matches.size();
            goToMatch();
        }
    }

    void findPrevious() {
        if (!matches.isEmpty()) {
            matchIndex = (matchIndex - 1 + matches.size()) % matches.size();
            goToMatch();
        }
    }

    void clearFind() {
        ribbon.findField.setText("");
        removeFindMarks();
        matches.clear();
        ribbon.findCount.setText(" ");
    }

    // ---------- SEARCH IN ALL NOTES ----------

    void searchAllNotes() {
        if (manager == null) {
            return;
        }
        JDialog dialog = new JDialog(frame, "Search all notes", true);
        dialog.setContentPane(buildSearchAllForm(dialog));
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    // A piece of text around the first match, so you can see where the word is
    private static String snippet(Note n, String lowerWord) {
        String text = RichText.plainText(n.body).replace('\n', ' ');
        int at = text.toLowerCase().indexOf(lowerWord);
        if (at < 0) {
            return text.length() > 70 ? text.substring(0, 70) + "..." : text;
        }
        int from = Math.max(0, at - 25);
        int to = Math.min(text.length(), at + lowerWord.length() + 45);
        return (from > 0 ? "..." : "") + text.substring(from, to) + (to < text.length() ? "..." : "");
    }

    JPanel buildSearchAllForm(JDialog dialog) {
        Theme th = Theme.current();
        JPanel form = new JPanel(new BorderLayout(0, 8));
        form.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        form.setBackground(Theme.lighter(th.bg1, 0.4));

        JLabel heading = new JLabel("Search all notes");
        heading.setFont(new Font("SansSerif", Font.BOLD, 20));
        heading.setForeground(th.dark);
        JTextField field = new JTextField(30);
        field.setFont(new Font("SansSerif", Font.PLAIN, 16));
        styleField(field);
        JPanel head = new JPanel(new BorderLayout(0, 6));
        head.setOpaque(false);
        head.add(heading, BorderLayout.NORTH);
        head.add(field, BorderLayout.CENTER);

        DefaultListModel<Note> results = new DefaultListModel<>();
        JList<Note> list = new JList<>(results);
        list.setFont(new Font("SansSerif", Font.PLAIN, 13));
        list.setVisibleRowCount(8);
        final String[] lastWord = {""};
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus) {
                super.getListCellRendererComponent(l, "", index, sel, focus);
                Note n = (Note) value;
                String title = n.title.trim().isEmpty() ? "Untitled note" : n.title.trim();
                setText("<html><b>" + escape(title) + "</b><br><span style='font-size:10px'>"
                        + escape(snippet(n, lastWord[0])) + "</span></html>");
                setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
                if (sel) {
                    setBackground(Theme.current().primary);
                    setForeground(Color.WHITE);
                }
                return this;
            }
        });
        JLabel info = new JLabel(" ");
        info.setFont(new Font("SansSerif", Font.PLAIN, 12));
        Runnable refresh = () -> {
            results.clear();
            String word = field.getText().trim().toLowerCase();
            lastWord[0] = word;
            if (word.isEmpty()) {
                info.setText("Type a word to look for it in every note.");
                return;
            }
            for (NoteTree.Row r : NoteTree.visible(allNotes, new HashSet<>(), word)) {
                results.addElement(r.note);
            }
            info.setText(results.isEmpty() ? "Nothing found." : results.size() + " note(s) found. Double-click one to open it.");
        };
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh.run(); }
            public void removeUpdate(DocumentEvent e) { refresh.run(); }
            public void changedUpdate(DocumentEvent e) { refresh.run(); }
        });
        Runnable open = () -> {
            Note n = list.getSelectedValue();
            if (n != null) {
                String word = field.getText().trim();
                if (dialog != null) {
                    dialog.dispose();
                }
                openNote(n);
                ribbon.showTab("Search");
                ribbon.findField.setText(word);
            }
        };
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    open.run();
                }
            }
        });
        field.addActionListener(e -> {
            if (!results.isEmpty() && list.getSelectedValue() == null) {
                list.setSelectedIndex(0);
            }
            open.run();
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(520, 260));

        Kit.ColorButton openButton = new Kit.ColorButton("Open", null, Kit.Style.PRIMARY);
        openButton.addActionListener(e -> open.run());
        Kit.ColorButton close = new Kit.ColorButton("Close", null, Kit.Style.SOFT);
        close.addActionListener(e -> {
            if (dialog != null) {
                dialog.dispose();
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(openButton);
        buttons.add(close);
        buttons.add(info);

        form.add(head, BorderLayout.NORTH);
        form.add(scroll, BorderLayout.CENTER);
        form.add(buttons, BorderLayout.SOUTH);
        refresh.run();
        return form;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ---------- HISTORY: older versions ----------

    private static final long VERSION_GAP = 5 * 60 * 1000;     // a new version at most every 5 minutes
    private static final int MAX_VERSIONS = 12;
    private static final int MAX_AUTO_VERSION_SIZE = 400_000;  // very big notes only get versions on request

    // Keeps a copy of the note as it is now (so you can go back to it later)
    private void snapshot(Note n, boolean force) {
        if (n.body.isEmpty() && n.title.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Note.Version last = n.versions.isEmpty() ? null : n.versions.get(n.versions.size() - 1);
        if (last != null && last.body.equals(n.body) && last.title.equals(n.title)) {
            return;                                             // nothing new to keep
        }
        if (!force && last != null && now - last.time < VERSION_GAP) {
            return;
        }
        if (!force && n.body.length() > MAX_AUTO_VERSION_SIZE) {
            return;
        }
        n.versions.add(new Note.Version(now, n.title, n.body));
        while (n.versions.size() > MAX_VERSIONS) {
            n.versions.remove(0);
        }
    }

    void saveVersionNow() {
        if (current == null || manager == null) {
            return;
        }
        saveCurrentNow();
        snapshot(current, true);
        try {
            manager.updateNote(current);
            statusLabel.setText("Version saved");
        } catch (Exception ex) {
            statusLabel.setText("Could not save: " + ex.getMessage());
        }
    }

    void showVersions() {
        if (current == null) {
            return;
        }
        saveCurrentNow();
        JDialog dialog = new JDialog(frame, "Page versions", true);
        dialog.setContentPane(buildVersionsForm(dialog));
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    JPanel buildVersionsForm(JDialog dialog) {
        Theme th = Theme.current();
        JPanel form = new JPanel(new BorderLayout(10, 8));
        form.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        form.setBackground(Theme.lighter(th.bg1, 0.4));

        JLabel heading = new JLabel("Page versions");
        heading.setFont(new Font("SansSerif", Font.BOLD, 20));
        heading.setForeground(th.dark);
        JLabel sub = new JLabel("The app keeps up to " + MAX_VERSIONS + " older copies of each note. Pick one to look at it.");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(heading, BorderLayout.NORTH);
        head.add(sub, BorderLayout.CENTER);

        DefaultListModel<Note.Version> model = new DefaultListModel<>();
        if (current != null) {
            for (int i = current.versions.size() - 1; i >= 0; i--) {
                model.addElement(current.versions.get(i));
            }
        }
        JList<Note.Version> list = new JList<>(model);
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM yyyy, h:mm a");
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus) {
                super.getListCellRendererComponent(l, "", index, sel, focus);
                Note.Version v = (Note.Version) value;
                String title = v.title.trim().isEmpty() ? "Untitled note" : v.title.trim();
                setText("<html><b>" + escape(fmt.format(new Date(v.time))) + "</b><br><span style='font-size:10px'>"
                        + escape(title.length() > 30 ? title.substring(0, 30) + "..." : title) + "</span></html>");
                setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
                if (sel) {
                    setBackground(Theme.current().primary);
                    setForeground(Color.WHITE);
                }
                return this;
            }
        });
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(230, 320));

        DrawPane preview = new DrawPane();
        RichText.setupDefaultStyle(preview.getStyledDocument());
        preview.setEditable(false);
        preview.setMargin(new Insets(8, 10, 8, 10));
        JScrollPane previewScroll = new JScrollPane(preview);
        previewScroll.setPreferredSize(new Dimension(460, 320));
        list.addListSelectionListener(e -> {
            Note.Version v = list.getSelectedValue();
            if (v != null && !e.getValueIsAdjusting()) {
                List<DrawPane.Stroke> strokes = new ArrayList<>();
                RichText.loadBody(preview.getStyledDocument(), v.body, current.assets, strokes);
                preview.setStrokes(strokes);
                preview.setCaretPosition(0);
            }
        });

        Kit.ColorButton restore = new Kit.ColorButton("Restore this version", Icons.undo(16, Color.WHITE), Kit.Style.PRIMARY);
        restore.addActionListener(e -> {
            Note.Version v = list.getSelectedValue();
            if (v == null) {
                return;
            }
            if (dialog != null) {
                dialog.dispose();
            }
            restoreVersion(v);
        });
        Kit.ColorButton close = new Kit.ColorButton("Close", null, Kit.Style.SOFT);
        close.addActionListener(e -> {
            if (dialog != null) {
                dialog.dispose();
            }
        });
        JLabel empty = new JLabel(model.isEmpty() ? "No older versions yet. They appear as you keep writing." : " ");
        empty.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(restore);
        buttons.add(close);
        buttons.add(empty);

        JPanel middle = new JPanel(new BorderLayout(10, 0));
        middle.setOpaque(false);
        middle.add(listScroll, BorderLayout.WEST);
        middle.add(previewScroll, BorderLayout.CENTER);
        form.add(head, BorderLayout.NORTH);
        form.add(middle, BorderLayout.CENTER);
        form.add(buttons, BorderLayout.SOUTH);
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
        return form;
    }

    void restoreVersion(Note.Version v) {
        if (current == null) {
            return;
        }
        saveCurrentNow();
        snapshot(current, true);              // keep today's version, so this can be undone
        loading = true;
        titleField.setText(v.title);
        List<DrawPane.Stroke> strokes = new ArrayList<>();
        RichText.loadBody(editor.getStyledDocument(), v.body, current.assets, strokes);
        editor.setStrokes(strokes);
        editor.setCaretPosition(0);
        undo.discardAllEdits();
        loading = false;
        current.title = v.title;
        dirty = true;
        saveCurrentNow();
        statusLabel.setText("Version restored");
    }

    // ---------- HISTORY: recent edits ----------

    void showRecent() {
        if (manager == null) {
            return;
        }
        saveCurrentNow();
        JDialog dialog = new JDialog(frame, "Recent edits", true);
        dialog.setContentPane(buildRecentForm(dialog));
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    JPanel buildRecentForm(JDialog dialog) {
        Theme th = Theme.current();
        JPanel form = new JPanel(new BorderLayout(0, 8));
        form.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        form.setBackground(Theme.lighter(th.bg1, 0.4));
        JLabel heading = new JLabel("Recent edits");
        heading.setFont(new Font("SansSerif", Font.BOLD, 20));
        heading.setForeground(th.dark);

        List<Note> recent = new ArrayList<>(NoteTree.live(allNotes));
        recent.sort((a, b) -> Long.compare(b.modified, a.modified));
        DefaultListModel<Note> model = new DefaultListModel<>();
        for (int i = 0; i < Math.min(30, recent.size()); i++) {
            model.addElement(recent.get(i));
        }
        JList<Note> list = new JList<>(model);
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM yyyy, h:mm a");
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus) {
                super.getListCellRendererComponent(l, "", index, sel, focus);
                Note n = (Note) value;
                setText("<html><b>" + escape(shortTitle(n, 40)) + "</b><br><span style='font-size:10px'>Edited "
                        + escape(fmt.format(new Date(n.modified))) + "</span></html>");
                setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
                if (sel) {
                    setBackground(Theme.current().primary);
                    setForeground(Color.WHITE);
                }
                return this;
            }
        });
        Runnable open = () -> {
            Note n = list.getSelectedValue();
            if (n != null) {
                if (dialog != null) {
                    dialog.dispose();
                }
                openNote(n);
            }
        };
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    open.run();
                }
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(420, 300));
        Kit.ColorButton openButton = new Kit.ColorButton("Open", null, Kit.Style.PRIMARY);
        openButton.addActionListener(e -> open.run());
        Kit.ColorButton close = new Kit.ColorButton("Close", null, Kit.Style.SOFT);
        close.addActionListener(e -> {
            if (dialog != null) {
                dialog.dispose();
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(openButton);
        buttons.add(close);
        form.add(heading, BorderLayout.NORTH);
        form.add(scroll, BorderLayout.CENTER);
        form.add(buttons, BorderLayout.SOUTH);
        return form;
    }

    // ---------- HISTORY: recycle bin ----------

    void showRecycleBin() {
        if (manager == null) {
            return;
        }
        saveCurrentNow();
        JDialog dialog = new JDialog(frame, "Recycle bin", true);
        dialog.setContentPane(buildRecycleForm(dialog));
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    JPanel buildRecycleForm(JDialog dialog) {
        Theme th = Theme.current();
        JPanel form = new JPanel(new BorderLayout(0, 8));
        form.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        form.setBackground(Theme.lighter(th.bg1, 0.4));
        JLabel heading = new JLabel("Recycle bin");
        heading.setFont(new Font("SansSerif", Font.BOLD, 20));
        heading.setForeground(th.dark);
        JLabel sub = new JLabel("Deleted notes stay here for 30 days. Then they are removed for good.");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(heading, BorderLayout.NORTH);
        head.add(sub, BorderLayout.CENTER);

        DefaultListModel<Note> model = new DefaultListModel<>();
        Runnable fill = () -> {
            model.clear();
            List<Note> bin = NoteTree.deleted(allNotes);
            bin.sort((a, b) -> Long.compare(b.deletedAt, a.deletedAt));
            for (Note n : bin) {
                model.addElement(n);
            }
        };
        fill.run();
        JList<Note> list = new JList<>(model);
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM yyyy, h:mm a");
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus) {
                super.getListCellRendererComponent(l, "", index, sel, focus);
                Note n = (Note) value;
                setText("<html><b>" + escape(shortTitle(n, 40)) + "</b><br><span style='font-size:10px'>Deleted "
                        + escape(fmt.format(new Date(n.deletedAt))) + "</span></html>");
                setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
                if (sel) {
                    setBackground(Theme.current().primary);
                    setForeground(Color.WHITE);
                }
                return this;
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(420, 280));

        Kit.ColorButton restore = new Kit.ColorButton("Restore", Icons.undo(16, Color.WHITE), Kit.Style.PRIMARY);
        restore.addActionListener(e -> {
            Note n = list.getSelectedValue();
            if (n != null) {
                restoreNote(n);
                fill.run();
            }
        });
        Kit.ColorButton forever = new Kit.ColorButton("Delete forever", Icons.trash(16, Color.WHITE), Kit.Style.DANGER);
        forever.addActionListener(e -> {
            Note n = list.getSelectedValue();
            if (n != null && JOptionPane.showConfirmDialog(dialog, "Delete this note for good? This cannot be undone.",
                    "Delete forever", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
                deleteForever(n);
                fill.run();
            }
        });
        Kit.ColorButton empty = new Kit.ColorButton("Empty bin", null, Kit.Style.SOFT);
        empty.addActionListener(e -> {
            if (!model.isEmpty() && JOptionPane.showConfirmDialog(dialog, "Delete everything in the bin for good?",
                    "Empty bin", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
                for (Note n : new ArrayList<>(NoteTree.deleted(allNotes))) {
                    deleteForever(n);
                }
                fill.run();
            }
        });
        Kit.ColorButton close = new Kit.ColorButton("Close", null, Kit.Style.SOFT);
        close.addActionListener(e -> {
            if (dialog != null) {
                dialog.dispose();
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(restore);
        buttons.add(forever);
        buttons.add(empty);
        buttons.add(close);
        form.add(head, BorderLayout.NORTH);
        form.add(scroll, BorderLayout.CENTER);
        form.add(buttons, BorderLayout.SOUTH);
        return form;
    }

    // Brings a note back (and the sub notes that were deleted together with it)
    void restoreNote(Note n) {
        List<Note> bring = new ArrayList<>();
        bring.add(n);
        for (Note d : NoteTree.descendants(n, allNotes)) {
            if (d.deleted && Math.abs(d.deletedAt - n.deletedAt) < 5000) {
                bring.add(d);
            }
        }
        Set<String> liveIds = new HashSet<>();
        for (Note live : NoteTree.live(allNotes)) {
            liveIds.add(live.id);
        }
        for (Note b : bring) {
            liveIds.add(b.id);
        }
        try {
            for (Note b : bring) {
                b.deleted = false;
                b.deletedAt = 0;
                if (!b.parentId.isEmpty() && !liveIds.contains(b.parentId)) {
                    b.parentId = "";                 // its main note is gone, so it becomes a main note
                }
                manager.updateNote(b);
            }
        } catch (Exception ex) {
            statusLabel.setText("Could not restore: " + ex.getMessage());
        }
        refreshList(null);
    }

    void deleteForever(Note n) {
        List<Note> remove = new ArrayList<>();
        remove.add(n);
        for (Note d : NoteTree.descendants(n, allNotes)) {
            if (d.deleted) {
                remove.add(d);
            }
        }
        try {
            for (Note r : remove) {
                manager.deleteNote(r);
                allNotes.remove(r);
            }
        } catch (Exception ex) {
            statusLabel.setText("Could not delete: " + ex.getMessage());
        }
    }

    // ---------- SHARE ----------

    private boolean confirmExport() {
        if (exportWarned) {
            return true;
        }
        int r = JOptionPane.showConfirmDialog(frame,
                "The copy you save or send is NOT locked.\nAnyone who gets it can read it.\n\nContinue?",
                "Save a copy", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r == JOptionPane.YES_OPTION) {
            exportWarned = true;
            return true;
        }
        return false;
    }

    // The open note, followed by its sub notes
    private List<Note> exportNotes() {
        saveCurrentNow();
        List<Note> list = new ArrayList<>();
        list.add(current);
        list.addAll(NoteTree.descendants(current, NoteTree.live(allNotes)));
        return list;
    }

    private File chooseSaveFile(String extension, String description) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save a copy");
        String name = current.title.trim().isEmpty() ? "My note" : current.title.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        chooser.setSelectedFile(new File(name + "." + extension));
        chooser.setFileFilter(new FileNameExtensionFilter(description, extension));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith("." + extension)) {
            file = new File(file.getParentFile(), file.getName() + "." + extension);
        }
        if (file.exists() && JOptionPane.showConfirmDialog(frame, file.getName() + " already exists. Replace it?",
                "Save a copy", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return null;
        }
        return file;
    }

    private void saved(File file) {
        statusLabel.setText("Saved " + file.getName());
    }

    void exportHtml() {
        if (current == null || !confirmExport()) {
            return;
        }
        File file = chooseSaveFile("html", "Web page (html)");
        if (file == null) {
            return;
        }
        try {
            Files.write(file.toPath(), Exporter.toHtml(exportNotes()).getBytes(StandardCharsets.UTF_8));
            saved(file);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Could not save: " + ex.getMessage());
        }
    }

    void exportText() {
        if (current == null || !confirmExport()) {
            return;
        }
        File file = chooseSaveFile("txt", "Text file (txt)");
        if (file == null) {
            return;
        }
        try {
            Files.write(file.toPath(), Exporter.toText(exportNotes()).getBytes(StandardCharsets.UTF_8));
            saved(file);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Could not save: " + ex.getMessage());
        }
    }

    void exportPicture() {
        if (current == null || !confirmExport()) {
            return;
        }
        File file = chooseSaveFile("png", "Picture (png)");
        if (file == null) {
            return;
        }
        try {
            saveCurrentNow();
            Exporter.writePng(Exporter.renderPage(editor, Math.max(900, editor.getWidth())), file);
            saved(file);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Could not save: " + ex.getMessage());
        }
    }

    void exportPdf() {
        if (current == null || !confirmExport()) {
            return;
        }
        File file = chooseSaveFile("pdf", "PDF file (pdf)");
        if (file == null) {
            return;
        }
        try {
            saveCurrentNow();
            Exporter.writePdf(Exporter.renderPage(editor, Math.max(900, editor.getWidth())), file);
            saved(file);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Could not save: " + ex.getMessage());
        }
    }

    void printNote() {
        if (current == null) {
            return;
        }
        try {
            editor.print();
        } catch (java.awt.print.PrinterException ex) {
            JOptionPane.showMessageDialog(frame, "Could not print: " + ex.getMessage());
        }
    }

    void emailNote() {
        if (current == null || !confirmExport()) {
            return;
        }
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
                JOptionPane.showMessageDialog(frame, "This computer has no email program set up.\nTry \"Copy text\" instead.");
                return;
            }
            List<Note> notes = exportNotes();
            String subject = URLEncoder.encode(shortTitle(current, 60), "UTF-8").replace("+", "%20");
            String body = URLEncoder.encode(Exporter.forEmail(notes, 1500), "UTF-8").replace("+", "%20");
            Desktop.getDesktop().mail(new URI("mailto:?subject=" + subject + "&body=" + body));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Could not open the email program: " + ex.getMessage());
        }
    }

    void copyText() {
        if (current == null || !confirmExport()) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(Exporter.toText(exportNotes())), null);
        statusLabel.setText("Text copied");
    }

    // ---------- VIEW ----------

    void showNotesList(boolean show) {
        leftPanel.setVisible(show);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    void setFullScreen(boolean on) {
        if (frame != null) {
            frame.setExtendedState(on ? JFrame.MAXIMIZED_BOTH : JFrame.NORMAL);
        }
    }

    // ---------- NOTES: show, save, add, pin, delete ----------

    private void titleChanged() {
        if (loading || current == null) {
            return;
        }
        current.title = titleField.getText();
        notesList.repaint();
        markDirty();
    }

    private void markDirty() {
        if (loading || current == null) {
            return;
        }
        dirty = true;
        statusLabel.setText("Saving...");
        saveTimer.restart();
    }

    // Saves the note being edited, if it has changes
    void saveCurrentNow() {
        if (saveTimer != null) {
            saveTimer.stop();
        }
        if (!dirty || current == null || manager == null) {
            return;
        }
        try {
            current.title = titleField.getText();
            current.body = RichText.toBody(editor.getStyledDocument(), current.assets, editor.strokes());
            current.modified = System.currentTimeMillis();
            snapshot(current, false);
            manager.updateNote(current);
            dirty = false;
            statusLabel.setText("All changes saved");
            refreshList(null);
            updateCrumbs();
        } catch (Exception ex) {
            statusLabel.setText("Could not save: " + ex.getMessage());
        }
    }

    // Puts the notes in order (main notes with their sub notes underneath). Keeps the selection.
    private void refreshList(Note select) {
        Note keep = (select != null) ? select : notesList.getSelectedValue();
        List<NoteTree.Row> rows = NoteTree.visible(allNotes, collapsed, listFilter == null ? "" : listFilter.getText());
        rowInfo.clear();
        selectionLock = true;
        listModel.clear();
        for (NoteTree.Row row : rows) {
            listModel.addElement(row.note);
            rowInfo.put(row.note.id, row);
        }
        if (keep != null) {
            notesList.setSelectedValue(keep, true);
        }
        selectionLock = false;
    }

    // Shows one note in the title box and writing area
    private void showNote(Note n) {
        loading = true;
        removeFindMarks();
        matches.clear();
        current = n;
        if (n == null) {
            titleField.setText("");
            RichText.loadBody(editor.getStyledDocument(), "");
            editor.setStrokes(new ArrayList<>());
            setEditorEnabled(false);
            rightLayout.show(rightCards, "empty");
            pinButton.setText("Pin note");
        } else {
            setEditorEnabled(true);
            rightLayout.show(rightCards, "editor");
            titleField.setText(n.title);
            List<DrawPane.Stroke> strokes = new ArrayList<>();
            RichText.loadBody(editor.getStyledDocument(), n.body, n.assets, strokes);
            editor.setStrokes(strokes);
            editor.setCaretPosition(0);
            pinButton.setText(n.pinned ? "Unpin note" : "Pin note");
            snapshot(n, false);
        }
        undo.discardAllEdits();
        editor.setTool(DrawPane.Tool.TYPE);
        ribbon.setTool(DrawPane.Tool.TYPE);
        dirty = false;
        loading = false;
        updateCrumbs();
        syncToolbar();
    }

    // Opens a note, even if its main note is closed or the list is filtered
    void openNote(Note n) {
        saveCurrentNow();
        Note p = NoteTree.parentOf(n, NoteTree.live(allNotes));
        int guard = 0;
        while (p != null && guard++ < 20) {
            collapsed.remove(p.id);
            p = NoteTree.parentOf(p, NoteTree.live(allNotes));
        }
        if (listFilter != null && !listFilter.getText().isEmpty()) {
            listFilter.setText("");
        }
        refreshList(n);
        showNote(n);
    }

    void newNote() {
        createNote("");
    }

    void newSubNote() {
        if (current == null) {
            return;
        }
        collapsed.remove(current.id);
        createNote(current.id);
    }

    private void createNote(String parentId) {
        if (manager == null) {
            return;
        }
        saveCurrentNow();
        Note n = new Note();
        n.parentId = parentId;
        try {
            manager.addNote(n);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Could not create note: " + ex.getMessage());
            return;
        }
        allNotes.add(n);
        if (listFilter != null && !listFilter.getText().isEmpty()) {
            listFilter.setText("");
        }
        refreshList(n);
        showNote(n);
        statusLabel.setText(" ");
        titleField.requestFocusInWindow();
    }

    // Puts the open note under the note just above it (at the same level)
    void makeSubNote() {
        if (current == null || manager == null) {
            return;
        }
        saveCurrentNow();
        int index = listModel.indexOf(current);
        NoteTree.Row mine = rowInfo.get(current.id);
        if (index < 0 || mine == null) {
            return;
        }
        Note newParent = null;
        for (int i = index - 1; i >= 0; i--) {
            NoteTree.Row r = rowInfo.get(listModel.get(i).id);
            if (r.depth == mine.depth) {
                newParent = r.note;
                break;
            }
            if (r.depth < mine.depth) {
                break;
            }
        }
        if (newParent == null) {
            statusLabel.setText("There is no note above to put it under");
            return;
        }
        current.parentId = newParent.id;
        collapsed.remove(newParent.id);
        persist(current);
        refreshList(current);
        updateCrumbs();
    }

    // Takes the open sub note out of its main note
    void makeMainNote() {
        if (current == null || manager == null) {
            return;
        }
        saveCurrentNow();
        Note parent = NoteTree.parentOf(current, NoteTree.live(allNotes));
        if (parent == null) {
            statusLabel.setText("This is already a main note");
            return;
        }
        current.parentId = parent.parentId == null ? "" : parent.parentId;
        persist(current);
        refreshList(current);
        updateCrumbs();
    }

    private void persist(Note n) {
        try {
            manager.updateNote(n);
        } catch (Exception ex) {
            statusLabel.setText("Could not save: " + ex.getMessage());
        }
    }

    private void togglePin() {
        if (current == null || manager == null) {
            return;
        }
        saveCurrentNow();
        current.pinned = !current.pinned;
        persist(current);
        pinButton.setText(current.pinned ? "Unpin note" : "Pin note");
        refreshList(current);
    }

    // Moves the open note (and its sub notes) to the Recycle bin
    void deleteCurrent() {
        if (current == null || manager == null) {
            return;
        }
        List<Note> subs = NoteTree.descendants(current, NoteTree.live(allNotes));
        String what = subs.isEmpty() ? "this note"
                : "this note and its " + subs.size() + " sub note" + (subs.size() == 1 ? "" : "s");
        int sure = JOptionPane.showConfirmDialog(frame,
                "Move " + what + " to the Recycle bin?\n(You can bring it back from History > Recycle bin.)",
                "Delete", JOptionPane.YES_NO_OPTION);
        if (sure != JOptionPane.YES_OPTION) {
            return;
        }
        saveTimer.stop();
        dirty = false;
        long now = System.currentTimeMillis();
        List<Note> gone = new ArrayList<>();
        gone.add(current);
        gone.addAll(subs);
        try {
            for (Note g : gone) {
                g.deleted = true;
                g.deletedAt = now;
                manager.updateNote(g);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Could not delete note: " + ex.getMessage());
            return;
        }
        current = null;
        refreshList(null);
        Note next = listModel.isEmpty() ? null : listModel.get(0);
        refreshList(next);
        showNote(next);
        statusLabel.setText("Moved to the Recycle bin");
    }

    private void openNotes(String pin) {
        manager = new NoteManager(pin);
        try {
            allNotes = manager.loadNotes();
            long thirtyDays = 30L * 24 * 60 * 60 * 1000;
            manager.purgeOldDeleted(thirtyDays);
            long now = System.currentTimeMillis();
            allNotes.removeIf(n -> n.deleted && n.deletedAt > 0 && now - n.deletedAt > thirtyDays);
        } catch (Exception ex) {
            manager = null;
            messageLabel.setText("Could not read notes: " + ex.getMessage());
            return;
        }
        refreshList(null);
        Note first = listModel.isEmpty() ? null : listModel.get(0);
        refreshList(first);
        showNote(first);
        statusLabel.setText(allNotes.isEmpty() ? "Click \"New note\" to start" : " ");
        cards.show(mainPanel, "notes");
        idleTimer.restart();
    }

    // Saves the last changes, clears the screen and goes back to the login page
    private void lockApp() {
        saveCurrentNow();
        idleTimer.stop();
        manager = null;
        allNotes = new ArrayList<>();
        collapsed.clear();
        rowInfo.clear();
        selectionLock = true;
        listModel.clear();
        if (listFilter != null) {
            listFilter.setText("");
        }
        selectionLock = false;
        showNote(null);
        if (ribbon != null) {
            ribbon.findField.setText("");
            ribbon.showTab("Home");
        }
        updateLoginMessage();
        cards.show(mainPanel, "login");
    }

    // ---------- LOOK: colours and character ----------

    private void showThemeDialog() {
        JDialog dialog = new JDialog(frame, "Make it yours", true);
        dialog.setContentPane(buildThemeForm(dialog));
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    // Updates every colourful part of the app after you pick something new
    private void applyTheme() {
        Theme th = Theme.current();
        for (JLabel label : darkLabels) {
            label.setForeground(th.dark);
        }
        for (JLabel label : primaryLabels) {
            label.setForeground(th.primary);
        }
        for (JTextComponent field : styledFields) {
            if (field == titleField) {
                field.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 2, 0, Theme.alpha(th.primary, 170)),
                        BorderFactory.createEmptyBorder(2, 2, 4, 2)));
            } else {
                styleField(field);
            }
        }
        if (loginTitle != null) {
            loginTitle.setIcon(Icons.lock(34, th.primary));
        }
        for (Runnable refresh : iconRefreshers) {
            refresh.run();
        }
        if (editorScroll != null) {
            editorScroll.setBorder(BorderFactory.createLineBorder(Theme.alpha(th.primary, 120), 2, true));
        }
        if (mainPanel != null) {
            mainPanel.repaint();
        }
        if (notesList != null) {
            notesList.repaint();
        }
    }

    private JPanel buildThemeForm(JDialog dialog) {
        Theme th = Theme.current();
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        form.setBackground(Theme.lighter(th.bg1, 0.4));

        JLabel heading = new JLabel("Make it yours");
        heading.setFont(new Font("SansSerif", Font.BOLD, 22));
        heading.setForeground(th.dark);
        JLabel sub = new JLabel("Pick colours and a character. The app remembers your choice.");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JLabel coloursTitle = new JLabel("Colours");
        coloursTitle.setFont(new Font("SansSerif", Font.BOLD, 15));
        JLabel characterTitle = new JLabel("Character");
        characterTitle.setFont(new Font("SansSerif", Font.BOLD, 15));

        List<ChoiceCard> choices = new ArrayList<>();
        Runnable afterChange = () -> {
            Theme.save();
            applyTheme();
            form.setBackground(Theme.lighter(Theme.current().bg1, 0.4));
            heading.setForeground(Theme.current().dark);
            for (ChoiceCard choice : choices) {
                choice.repaint();
            }
            form.repaint();
        };

        JPanel colourRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        colourRow.setOpaque(false);
        for (Theme t : Theme.ALL) {
            ChoiceCard chip = new ChoiceCard(new ThemeChip(t), t.name,
                    () -> Theme.current() == t,
                    () -> {
                        Theme.setCurrent(t);
                        afterChange.run();
                    });
            choices.add(chip);
            colourRow.add(chip);
        }

        JPanel characterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        characterRow.setOpaque(false);
        for (String name : new String[]{"Mika", "Droney", "Mochi"}) {
            ChoiceCard card = new ChoiceCard(new Mascot.MascotPanel(92, false, name), name,
                    () -> Theme.mascot().equals(name),
                    () -> {
                        Theme.setMascot(name);
                        afterChange.run();
                    });
            choices.add(card);
            characterRow.add(card);
        }
        Runnable choosePicture = () -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Choose a picture");
            chooser.setFileFilter(new FileNameExtensionFilter("Pictures (png, jpg, gif)", "png", "jpg", "jpeg", "gif", "bmp"));
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                try {
                    Theme.setPicture(chooser.getSelectedFile().getPath());
                    Theme.setMascot("Picture");
                    afterChange.run();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Picture", JOptionPane.WARNING_MESSAGE);
                }
            }
        };
        ChoiceCard pictureCard = new ChoiceCard(new PictureThumb(), "My picture",
                () -> Theme.mascot().equals("Picture"),
                () -> {
                    if (Theme.picture() == null) {
                        choosePicture.run();
                    } else {
                        Theme.setMascot("Picture");
                        afterChange.run();
                    }
                });
        choices.add(pictureCard);
        characterRow.add(pictureCard);

        Kit.LinkButton another = new Kit.LinkButton("Choose a picture from my computer...");
        another.addActionListener(e -> choosePicture.run());
        Kit.ColorButton done = new Kit.ColorButton("Done", Icons.check(16, Color.WHITE), Kit.Style.PRIMARY);
        done.addActionListener(e -> {
            if (dialog != null) {
                dialog.dispose();
            }
        });
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        bottom.setOpaque(false);
        bottom.add(done);
        bottom.add(another);

        for (JComponent part : new JComponent[]{heading, sub, coloursTitle, colourRow, characterTitle, characterRow, bottom}) {
            part.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        form.add(heading);
        form.add(Box.createVerticalStrut(2));
        form.add(sub);
        form.add(Box.createVerticalStrut(14));
        form.add(coloursTitle);
        form.add(colourRow);
        form.add(Box.createVerticalStrut(10));
        form.add(characterTitle);
        form.add(characterRow);
        form.add(Box.createVerticalStrut(10));
        form.add(bottom);
        return form;
    }

    // ---------- SMALL HELPERS FOR DRAWING ----------

    // A little coloured square (used in the colour buttons)
    private static class SwatchIcon implements Icon {
        Color color;

        SwatchIcon(Color color) {
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

    // How each note looks in the list: a colourful card with the title, the date and a pin.
    // A small arrow shows when a note has sub notes; sub notes are pushed to the right.
    private static class NoteCard extends JComponent implements ListCellRenderer<Note> {
        static final int INDENT = 16;
        private static final SimpleDateFormat DATE = new SimpleDateFormat("d MMM yyyy, h:mm a");
        private final Function<Note, NoteTree.Row> info;
        private Note note;
        private boolean selected;

        NoteCard(Function<Note, NoteTree.Row> info) {
            this.info = info;
        }

        public Component getListCellRendererComponent(JList<? extends Note> list, Note value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            note = value;
            selected = isSelected;
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(200, 60);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Theme th = Theme.current();
            NoteTree.Row row = info.apply(note);
            int depth = row == null ? 0 : row.depth;
            int w = getWidth();
            int h = getHeight();
            int x0 = 3 + depth * INDENT;
            Color stripe = Color.getHSBColor((float) (((note.id.hashCode() & 0x7fffffff) % 360) / 360.0), 0.55f, 0.95f);

            RoundRectangle2D card = new RoundRectangle2D.Double(x0, 3, Math.max(40, w - x0 - 5), h - 7, 20, 20);
            if (selected) {
                g.setPaint(new GradientPaint(0, 0, th.primary, w, h, Theme.mix(th.primary, th.accent, 0.55)));
                g.fill(card);
            } else {
                g.setColor(new Color(255, 255, 255, 240));
                g.fill(card);
                g.setColor(Theme.alpha(stripe, 140));
                g.setStroke(new BasicStroke(1.4f));
                g.draw(card);
            }
            // the little arrow for notes that have sub notes
            if (row != null && row.hasChildren) {
                g.setColor(selected ? Color.WHITE : th.primary);
                java.awt.geom.Path2D arrow = new java.awt.geom.Path2D.Double();
                double cx = x0 + 12;
                double cy = h / 2.0;
                if (row.collapsed) {
                    arrow.moveTo(cx - 3, cy - 5);
                    arrow.lineTo(cx + 4, cy);
                    arrow.lineTo(cx - 3, cy + 5);
                } else {
                    arrow.moveTo(cx - 5, cy - 3);
                    arrow.lineTo(cx + 5, cy - 3);
                    arrow.lineTo(cx, cy + 4);
                }
                arrow.closePath();
                g.fill(arrow);
            }
            g.setColor(selected ? new Color(255, 255, 255, 215) : stripe);
            g.fill(new RoundRectangle2D.Double(x0 + 24, 13, 6, h - 26, 6, 6));

            boolean untitled = note.title.trim().isEmpty();
            String title = untitled ? "Untitled note" : note.title.trim();
            Font titleFont = new Font("SansSerif", untitled ? Font.ITALIC : Font.BOLD, depth > 0 ? 13 : 14);
            g.setFont(titleFont);
            FontMetrics fm = g.getFontMetrics();
            int textX = x0 + 38;
            int room = w - textX - 14 - (note.pinned ? 22 : 0);
            if (fm.stringWidth(title) > room) {
                while (title.length() > 1 && fm.stringWidth(title + "...") > room) {
                    title = title.substring(0, title.length() - 1);
                }
                title = title.trim() + "...";
            }
            g.setColor(selected ? Color.WHITE : (untitled ? GREY_TEXT : th.dark));
            g.drawString(title, textX, 26);

            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(selected ? new Color(255, 255, 255, 225) : GREY_TEXT);
            g.drawString(DATE.format(new Date(note.modified)), textX, 44);

            if (note.pinned) {
                Icons.pin(18, selected ? Color.WHITE : new Color(230, 90, 40)).paintIcon(this, g, w - 32, 9);
            }
            g.dispose();
        }
    }

    // A clickable card in the "Make it yours" window
    private static class ChoiceCard extends JPanel {
        private final BooleanSupplier selected;

        ChoiceCard(JComponent preview, String name, BooleanSupplier selected, Runnable onClick) {
            super(new BorderLayout(0, 4));
            this.selected = selected;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(8, 8, 6, 8));
            JLabel label = new JLabel(name, JLabel.CENTER);
            label.setFont(new Font("SansSerif", Font.BOLD, 12));
            add(preview, BorderLayout.CENTER);
            add(label, BorderLayout.SOUTH);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onClick.run();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Theme th = Theme.current();
            RoundRectangle2D shape = new RoundRectangle2D.Double(1.5, 1.5, getWidth() - 4, getHeight() - 4, 22, 22);
            if (selected.getAsBoolean()) {
                g.setColor(Theme.alpha(th.primary, 45));
                g.fill(shape);
                g.setColor(th.primary);
                g.setStroke(new BasicStroke(3f));
            } else {
                g.setColor(new Color(255, 255, 255, 170));
                g.fill(shape);
                g.setColor(new Color(0, 0, 0, 40));
                g.setStroke(new BasicStroke(1.4f));
            }
            g.draw(shape);
            g.dispose();
        }
    }

    // A small preview of a colour theme
    private static class ThemeChip extends JComponent {
        private final Theme theme;

        ThemeChip(Theme theme) {
            this.theme = theme;
            setPreferredSize(new Dimension(86, 60));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            RoundRectangle2D shape = new RoundRectangle2D.Double(2, 2, w - 4, h - 4, 18, 18);
            g.setPaint(new GradientPaint(0, 0, theme.bg1, w, h, theme.bg2));
            g.fill(shape);
            g.setColor(theme.primary);
            g.fill(new Ellipse2D.Double(w / 2.0 - 22, h / 2.0 - 11, 22, 22));
            g.setColor(theme.accent);
            g.fill(new Ellipse2D.Double(w / 2.0 + 2, h / 2.0 - 11, 22, 22));
            g.setColor(new Color(0, 0, 0, 35));
            g.draw(shape);
            g.dispose();
        }
    }

    // The "My picture" choice: shows your picture, or a plus sign if you have not picked one
    private static class PictureThumb extends JComponent {
        PictureThumb() {
            setPreferredSize(new Dimension(92, 92));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Theme th = Theme.current();
            if (Theme.picture() != null) {
                Mascot.draw(g, "Picture", getWidth() / 2.0, getHeight() / 2.0, Math.min(getWidth(), getHeight()), 0, false);
            } else {
                g.setColor(Theme.alpha(th.primary, 60));
                g.fill(new Ellipse2D.Double(14, 14, getWidth() - 28, getHeight() - 28));
                Icons.plus(30, th.primary).paintIcon(this, g, getWidth() / 2 - 15, getHeight() / 2 - 15);
            }
            g.dispose();
        }
    }
}