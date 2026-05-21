package Pinball;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import javax.swing.border.*;

/**
 * =============================================================================
 * GameWindow.java — Main Game Frame, HUD, Store UI & Score Persistence
 * =============================================================================
 * Hosts the PinballPanel (the actual game) inside a styled JFrame, renders
 * the heads-up display (score, money, lives, powerups), provides the in-game
 * Store dialog where players spend coins on powerups, and delegates score
 * saving to ScoreManager.
 *
 * Key responsibilities:
 * • Build and layout the game frame with HUD sidepanel
 * • Refresh HUD on every game tick via GameListener callback
 * • Open/close the Store dialog (PowerupStore inner class)
 * • Save score to scores.txt when a game ends
 * =============================================================================
 */
public class GameWindow extends JFrame implements GameListener {

    // ── Fields ────────────────────────────────────────────────────────────────
    private final String     username;
    private final String     scoresFile;
    private       PinballPanel gamePanel;

    // HUD labels
    private JLabel lblScore, lblMoney, lblLives, lblMultiplier;
    private JLabel lblPowerupActive;
    private JLabel lblMuteHint; // Mute label reference
    public JLabel lblPersonalBest;
    private JProgressBar ballSpeedBar;

    // Sidebar panels
    private JPanel powerupIndicatorPanel;

    // ── Constructor ───────────────────────────────────────────────────────────
    /**
     * Constructs the main game window for the given authenticated user.
     *
     * @param username   the logged-in player's name (displayed in title bar)
     * @param scoresFile path to the scores persistence file
     */
    public GameWindow(String username, String scoresFile) {
        super("PINBALL ARCADE  ◈  Player: " + username);
        this.username   = username;
        this.scoresFile = scoresFile;
        buildUI();
    }

    // ── Frame Construction ────────────────────────────────────────────────────
    public void playSound(String soundFile) {
        try {
            File file = new File(soundFile);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(file);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }   
    
    private void buildUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setLayout(new BorderLayout(0, 0));

        // Dark background
        getContentPane().setBackground(AuthManager.COL_BG);

        // Game panel (the playfield)
        gamePanel = new PinballPanel(this);
        add(gamePanel, BorderLayout.CENTER);

        // HUD sidebar
        add(buildHudPanel(), BorderLayout.EAST);

        // Bottom bar
        add(buildBottomBar(), BorderLayout.SOUTH);

        // Set up the 'M' Key binding for muting/unmuting
        setupMuteHotkey();

        pack();
        setLocationRelativeTo(null);
        setMinimumSize(getSize());
    }

    private int getPersonalBest() {

        List<String[]> scores =
                ScoreManager.loadAllScores(scoresFile);

        int best = 0;

        for (String[] s : scores) {

            if (s[0].equalsIgnoreCase(username)) {

                try {
                    best = Math.max(best,
                            Integer.parseInt(s[1]));
                } catch (Exception ignored) {}
            }
        }

        return best;
    }

    /**
     * Constructs the right-side HUD panel showing score, money, lives,
     * active powerups, and control hints.
     */
    private JPanel buildHudPanel() {
        JPanel hud = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(AuthManager.COL_PANEL);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(AuthManager.COL_ACCENT.darker().darker());
                g2.drawLine(0, 0, 0, getHeight());
            }
        };
        hud.setPreferredSize(new Dimension(200, 0));
        hud.setLayout(new BoxLayout(hud, BoxLayout.Y_AXIS));
        hud.setBorder(new EmptyBorder(16, 12, 16, 12));

        hud.add(hudTitle("◈ HUD ◈"));
        hud.add(Box.createVerticalStrut(12));

        lblScore       = hudValue("0",          AuthManager.COL_ACCENT);
        lblMoney       = hudValue("$0",          new Color(255,220,60));
        lblLives       = hudValue("♥♥♥",         new Color(255,80,80));
        lblMultiplier  = hudValue("×1",          new Color(120,255,120));

        lblPersonalBest = hudValue(String.format("%,d", getPersonalBest()), new Color(255,120,255));

        hud.add(hudRow("SCORE",       lblScore));
        hud.add(Box.createVerticalStrut(10));
        hud.add(hudRow("COINS",       lblMoney));
        hud.add(Box.createVerticalStrut(10));
        hud.add(hudRow("LIVES",       lblLives));
        hud.add(Box.createVerticalStrut(10));
        hud.add(hudRow("MULTI", lblMultiplier));
        hud.add(Box.createVerticalStrut(10));

        hud.add(hudRow("PERSONAL BEST", lblPersonalBest));
        hud.add(Box.createVerticalStrut(16));

        hud.add(separatorLine());

        // Powerup active indicator
        JLabel pwTitle = hudTitle("POWERUP");
        hud.add(pwTitle);
        hud.add(Box.createVerticalStrut(6));
        lblPowerupActive = new JLabel("NONE", SwingConstants.CENTER);
        lblPowerupActive.setFont(new Font("Monospaced", Font.BOLD, 12));
        lblPowerupActive.setForeground(new Color(160,160,200));
        lblPowerupActive.setAlignmentX(Component.CENTER_ALIGNMENT);
        hud.add(lblPowerupActive);

        hud.add(Box.createVerticalStrut(16));
        hud.add(separatorLine());

        // Ball speed bar
        JLabel speedTitle = hudTitle("BALL SPEED");
        hud.add(speedTitle);
        hud.add(Box.createVerticalStrut(6));
        ballSpeedBar = new JProgressBar(0, 100);
        ballSpeedBar.setValue(30);
        ballSpeedBar.setForeground(AuthManager.COL_ACCENT2);
        ballSpeedBar.setBackground(new Color(20,20,50));
        ballSpeedBar.setMaximumSize(new Dimension(180, 14));
        ballSpeedBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        hud.add(ballSpeedBar);

        hud.add(Box.createVerticalStrut(16));
        hud.add(separatorLine());

        // Controls cheat-sheet
        hud.add(hudTitle("CONTROLS"));
        hud.add(Box.createVerticalStrut(6));
        
        // Dynamic mute control hint setup
        String initialMuteText = MusicPlayer.isMuted() ? "M  Unmute Music" : "M  Mute Music";
        lblMuteHint = new JLabel(initialMuteText);
        lblMuteHint.setFont(new Font("Monospaced", Font.BOLD, 10));
        lblMuteHint.setForeground(MusicPlayer.isMuted() ? AuthManager.COL_ACCENT2 : new Color(120,120,170));
        lblMuteHint.setAlignmentX(Component.CENTER_ALIGNMENT);

        String[] controls = {"← Left flipper", "→ Right flipper", "Space  Launch", "S  Open Store", "R  Restart", "Esc  Quit", "P  Pause / Resume"};
        for (String c : controls) {
            JLabel cl = new JLabel(c);
            cl.setFont(new Font("Monospaced", Font.PLAIN, 10));
            cl.setForeground(new Color(120,120,170));
            cl.setAlignmentX(Component.CENTER_ALIGNMENT);
            hud.add(cl);
            hud.add(Box.createVerticalStrut(3));
        }
        
        // Add mute helper text row to instructions sheet
        hud.add(lblMuteHint);

        return hud;
    }

    private JPanel buildBottomBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 6));
        bar.setBackground(new Color(8,8,24));
        bar.setBorder(BorderFactory.createMatteBorder(1,0,0,0, AuthManager.COL_ACCENT.darker()));

        JButton storeBtn = barButton("🏪 STORE [S]", new Color(255,200,0));
        JButton restartBtn= barButton("⟳ RESTART [R]", AuthManager.COL_ACCENT);
        JButton quitBtn  = barButton("✕ QUIT [Esc]",  AuthManager.COL_ACCENT2);

        storeBtn.addActionListener(e -> openStore());
        restartBtn.addActionListener(e -> gamePanel.restartGame());
        quitBtn.addActionListener(e -> handleQuit());

        bar.add(storeBtn);
        bar.add(restartBtn);
        bar.add(quitBtn);
        return bar;
    }

    /** Binds the 'M' key to toggle mute on the MusicPlayer without interrupting focus fields */
    private void setupMuteHotkey() {
        JComponent content = (JComponent) getContentPane();
        content.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_M, 0), "gameToggleMute");
        content.getActionMap().put("gameToggleMute", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MusicPlayer.toggleMute();
                updateMuteText();
            }
        });
    }

    /** Changes HUD controls cheat-sheet color indicators based on current global audio parameters */
    private void updateMuteText() {
        if (lblMuteHint != null) {
            if (MusicPlayer.isMuted()) {
                lblMuteHint.setText("M  Unmute Music");
                lblMuteHint.setForeground(AuthManager.COL_ACCENT2); // Hot Pink warning highlight
            } else {
                lblMuteHint.setText("M  Mute Music");
                lblMuteHint.setForeground(new Color(120,120,170));  // Standard gray-blue text tint
            }
        }
    }

    // ── GameListener Implementation ───────────────────────────────────────────

    /**
     * Called by PinballPanel on every physics tick to refresh the HUD.
     *
     * @param state current snapshot of game state
     */
    @Override
    public void onGameStateChanged(GameState state) {
        SwingUtilities.invokeLater(() -> {
            lblScore.setText(String.format("%,d", state.score));
            lblMoney.setText("$" + state.money);
            lblLives.setText(buildLivesString(state.lives));
            lblMultiplier.setText("×" + state.multiplier);

            int pb = Math.max(getPersonalBest(), state.score);

            lblPersonalBest.setText(String.format("%,d", pb));

            // Active powerup label
            if (state.activePowerup != null && !state.activePowerup.isEmpty()) {
                lblPowerupActive.setText(state.activePowerup);
                lblPowerupActive.setForeground(new Color(255,220,60));
            } else {
                lblPowerupActive.setText("NONE");
                lblPowerupActive.setForeground(new Color(160,160,200));
            }

            // Ball speed indicator (normalised 0–100)
            int spd = Math.min(100, (int)(state.ballSpeed * 5));
            ballSpeedBar.setValue(spd);
        });
    }

    /**
     * Called when all lives are lost. Saves the score and prompts for replay.
     *
     * @param finalScore the player's total score
     */
    @Override
    public void onGameOver(int finalScore) {
        ScoreManager.saveScore(scoresFile, username, finalScore);
        SwingUtilities.invokeLater(() -> {
            int choice = JOptionPane.showConfirmDialog(this,
                    String.format("GAME OVER!\n\nPlayer : %s\nScore  : %,d\n\nPlay again?",
                            username, finalScore),
                    "Game Over", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) gamePanel.restartGame();
            else { dispose(); new AuthManager().showLoginScreen(); }
        });
    }

    // ── Store Dialog ──────────────────────────────────────────────────────────

    /** Opens the in-game powerup store modal. */
    public void openStore() {
        gamePanel.pauseGame(true);
        new PowerupStore(this, gamePanel).setVisible(true);
        gamePanel.pauseGame(false);
        gamePanel.requestFocusInWindow();
    }

    // ── Quit Handling ─────────────────────────────────────────────────────────

    private void handleQuit() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Save score and quit?", "Quit", JOptionPane.YES_NO_CANCEL_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            ScoreManager.saveScore(scoresFile, username, gamePanel.getCurrentScore());
            dispose(); System.exit(0);
        } else if (choice == JOptionPane.NO_OPTION) {
            dispose(); System.exit(0);
        }
    }

    // ── UI Helpers ────────────────────────────────────────────────────────────

    private String buildLivesString(int lives) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lives; i++) sb.append("♥");
        return sb.length() > 0 ? sb.toString() : "✕";
    }

    private JLabel hudTitle(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(new Font("Monospaced", Font.BOLD, 11));
        l.setForeground(AuthManager.COL_ACCENT);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private JLabel hudValue(String text, Color color) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(new Font("Monospaced", Font.BOLD, 18));
        l.setForeground(color);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private JPanel hudRow(String label, JLabel value) {
        JPanel row = new JPanel(new GridLayout(2,1,0,2));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(200, 52));
        row.setAlignmentX(Component.CENTER_ALIGNMENT);
        row.add(hudTitle(label));
        row.add(value);
        return row;
    }

    private JSeparator separatorLine() {
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(60,60,100));
        sep.setMaximumSize(new Dimension(180, 1));
        return sep;
    }

    private JButton barButton(String text, Color accent) {
        JButton b = new JButton(text);
        b.setFont(new Font("Monospaced", Font.BOLD, 12));
        b.setForeground(accent);
        b.setBackground(new Color(20,20,50));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accent, 1),
                new EmptyBorder(6,12,6,12)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter(){
            public void mouseEntered(MouseEvent e){b.setBackground(new Color(40,40,80));}
            public void mouseExited (MouseEvent e){b.setBackground(new Color(20,20,50));}
        });
        return b;
    }
}

// =============================================================================
// PowerupStore — Inner-class modal Store dialog
// =============================================================================
/**
 * Modal dialog presenting the powerup store. Players spend in-game coins
 * to purchase upgrades that modify gameplay via PinballPanel.applyPowerup().
 *
 * Available powerups:
 * • MULTI×2    — doubles score multiplier
 * • MAGNET     — ball is attracted toward flippers
 * • FIREBALL   — ball destroys bumpers on contact (10 hits)
 * • SLOW-MO    — halves ball speed for 15 seconds
 * • EXTRA LIFE — grants an additional life
 * • MEGA BUMPER— bumpers worth 3× coins for 20 seconds
 */
class PowerupStore extends JDialog {

    private static final Object[][] POWERUPS = {
        {"⚡ MULTI ×2",    50,  "Doubles score multiplier for 30s",       "MULTI2"},
        {"🧲 MAGNET",      80,  "Ball drifts toward flippers (20s)",      "MAGNET"},
        {"🔥 FIREBALL",    120, "Ball destroys bumpers on hit (10 hits)",  "FIREBALL"},
        {"🐢 SLOW-MO",     60,  "Halves ball speed for 15 seconds",        "SLOWMO"},
        {"♥  EXTRA LIFE",  150, "Gain one extra life instantly",           "EXTRALIFE"},
        {"💥 MEGA BUMPER", 100, "Bumpers worth 3× coins (20s)",            "MEGABUMP"},
    };

    private final PinballPanel game;
    private JLabel coinsLabel;

    PowerupStore(GameWindow parent, PinballPanel game) {
        super(parent, "🏪  POWERUP STORE", true);
        this.game = game;
        buildUI();
        setSize(480, 560);
        setResizable(false);
        setLocationRelativeTo(parent);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0,12)) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(AuthManager.COL_BG); g.fillRect(0,0,getWidth(),getHeight());
            }
        };
        root.setBorder(new EmptyBorder(20,20,20,20));

        JLabel title = new JLabel("◈ POWERUP STORE ◈", SwingConstants.CENTER);
        title.setFont(new Font("Monospaced", Font.BOLD, 22));
        title.setForeground(new Color(255,200,0));
        root.add(title, BorderLayout.NORTH);

        JPanel coinRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        coinRow.setOpaque(false);
        coinsLabel = new JLabel("Your Coins: $" + game.getMoney());
        coinsLabel.setFont(new Font("Monospaced", Font.BOLD, 16));
        coinsLabel.setForeground(new Color(255,220,60));
        coinRow.add(coinsLabel);

        JPanel grid = new JPanel(new GridLayout(POWERUPS.length, 1, 0, 10));
        grid.setOpaque(false);

        for (Object[] pw : POWERUPS) {
            grid.add(buildPowerupCard(pw));
        }

        JPanel center = new JPanel(new BorderLayout(0,10));
        center.setOpaque(false);
        center.add(coinRow, BorderLayout.NORTH);
        center.add(grid,    BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);

        JButton closeBtn = new JButton("✕  CLOSE STORE");
        closeBtn.setFont(new Font("Monospaced", Font.BOLD, 13));
        closeBtn.setForeground(AuthManager.COL_ACCENT2);
        closeBtn.setBackground(new Color(30,10,30));
        closeBtn.setFocusPainted(false);
        closeBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AuthManager.COL_ACCENT2),
                new EmptyBorder(8,16,8,16)));
        closeBtn.addActionListener(e -> dispose());
        JPanel southRow = new JPanel(); southRow.setOpaque(false);
        southRow.add(closeBtn);
        root.add(southRow, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel buildPowerupCard(Object[] pw) {
        String name = (String) pw[0];
        int    cost = (int)    pw[1];
        String desc = (String) pw[2];
        String id   = (String) pw[3];

        JPanel card = new JPanel(new BorderLayout(10,0));
        card.setBackground(new Color(16,16,40));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60,60,120), 1),
                new EmptyBorder(8,12,8,12)));

        JPanel info = new JPanel(new GridLayout(2,1,0,2));
        info.setOpaque(false);
        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(new Font("Monospaced", Font.BOLD, 13));
        nameLabel.setForeground(AuthManager.COL_ACCENT);
        JLabel descLabel = new JLabel(desc);
        descLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
        descLabel.setForeground(new Color(140,140,180));
        info.add(nameLabel); info.add(descLabel);

        JPanel rightPanel = new JPanel(new GridLayout(2,1,0,4));
        rightPanel.setOpaque(false);
        JLabel costLabel = new JLabel("$" + cost, SwingConstants.CENTER);
        costLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
        costLabel.setForeground(new Color(255,220,60));

        JButton buyBtn = new JButton("BUY");
        buyBtn.setFont(new Font("Monospaced", Font.BOLD, 11));
        buyBtn.setForeground(new Color(20,20,40));
        buyBtn.setBackground(new Color(255,200,0));
        buyBtn.setFocusPainted(false);
        buyBtn.setBorder(new EmptyBorder(4,10,4,10));
        buyBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        buyBtn.addActionListener(e -> handleBuy(id, name, cost));

        rightPanel.add(costLabel); rightPanel.add(buyBtn);

        card.add(info,       BorderLayout.CENTER);
        card.add(rightPanel, BorderLayout.EAST);
        return card;
    }

    private void handleBuy(String id, String name, int cost) {
        if (game.getMoney() < cost) {
            JOptionPane.showMessageDialog(this,
                    "Not enough coins!\nYou need $" + cost + " but have $" + game.getMoney(),
                    "Insufficient Funds", JOptionPane.WARNING_MESSAGE);
            return;
        }
        game.spendMoney(cost);
        game.applyPowerup(id);
        coinsLabel.setText("Your Coins: $" + game.getMoney());
        JOptionPane.showMessageDialog(this,
                name + " activated!\nCoins remaining: $" + game.getMoney(),
                "Powerup Activated", JOptionPane.INFORMATION_MESSAGE);
    }
}

// =============================================================================
// ScoreManager — Static utility for score file I/O
// =============================================================================
class ScoreManager {
    private ScoreManager() {}

    public static void saveScore(String filepath, String username, int score) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());
        List<String[]> all = loadAllScores(filepath);
        all.add(new String[]{username, String.valueOf(score), timestamp});
        all.sort((a, b) -> Integer.compare(Integer.parseInt(b[1]), Integer.parseInt(a[1])));

        try (PrintWriter pw = new PrintWriter(new FileWriter(filepath))) {
            for (String[] entry : all) {
                pw.println(entry[0] + ":" + entry[1] + ":" + entry[2]);
            }
        } catch (IOException e) {
            System.err.println("[ScoreManager] Failed to write scores: " + e.getMessage());
        }
    }

    public static List<String[]> loadAllScores(String filepath) {
        List<String[]> list = new ArrayList<>();
        File f = new File(filepath);
        if (!f.exists()) return list;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":", 3);
                if (parts.length >= 2) {
                    list.add(new String[]{
                        parts[0],
                        parts[1],
                        parts.length == 3 ? parts[2] : ""
                    });
                }
            }
        } catch (IOException e) {
            System.err.println("[ScoreManager] Failed to read scores: " + e.getMessage());
        }
        list.sort((a,b) -> {
            try { return Integer.compare(Integer.parseInt(b[1]), Integer.parseInt(a[1])); }
            catch (NumberFormatException ex) { return 0; }
        });
        return list;
    }
}

// =============================================================================
// GameState — Data transfer object for HUD updates
// =============================================================================
class GameState {
    final int    score;
    final int    money;
    final int    lives;
    final int    multiplier;
    final String activePowerup;
    final double ballSpeed;

    GameState(int score, int money, int lives, int multiplier,
              String activePowerup, double ballSpeed) {
        this.score         = score;
        this.money         = money;
        this.lives         = lives;
        this.multiplier    = multiplier;
        this.activePowerup = activePowerup;
        this.ballSpeed     = ballSpeed;
    }
}

// =============================================================================
// GameListener — Callback interface from PinballPanel to GameWindow
// =============================================================================
interface GameListener {
    void onGameStateChanged(GameState state);
    void onGameOver(int finalScore);
}