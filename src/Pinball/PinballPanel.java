package Pinball;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/**
 * =============================================================================
 * PinballPanel.java — Core Game Engine, Physics & Rendering
 * =============================================================================
 * Self-contained JPanel that drives the entire pinball simulation:
 *
 *  Physics
 *  ───────
 *  • Ball dynamics: velocity, gravity, drag, wall reflection
 *  • Flipper rotation via angular constraints (left/right independently)
 *  • Bumper collision: circular intersection + impulse response
 *  • Slingshot zones: triangular trigger regions on side walls
 *  • Lost-ball detection: ball exits bottom → life lost, ball re-launched
 *
 *  Game Mechanics
 *  ──────────────
 *  • Score awarded per bumper hit, scaled by multiplier
 *  • Money awarded per bumper hit (base 10 coins, MEGABUMP triples it)
 *  • Powerup system (see applyPowerup()):
 *      MULTI2     – 2× score multiplier for 30 s
 *      MAGNET     – horizontal force toward centre (20 s)
 *      FIREBALL   – ball paints bumpers red, destroys on 10 hits (visual)
 *      SLOWMO     – velocity halved for 15 s
 *      EXTRALIFE  – +1 life
 *      MEGABUMP   – bumper coin value ×3 for 20 s
 *
 *  Rendering
 *  ─────────
 *  • Double-buffered via Swing (paintComponent)
 *  • Neon glow simulation via multiple translucent concentric shapes
 *  • Particle system: hit sparks, coin popups, trail
 *  • Animated score/coin popup text floats upward on each bumper hit
 *
 *  Controls
 *  ────────
 *  ← / Z    – left flipper up
 *  → / /    – right flipper up
 *  Space    – launch ball (when waiting)
 *  S        – open Store (delegated to GameWindow)
 *  R        – restart game
 *  Esc      – quit
 * =============================================================================
 */
public class PinballPanel extends JPanel implements ActionListener, KeyListener {

    // ── Dimensions ─────────────────────────────────────────────────────────────
    static final int W = 520;   // playfield width
    static final int H = 720;   // playfield height

    // ── Physics Constants ──────────────────────────────────────────────────────
    private static final double GRAVITY       = 0.38;
    private static final double FRICTION      = 0.987;
    private static final double BALL_RADIUS   = 11.0;
    private static final double WALL_RESTITUTION = 0.72;
    private static final double BUMPER_RESTITUTION= 1.5;
    private static final double FLIPPER_IMPULSE   = 18.0;

    // ── Flipper Geometry ───────────────────────────────────────────────────────
    private static final int   FLIPPER_W   = 70;
    private static final int   FLIPPER_H   = 12;
    private static final int   LF_X        = 100;   // left flipper pivot X
    private static final int   RF_X        = W - 100;// right flipper pivot X
    private static final int   FLIPPER_Y   = H - 90;
    private static final double FLIPPER_UP  = -Math.PI / 5;
    private static final double FLIPPER_DOWN=  Math.PI / 5;
    private static final double FLIPPER_SPD = 0.50;

    // ── Ball Launch Position ───────────────────────────────────────────────────
    private static final int LAUNCH_X = W - 26;
    private static final int LAUNCH_Y = H - 140;

    // ── Game State ─────────────────────────────────────────────────────────────
    private int     score       = 0;
    private int     money       = 0;
    private int     lives       = 3;
    private int     multiplier  = 1;
    private boolean gameOver    = false;
    private boolean paused      = false;
    private boolean ballInPlay  = false;
    private boolean leftFlipperUp  = false;
    private boolean rightFlipperUp = false;
    private double  leftFlipperAngle  = FLIPPER_DOWN;
    private double  rightFlipperAngle = -FLIPPER_DOWN;

    // ── Ball ───────────────────────────────────────────────────────────────────
    private double bx, by, bvx, bvy;

    // ── Bumpers ───────────────────────────────────────────────────────────────
    private List<Bumper>    bumpers    = new ArrayList<>();
    private List<Slingshot> slingshots = new ArrayList<>();

    // ── Particles ─────────────────────────────────────────────────────────────
    private List<Particle>   particles   = new ArrayList<>();
    private List<PopupText>  popupTexts  = new ArrayList<>();
    private List<Point2D.Double> ballTrail = new LinkedList<>();
    private static final int TRAIL_LEN = 18;

    // ── Powerup State ─────────────────────────────────────────────────────────
    private String activePowerup    = "";
    private long   powerupEndMillis = 0;
    private int    fireballHits     = 0;
    private boolean megaBumpActive  = false;
    private boolean magnetActive    = false;
    private boolean slowMoActive    = false;

    // ── Tick Timer ────────────────────────────────────────────────────────────
    private final javax.swing.Timer timer;

    // ── Callback ──────────────────────────────────────────────────────────────
    private final GameListener listener;

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color COL_FIELD   = new Color( 8,  8, 22);
    private static final Color COL_WALL    = new Color(40, 40,100);
    private static final Color COL_BALL    = new Color(220,230,255);
    private static final Color COL_FLIPPER = new Color( 0,200,255);

    // ==========================================================================
    // Constructor
    // ==========================================================================
    /**
     * Initialises the playfield, bumpers, slingshots, ball and game timer.
     *
     * @param listener callback target (typically GameWindow)
     */
    public PinballPanel(GameListener listener) {
        this.listener = listener;
        setPreferredSize(new Dimension(W, H));
        setBackground(COL_FIELD);
        setFocusable(true);
        addKeyListener(this);

        initBumpers();
        initSlingshots();
        resetBall();

        timer = new javax.swing.Timer(16, this);  // ~60 fps
        timer.start();
    }

    // ==========================================================================
    // Initialisation
    // ==========================================================================

    /** Places circular bumpers in a classic diamond/triangular layout. */
    private void initBumpers() {
        bumpers.clear();
        // Top cluster
        bumpers.add(new Bumper(W/2,      150, 28, new Color( 0,220,255)));
        bumpers.add(new Bumper(W/2 - 80, 210, 24, new Color(255, 60,180)));
        bumpers.add(new Bumper(W/2 + 80, 210, 24, new Color(255, 60,180)));
        bumpers.add(new Bumper(W/2,      270, 24, new Color(120,255, 60)));
        // Mid cluster
        bumpers.add(new Bumper(W/2 - 110,340, 22, new Color(255,200,  0)));
        bumpers.add(new Bumper(W/2 + 110,340, 22, new Color(255,200,  0)));
        bumpers.add(new Bumper(W/2,      370, 20, new Color(200, 80,255)));
        // Lower pair
        bumpers.add(new Bumper(W/2 - 70, 440, 20, new Color( 0,255,160)));
        bumpers.add(new Bumper(W/2 + 70, 440, 20, new Color( 0,255,160)));
    }

    /** Creates the two slingshot regions on the inner side walls. */
    private void initSlingshots() {
        slingshots.clear();
        // Left slingshot (triangular zone near left wall)
        slingshots.add(new Slingshot(
                new int[]{50, 110, 50},
                new int[]{480, 430, 380},
                new Color(255,150,0), true));
        // Right slingshot
        slingshots.add(new Slingshot(
                new int[]{W-50, W-110, W-50},
                new int[]{480,  430,   380},
                new Color(255,150,0), false));
    }

    /** Positions the ball at the launch lane and zeroes velocity. */
    private void resetBall() {
        bx  = LAUNCH_X;
        by  = LAUNCH_Y;
        bvx = 0;
        bvy = 0;
        ballInPlay = false;
        ballTrail.clear();
    }

    // ==========================================================================
    // Public API (called by GameWindow / Store)
    // ==========================================================================

    /** @return current coin count */
    public int getMoney() { return money; }

    /** @return current score */
    public int getCurrentScore() { return score; }

    /**
     * Deducts coins after a successful store purchase.
     * @param amount coins to deduct (must be ≤ current money)
     */
    public void spendMoney(int amount) { money = Math.max(0, money - amount); }

    /**
     * Pauses or resumes the game timer.
     * @param pause true to pause, false to resume
     */
    public void pauseGame(boolean pause) {
        paused = pause;
        if (pause) timer.stop(); else { timer.start(); requestFocusInWindow(); }
    }

    /** Resets all state and starts a fresh game. */
    public void restartGame() {
        score       = 0;  money   = 0;
        lives       = 3;  multiplier = 1;
        gameOver    = false; paused = false;
        activePowerup = ""; powerupEndMillis = 0;
        megaBumpActive = magnetActive = slowMoActive = false;
        fireballHits = 0;
        particles.clear(); popupTexts.clear(); ballTrail.clear();
        initBumpers(); initSlingshots();
        resetBall();
        if (!timer.isRunning()) timer.start();
        requestFocusInWindow();
    }

    /**
     * Activates a powerup purchased from the store.
     *
     * @param id powerup identifier matching store definitions
     */
    public void applyPowerup(String id) {
        long now = System.currentTimeMillis();
        switch (id) {
            case "MULTI2":
                multiplier = 2;
                activePowerup    = "MULTI ×2";
                powerupEndMillis = now + 30_000;
                break;
            case "MAGNET":
                magnetActive     = true;
                activePowerup    = "MAGNET";
                powerupEndMillis = now + 20_000;
                break;
            case "FIREBALL":
                fireballHits     = 10;
                activePowerup    = "FIREBALL";
                powerupEndMillis = now + Long.MAX_VALUE / 2; // ends on 10 hits
                break;
            case "SLOWMO":
                slowMoActive     = true;
                bvx *= 0.5; bvy *= 0.5;
                activePowerup    = "SLOW-MO";
                powerupEndMillis = now + 15_000;
                break;
            case "EXTRALIFE":
                lives = Math.min(lives + 1, 5);
                activePowerup    = "EXTRA LIFE";
                powerupEndMillis = now + 2_000; // brief display
                break;
            case "MEGABUMP":
                megaBumpActive   = true;
                activePowerup    = "MEGA BUMPER";
                powerupEndMillis = now + 20_000;
                break;
        }
    }

    // ==========================================================================
    // Game Loop (ActionListener)
    // ==========================================================================

    /**
     * Main game loop tick, called by the Swing timer at ~60 fps.
     * Steps: expire powerups → physics → collisions → boundary → particles
     *        → notify listener → repaint.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (paused || gameOver) return;
        expirePowerups();
        updateFlippers();
        if (ballInPlay) {
            applyPhysics();
            handleWallCollisions();
            handleBumperCollisions();
            handleSlingshotCollisions();
            handleFlipperCollisions();
            checkBallLost();
        }
        updateParticles();
        updatePopups();
        notifyListener();
        repaint();
    }

    // ── Physics ────────────────────────────────────────────────────────────────

    private void applyPhysics() {
        bvy += GRAVITY;
        bvx *= FRICTION;
        bvy *= FRICTION;

        if (magnetActive) {
            // Gentle horizontal pull toward centre
            double centre = W / 2.0;
            bvx += (centre - bx) * 0.003;
        }

        double speedCap = slowMoActive ? 10.0 : 20.0;
        double speed = Math.sqrt(bvx*bvx + bvy*bvy);
        if (speed > speedCap) { bvx = bvx/speed*speedCap; bvy = bvy/speed*speedCap; }

        bx += bvx;
        by += bvy;

        // Update trail
        ballTrail.add(new Point2D.Double(bx, by));
        if (ballTrail.size() > TRAIL_LEN) ballTrail.remove(0);
    }

    private void updateFlippers() {
        double target = leftFlipperUp ? FLIPPER_UP : FLIPPER_DOWN;
        leftFlipperAngle  += (target - leftFlipperAngle)  * FLIPPER_SPD * 3;

        double rTarget = rightFlipperUp ? -FLIPPER_UP : -FLIPPER_DOWN;
        rightFlipperAngle += (rTarget - rightFlipperAngle) * FLIPPER_SPD * 3;
    }

    // ── Wall Collisions ────────────────────────────────────────────────────────

    private void handleWallCollisions() {
        // Left wall
        if (bx - BALL_RADIUS < 30) {
            bx  = 30 + BALL_RADIUS;
            bvx = Math.abs(bvx) * WALL_RESTITUTION;
            spawnWallSparks(bx, by);
        }
        // Right wall (leave gap for launch lane)
        if (bx + BALL_RADIUS > W - 30) {
            bx  = W - 30 - BALL_RADIUS;
            bvx = -Math.abs(bvx) * WALL_RESTITUTION;
            spawnWallSparks(bx, by);
        }
        // Top wall
        if (by - BALL_RADIUS < 20) {
            by  = 20 + BALL_RADIUS;
            bvy = Math.abs(bvy) * WALL_RESTITUTION;
        }
    }

    // ── Bumper Collisions ──────────────────────────────────────────────────────

    private void handleBumperCollisions() {
        for (Iterator<Bumper> it = bumpers.iterator(); it.hasNext(); ) {
            Bumper b = it.next();
            double dx = bx - b.x;
            double dy = by - b.y;
            double dist = Math.sqrt(dx*dx + dy*dy);
            double minDist = BALL_RADIUS + b.radius;

            if (dist < minDist && dist > 0.1) {
                // Reflect ball outward
                double nx = dx / dist, ny = dy / dist;
                double dot = bvx*nx + bvy*ny;
                bvx = (bvx - 2*dot*nx) * BUMPER_RESTITUTION;
                bvy = (bvy - 2*dot*ny) * BUMPER_RESTITUTION;

                // Push ball out of bumper
                double overlap = minDist - dist + 1;
                bx += nx * overlap;
                by += ny * overlap;

                onBumperHit(b, it);
            }
        }
    }

    /**
     * Handles the game logic consequences of hitting a bumper.
     *
     * @param b  the struck bumper
     * @param it iterator (for fireball removal)
     */
    private void onBumperHit(Bumper b, Iterator<Bumper> it) {
        // Score
        int pts = 100 * multiplier;
        score += pts;

        // Money
        int coins = megaBumpActive ? 30 : 10;
        money += coins;

        // Fireball: remove bumper after n hits
        if (fireballHits > 0) {
            b.fireballHits++;
            if (b.fireballHits >= 3) {
                it.remove();
                spawnExplosion(b.x, b.y, b.color);
                fireballHits--;
                if (fireballHits <= 0) {
                    activePowerup = "";
                }
                return;
            }
        }

        // Flash bumper
        b.flash();
        // Sparks
        spawnBumperSparks(b.x, b.y, b.color);
        // Popup text
        popupTexts.add(new PopupText(
                "+" + pts + "  $" + coins,
                (int)b.x, (int)b.y - (int)b.radius - 10, b.color));
    }

    // ── Slingshot Collisions ───────────────────────────────────────────────────

    private void handleSlingshotCollisions() {
        for (Slingshot s : slingshots) {
            if (s.contains(bx, by)) {
                // Kick ball toward centre
                if (s.isLeft) {
                    bvx =  Math.abs(bvx) * 1.4 + 3;
                } else {
                    bvx = -Math.abs(bvx) * 1.4 - 3;
                }
                bvy *= -0.8;
                score += 25 * multiplier;
                s.flash();
                spawnBumperSparks(bx, by, s.color);
            }
        }
    }

    // ── Flipper Collisions ────────────────────────────────────────────────────

    /**
     * Simplified flipper collision: treats each flipper as a rectangle and
     * applies an upward impulse when the ball intersects it while moving up.
     */
    private void handleFlipperCollisions() {
        // Left flipper
        checkFlipperHit(LF_X, FLIPPER_Y, leftFlipperAngle, true);
        // Right flipper
        checkFlipperHit(RF_X, FLIPPER_Y, rightFlipperAngle, false);
    }

    private void checkFlipperHit(int px, int py, double angle, boolean isLeft) {
        // Approximate the flipper tip position
        double tipX = px + Math.cos(angle) * FLIPPER_W * (isLeft ? 1 : -1);
        double tipY = py + Math.sin(angle) * FLIPPER_W;

        // Midpoint of flipper segment
        double midX = (px + tipX) / 2;
        double midY = (py + tipY) / 2;

        double dx = bx - midX;
        double dy = by - midY;
        double dist = Math.sqrt(dx*dx + dy*dy);

        if (dist < BALL_RADIUS + FLIPPER_H + 6) {
            // Push ball upward with flipper impulse
            boolean flipping = isLeft ? leftFlipperUp : rightFlipperUp;
            if (flipping) {
                bvy = -FLIPPER_IMPULSE;
                bvx += isLeft ? 2.0 : -2.0;
            } else {
                // Passive: just deflect
                if (bvy > 0) bvy = -bvy * 0.5;
            }
            by = Math.min(by, py - BALL_RADIUS - 2);
        }
    }

    // ── Ball Lost ─────────────────────────────────────────────────────────────

    private void checkBallLost() {
        if (by > H + 30) {
            lives--;
            if (lives <= 0) {
                gameOver = true;
                timer.stop();
                listener.onGameOver(score);
            } else {
                resetBall();
            }
        }
    }

    // ── Powerup Expiry ─────────────────────────────────────────────────────────

    private void expirePowerups() {
        if (activePowerup.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now > powerupEndMillis) {
            // Reset the relevant flag
            switch (activePowerup) {
                case "MULTI ×2":  multiplier = 1;        break;
                case "MAGNET":    magnetActive  = false;  break;
                case "SLOW-MO":   slowMoActive  = false;  break;
                case "MEGA BUMPER": megaBumpActive = false; break;
            }
            activePowerup = "";
        }
    }

    // ── Particle System ───────────────────────────────────────────────────────

    private void spawnBumperSparks(double x, double y, Color col) {
        Random rnd = new Random();
        for (int i = 0; i < 12; i++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            double spd   = 2 + rnd.nextDouble() * 4;
            particles.add(new Particle(x, y,
                    Math.cos(angle)*spd, Math.sin(angle)*spd, col, 22 + rnd.nextInt(14)));
        }
    }

    private void spawnWallSparks(double x, double y) {
        Random rnd = new Random();
        for (int i = 0; i < 5; i++) {
            double angle = rnd.nextDouble() * Math.PI;
            particles.add(new Particle(x, y,
                    Math.cos(angle)*3, -Math.abs(Math.sin(angle))*3,
                    new Color(100,180,255), 14));
        }
    }

    private void spawnExplosion(double x, double y, Color col) {
        Random rnd = new Random();
        for (int i = 0; i < 30; i++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            double spd   = 2 + rnd.nextDouble() * 7;
            particles.add(new Particle(x, y,
                    Math.cos(angle)*spd, Math.sin(angle)*spd, col, 40 + rnd.nextInt(20)));
        }
    }

    private void updateParticles() {
        particles.removeIf(p -> { p.update(); return p.isDead(); });
    }

    private void updatePopups() {
        popupTexts.removeIf(p -> { p.update(); return p.isDead(); });
    }

    // ── Listener Notification ─────────────────────────────────────────────────

    private void notifyListener() {
        double speed = Math.sqrt(bvx*bvx + bvy*bvy);
        listener.onGameStateChanged(new GameState(
                score, money, lives, multiplier, activePowerup, speed));
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawBackground(g2);
        drawWalls(g2);
        drawSlingshots(g2);
        drawBumpers(g2);
        drawBallTrail(g2);
        drawBall(g2);
        drawFlippers(g2);
        drawParticles(g2);
        drawPopups(g2);
        drawLaunchHint(g2);
        drawGameOverOverlay(g2);
        drawPauseOverlay(g2);
    }

    private void drawBackground(Graphics2D g2) {
        // Radial gradient for depth
        RadialGradientPaint bg = new RadialGradientPaint(
                new Point2D.Float(W/2f, H/2f), H * 0.7f,
                new float[]{0f, 1f},
                new Color[]{new Color(12,12,35), new Color(4,4,15)});
        g2.setPaint(bg);
        g2.fillRect(0, 0, W, H);

        // Subtle grid
        g2.setColor(new Color(255,255,255,8));
        for (int x = 0; x < W; x += 40) g2.drawLine(x, 0, x, H);
        for (int y = 0; y < H; y += 40) g2.drawLine(0, y, W, y);
    }

    private void drawWalls(Graphics2D g2) {
        g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(COL_WALL);

        // Left wall
        g2.drawLine(30, 20, 30, H);
        // Right wall (with gap for launch lane)
        g2.drawLine(W-30, 20, W-30, H-170);
        // Launch lane
        g2.setColor(new Color(60,60,120));
        g2.drawLine(W-50, H-170, W-50, H);
        // Top
        g2.setColor(COL_WALL);
        g2.drawLine(30, 20, W-30, 20);

        // Diagonal gutters
        g2.setStroke(new BasicStroke(3f));
        g2.setColor(new Color(80,80,150));
        g2.drawLine(30, H-120, 100, FLIPPER_Y + 10);
        g2.drawLine(W-30, H-120, W-100, FLIPPER_Y + 10);
    }

    private void drawSlingshots(Graphics2D g2) {
        for (Slingshot s : slingshots) {
            int alpha = s.isFlashing ? 200 : 80;
            g2.setColor(new Color(s.color.getRed(), s.color.getGreen(), s.color.getBlue(), alpha));
            g2.fillPolygon(s.xs, s.ys, 3);
            g2.setColor(s.color);
            g2.setStroke(new BasicStroke(2f));
            g2.drawPolygon(s.xs, s.ys, 3);
            s.updateFlash();
        }
    }

    private void drawBumpers(Graphics2D g2) {
        for (Bumper b : bumpers) {
            int r = (int) b.radius;
            boolean fireball = fireballHits > 0;

            // Glow layers
            for (int glow = 4; glow >= 1; glow--) {
                int glowAlpha = b.isFlashing ? 80 : 30;
                Color gc = fireball ? new Color(255,80,0,glowAlpha*glow)
                                    : new Color(b.color.getRed(), b.color.getGreen(),
                                               b.color.getBlue(), glowAlpha * glow);
                g2.setColor(gc);
                int gr = r + glow * 5;
                g2.fillOval((int)b.x - gr, (int)b.y - gr, gr*2, gr*2);
            }

            // Main circle
            Color fill = b.isFlashing
                    ? (fireball ? new Color(255,120,0) : b.color.brighter())
                    : (fireball ? new Color(180,40,0) : new Color(20,20,50));
            g2.setColor(fill);
            g2.fillOval((int)b.x-r, (int)b.y-r, r*2, r*2);

            // Ring
            g2.setColor(fireball ? new Color(255,100,0) : b.color);
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawOval((int)b.x-r, (int)b.y-r, r*2, r*2);

            // Inner highlight
            g2.setColor(new Color(255,255,255,60));
            g2.fillOval((int)b.x-r/2, (int)b.y-r/2, r/2, r/2);

            b.updateFlash();
        }
    }

    private void drawBallTrail(Graphics2D g2) {
        int n = ballTrail.size();
        for (int i = 0; i < n; i++) {
            Point2D.Double p = ballTrail.get(i);
            float t = (float) i / n;
            int alpha = (int)(t * 120);
            double tr = BALL_RADIUS * t * 0.8;
            Color trailCol = fireballHits > 0
                    ? new Color(255, (int)(100*t), 0, alpha)
                    : new Color(100, 180, 255, alpha);
            g2.setColor(trailCol);
            g2.fillOval((int)(p.x - tr), (int)(p.y - tr), (int)(tr*2), (int)(tr*2));
        }
    }

    private void drawBall(Graphics2D g2) {
        int r = (int) BALL_RADIUS;

        // Glow
        for (int glow = 3; glow >= 1; glow--) {
            Color gc = fireballHits > 0
                    ? new Color(255,120,0,30*glow)
                    : new Color(100,180,255,25*glow);
            g2.setColor(gc);
            int gr = r + glow*4;
            g2.fillOval((int)bx-gr, (int)by-gr, gr*2, gr*2);
        }

        // Ball body
        Color ballBody = fireballHits > 0 ? new Color(255,140,40) : COL_BALL;
        g2.setColor(ballBody);
        g2.fillOval((int)bx-r, (int)by-r, r*2, r*2);

        // Highlight
        g2.setColor(new Color(255,255,255,180));
        g2.fillOval((int)bx-r/3-2, (int)by-r/3-2, r/2, r/2);
    }

    private void drawFlippers(Graphics2D g2) {
        g2.setStroke(new BasicStroke(FLIPPER_H, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Glow
        g2.setColor(new Color(0,200,255,60));
        g2.setStroke(new BasicStroke(FLIPPER_H + 8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        drawFlipperShape(g2, LF_X, FLIPPER_Y, leftFlipperAngle, true);
        drawFlipperShape(g2, RF_X, FLIPPER_Y, rightFlipperAngle, false);

        // Main
        g2.setColor(COL_FLIPPER);
        g2.setStroke(new BasicStroke(FLIPPER_H, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        drawFlipperShape(g2, LF_X, FLIPPER_Y, leftFlipperAngle, true);
        drawFlipperShape(g2, RF_X, FLIPPER_Y, rightFlipperAngle, false);

        // Pivot dots
        g2.setColor(new Color(0,255,220));
        g2.fillOval(LF_X-5, FLIPPER_Y-5, 10, 10);
        g2.fillOval(RF_X-5, FLIPPER_Y-5, 10, 10);
    }

    private void drawFlipperShape(Graphics2D g2, int px, int py, double angle, boolean isLeft) {
        int dir = isLeft ? 1 : -1;
        int tx = (int)(px + Math.cos(angle) * FLIPPER_W * dir);
        int ty = (int)(py + Math.sin(angle) * FLIPPER_W);
        g2.drawLine(px, py, tx, ty);
    }

    private void drawParticles(Graphics2D g2) {
        for (Particle p : particles) {
            float alpha = (float) p.life / p.maxLife;
            Color c = new Color(
                    p.color.getRed()/255f,
                    p.color.getGreen()/255f,
                    p.color.getBlue()/255f, alpha);
            g2.setColor(c);
            int sz = Math.max(1, (int)(4 * alpha));
            g2.fillOval((int)p.x-sz/2, (int)p.y-sz/2, sz, sz);
        }
    }

    private void drawPopups(Graphics2D g2) {
        for (PopupText p : popupTexts) {
            float alpha = (float) p.life / p.maxLife;
            g2.setFont(new Font("Monospaced", Font.BOLD, 13));
            g2.setColor(new Color(p.color.getRed()/255f,
                    p.color.getGreen()/255f, p.color.getBlue()/255f, alpha));
            g2.drawString(p.text, p.x, p.y);
        }
    }

    private void drawLaunchHint(Graphics2D g2) {
        if (!ballInPlay && !gameOver) {
            g2.setFont(new Font("Monospaced", Font.BOLD, 14));
            g2.setColor(AuthManager.COL_ACCENT);
            g2.drawString("SPACE to launch", W/2 - 70, H/2);
        }
    }

    private void drawGameOverOverlay(Graphics2D g2) {
        if (!gameOver) return;
        g2.setColor(new Color(0,0,0,160));
        g2.fillRect(0, 0, W, H);
        g2.setFont(new Font("Monospaced", Font.BOLD, 36));
        g2.setColor(AuthManager.COL_ACCENT2);
        g2.drawString("GAME OVER", W/2-100, H/2-20);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 18));
        g2.setColor(AuthManager.COL_TEXT);
        g2.drawString("Score: " + score, W/2-50, H/2+20);
    }

    private void drawPauseOverlay(Graphics2D g2) {
        if (!paused) return;
        g2.setColor(new Color(0,0,0,120));
        g2.fillRect(0, 0, W, H);
        g2.setFont(new Font("Monospaced", Font.BOLD, 32));
        g2.setColor(new Color(255,220,60));
        g2.drawString("PAUSED", W/2-65, H/2);
    }

    // ==========================================================================
    // KeyListener
    // ==========================================================================

    @Override public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        int kc = e.getKeyCode();
        switch (kc) {
            case KeyEvent.VK_LEFT:  case KeyEvent.VK_Z: leftFlipperUp  = true; break;
            case KeyEvent.VK_RIGHT: case KeyEvent.VK_SLASH: rightFlipperUp = true; break;
            case KeyEvent.VK_SPACE:
                if (!ballInPlay && !gameOver) {
                    ballInPlay = true;
                    bvx = -3.0;
                    bvy = -14.0;
                }
                break;
            case KeyEvent.VK_S:
                if (!gameOver && listener instanceof GameWindow) {
                    ((GameWindow) listener).openStore();
                }
                break;
            case KeyEvent.VK_R: restartGame(); break;
            case KeyEvent.VK_ESCAPE: System.exit(0); break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int kc = e.getKeyCode();
        switch (kc) {
            case KeyEvent.VK_LEFT:  case KeyEvent.VK_Z: leftFlipperUp  = false; break;
            case KeyEvent.VK_RIGHT: case KeyEvent.VK_SLASH: rightFlipperUp = false; break;
        }
    }
}

// =============================================================================
// Bumper — Circular obstacle that scores and coins on hit
// =============================================================================
/**
 * Circular bumper placed on the playfield. Tracks its own flash state and
 * fireball hit counter for visual feedback.
 */
class Bumper {
    double x, y, radius;
    Color  color;
    boolean isFlashing  = false;
    int     flashTimer  = 0;
    int     fireballHits= 0;

    Bumper(double x, double y, double r, Color c) {
        this.x = x; this.y = y; this.radius = r; this.color = c;
    }

    /** Triggers the visual flash effect on collision. */
    void flash() { isFlashing = true; flashTimer = 6; }

    /** Called each tick to decrement flash duration. */
    void updateFlash() {
        if (isFlashing && --flashTimer <= 0) isFlashing = false;
    }
}

// =============================================================================
// Slingshot — Triangular side-wall kick zone
// =============================================================================
/**
 * Triangular region on the inner side walls that kicks the ball toward the
 * centre when entered.
 */
class Slingshot {
    int[]   xs, ys;
    Color   color;
    boolean isLeft;
    boolean isFlashing = false;
    int     flashTimer = 0;
    private Polygon poly;

    Slingshot(int[] xs, int[] ys, Color color, boolean isLeft) {
        this.xs = xs; this.ys = ys; this.color = color; this.isLeft = isLeft;
        this.poly = new Polygon(xs, ys, 3);
    }

    /** @return true if the given point lies inside this slingshot triangle */
    boolean contains(double x, double y) { return poly.contains(x, y); }

    void flash()       { isFlashing = true; flashTimer = 8; }
    void updateFlash() { if (isFlashing && --flashTimer <= 0) isFlashing = false; }
}

// =============================================================================
// Particle — Single spark emitted on collision
// =============================================================================
/**
 * Short-lived particle used for hit spark effects.
 */
class Particle {
    double x, y, vx, vy;
    Color  color;
    int    life, maxLife;

    Particle(double x, double y, double vx, double vy, Color c, int life) {
        this.x = x; this.y = y; this.vx = vx; this.vy = vy;
        this.color = c; this.life = this.maxLife = life;
    }

    void    update()  { x += vx; y += vy; vy += 0.2; vx *= 0.93; life--; }
    boolean isDead()  { return life <= 0; }
}

// =============================================================================
// PopupText — Floating score/coin announcement
// =============================================================================
/**
 * Short-lived floating text label that rises from a bumper hit location
 * to display the earned score and coin values.
 */
class PopupText {
    String text;
    int    x, y;
    Color  color;
    int    life, maxLife;

    PopupText(String text, int x, int y, Color color) {
        this.text = text; this.x = x; this.y = y; this.color = color;
        this.life = this.maxLife = 50;
    }

    void    update()  { y -= 1; life--; }
    boolean isDead()  { return life <= 0; }
}