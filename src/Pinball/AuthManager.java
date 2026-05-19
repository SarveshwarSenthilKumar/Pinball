package Pinball;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;

/**
 * =============================================================================
 * AuthManager.java — Authentication & Application Entry Point
 * =============================================================================
 * Handles user registration, login, and session management for the Pinball
 * game. Credentials are stored in "users.txt" (username:passwordHash format).
 * Launches the main game window upon successful authentication.
 *
 * File format (users.txt):
 *   username:sha256hash
 *
 * Usage:
 *   javac *.java && java AuthManager
 * =============================================================================
 */
public class AuthManager {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final String USERS_FILE  = "users.txt";
    private static final String SCORES_FILE = "scores.txt";

    // Neon arcade colour palette
    static final Color COL_BG      = new Color(5,   5,  20);
    static final Color COL_PANEL   = new Color(12,  12,  35);
    static final Color COL_ACCENT  = new Color(0,  220, 255);
    static final Color COL_ACCENT2 = new Color(255, 60, 180);
    static final Color COL_TEXT    = new Color(200, 200, 230);
    static final Color COL_BUTTON  = new Color(30,  30,  70);
    static final Font  FONT_TITLE  = new Font("Monospaced", Font.BOLD, 28);
    static final Font  FONT_LABEL  = new Font("Monospaced", Font.PLAIN, 13);
    static final Font  FONT_BTN    = new Font("Monospaced", Font.BOLD,  14);

    // ── Entry Point ───────────────────────────────────────────────────────────
    /**
     * Application entry point. Initialises look-and-feel and shows the
     * authentication dialog on the Swing event-dispatch thread.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {}
            new AuthManager().showLoginScreen();
        });
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Shows the login / register dialog and blocks until the user is authenticated. */
    public void showLoginScreen() {
        JFrame frame = buildLoginFrame();
        frame.setVisible(true);
    }

    // ── Frame Construction ────────────────────────────────────────────────────

    private JFrame buildLoginFrame() {
        JFrame frame = new JFrame("PINBALL ARCADE — Login");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setSize(576, 624);
        frame.setLocationRelativeTo(null);
        frame.setContentPane(buildLoginPanel(frame));
        return frame;
    }

    /**
     * Constructs the login panel with title, input fields, and action buttons.
     *
     * @param frame parent frame (used for dispose after login)
     */
    private JPanel buildLoginPanel(JFrame frame) {
        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(COL_BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // subtle scanline effect
                g2.setColor(new Color(255,255,255,6));
                for (int y = 0; y < getHeight(); y += 3) g2.drawLine(0, y, getWidth(), y);
            }
        };
        root.setBorder(new EmptyBorder(30, 40, 30, 40));

        // ── Title block ──
        JPanel titlePanel = new JPanel(new GridLayout(3,1,0,6));
        titlePanel.setOpaque(false);

        JLabel neon1 = neonLabel("◈ PINBALL ARCADE ◈", FONT_TITLE, COL_ACCENT);
        JLabel neon2 = neonLabel("PLAYER AUTHENTICATION", new Font("Monospaced",Font.BOLD,13), COL_ACCENT2);
        JLabel neon3 = neonLabel("─────────────────────────────", FONT_LABEL, new Color(80,80,120));
        titlePanel.add(neon1); titlePanel.add(neon2); titlePanel.add(neon3);
        root.add(titlePanel, BorderLayout.NORTH);

        // ── Form ──
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10,0,10,0);
        gbc.fill   = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 2;

        JTextField  userField = styledField();
        JPasswordField passField = styledPassField();

        addFormRow(form, gbc, 0, "USERNAME :", userField);
        addFormRow(form, gbc, 1, "PASSWORD :", passField);
        root.add(form, BorderLayout.CENTER);

        // ── Buttons ──
        JPanel btnPanel = new JPanel(new GridLayout(2,1,0,10));
        btnPanel.setOpaque(false);
        btnPanel.setBorder(new EmptyBorder(20,0,0,0));

        JButton loginBtn    = styledButton("▶  LOGIN",    COL_ACCENT);
        JButton registerBtn = styledButton("✦  REGISTER", COL_ACCENT2);

        loginBtn.addActionListener(e -> handleLogin(frame, userField.getText().trim(),
                new String(passField.getPassword())));
        registerBtn.addActionListener(e -> handleRegister(frame, userField.getText().trim(),
                new String(passField.getPassword())));

        // Allow Enter key to trigger login
        passField.addActionListener(e -> loginBtn.doClick());

        // High-scores button
        JButton scoresBtn = styledButton("🏆  HIGH SCORES", new Color(255,200,0));
        scoresBtn.addActionListener(e -> showHighScores(frame));

        btnPanel.add(loginBtn);
        btnPanel.add(registerBtn);

        JPanel south = new JPanel(new BorderLayout(0,8));
        south.setOpaque(false);
        south.add(btnPanel, BorderLayout.NORTH);
        south.add(scoresBtn, BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);

        return root;
    }

    // ── Event Handlers ────────────────────────────────────────────────────────

    /**
     * Validates credentials against users.txt and launches the game.
     *
     * @param frame    the authentication frame to dispose on success
     * @param username entered username
     * @param password entered plain-text password
     */
    private void handleLogin(JFrame frame, String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            showError(frame, "Username and password cannot be empty.");
            return;
        }
        Map<String,String> users = loadUsers();
        String hash = hashPassword(password);
        if (users.containsKey(username) && users.get(username).equals(hash)) {
            frame.dispose();
            launchGame(username);
        } else {
            showError(frame, "Invalid credentials. Please try again.");
        }
    }

    /**
     * Registers a new user and stores their credentials.
     *
     * @param frame    parent frame for dialogs
     * @param username desired username
     * @param password desired plain-text password
     */
    private void handleRegister(JFrame frame, String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            showError(frame, "Username and password cannot be empty.");
            return;
        }
        if (username.length() < 3) {
            showError(frame, "Username must be at least 3 characters.");
            return;
        }
        if (password.length() < 4) {
            showError(frame, "Password must be at least 4 characters.");
            return;
        }
        Map<String,String> users = loadUsers();
        if (users.containsKey(username)) {
            showError(frame, "Username already taken. Please choose another.");
            return;
        }
        users.put(username, hashPassword(password));
        saveUsers(users);
        JOptionPane.showMessageDialog(frame,
                "Account created for \"" + username + "\"!\nYou may now log in.",
                "Registration Successful", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Displays a scrollable high-scores dialog loaded from scores.txt. */
    private void showHighScores(JFrame frame) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════╗\n");
        sb.append("║        HALL  OF  FAME        ║\n");
        sb.append("╠══════════════════════════════╣\n");
        java.util.List<String[]> scores = ScoreManager.loadAllScores(SCORES_FILE);
        if (scores.isEmpty()) {
            sb.append("║   No scores recorded yet.    ║\n");
        } else {
            int rank = 1;
            for (String[] entry : scores) {
                String line = String.format("║ %2d. %-12s %10s ║", rank++, entry[0], entry[1]);
                sb.append(line).append("\n");
                if (rank > 10) break;
            }
        }
        sb.append("╚══════════════════════════════╝");
        JTextArea ta = new JTextArea(sb.toString());
        ta.setFont(new Font("Monospaced", Font.PLAIN, 13));
        ta.setEditable(false);
        ta.setBackground(COL_BG);
        ta.setForeground(COL_ACCENT);
        JOptionPane.showMessageDialog(frame, ta, "High Scores", JOptionPane.PLAIN_MESSAGE);
    }

    // ── Game Launch ───────────────────────────────────────────────────────────

    /**
     * Creates and displays the main game window for the authenticated user.
     *
     * @param username the logged-in player's username
     */
    private void launchGame(String username) {
        SwingUtilities.invokeLater(() -> {
            GameWindow window = new GameWindow(username, SCORES_FILE);
            window.setVisible(true);
        });
    }

    // ── File I/O ──────────────────────────────────────────────────────────────

    /** Reads users.txt and returns a username→passwordHash map. */
    private Map<String,String> loadUsers() {
        Map<String,String> map = new LinkedHashMap<>();
        File f = new File(USERS_FILE);
        if (!f.exists()) return map;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) map.put(parts[0], parts[1]);
            }
        } catch (IOException e) {
            System.err.println("[AuthManager] Failed to read " + USERS_FILE + ": " + e.getMessage());
        }
        return map;
    }

    /** Persists the username→passwordHash map to users.txt. */
    private void saveUsers(Map<String,String> users) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(USERS_FILE))) {
            users.forEach((u, h) -> pw.println(u + ":" + h));
        } catch (IOException e) {
            System.err.println("[AuthManager] Failed to write " + USERS_FILE + ": " + e.getMessage());
        }
    }

    // ── Crypto Utility ────────────────────────────────────────────────────────

    /**
     * Hashes a plain-text password using SHA-256.
     *
     * @param password the plain-text password
     * @return hex-encoded SHA-256 digest
     */
    static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ── UI Helpers ────────────────────────────────────────────────────────────

    private JLabel neonLabel(String text, Font font, Color color) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(font); l.setForeground(color); l.setOpaque(false);
        return l;
    }

    private JTextField styledField() {
        JTextField f = new JTextField();
        f.setBackground(COL_BUTTON); f.setForeground(COL_TEXT);
        f.setCaretColor(COL_ACCENT); f.setFont(FONT_LABEL);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COL_ACCENT, 1),
                new EmptyBorder(6,8,6,8)));
        return f;
    }

    private JPasswordField styledPassField() {
        JPasswordField f = new JPasswordField();
        f.setBackground(COL_BUTTON); f.setForeground(COL_TEXT);
        f.setCaretColor(COL_ACCENT); f.setFont(FONT_LABEL);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COL_ACCENT2, 1),
                new EmptyBorder(6,8,6,8)));
        return f;
    }

    private JButton styledButton(String text, Color accent) {
        JButton b = new JButton(text);
        b.setFont(FONT_BTN); b.setForeground(accent);
        b.setBackground(COL_BUTTON);
        b.setFocusPainted(false); b.setBorderPainted(true);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accent, 1),
                new EmptyBorder(10,16,10,16)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(new Color(40,40,90)); }
            public void mouseExited (MouseEvent e) { b.setBackground(COL_BUTTON); }
        });
        return b;
    }

    private void addFormRow(JPanel form, GridBagConstraints gbc,
                            int row, String labelText, JComponent field) {
        gbc.gridy = row * 2;
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(FONT_LABEL); lbl.setForeground(COL_ACCENT);
        form.add(lbl, gbc);
        gbc.gridy = row * 2 + 1;
        form.add(field, gbc);
    }

    private void showError(JFrame parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
}