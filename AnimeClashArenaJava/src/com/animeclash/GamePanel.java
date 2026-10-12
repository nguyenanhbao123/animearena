package com.animeclash;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * GamePanel — vòng lặp game chính, xử lý input, render
 * Chạy ở 60 FPS dùng javax.swing.Timer
 */
public class GamePanel extends JPanel implements ActionListener, KeyListener, MouseListener, MouseMotionListener {

    // ─── CONSTANTS ───────────────────────────────────
    public static final int W = AnimeClashArena.GAME_W;
    public static final int H = AnimeClashArena.GAME_H;
    public static final int FLOOR_Y    = 400;
    public static final int FPS        = 60;
    public static final int DELAY_MS   = 1000 / FPS;

    // Game States
    public enum State { TITLE, SELECT, COUNTDOWN, PLAYING, PAUSED, ROUND_END, GAME_OVER }

    // ─── FIELDS ──────────────────────────────────────
    private State gameState = State.SELECT;
    private Timer gameTimer;
    private int mouseX = 0, mouseY = 0;
    private boolean mouseClicked = false;
    private boolean showSkillInfo = false;
    private boolean showLoreInfo  = false;

    private Player  player;
    private EnemyAI enemy;
    private HUD     hud;
    private Background background;

    // Input
    private final Set<Integer> keysHeld    = new HashSet<>();
    private final Set<Integer> keysPressed = new HashSet<>();  // single-frame press

    // Rounds
    private int round    = 1;
    private int p1Wins   = 0;
    private int aiWins   = 0;
    private int timerSec = 99;
    private int timerTick = 0;  // frame counter for 1-second intervals

    // Countdown / announce
    private int  countdownVal   = 3;
    private int  countdownTimer = 0;
    private String announceText = "";
    private int  announceTimer  = 0;

    // Round end
    private String roundEndText  = "";
    private int    roundEndTimer = 0;

    // Score
    public static int highScore = 0;
    private int score = 0;
    private int timeBonus = 0;
    private int hpBonus = 0;
    private int perfectBonus = 0;
    private int winBonus = 0;
    private int totalScore = 0;
    private String finalRank = "";
    private int gameOverFrame = 0;
    private boolean perfectRound = true;

    // Particles & Effects
    private final List<Particle>  particles  = new ArrayList<>();
    private final List<HitEffect> hitEffects = new ArrayList<>();

    // ─── HITSTOP ─────────────────────────────────────
    // Freeze game vài frame khi đánh trúng → cảm giác "impact"
    private int hitstopTimer = 0;

    // ─── SCREEN SHAKE ────────────────────────────────
    private float shakeX = 0, shakeY = 0;
    private float shakeIntensity = 0;
    private int   shakeDuration  = 0;

    // Title screen animation
    private float titlePulse = 0f;
    private int   titleFrame = 0;

    // Double-tap dash detection
    private long lastLeftPress  = 0;
    private long lastRightPress = 0;

    // Edge detection for jump
    private boolean jumpKeyWasPressed = false;

    // ─── PAUSE MENU ──────────────────────────────────
    private int pauseMenuIndex = 0; // 0=Resume, 1=Restart, 2=Quit
    private State stateBeforePause = State.PLAYING;

    // ─── GAME MODE ──────────────────────────────────
    // 0 = 1P vs AI, 1 = 2P PvP
    private int gameMode = 0;
    private Player player2; // dùng cho PvP mode

    // ─── SELECT SCREEN ──────────────────────────────
    private int selectPhase = 0; // 0: Mode, 1: P1 Char, 2: P2 Char, 3: AI Difficulty
    private int selectIndex = 0; // index dùng chung cho menu hiện tại
    private int p1CharID = 0;
    private int p2CharID = 1;
    private int aiDifficulty = 1; // 0: Dễ, 1: Vừa, 2: Khó
    
    private final String[] charNames = {"Naru (Balanced)", "Gokai (Tanker)", "Kira (Mage)", "Zin (Sword)"};

    // ─── CONSTRUCTOR ─────────────────────────────────
    public GamePanel() {
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);

        background = new Background();
        player  = new Player(this, 0);
        enemy   = new EnemyAI(this, 1);
        hud     = new HUD(this);

        gameTimer = new Timer(DELAY_MS, this);
    }

    public void startGame() {
        requestFocusInWindow();
        gameTimer.start();
        SoundManager.playTitleBGM();
    }

    // ─── GAME LOOP ────────────────────────────────────
    @Override
    public void actionPerformed(ActionEvent e) {
        update();
        repaint();
        keysPressed.clear();
    }

    private void update() {
        titlePulse += 0.05f;
        background.update();

        // Screen shake decay
        if (shakeDuration > 0) {
            shakeDuration--;
            shakeX = (float)(Math.random() * 2 - 1) * shakeIntensity * (shakeDuration / 6f);
            shakeY = (float)(Math.random() * 2 - 1) * shakeIntensity * (shakeDuration / 6f);
            if (shakeDuration <= 0) { shakeX = 0; shakeY = 0; shakeIntensity = 0; }
        }

        switch (gameState) {
            case TITLE     -> updateTitle();
            case SELECT    -> updateSelect();
            case COUNTDOWN -> updateCountdown();
            case PLAYING   -> updatePlaying();
            case PAUSED    -> updatePaused();
            case ROUND_END -> updateRoundEnd();
            case GAME_OVER -> updateGameOver();
        }

        updateParticles();
        updateHitEffects();
    }

    private void updateTitle() {
        titleFrame++;
        // Nhấn Enter hoặc Space → bắt đầu trận đấu
        if (keysPressed.contains(KeyEvent.VK_ENTER) ||
            keysPressed.contains(KeyEvent.VK_SPACE)) {
            SoundManager.playRoundStart();
            SoundManager.playBattleBGM();
            startNewMatch();
        }
        // Nhấn ESC → Quay lại màn chọn chế độ / nhân vật
        if (keysPressed.contains(KeyEvent.VK_ESCAPE)) {
            gameState = State.SELECT;
            selectPhase = 0;
            selectIndex = 0;
        }
    }

    private void updateCountdown() {
        countdownTimer++;
        if (countdownTimer >= FPS) {  // mỗi giây giảm 1
            countdownTimer = 0;
            countdownVal--;
            if (countdownVal > 0) {
                SoundManager.playCountdown();
            } else if (countdownVal == 0) {
                SoundManager.playRoundStart();
            }
            
            if (countdownVal == 0) {
                // "READY..." đã được render 1 giây rồi, chuyển sang PLAYING
                announceText  = "FIGHT!";
                announceTimer = 60;
                gameState = State.PLAYING;
            }
        }
    }

    private void updatePlaying() {
        // ★ HITSTOP: khi timer > 0, skip entity update (chỉ render)
        if (hitstopTimer > 0) {
            hitstopTimer--;
            return;
        }

        // Announce fade
        if (announceTimer > 0) announceTimer--;

        // ── Timer đếm ngược ──
        timerTick++;
        if (timerTick >= FPS) {
            timerTick = 0;
            timerSec--;
            if (timerSec <= 0) {
                timerSec = 0;
                endRound(player.getHp() >= enemy.getHp() ? "p1" : "ai");
                return;
            }
        }

        // ── Input player ──
        handlePlayerInput();

        // ★ Sửa lỗi khựng phím WASD (do Unikey): Ở chế độ 1 người chơi, cho phép P1 dùng luôn phím Mũi Tên cho mượt
        if (gameMode == 0) {
            if (keysHeld.contains(KeyEvent.VK_LEFT)) keysHeld.add(KeyEvent.VK_A);
            if (keysHeld.contains(KeyEvent.VK_RIGHT)) keysHeld.add(KeyEvent.VK_D);
        }

        // ── Update entities ──
        player.update(keysHeld);
        if (gameMode == 1 && player2 != null) {
            player2.update(keysHeld);
        } else {
            enemy.update(player);
        }

        // ★ COLLISION PUSHBACK — đẩy 2 nhân vật ra khi chồng lên nhau
        CharacterBase c1 = player;
        CharacterBase c2 = (gameMode == 1 && player2 != null) ? player2 : enemy;
        float overlap = getOverlap(c1, c2);
        if (overlap > 0) {
            float push = overlap / 2f + 1f;
            if (c1.getCenterX() < c2.getCenterX()) {
                c1.x -= push;
                c2.x += push;
            } else {
                c1.x += push;
                c2.x -= push;
            }
            // Clamp to screen
            c1.x = Math.max(0, Math.min(W - c1.width, c1.x));
            c2.x = Math.max(0, Math.min(W - c2.width, c2.x));
        }

        // ── Check ko ──
        if (player.isDead() && gameState == State.PLAYING)
            endRound("ai");
        CharacterBase opponent = (gameMode == 1 && player2 != null) ? player2 : enemy;
        if (opponent.isDead() && gameState == State.PLAYING)
            endRound("p1");
    }

    /** Tính overlap giữa 2 hitbox */
    private float getOverlap(CharacterBase a, CharacterBase b) {
        float aLeft = a.x, aRight = a.x + a.width;
        float bLeft = b.x, bRight = b.x + b.width;
        float overlap = Math.min(aRight, bRight) - Math.max(aLeft, bLeft);
        return Math.max(0, overlap);
    }

    private void handlePlayerInput() {
        // Xác định đối thủ tùy game mode
        CharacterBase p1Target = (gameMode == 1 && player2 != null) ? player2 : enemy;

        // Dash — double tap A/A or D/D
        long now = System.currentTimeMillis();
        if (keysPressed.contains(KeyEvent.VK_A)) {
            if (now - lastLeftPress < 320)  player.doDash(-1);
            lastLeftPress = now;
        }
        if (keysPressed.contains(KeyEvent.VK_D)) {
            if (now - lastRightPress < 320) player.doDash(1);
            lastRightPress = now;
        }

        // Jump (Edge detection to prevent OS key repeat from triggering double jump instantly)
        boolean jumpKeyIsPressed = keysHeld.contains(KeyEvent.VK_W) || keysHeld.contains(KeyEvent.VK_SPACE);
        if (jumpKeyIsPressed && !jumpKeyWasPressed) {
            player.doJump();
        }
        jumpKeyWasPressed = jumpKeyIsPressed;

        // Ultimate: J + K + L simultaneously
        boolean jDown = keysHeld.contains(KeyEvent.VK_J);
        boolean kDown = keysHeld.contains(KeyEvent.VK_K);
        boolean lDown = keysHeld.contains(KeyEvent.VK_L);

        if (jDown && kDown && lDown) {
            player.doUltimate(p1Target);
        } else {
            // Attack
            if (keysPressed.contains(KeyEvent.VK_J)) {
                player.doAttack(p1Target);
            }
            // Skill 1
            if (keysPressed.contains(KeyEvent.VK_K)) {
                player.doSkill1(p1Target);
            }
            // Skill 2
            if (keysPressed.contains(KeyEvent.VK_L)) {
                player.doSkill2(p1Target);
            }
        }

        // ★ PLAYER 2 INPUT (PvP mode) — Arrow Keys + Numpad or Number row
        if (gameMode == 1 && player2 != null) {
            // P2 Jump
            if (keysPressed.contains(KeyEvent.VK_UP)) {
                player2.doJump();
            }

            // Các phím skill hỗ trợ cả Numpad (khi bật NumLock), phím End/Down/PgDn (khi tắt NumLock) và phím số hàng trên (1, 2, 3)
            boolean btn1 = keysPressed.contains(KeyEvent.VK_NUMPAD1) || keysPressed.contains(KeyEvent.VK_1) || keysPressed.contains(KeyEvent.VK_END);
            boolean btn2 = keysPressed.contains(KeyEvent.VK_NUMPAD2) || keysPressed.contains(KeyEvent.VK_2) || keysPressed.contains(KeyEvent.VK_DOWN);
            boolean btn3 = keysPressed.contains(KeyEvent.VK_NUMPAD3) || keysPressed.contains(KeyEvent.VK_3) || keysPressed.contains(KeyEvent.VK_PAGE_DOWN);

            // P2 Ultimate (1+2+3)
            boolean ult1Down = keysHeld.contains(KeyEvent.VK_NUMPAD1) || keysHeld.contains(KeyEvent.VK_1) || keysHeld.contains(KeyEvent.VK_END);
            boolean ult2Down = keysHeld.contains(KeyEvent.VK_NUMPAD2) || keysHeld.contains(KeyEvent.VK_2) || keysHeld.contains(KeyEvent.VK_DOWN);
            boolean ult3Down = keysHeld.contains(KeyEvent.VK_NUMPAD3) || keysHeld.contains(KeyEvent.VK_3) || keysHeld.contains(KeyEvent.VK_PAGE_DOWN);

            if (ult1Down && ult2Down && ult3Down) {
                player2.doUltimate(player);
            } else {
                // P2 Attack (1)
                if (btn1) {
                    player2.doAttack(player);
                }
                // P2 Skill 1 (2)
                if (btn2) {
                    player2.doSkill1(player);
                }
                // P2 Skill 2 (3)
                if (btn3) {
                    player2.doSkill2(player);
                }
            }

            // P2 movement: Arrow left/right handled in Player2's update via keysHeld
            // P2 facing toward P1
            float dx2 = player.getCenterX() - player2.getCenterX();
            player2.facing = dx2 > 0 ? 1 : -1;
        }
    }

    private void updateRoundEnd() {
        roundEndTimer--;
        if (roundEndTimer <= 0) {
            if (p1Wins >= 2 || aiWins >= 2) {
                calculateFinalScore();
                gameState = State.GAME_OVER;
                gameOverFrame = 0;
            } else {
                round++;
                startRound();
            }
        }
    }

    private void updateGameOver() {
        gameOverFrame++;
        
        // Key handling (allow skip anim)
        if (keysPressed.contains(KeyEvent.VK_ENTER)) {
            if (gameOverFrame < 320) {
                gameOverFrame = 320; // skip to end
            } else {
                gameState = State.SELECT;
                selectPhase = 0;
                selectIndex = 0;
                SoundManager.playTitleBGM();
            }
        }
        if (keysPressed.contains(KeyEvent.VK_ESCAPE)) {
            gameState = State.SELECT;
            selectPhase = 0;
            selectIndex = 0;
            SoundManager.playTitleBGM();
        }
    }

    private void calculateFinalScore() {
        boolean playerWon = p1Wins >= 2;
        if (playerWon) {
            winBonus = 2000;
        } else {
            winBonus = 0;
        }
        totalScore = score + timeBonus + hpBonus + perfectBonus + winBonus;
        if (totalScore > highScore) {
            highScore = totalScore;
        }

        if (totalScore >= 12000) finalRank = "S";
        else if (totalScore >= 10000) finalRank = "A";
        else if (totalScore >= 6000) finalRank = "B";
        else if (totalScore >= 3000) finalRank = "C";
        else finalRank = "D";
    }

    // ─── ROUND MANAGEMENT ────────────────────────────
    public void startNewMatch() {
        p1Wins  = 0;
        aiWins  = 0;
        round   = 1;
        score   = 0;
        timeBonus = 0;
        hpBonus = 0;
        perfectBonus = 0;
        winBonus = 0;
        totalScore = 0;
        finalRank = "";
        gameOverFrame = 0;
        startRound();
    }

    private void startRound() {
        timerSec    = 99;
        timerTick   = 0;
        perfectRound = true;
        particles.clear();
        hitEffects.clear();

        player.reset(160);
        if (gameMode == 1 && player2 != null) {
            player2.reset(720);
            player2.facing = -1;
        } else {
            enemy.reset(720);
        }

        countdownVal   = 3;
        countdownTimer = 0;
        gameState = State.COUNTDOWN;
        announceText  = "";
        announceTimer = 0;
    }

    public void endRound(String winner) {
        if (gameState != State.PLAYING) return;
        gameState = State.ROUND_END;
        SoundManager.playRoundEnd();

        if ("p1".equals(winner)) {
            p1Wins++;
            roundEndText = "K.O.!";
            // Calculate round bonuses
            timeBonus += timerSec * 50;
            hpBonus += (int)(player.getHpPercent() * 1000);
            if (perfectRound) perfectBonus += 1500;
            spawnBurst(enemy.getCenterX(), enemy.getCenterY(), 80, new Color(255,215,0));
        } else {
            aiWins++;
            spawnBurst(player.getCenterX(), player.getCenterY(), 80, new Color(255,45,120));
        }
        roundEndTimer = FPS * 3;  // 3 giây
    }

    // ─── PARTICLES ───────────────────────────────────
    public void spawnParticles(float x, float y, int count, Color color, float speed) {
        for (int i = 0; i < count; i++) {
            particles.add(new Particle(x, y, color, speed));
        }
    }

    public void spawnBurst(float x, float y, int count, Color color) {
        spawnParticles(x, y, count, color, 8f);
    }

    public void addHitEffect(float x, float y, int dmg, String type) {
        hitEffects.add(new HitEffect(x, y, dmg, type));
    }

    private void updateParticles() {
        particles.removeIf(p -> { p.update(); return p.isDead(); });
    }

    private void updateHitEffects() {
        hitEffects.removeIf(h -> { h.update(); return h.isDead(); });
    }

    // ─── RENDER ──────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // ★ SCREEN SHAKE: dịch toàn bộ canvas
        g2.translate(shakeX, shakeY);

        switch (gameState) {
            case TITLE     -> renderTitle(g2);
            case SELECT    -> renderSelect(g2);
            case COUNTDOWN -> { renderGame(g2); renderCountdown(g2); }
            case PLAYING   -> { renderGame(g2); renderAnnounce(g2); }
            case PAUSED    -> { renderGame(g2); renderPaused(g2); }
            case ROUND_END -> { renderGame(g2); renderRoundEnd(g2); }
            case GAME_OVER -> renderGameOver(g2);
        }

        // ★ Reset shake offset sau khi vẽ
        g2.translate(-shakeX, -shakeY);
    }

    // ─── TITLE SCREEN ────────────────────────────────
    private void renderTitle(Graphics2D g) {
        // Background
        background.render(g);

        // Dark overlay
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, 0, W, H);

        // Title glow layers
        float pulse = (float)(Math.sin(titlePulse) * 0.3f + 0.7f);

        // "ANIME CLASH ARENA"
        Font titleFont = new Font("Arial Black", Font.BOLD, 62);
        g.setFont(titleFont);
        String titleStr = "ANIME CLASH ARENA";
        FontMetrics fm = g.getFontMetrics();
        int tx = (W - fm.stringWidth(titleStr)) / 2;
        int ty = 120;

        // Shadow layers
        for (int i = 6; i > 0; i--) {
            g.setColor(new Color(255, 107, 0, (int)(20 * pulse)));
            g.drawString(titleStr, tx + i, ty + i);
        }
        // Glitch offset
        if (titleFrame % 120 < 5) {
            g.setColor(new Color(0, 212, 255, 160));
            g.drawString(titleStr, tx + 3, ty);
            g.setColor(new Color(255, 45, 120, 160));
            g.drawString(titleStr, tx - 3, ty);
        }
        // Main text
        GradientPaint gp = new GradientPaint(tx, ty-60, new Color(255,215,0), tx, ty, new Color(255,107,0));
        g.setPaint(gp);
        g.drawString(titleStr, tx, ty);

        // Subtitle
        Font subFont = new Font("Arial", Font.BOLD, 18);
        g.setFont(subFont);
        String subStr = "THE GLITCHED TOURNAMENT";
        fm = g.getFontMetrics();
        int sx = (W - fm.stringWidth(subStr)) / 2;
        g.setColor(new Color(0, 255, 204, (int)(200 * pulse)));
        g.drawString(subStr, sx, 160);

        // Divider line
        int lx1 = W/2 - 200, lx2 = W/2 + 200, ly = 180;
        LinearGradientPaint linePaint = new LinearGradientPaint(
                lx1, ly, lx2, ly,
                new float[]{0f, 0.5f, 1f},
                new Color[]{
                    new Color(255,215,0,0),
                    new Color(255,215,0,200),
                    new Color(255,215,0,0)
                });
        g.setPaint(linePaint);
        g.setStroke(new BasicStroke(2));
        g.drawLine(lx1, ly, lx2, ly);

        // ─── Character panels ───────────────────────────────
        drawTitleCharPanel(g, 180, 195, true, pulse);   // NARU (left)
        drawTitleCharPanel(g, W - 180, 195, false, pulse); // GOKAI (right)

        // ─── VS badge ──────────────────────────────────────
        int vsX = W / 2, vsY = 330;
        // VS glow ring
        g.setColor(new Color(255, 45, 120, (int)(40 * pulse)));
        g.fillOval(vsX - 50, vsY - 50, 100, 100);
        g.setColor(new Color(255, 45, 120, (int)(25 * pulse)));
        g.fillOval(vsX - 65, vsY - 65, 130, 130);
        // VS text
        Font vsFont = new Font("Arial Black", Font.BOLD, 52);
        g.setFont(vsFont);
        String vs = "VS";
        fm = g.getFontMetrics();
        // Shadow
        g.setColor(new Color(100, 0, 40, 180));
        g.drawString(vs, (W - fm.stringWidth(vs)) / 2 + 3, vsY + 3);
        // Main
        g.setColor(new Color(255, 45, 120, (int)(220 * pulse)));
        g.drawString(vs, (W - fm.stringWidth(vs)) / 2, vsY);

        // ─── Press Enter prompt ─────────────────────────────
        Font promptFont = new Font("Courier New", Font.BOLD, 16);
        g.setFont(promptFont);
        String prompt = titleFrame % 60 < 45 ? "▶  NHẤN ENTER ĐỂ BẮT ĐẦU  ◀" : "";
        fm = g.getFontMetrics();
        g.setColor(new Color(255, 215, 0));
        g.drawString(prompt, (W - fm.stringWidth(prompt)) / 2, 470);

        // ─── Controls hint ──────────────────────────────────
        Font hintFont = new Font("Arial", Font.PLAIN, 11);
        g.setFont(hintFont);
        g.setColor(new Color(255,255,255,120));
        String ctrl = "A/D: Di chuyển  |  W/Space: Nhảy  |  J: Đánh  |  K: Skill1  |  L: Skill2  |  J+K+L: ULTIMATE";
        fm = g.getFontMetrics();
        g.drawString(ctrl, (W - fm.stringWidth(ctrl)) / 2, 510);
    }

    private void drawTitleCharPanel(Graphics2D g, int cx, int cy, boolean isPlayer, float pulse) {
        float t = titleFrame * 0.05f;
        int cID = isPlayer ? p1CharID : p2CharID;

        // ─── Aura glow background ───────────────────────────
        Color auraColor = switch(cID) {
            case 0 -> new Color(255, 107, 0);
            case 1 -> new Color(123, 47, 190);
            case 2 -> new Color(0, 180, 255);
            case 3 -> new Color(220, 20, 60);
            default -> new Color(255, 107, 0);
        };

        for (int r = 110; r > 0; r -= 18) {
            g.setColor(new Color(auraColor.getRed(), auraColor.getGreen(), auraColor.getBlue(),
                (int)(pulse * 12)));
            g.fillOval(cx - r, cy - (int)(r * 1.6f), r * 2, (int)(r * 3.2f));
        }

        // ─── Orbiting energy particles ──────────────────────
        int orbCount = (cID == 0 || cID == 2) ? 6 : 5;
        Color orbColor = switch(cID) {
            case 0 -> new Color(0, 212, 255);
            case 1 -> new Color(255, 215, 0);
            case 2 -> new Color(255, 255, 255);
            case 3 -> new Color(255, 215, 0);
            default -> new Color(0, 212, 255);
        };

        for (int i = 0; i < orbCount; i++) {
            double angle = t + i * (2 * Math.PI / orbCount);
            int ox = (int)(cx + Math.cos(angle) * 75);
            int oy = (int)(cy + Math.sin(angle) * 40);
            g.setColor(new Color(orbColor.getRed(), orbColor.getGreen(), orbColor.getBlue(), 180));
            g.fillOval(ox - 4, oy - 4, 8, 8);
            // Trail
            g.setColor(new Color(orbColor.getRed(), orbColor.getGreen(), orbColor.getBlue(), 60));
            g.fillOval(ox - 7, oy - 7, 14, 14);
        }

        // ─── Character sprite ───────────────────────────────
        java.awt.image.BufferedImage sprite = switch(cID) {
            case 0 -> AssetLoader.naruIdle;
            case 1 -> AssetLoader.gokaiIdle;
            case 2 -> AssetLoader.kiraIdle;
            case 3 -> AssetLoader.zinIdle;
            default -> null;
        };

        int sprW = switch(cID) {
            case 0 -> 160;
            case 1 -> 190;
            case 2 -> 160;
            case 3 -> 160;
            default -> 160;
        };
        int sprH = switch(cID) {
            case 0 -> 200;
            case 1 -> 240;
            case 2 -> 190;
            case 3 -> 210;
            default -> 200;
        };

        // Nhân vật nhún nhẹ theo thời gian
        int bobY = (int)(Math.sin(t * 1.5f) * 6);
        int sprX = cx - sprW / 2;
        int sprY = cy - sprH / 2 + bobY;

        if (sprite != null) {
            // Cả 2 nhân vật quay mặt vào VS (quay vào giữa)
            boolean defaultFacesRight = (cID == 0 || cID == 3);
            boolean needFlip = isPlayer ? !defaultFacesRight : defaultFacesRight;

            if (!needFlip) {
                g.drawImage(sprite, sprX, sprY, sprX + sprW, sprY + sprH,
                    0, 0, sprite.getWidth(), sprite.getHeight(), null);
            } else {
                g.drawImage(sprite, sprX + sprW, sprY, sprX, sprY + sprH,
                    0, 0, sprite.getWidth(), sprite.getHeight(), null);
            }
        } else {
            // Fallback: vẽ hình học
            if (cID == 1) renderTitleCharGokai(g, cx, cy + 30);
            else renderTitleCharNaru(g, cx, cy + 30);
        }

        // ─── Info panel (tên + stats) ───────────────────────
        int panelY = cy + sprH / 2 + 15;
        // Nền panel mờ
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRoundRect(cx - 90, panelY, 180, 65, 10, 10);
        g.setColor(auraColor);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(cx - 90, panelY, 180, 65, 10, 10);
        g.setStroke(new BasicStroke(1));

        // Tên nhân vật
        g.setFont(new Font("Courier New", Font.BOLD, 15));
        g.setColor(auraColor);
        String name = switch(cID) {
            case 0 -> "NARU";
            case 1 -> "GOKAI";
            case 2 -> "KIRA";
            case 3 -> "ZIN";
            default -> "NARU";
        };
        FontMetrics fm2 = g.getFontMetrics();
        g.drawString(name, cx - fm2.stringWidth(name) / 2, panelY + 18);

        // Type
        g.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g.setColor(new Color(255, 255, 255, 180));
        String type = switch(cID) {
            case 0 -> "Balanced Fighter";
            case 1 -> "Tanker & Shield";
            case 2 -> "Mage Assassin";
            case 3 -> "Sword Master";
            default -> "Balanced Fighter";
        };
        fm2 = g.getFontMetrics();
        g.drawString(type, cx - fm2.stringWidth(type) / 2, panelY + 34);

        // Stats mini bars
        String[] statNames = switch(cID) {
            case 0 -> new String[]{"HP", "SPD", "ATK"};
            case 1 -> new String[]{"HP", "DEF", "ATK"};
            case 2 -> new String[]{"HP", "SPD", "ATK"};
            case 3 -> new String[]{"HP", "SPD", "ATK"};
            default -> new String[]{"HP", "SPD", "ATK"};
        };
        float[] statVals = switch(cID) {
            case 0 -> new float[]{0.75f, 0.75f, 0.70f}; // Naru Balanced
            case 1 -> new float[]{1.00f, 0.95f, 0.45f}; // Gokai Tanker
            case 2 -> new float[]{0.60f, 0.95f, 0.95f}; // Kira Mage
            case 3 -> new float[]{0.70f, 0.80f, 0.85f}; // Zin Swordsman
            default -> new float[]{0.75f, 0.75f, 0.70f};
        };
        Color secondColor = switch(cID) {
            case 1 -> new Color(255, 165, 0); // Orange for DEF
            default -> new Color(0, 180, 255); // Blue for SPD
        };
        Color[] statColors = {
            new Color(80, 220, 80), // HP
            secondColor,            // DEF / SPD
            new Color(255, 80, 80)  // ATK
        };
        for (int i = 0; i < 3; i++) {
            int bx = cx - 78 + i * 58;
            int by = panelY + 46;
            // label
            g.setFont(new Font("Segoe UI", Font.BOLD, 9));
            g.setColor(new Color(255, 255, 255, 140));
            g.drawString(statNames[i], bx, by - 1);
            // bar bg
            g.setColor(new Color(30, 30, 50));
            g.fillRoundRect(bx, by + 2, 50, 6, 3, 3);
            // bar fill
            g.setColor(statColors[i]);
            g.fillRoundRect(bx, by + 2, (int)(50 * statVals[i]), 6, 3, 3);
        }
    }

    // Title screen character drawing (bigger, pose)
    private void renderTitleCharNaru(Graphics2D g, int cx, int cy) {
        // Aura glow
        for (int r = 60; r > 0; r -= 10) {
            g.setColor(new Color(255, 107, 0, 8));
            g.fillOval(cx - r, cy - r * 2, r * 2, r * 4);
        }
        // Body
        g.setColor(new Color(255, 107, 0));
        g.fillRoundRect(cx - 22, cy - 65, 44, 50, 8, 8);
        // Torso accent
        g.setColor(new Color(255, 215, 0));
        g.fillRect(cx - 22, cy - 65, 44, 6);
        // Head
        g.setColor(new Color(255, 200, 160));
        g.fillOval(cx - 18, cy - 110, 36, 40);
        // Hair spiky
        g.setColor(new Color(255, 80, 0));
        int[] hx = {cx-18, cx-10, cx-6, cx+2, cx+8, cx+18};
        int[] hy = {cy-105, cy-130, cy-118, cy-135, cy-120, cy-108};
        for (int i = 0; i < hx.length; i++) {
            g.fillOval(hx[i] - 6, hy[i] - 6, 14, 16);
        }
        // Eyes (cyan glow)
        g.setColor(new Color(0, 255, 204));
        g.fillOval(cx - 12, cy - 94, 8, 6);
        g.fillOval(cx + 4, cy - 94, 8, 6);
        // Scarf
        g.setColor(new Color(255, 215, 0));
        g.fillRoundRect(cx - 16, cy - 68, 32, 8, 4, 4);
        // Arms — fighting stance
        g.setColor(new Color(255, 140, 0));
        g.fillRoundRect(cx - 38, cy - 62, 18, 30, 6, 6); // left arm up
        g.fillRoundRect(cx + 22, cy - 55, 18, 22, 6, 6); // right arm
        // Fist left (up guard)
        g.setColor(new Color(255, 80, 0));
        g.fillOval(cx - 40, cy - 72, 16, 14);
        // Legs
        g.setColor(new Color(180, 40, 0));
        g.fillRoundRect(cx - 18, cy - 16, 16, 30, 4, 4);
        g.fillRoundRect(cx + 2, cy - 16, 16, 30, 4, 4);
        // Energy sparkles around
        float t = titleFrame * 0.05f;
        g.setColor(new Color(255, 215, 0, 160));
        for (int i = 0; i < 6; i++) {
            double a = t + i * Math.PI / 3;
            int px = (int)(cx + Math.cos(a) * 50);
            int py = (int)(cy - 40 + Math.sin(a) * 35);
            g.fillOval(px - 3, py - 3, 6, 6);
        }
    }

    private void renderTitleCharGokai(Graphics2D g, int cx, int cy) {
        // Aura glow
        for (int r = 70; r > 0; r -= 12) {
            g.setColor(new Color(123, 47, 190, 8));
            g.fillOval(cx - r, cy - r * 2, r * 2, r * 4);
        }
        // Legs — wide stance
        g.setColor(new Color(30, 30, 70));
        g.fillRoundRect(cx - 28, cy - 20, 22, 35, 4, 4);
        g.fillRoundRect(cx + 6, cy - 20, 22, 35, 4, 4);
        // Armor knee plates
        g.setColor(new Color(255, 215, 0));
        g.fillRect(cx - 26, cy - 5, 18, 5);
        g.fillRect(cx + 8, cy - 5, 18, 5);
        // Torso — wide
        g.setColor(new Color(40, 40, 80));
        g.fillRoundRect(cx - 32, cy - 80, 64, 62, 8, 8);
        // Armor chest plate
        g.setColor(new Color(255, 215, 0));
        g.fillRect(cx - 32, cy - 80, 64, 6);
        g.fillRect(cx - 32, cy - 22, 64, 5);
        // Shoulders — massive
        g.setColor(new Color(20, 20, 50));
        g.fillOval(cx - 48, cy - 82, 24, 22);
        g.fillOval(cx + 24, cy - 82, 24, 22);
        // Arms crossed
        g.setColor(new Color(50, 50, 90));
        g.fillRoundRect(cx - 46, cy - 72, 20, 45, 6, 6);
        g.fillRoundRect(cx + 26, cy - 72, 20, 45, 6, 6);
        // Gauntlets
        g.setColor(new Color(80, 60, 20));
        g.fillRoundRect(cx - 48, cy - 32, 22, 18, 4, 4);
        g.fillRoundRect(cx + 26, cy - 32, 22, 18, 4, 4);
        // Head — helmet
        g.setColor(new Color(30, 30, 60));
        g.fillOval(cx - 24, cy - 120, 48, 45);
        // Helmet crest
        g.setColor(new Color(255, 215, 0));
        g.fillRect(cx - 24, cy - 120, 48, 6);
        // Eyes — glowing yellow slits
        g.setColor(new Color(255, 215, 0));
        g.fillRect(cx - 16, cy - 101, 12, 5);
        g.fillRect(cx + 4, cy - 101, 12, 5);
        // Gold trim details
        g.setColor(new Color(255, 200, 0, 180));
        g.setStroke(new BasicStroke(2));
        g.drawRect(cx - 20, cy - 74, 40, 52);
        g.setStroke(new BasicStroke(1));
        // Aura rings
        float t = titleFrame * 0.04f;
        g.setColor(new Color(255, 215, 0, 60));
        g.setStroke(new BasicStroke(2));
        for (int r = 45; r < 70; r += 12) {
            float rr = r + (float)Math.sin(t + r * 0.1f) * 5;
            g.drawOval((int)(cx - rr), (int)(cy - 55 - rr * 1.8f), (int)(rr * 2), (int)(rr * 3.6f));
        }
        g.setStroke(new BasicStroke(1));
    }

    // ─── GAME RENDER ─────────────────────────────────
    private void renderGame(Graphics2D g) {
        background.render(g);
        drawParticles(g);
        if (gameMode == 1 && player2 != null) {
            player2.render(g);
        } else {
            enemy.render(g);
        }
        player.render(g);
        drawHitEffects(g);
        CharacterBase opponent = (gameMode == 1 && player2 != null) ? player2 : enemy;
        hud.render(g, player, opponent, timerSec, round, p1Wins, aiWins, score, gameMode);
    }

    private void renderCountdown(Graphics2D g) {
        String s = countdownVal > 0 ? String.valueOf(countdownVal) : "READY...";
        drawCenteredBigText(g, s, H / 2 - 20, new Color(255, 215, 0));
    }

    private void renderAnnounce(Graphics2D g) {
        if (announceTimer > 0) {
            float alpha = Math.min(1f, announceTimer / 20f);
            Color c = "FIGHT!".equals(announceText)
                    ? new Color(255, 45, 120, (int)(alpha * 255))
                    : new Color(255, 215, 0, (int)(alpha * 255));
            drawCenteredBigText(g, announceText, H / 2 - 30, c);
        }
    }

    private void renderRoundEnd(Graphics2D g) {
        // Dark overlay
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, W, H);

        // Slow-mo effect: draw KO text
        Color koColor = p1Wins > aiWins ? new Color(255,215,0) : new Color(255,45,120);
        drawCenteredBigText(g, roundEndText, H / 2 - 30, koColor);

        // Round result sub-text
        String p1Name = charNames[p1CharID].split(" ")[0].toUpperCase();
        String p2Name = charNames[p2CharID].split(" ")[0].toUpperCase();
        String sub = p1Wins > aiWins
                ? p1Name + " THẮNG HIỆP " + (p1Wins + aiWins)
                : p2Name + " THẮNG HIỆP " + (p1Wins + aiWins);
        Font f = new Font("Courier New", Font.BOLD, 18);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Color.WHITE);
        g.drawString(sub, (W - fm.stringWidth(sub)) / 2, H / 2 + 40);
    }

    private void renderGameOver(Graphics2D g) {
        background.render(g);
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, W, H);
        
        boolean playerWon = p1Wins >= 2;
        Color titleColor = playerWon ? new Color(255, 215, 0) : new Color(255, 45, 120);
        String p1Name = charNames[p1CharID].split(" ")[0].toUpperCase();
        String p2Name = charNames[p2CharID].split(" ")[0].toUpperCase();
        String title = playerWon ? p1Name + " VICTORY!" : (gameMode == 1 ? p2Name + " VICTORY!" : "DEFEAT...");
        drawCenteredBigText(g, title, 160, titleColor);

        // Score board
        int bx = W/2 - 200, by = 180, bw = 400, bh = 275;
        g.setColor(new Color(10, 0, 30, 220));
        g.fillRoundRect(bx, by, bw, bh, 16, 16);
        g.setColor(new Color(255, 215, 0, 100));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(bx, by, bw, bh, 16, 16);
        g.setStroke(new BasicStroke(1));

        Font tf = new Font("Courier New", Font.BOLD, 14);
        g.setFont(tf);
        FontMetrics fm = g.getFontMetrics();
        
        String[] labels = {"Combat Score", "Time Bonus", "HP Bonus", "Perfect Bonus", "Win Bonus", "──────────────", "TOTAL SCORE", "HIGH SCORE"};
        int[] vals = {score, timeBonus, hpBonus, perfectBonus, winBonus, 0, totalScore, highScore};
        int[] delays = {30, 60, 90, 120, 150, 180, 210, 235}; // frames when they appear
        
        for (int i = 0; i < labels.length; i++) {
            if (gameOverFrame < delays[i]) break; // only draw up to current frame
            
            // play sound once when appearing
            if (gameOverFrame == delays[i] && i != 5) SoundManager.playScoreCount();

            int y = by + 30 + i * 27;
            if (i == 5) { // divider
                g.setColor(new Color(255,255,255,60));
                g.drawLine(bx + 20, y - 5, bx + bw - 20, y - 5);
                continue;
            }
            g.setColor(i >= 6 ? new Color(255,215,0) : new Color(200,200,200));
            g.drawString(labels[i], bx + 30, y);
            
            // animated count up
            int displayVal = vals[i];
            int animDur = 25;
            if (gameOverFrame < delays[i] + animDur && vals[i] > 0) {
                float progress = (float)(gameOverFrame - delays[i]) / animDur;
                displayVal = (int)(vals[i] * progress);
                if (gameOverFrame % 4 == 0) SoundManager.playScoreCount(); // counting sound
            }
            
            String valStr = String.valueOf(displayVal);
            g.setColor(new Color(0, 255, 204));
            g.drawString(valStr, bx + bw - 30 - fm.stringWidth(valStr), y);
        }

        // Rank Stamp
        if (gameOverFrame >= 260) {
            if (gameOverFrame == 260) {
                SoundManager.playRankStamp(); // play once
                // particles explosion
                for(int i=0; i<30; i++) particles.add(new Particle(bx + bw + 40, by + 120, new Color(255,215,0), 6f));
            }
            
            // Rank letter
            int rx = bx + bw + 40, ry = by + 120;
            // Stamp animation (scale down)
            float scale = gameOverFrame < 275 ? 3f - 2f * ((gameOverFrame - 260) / 15f) : 1f;
            
            Color rc;
            switch(finalRank) {
                case "S": rc = new Color(255, 215, 0); break;
                case "A": rc = new Color(255, 45, 120); break;
                case "B": rc = new Color(0, 180, 255); break;
                case "C": rc = new Color(80, 220, 80); break;
                default:  rc = new Color(150, 150, 150); break;
            }
            
            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(rx, ry);
            g2.scale(scale, scale);
            g2.rotate(Math.toRadians(15)); // slightly tilted
            
            // Draw rank letter
            Font rf = new Font("Arial Black", Font.BOLD, 120);
            g2.setFont(rf);
            FontMetrics rfm = g2.getFontMetrics();
            int rtw = rfm.stringWidth(finalRank);
            
            // Glow
            g2.setColor(new Color(rc.getRed(), rc.getGreen(), rc.getBlue(), 80));
            g2.drawString(finalRank, -rtw/2 + 5, rfm.getAscent()/2 + 5);
            g2.setColor(rc);
            g2.drawString(finalRank, -rtw/2, rfm.getAscent()/2);
            
            g2.dispose();
            
            // Rank Label
            if (gameOverFrame > 280) {
                g.setFont(new Font("Arial", Font.BOLD, 18));
                g.setColor(Color.WHITE);
                g.drawString("RANK", rx - rtw/2 - 5, ry - 70);
            }
        }

        // Restart prompt
        if (gameOverFrame > 320) {
            Font pf = new Font("Courier New", Font.BOLD, 15);
            g.setFont(pf);
            g.setColor(titleFrame % 60 < 40 ? new Color(255,215,0) : Color.WHITE);
            String restart = "NHẤN ENTER để chơi lại  |  ESC để thoát";
            fm = g.getFontMetrics();
            g.drawString(restart, (W - fm.stringWidth(restart)) / 2, 510);
        }
    }

    private void drawCenteredBigText(Graphics2D g, String text, int y, Color color) {
        Font f = new Font("Segoe UI", Font.BOLD, 64);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int x = (W - fm.stringWidth(text)) / 2;
        // Shadow
        g.setColor(new Color(0, 0, 0, 150));
        g.drawString(text, x + 4, y + 4);
        // Glow
        for (int i = 8; i > 0; i--) {
            Color gc = new Color(color.getRed(), color.getGreen(), color.getBlue(), 15);
            g.setColor(gc);
            g.drawString(text, x - i/2, y - i/2);
        }
        g.setColor(color);
        g.drawString(text, x, y);
    }

    private void drawParticles(Graphics2D g) {
        for (Particle p : particles) p.render(g);
    }

    private void drawHitEffects(Graphics2D g) {
        for (HitEffect h : hitEffects) h.render(g);
    }

    // ─── KEYBOARD ────────────────────────────────────────
    @Override public void keyPressed(KeyEvent e)  {
        int code = e.getKeyCode();
        keysHeld.add(code);
        keysPressed.add(code);

        // ★ ESC: Pause toggle (trong PLAYING) hoặc unpause (trong PAUSED)
        if (code == KeyEvent.VK_ESCAPE) {
            if (gameState == State.PLAYING) {
                stateBeforePause = State.PLAYING;
                gameState = State.PAUSED;
                pauseMenuIndex = 0;
            } else if (gameState == State.PAUSED) {
                gameState = stateBeforePause;
            }
        }
    }
    @Override public void keyReleased(KeyEvent e) { keysHeld.remove(e.getKeyCode()); }
    @Override public void keyTyped(KeyEvent e) {}

    // ─── GETTERS ─────────────────────────────────────
    public List<Particle>  getParticles()  { return particles; }
    public List<HitEffect> getHitEffects() { return hitEffects; }
    public State getGameState()            { return gameState; }
    public boolean isPerfectRound()        { return perfectRound; }
    public void setPerfectRound(boolean v) { perfectRound = v; }
    public void addScore(int v)            { score += v; }
    public int getScore()                  { return score; }

    public void showAnnounce(String text, int frames) {
        announceText  = text;
        announceTimer = frames;
    }

    /** ★ Trigger hitstop — freeze game vài frame */
    public void triggerHitstop(int frames) {
        hitstopTimer = Math.max(hitstopTimer, frames);
    }

    /** ★ Trigger screen shake */
    public void triggerShake(float intensity, int duration) {
        if (intensity > shakeIntensity) {
            shakeIntensity = intensity;
            shakeDuration  = duration;
        }
    }

    // ─── SELECT SCREEN ───────────────────────────────
    private void updateSelect() {
        if (showSkillInfo || showLoreInfo) {
            if (keysPressed.contains(KeyEvent.VK_ESCAPE) || keysPressed.contains(KeyEvent.VK_TAB) || keysPressed.contains(KeyEvent.VK_C) || keysPressed.contains(KeyEvent.VK_ENTER)) {
                showSkillInfo = false;
                showLoreInfo  = false;
                SoundManager.playJump();
            }
            if (mouseClicked) {
                int popW = 760, popH = 480;
                int popX = (W - popW) / 2, popY = (H - popH) / 2;
                int closeX = popX + popW - 45, closeY = popY + 10, closeW = 35, closeH = 35;
                if (mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= closeY && mouseY <= closeY + closeH) {
                    showSkillInfo = false;
                    showLoreInfo  = false;
                    SoundManager.playJump();
                }
                mouseClicked = false;
            }
            keysPressed.clear();
            return;
        }

        if (selectPhase == 1 || selectPhase == 2) {
            int iconX = W - 180, iconY = 20, iconW = 160, iconH = 38;
            int loreX = W - 350, loreY = 20, loreW = 160, loreH = 38;
            if (keysPressed.contains(KeyEvent.VK_TAB) || (mouseClicked && mouseX >= iconX && mouseX <= iconX + iconW && mouseY >= iconY && mouseY <= iconY + iconH)) {
                showSkillInfo = true;
                SoundManager.playJump();
                mouseClicked = false;
                keysPressed.clear();
                return;
            }
            if (keysPressed.contains(KeyEvent.VK_C) || (mouseClicked && mouseX >= loreX && mouseX <= loreX + loreW && mouseY >= loreY && mouseY <= loreY + loreH)) {
                showLoreInfo = true;
                SoundManager.playJump();
                mouseClicked = false;
                keysPressed.clear();
                return;
            }
        }
        mouseClicked = false;

        if (keysPressed.contains(KeyEvent.VK_LEFT) || keysPressed.contains(KeyEvent.VK_A)) {
            int max = selectPhase == 0 ? 2 : (selectPhase == 3 ? 3 : 4);
            selectIndex = (selectIndex - 1 + max) % max;
        }
        if (keysPressed.contains(KeyEvent.VK_RIGHT) || keysPressed.contains(KeyEvent.VK_D)) {
            int max = selectPhase == 0 ? 2 : (selectPhase == 3 ? 3 : 4);
            selectIndex = (selectIndex + 1) % max;
        }
        if (keysPressed.contains(KeyEvent.VK_ESCAPE)) {
            if (selectPhase > 0) {
                selectPhase--;
                selectIndex = 0;
            }
        }
        if (keysPressed.contains(KeyEvent.VK_ENTER)) {
            SoundManager.playJump(); // Select sound
            if (selectPhase == 0) {
                gameMode = selectIndex;
                selectPhase = 1;
                selectIndex = p1CharID;
            } else if (selectPhase == 1) {
                p1CharID = selectIndex;
                selectPhase = 2;
                selectIndex = p2CharID;
            } else if (selectPhase == 2) {
                p2CharID = selectIndex;
                
                // Initialize characters with selected IDs
                player = new Player(this, p1CharID);
                if (gameMode == 1) {
                    player2 = new Player(this, p2CharID, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT);
                    SoundManager.playRoundStart();
                    gameState = State.TITLE;
                    titleFrame = 0;
                } else {
                    selectPhase = 3; // Chọn độ khó AI
                    selectIndex = aiDifficulty;
                }
            } else if (selectPhase == 3) {
                aiDifficulty = selectIndex;
                player = new Player(this, p1CharID);
                enemy = new EnemyAI(this, p2CharID);
                enemy.setDifficulty(aiDifficulty);

                SoundManager.playRoundStart();
                gameState = State.TITLE;
                titleFrame = 0;
            }
        }
        keysPressed.clear();
    }

    private void renderSelect(Graphics2D g) {
        background.render(g);
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, W, H);

        Font titleFont = new Font("Segoe UI", Font.BOLD, 36);
        g.setFont(titleFont);
        FontMetrics fm = g.getFontMetrics();
        String title = switch (selectPhase) {
            case 0 -> "CHỌN CHẾ ĐỘ CHƠI";
            case 1 -> "PLAYER 1: CHỌN NHÂN VẬT";
            case 2 -> gameMode == 1 ? "PLAYER 2: CHỌN NHÂN VẬT" : "AI: CHỌN NHÂN VẬT";
            case 3 -> "CHỌN ĐỘ KHÓ AI";
            default -> "";
        };
        
        g.setColor(new Color(255, 215, 0));
        g.drawString(title, (W - fm.stringWidth(title)) / 2, 100);

        if (selectPhase == 0) {
            String[] options = {"1P vs AI", "2P vs 2P (PvP)"};
            String[] descs = {"Chơi với máy", "Hai người chơi"};
            for (int i = 0; i < options.length; i++) {
                renderOptionCard(g, W / 2 - 180, 160 + i * 120, 360, 90, options[i], descs[i], selectIndex == i);
            }
        } else if (selectPhase == 3) {
            String[] options = {"DỄ (EASY)", "VỪA (MEDIUM)", "KHÓ (HARD)"};
            String[] descs = {"AI chậm, ít dùng chiêu, sát thương giảm 20%", "AI cân bằng, phản xạ khá, thông số gốc 100%", "AI cực thông minh, combo liên tục, sát thương tăng 25%"};
            for (int i = 0; i < options.length; i++) {
                renderOptionCard(g, W / 2 - 270, 150 + i * 110, 540, 90, options[i], descs[i], selectIndex == i);
            }
        } else {
            // Draw character cards
            int cardW = 160, cardH = 220;
            int startX = (W - (4 * cardW + 3 * 20)) / 2;
            for (int i = 0; i < 4; i++) {
                int bx = startX + i * (cardW + 20), by = 150;
                boolean selected = selectIndex == i;
                
                g.setColor(selected ? new Color(255, 215, 0, 40) : new Color(20, 10, 40, 200));
                g.fillRoundRect(bx, by, cardW, cardH, 16, 16);
                g.setColor(selected ? new Color(255, 215, 0) : new Color(100, 100, 140));
                g.setStroke(new BasicStroke(selected ? 3f : 1f));
                g.drawRoundRect(bx, by, cardW, cardH, 16, 16);
                g.setStroke(new BasicStroke(1));

                // Draw Character Sprite inside card
                java.awt.image.BufferedImage img = switch(i) {
                    case 0 -> AssetLoader.naruIdle;
                    case 1 -> AssetLoader.gokaiIdle;
                    case 2 -> AssetLoader.kiraIdle;
                    case 3 -> AssetLoader.zinIdle;
                    default -> null;
                };
                if (img != null) {
                    int iw = 110, ih = 140;
                    g.drawImage(img, bx + (cardW - iw)/2, by + 25, iw, ih, null);
                }

                // Draw Name
                g.setFont(new Font("Segoe UI", Font.BOLD, 16));
                g.setColor(selected ? new Color(255, 215, 0) : Color.WHITE);
                String name = charNames[i];
                fm = g.getFontMetrics();
                g.drawString(name, bx + (cardW - fm.stringWidth(name)) / 2, by + cardH - 20);
                
                if (selected) {
                    g.drawString("▼", bx + cardW/2 - 8, by - 10);
                }
            }

            // ★ BẢNG BIỂU ĐỒ THÔNG SỐ NHÂN VẬT (CHARACTER STATS CHART)
            int panelW = 660, panelH = 100;
            int panelX = (W - panelW) / 2, panelY = 385;
            
            g.setColor(new Color(15, 15, 25, 220));
            g.fillRoundRect(panelX, panelY, panelW, panelH, 12, 12);
            g.setColor(new Color(255, 215, 0, 120));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(panelX, panelY, panelW, panelH, 12, 12);
            g.setStroke(new BasicStroke(1f));

            String roleTitle = switch(selectIndex) {
                case 0 -> "NARU - Đấu Sĩ Cân Bằng | Sát thương khá, linh hoạt, toàn diện các mặt.";
                case 1 -> "GOKAI - Tanker Tiên Phong | Máu trâu, phòng thủ cực vững nhưng sát thương thấp.";
                case 2 -> "KIRA - Pháp Sư Sát Thủ | Di chuyển siêu tốc, dồn sát thương lớn, máu giấy.";
                case 3 -> "ZIN - Bậc Thầy Kiếm Thuật | Phạm vi đánh tốt, sát thương đòn đánh cao.";
                default -> "";
            };
            g.setFont(new Font("Segoe UI", Font.BOLD, 14));
            g.setColor(new Color(255, 215, 0));
            g.drawString(roleTitle, panelX + 20, panelY + 25);

            String[] statLabels = {"SINH LỰC (HP)", "SÁT THƯƠNG (ATK)", "TỐC ĐỘ (SPD)", "PHÒNG THỦ (DEF)"};
            float[] statRates = switch(selectIndex) {
                case 0 -> new float[]{0.75f, 0.70f, 0.75f, 0.70f};
                case 1 -> new float[]{1.00f, 0.45f, 0.55f, 0.95f};
                case 2 -> new float[]{0.60f, 0.95f, 0.95f, 0.55f};
                case 3 -> new float[]{0.70f, 0.85f, 0.80f, 0.75f};
                default -> new float[]{0.75f, 0.70f, 0.75f, 0.70f};
            };
            Color[] statColors = {
                new Color(80, 220, 80),   // Green
                new Color(255, 80, 80),   // Red
                new Color(0, 191, 255),   // Cyan/Blue
                new Color(255, 165, 0)    // Orange
            };

            for (int j = 0; j < 4; j++) {
                int col = j % 2;
                int row = j / 2;
                int barX = panelX + 20 + col * 320;
                int barY = panelY + 42 + row * 26;
                
                g.setFont(new Font("Segoe UI", Font.BOLD, 12));
                g.setColor(new Color(220, 220, 220));
                g.drawString(statLabels[j], barX, barY + 12);
                
                int barBgX = barX + 130;
                int barW = 160;
                int barH = 12;
                g.setColor(new Color(40, 40, 60));
                g.fillRoundRect(barBgX, barY + 2, barW, barH, 6, 6);
                
                g.setColor(statColors[j]);
                g.fillRoundRect(barBgX, barY + 2, (int)(barW * statRates[j]), barH, 6, 6);
            }
            // Nút Icon [i] Chi Tiết Kỹ Năng
            int iconX = W - 180, iconY = 20, iconW = 160, iconH = 38;
            boolean hover = (mouseX >= iconX && mouseX <= iconX + iconW && mouseY >= iconY && mouseY <= iconY + iconH);
            g.setColor(hover ? new Color(255, 215, 0) : new Color(40, 40, 60, 220));
            g.fillRoundRect(iconX, iconY, iconW, iconH, 15, 15);
            g.setColor(hover ? Color.BLACK : new Color(255, 215, 0));
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(iconX, iconY, iconW, iconH, 15, 15);
            g.setStroke(new BasicStroke(1f));

            g.setFont(new Font("Segoe UI", Font.BOLD, 14));
            String btnText = "[i] Chi Tiết Kỹ Năng";
            FontMetrics fmBtn = g.getFontMetrics();
            g.drawString(btnText, iconX + (iconW - fmBtn.stringWidth(btnText))/2, iconY + 24);

            // Nút Icon [C] Cốt Truyện
            int loreX = W - 350, loreY = 20, loreW = 160, loreH = 38;
            boolean hoverLore = (mouseX >= loreX && mouseX <= loreX + loreW && mouseY >= loreY && mouseY <= loreY + loreH);
            g.setColor(hoverLore ? new Color(0, 212, 255) : new Color(40, 40, 60, 220));
            g.fillRoundRect(loreX, loreY, loreW, loreH, 15, 15);
            g.setColor(hoverLore ? Color.BLACK : new Color(0, 212, 255));
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(loreX, loreY, loreW, loreH, 15, 15);
            g.setStroke(new BasicStroke(1f));

            g.setFont(new Font("Segoe UI", Font.BOLD, 14));
            String loreBtnText = "[C] Cốt Truyện";
            FontMetrics fmLore = g.getFontMetrics();
            g.drawString(loreBtnText, loreX + (loreW - fmLore.stringWidth(loreBtnText))/2, loreY + 24);
        }

        g.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        g.setColor(new Color(200, 200, 200));
        String hint = "◀ ▶: Chọn  |  ENTER: Xác nhận  |  TAB: Kỹ Năng  |  C: Cốt Truyện  |  ESC: Quay lại";
        fm = g.getFontMetrics();
        g.drawString(hint, (W - fm.stringWidth(hint)) / 2, H - 20);

        if (showSkillInfo) {
            renderSkillInfoPage(g);
        } else if (showLoreInfo) {
            renderLoreInfoPage(g);
        }
    }

    private void renderLoreInfoPage(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 215));
        g.fillRect(0, 0, W, H);

        int popW = 760, popH = 480;
        int popX = (W - popW) / 2, popY = (H - popH) / 2;

        // Nền popup
        g.setColor(new Color(15, 18, 30, 245));
        g.fillRoundRect(popX, popY, popW, popH, 20, 20);
        g.setColor(new Color(0, 212, 255));
        g.setStroke(new BasicStroke(2.5f));
        g.drawRoundRect(popX, popY, popW, popH, 20, 20);
        g.setStroke(new BasicStroke(1f));

        // Nút Close [X]
        int closeX = popX + popW - 45, closeY = popY + 10, closeW = 35, closeH = 35;
        boolean hoverClose = (mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= closeY && mouseY <= closeY + closeH);
        g.setColor(hoverClose ? new Color(255, 60, 60) : new Color(60, 40, 60));
        g.fillRoundRect(closeX, closeY, closeW, closeH, 10, 10);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 18));
        FontMetrics fmClose = g.getFontMetrics();
        g.drawString("X", closeX + (closeW - fmClose.stringWidth("X"))/2, closeY + 24);

        // Header Title
        String name = charNames[selectIndex].toUpperCase();
        g.setFont(new Font("Segoe UI", Font.BOLD, 24));
        g.setColor(new Color(0, 212, 255));
        g.drawString("CỐT TRUYỆN & TIỂU SỬ: " + name, popX + 25, popY + 38);

        g.setColor(new Color(100, 100, 140, 100));
        g.drawLine(popX + 25, popY + 52, popX + popW - 25, popY + 52);

        // Portrait Icon bên trái
        java.awt.image.BufferedImage img = switch(selectIndex) {
            case 0 -> AssetLoader.naruIdle;
            case 1 -> AssetLoader.gokaiIdle;
            case 2 -> AssetLoader.kiraIdle;
            case 3 -> AssetLoader.zinIdle;
            default -> null;
        };

        int imgBoxX = popX + 25, imgBoxY = popY + 70, imgBoxW = 160, imgBoxH = 220;
        g.setColor(new Color(25, 25, 45, 220));
        g.fillRoundRect(imgBoxX, imgBoxY, imgBoxW, imgBoxH, 15, 15);
        g.setColor(new Color(255, 215, 0));
        g.drawRoundRect(imgBoxX, imgBoxY, imgBoxW, imgBoxH, 15, 15);
        if (img != null) {
            g.drawImage(img, imgBoxX + 25, imgBoxY + 35, 110, 150, null);
        }

        // Tên danh hiệu dưới ảnh
        g.setFont(new Font("Segoe UI", Font.BOLD, 14));
        g.setColor(new Color(255, 215, 0));
        String titleName = switch(selectIndex) {
            case 0 -> "Đấu Sĩ Hoàng Kim";
            case 1 -> "Chiến Thần Địa Chấn";
            case 2 -> "Bóng Ma Điện Từ";
            case 3 -> "Độc Cô Kiếm Thần";
            default -> "";
        };
        FontMetrics fmT = g.getFontMetrics();
        g.drawString(titleName, imgBoxX + (imgBoxW - fmT.stringWidth(titleName))/2, imgBoxY + imgBoxH - 15);

        // Nội dung cốt truyện bên phải
        int textX = imgBoxX + imgBoxW + 25;
        int textW = popW - imgBoxW - 75;

        String p1Title = "📜 BỐI CẢNH & XUẤT THÂN";
        String p1Desc = switch(selectIndex) {
            case 0 -> "Sinh ra tại Vương quốc Gió Thần, một vùng đất thiêng liêng từng chìm trong ách thống trị của ác quỷ bóng tối. Naru là đệ tử nhập thất cuối cùng kế thừa trọn vẹn Thần Long Quyền – tuyệt kỹ võ học hoàng kim đã thất truyền từ ngàn năm trước.";
            case 1 -> "Mang trong mình dòng máu vĩ đại của những Cự Nhân dung nham dưới đáy Hỏa Ngục. Gokai từng là vị đại tướng oai hùng chỉ huy toàn bộ quân đoàn Tiên Phong, mang trọng trách cất giữ và bảo vệ lõi nhân nhiệt lượng của Trái Đất.";
            case 2 -> "Từng là nhà khoa học gia thiên tài đứng đầu Siêu Đô Thị Neo-Core, Kira bị chính phủ ngầm ám hại và cải tạo toàn bộ cơ thể thành một cấu trúc bán cơ khí siêu dẫn, biến anh thành một cỗ máy xử lý không có cảm xúc.";
            case 3 -> "Con trai độc nhất của gia tộc Kiếm Sư huyền thoại, những người đã từng vung kiếm chém đứt ranh giới giữa không gian và thời gian trong cuộc đại chiến Bách Tộc. Zin từ nhỏ đã làm bạn với cô độc và đao kiếm.";
            default -> "";
        };

        String p2Title = "👤 TÍNH CÁCH & CON NGƯỜI";
        String p2Desc = switch(selectIndex) {
            case 0 -> "Quyết đoán, trượng nghĩa và có tấm lòng bao dung trân trọng sự sống. Từng trải qua đau thương khi chứng kiến quê hương sụp đổ, Naru thề sẽ dùng đôi tay mình dẹp tan mọi khổ đau, không bao giờ để thế giới rơi vào cảnh lầm than.";
            case 1 -> "Trung thành, can trường và vững chãi như núi đá thiên thu. Ẩn sau vẻ ngoài dữ tằn, khổng lồ và giọng nói ồm ồm cộc cằn là một trái tim nhân hậu, luôn sẵn sàng lấy thân mình làm tấm khiên che chắn cho đồng đội vào sinh ra tử.";
            case 2 -> "Lạnh lùng, sắc bén, tính toán đường đi nước bước bằng tư duy siêu việt của trí tuệ nhân tạo. Kira luôn bị giằng xé dữ dội giữa chút bản ngã con người còn sót lại và những chỉ thị lập trình tàn nhẫn được cấy vào vỏ não.";
            case 3 -> "Tâm lặng như thủy, ý bén như đao. Zin theo đuổi đỉnh cao kiếm đạo một cách kiên trì và nghiêm khắc đến mức hà khắc, tin rằng mỗi đường kiếm khi vung ra đều chứa đựng trọn vẹn chân lý và quy luật sinh diệt của vũ trụ.";
            default -> "";
        };

        String p3Title = "⚔️ LÝ DO CHIẾN ĐẤU";
        String p3Desc = switch(selectIndex) {
            case 0 -> "Naru hiên ngang bước vào đấu trường Glitched Tournament với mong muốn giải phóng các linh hồn vô tội bị phong ấn trong những đoạn mã nguồn hư hỏng, mang lại ánh sáng và vĩnh cửu tự do cho muôn loài.";
            case 1 -> "Lõi Hỏa Ngục đang suy tàn nghiêm trọng do sự nhiễu loạn từ Đấu Trường Glitch. Gokai xuất chiến để giành lấy Nguồn Năng Lượng Tối Thượng, nhằm thắp lại ngọn lửa sinh mệnh bừng sáng cho quê hương.";
            case 2 -> "Kira tham chiến nhằm bẻ khóa vĩnh viễn hệ thống, tìm lại trọn vẹn ký ức con người đã bị tước đoạt và báo thù những kẻ đứng sau thao túng Đấu Trường, mở ra kỷ nguyên mới cho thế giới số.";
            case 3 -> "Nhận thấy Đấu Trường Glitched Tournament ẩn chứa những lỗ hổng không gian đe dọa sự tồn vong của thực tại, Zin xuất quan để dùng thanh kiếm vô thượng của mình tu bổ ranh giới đất trời.";
            default -> "";
        };

        // Hàm vẽ bọc dòng (Word wrap)
        int curY = popY + 70;
        curY = drawLoreSection(g, p1Title, p1Desc, textX, curY, textW, new Color(255, 215, 0));
        curY += 10;
        curY = drawLoreSection(g, p2Title, p2Desc, textX, curY, textW, new Color(0, 255, 204));
        curY += 10;
        drawLoreSection(g, p3Title, p3Desc, textX, curY, textW, new Color(255, 80, 80));

        // Text hướng dẫn đóng trang
        g.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        g.setColor(new Color(200, 200, 200));
        String closeHint = "Nhấn [ESC], [C] hoặc click [X] để đóng bảng cốt truyện";
        FontMetrics fmH = g.getFontMetrics();
        g.drawString(closeHint, popX + (popW - fmH.stringWidth(closeHint))/2, popY + popH - 15);
    }

    private int drawLoreSection(Graphics2D g, String title, String text, int x, int y, int w, Color titleColor) {
        g.setFont(new Font("Segoe UI", Font.BOLD, 16));
        g.setColor(titleColor);
        g.drawString(title, x, y);
        y += 22;

        g.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        g.setColor(new Color(240, 240, 255));
        FontMetrics fm = g.getFontMetrics();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();

        for (String word : words) {
            if (fm.stringWidth(line + word) < w) {
                line.append(word).append(" ");
            } else {
                g.drawString(line.toString(), x, y);
                y += 20;
                line = new StringBuilder(word).append(" ");
            }
        }
        if (line.length() > 0) {
            g.drawString(line.toString(), x, y);
            y += 20;
        }
        return y;
    }

    private void renderSkillInfoPage(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 210));
        g.fillRect(0, 0, W, H);

        int popW = 760, popH = 480;
        int popX = (W - popW) / 2, popY = (H - popH) / 2;

        g.setColor(new Color(20, 20, 35, 245));
        g.fillRoundRect(popX, popY, popW, popH, 20, 20);
        g.setColor(new Color(0, 212, 255));
        g.setStroke(new BasicStroke(2.5f));
        g.drawRoundRect(popX, popY, popW, popH, 20, 20);
        g.setStroke(new BasicStroke(1f));

        int closeX = popX + popW - 45, closeY = popY + 10, closeW = 35, closeH = 35;
        boolean hoverClose = (mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= closeY && mouseY <= closeY + closeH);
        g.setColor(hoverClose ? new Color(255, 60, 60) : new Color(60, 40, 60));
        g.fillRoundRect(closeX, closeY, closeW, closeH, 10, 10);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 18));
        FontMetrics fmClose = g.getFontMetrics();
        g.drawString("X", closeX + (closeW - fmClose.stringWidth("X"))/2, closeY + 24);

        String name = charNames[selectIndex].toUpperCase();
        g.setFont(new Font("Segoe UI", Font.BOLD, 22));
        g.setColor(new Color(255, 215, 0));
        g.drawString("THÔNG SỐ & CHI TIẾT KỸ NĂNG: " + name, popX + 25, popY + 35);

        g.setColor(new Color(100, 100, 140, 100));
        g.drawLine(popX + 25, popY + 48, popX + popW - 25, popY + 48);

        String s1Name = "", s1Key = "Phím: K (P1) / Phím 2 (P2)", s1Effect = "", s1Stat = "";
        String s2Name = "", s2Key = "Phím: L (P1) / Phím 3 (P2)", s2Effect = "", s2Stat = "";
        String ultName = "", ultKey = "Phím: J+K+L (P1) / Phím 1+2+3 (P2)", ultEffect = "", ultStat = "";
        java.awt.image.BufferedImage img1 = null, img2 = null, img3 = null;

        switch (selectIndex) {
            case 0 -> { // Naru
                s1Name = "1. SKILL 1: PHÂN THÂN BÓNG ĐÊM";
                s1Effect = "Tác dụng: Triệu hồi một ảnh ảo phân thân lao về phía trước tấn công.";
                s1Stat = "Chỉ số tăng: Gây sát thương liên tục và phân tán sự chú ý của địch.";
                img1 = AssetLoader.naruSkill1 != null ? AssetLoader.naruSkill1 : AssetLoader.naruAttack;

                s2Name = "2. SKILL 2: THẦN LONG QUYỀN";
                s2Effect = "Tác dụng: Vận khí hội tụ sức mạnh hoàng kim vào nắm đấm rồi tung đòn.";
                s2Stat = "Chỉ số tăng: Gây sát thương mạnh mẽ và tăng tốc độ di chuyển.";
                img2 = AssetLoader.naruSkill2 != null ? AssetLoader.naruSkill2 : AssetLoader.naruIdle;

                ultName = "3. COMBO ULTIMATE: ĐẠI LONG XOÁY";
                ultEffect = "Tác dụng: Triệu hồi thần long hoàng kim cuộn xoáy thăng thiên.";
                ultStat = "Chỉ số tăng: Gây 420 Sát Thương bùng nổ diện rộng, hất tung kẻ địch.";
                img3 = AssetLoader.naruUltimate != null ? AssetLoader.naruUltimate : AssetLoader.naruAttack;
            }
            case 1 -> { // Gokai
                s1Name = "1. SKILL 1: CUỒNG NỘ TIÊN PHONG";
                s1Effect = "Tác dụng: Gầm lên thị uy, giải phóng sức mạnh cường hóa cơ thể khổng lồ.";
                s1Stat = "Chỉ số tăng: Tăng mạnh Giáp bảo vệ, giảm lượng sát thương nhận vào.";
                img1 = AssetLoader.gokaiSkill1 != null ? AssetLoader.gokaiSkill1 : AssetLoader.gokaiAttack;

                s2Name = "2. SKILL 2: BÚA TẠ ĐỊA CHẤN";
                s2Effect = "Tác dụng: Lấy đà vung búa tạ khổng lồ quật mạnh xuống đập tan giáp địch.";
                s2Stat = "Chỉ số tăng: Đòn đánh mang sức nặng ngàn cân gây choáng và giảm tốc độ.";
                img2 = AssetLoader.gokaiSkill2 != null ? AssetLoader.gokaiSkill2 : AssetLoader.gokaiIdle;

                ultName = "3. COMBO ULTIMATE: VÔ CỰC HỎA";
                ultEffect = "Tác dụng: Nhảy lên cao và nện búa tạo cơn địa chấn dung nham rực lửa.";
                ultStat = "Chỉ số tăng: Gây 320 Sát Thương dung nham, hất văng và phá vỡ thế phòng ngự.";
                img3 = AssetLoader.gokaiUltimate != null ? AssetLoader.gokaiUltimate : AssetLoader.gokaiAttack;
            }
            case 2 -> { // Kira
                s1Name = "1. SKILL 1: LÁ CHẮN ĐIỆN TỪ (PHẢN ĐÒN)";
                s1Effect = "Tác dụng: Tạo ra trường lực điện từ công nghệ cao bao bọc cơ thể.";
                s1Stat = "Chỉ số tăng: Vô hiệu hóa sát thương và lập tức Phản Đòn khi bị cận chiến.";
                img1 = AssetLoader.kiraSkill1 != null ? AssetLoader.kiraSkill1 : AssetLoader.kiraAttack;

                s2Name = "2. SKILL 2: TIA CHỚP QUANG TRƯỜNG";
                s2Effect = "Tác dụng: Bắn ra quả cầu quang trường điện từ đuổi theo mục tiêu.";
                s2Stat = "Chỉ số tăng: Sát thương phép thuật tầm xa và khóa chặt chuyển động của địch.";
                img2 = AssetLoader.kiraSkill2 != null ? AssetLoader.kiraSkill2 : AssetLoader.kiraIdle;

                ultName = "3. COMBO ULTIMATE: HỦY DIỆT ĐIỆN TỪ";
                ultEffect = "Tác dụng: Tụ năng lượng tối đa bắn ra chùm laser điện từ tím huỷ diệt.";
                ultStat = "Chỉ số tăng: Gây 500 Sát Thương chuẩn xuyên thấu mọi giáp phòng ngự.";
                img3 = AssetLoader.kiraUltimate != null ? AssetLoader.kiraUltimate : AssetLoader.kiraAttack;
            }
            case 3 -> { // Zin
                s1Name = "1. SKILL 1: THẦN TỐC PHẢN TRẢM";
                s1Effect = "Tác dụng: Dùng tốc độ thần sầu vung kiếm tạo thế phòng ngự phản công.";
                s1Stat = "Chỉ số tăng: Phá giải đòn tấn công của địch và hất văng kẻ địch ra xa.";
                img1 = AssetLoader.zinSkill1 != null ? AssetLoader.zinSkill1 : AssetLoader.zinAttack;

                s2Name = "2. SKILL 2: NHẤT KIẾM XUYÊN VÂN";
                s2Effect = "Tác dụng: Cầm kiếm lướt đi như một vệt sáng chém rách không gian phía trước.";
                s2Stat = "Chỉ số tăng: Gây sát thương vật lý sắc bén và tiếp cận mục tiêu trong chớp mắt.";
                img2 = AssetLoader.zinSkill2 != null ? AssetLoader.zinSkill2 : AssetLoader.zinIdle;

                ultName = "3. COMBO ULTIMATE: BÃO KIẾM";
                ultEffect = "Tác dụng: Xoay kiếm với tốc độ cao tạo thành cơn lốc trảm phong lục bảo.";
                ultStat = "Chỉ số tăng: Gây 460 Sát Thương liên hoàn diện rộng, xé toạc đội hình địch.";
                img3 = AssetLoader.zinUltimate != null ? AssetLoader.zinUltimate : AssetLoader.zinAttack;
            }
        }

        // Thông số box chung
        int boxW = popW - 44;
        int boxH = 124;
        int boxX = popX + 22;

        // ─── BOX 1 (SKILL 1) ───
        int boxY1 = popY + 58;
        g.setColor(new Color(25, 25, 45, 210));
        g.fillRoundRect(boxX, boxY1, boxW, boxH, 15, 15);
        g.setColor(new Color(0, 212, 255));
        g.drawRoundRect(boxX, boxY1, boxW, boxH, 15, 15);

        // Icon 1
        g.setColor(new Color(15, 15, 25, 220));
        g.fillRoundRect(boxX + 12, boxY1 + 12, 100, 100, 12, 12);
        g.setColor(new Color(0, 212, 255));
        g.drawRoundRect(boxX + 12, boxY1 + 12, 100, 100, 12, 12);
        if (img1 != null) {
            g.drawImage(img1, boxX + 17, boxY1 + 17, 90, 90, null);
        } else {
            g.setColor(new Color(0, 212, 255));
            g.setFont(new Font("Segoe UI", Font.BOLD, 14));
            g.drawString("SKILL 1", boxX + 35, boxY1 + 65);
        }

        int textX = boxX + 128;
        g.setFont(new Font("Segoe UI", Font.BOLD, 18));
        g.setColor(new Color(0, 212, 255));
        g.drawString(s1Name, textX, boxY1 + 30);
        g.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        g.setColor(new Color(200, 200, 220));
        g.drawString(s1Key, textX, boxY1 + 52);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        g.setColor(Color.WHITE);
        g.drawString(s1Effect, textX, boxY1 + 80);
        g.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g.setColor(new Color(80, 220, 80));
        g.drawString(s1Stat, textX, boxY1 + 106);

        // ─── BOX 2 (SKILL 2) ───
        int boxY2 = popY + 192;
        g.setColor(new Color(25, 45, 35, 210));
        g.fillRoundRect(boxX, boxY2, boxW, boxH, 15, 15);
        g.setColor(new Color(80, 255, 150));
        g.drawRoundRect(boxX, boxY2, boxW, boxH, 15, 15);

        // Icon 2
        g.setColor(new Color(15, 25, 20, 220));
        g.fillRoundRect(boxX + 12, boxY2 + 12, 100, 100, 12, 12);
        g.setColor(new Color(80, 255, 150));
        g.drawRoundRect(boxX + 12, boxY2 + 12, 100, 100, 12, 12);
        if (img2 != null) {
            g.drawImage(img2, boxX + 17, boxY2 + 17, 90, 90, null);
        } else {
            g.setColor(new Color(80, 255, 150));
            g.setFont(new Font("Segoe UI", Font.BOLD, 14));
            g.drawString("SKILL 2", boxX + 35, boxY2 + 65);
        }

        g.setFont(new Font("Segoe UI", Font.BOLD, 18));
        g.setColor(new Color(80, 255, 150));
        g.drawString(s2Name, textX, boxY2 + 30);
        g.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        g.setColor(new Color(200, 200, 220));
        g.drawString(s2Key, textX, boxY2 + 52);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        g.setColor(Color.WHITE);
        g.drawString(s2Effect, textX, boxY2 + 80);
        g.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g.setColor(new Color(80, 220, 80));
        g.drawString(s2Stat, textX, boxY2 + 106);

        // ─── BOX 3 (ULTIMATE) ───
        int boxY3 = popY + 326;
        g.setColor(new Color(50, 20, 30, 210));
        g.fillRoundRect(boxX, boxY3, boxW, boxH, 15, 15);
        g.setColor(new Color(255, 80, 80));
        g.drawRoundRect(boxX, boxY3, boxW, boxH, 15, 15);

        // Icon 3
        g.setColor(new Color(25, 15, 15, 220));
        g.fillRoundRect(boxX + 12, boxY3 + 12, 100, 100, 12, 12);
        g.setColor(new Color(255, 80, 80));
        g.drawRoundRect(boxX + 12, boxY3 + 12, 100, 100, 12, 12);
        if (img3 != null) {
            g.drawImage(img3, boxX + 17, boxY3 + 17, 90, 90, null);
        } else {
            g.setColor(new Color(255, 80, 80));
            g.setFont(new Font("Segoe UI", Font.BOLD, 14));
            g.drawString("ULTIMATE", boxX + 28, boxY3 + 65);
        }

        g.setFont(new Font("Segoe UI", Font.BOLD, 18));
        g.setColor(new Color(255, 80, 80));
        g.drawString(ultName, textX, boxY3 + 30);
        g.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        g.setColor(new Color(200, 200, 220));
        g.drawString(ultKey, textX, boxY3 + 52);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        g.setColor(Color.WHITE);
        g.drawString(ultEffect, textX, boxY3 + 80);
        g.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g.setColor(new Color(255, 215, 0));
        g.drawString(ultStat, textX, boxY3 + 106);

        g.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        g.setColor(new Color(200, 200, 200));
        String closeHint = "Nhấn [ESC], [TAB] hoặc click [X] để đóng bảng thông số";
        FontMetrics fmH = g.getFontMetrics();
        g.drawString(closeHint, popX + (popW - fmH.stringWidth(closeHint))/2, popY + popH - 15);
    }

    private void renderOptionCard(Graphics2D g, int bx, int by, int bw, int bh, String title, String desc, boolean selected) {
        g.setColor(selected ? new Color(255, 215, 0, 40) : new Color(20, 10, 40, 200));
        g.fillRoundRect(bx, by, bw, bh, 16, 16);
        g.setColor(selected ? new Color(255, 215, 0) : new Color(100, 100, 140));
        g.setStroke(new BasicStroke(selected ? 3f : 1f));
        g.drawRoundRect(bx, by, bw, bh, 16, 16);
        g.setStroke(new BasicStroke(1));

        g.setFont(new Font("Segoe UI", Font.BOLD, 24));
        g.setColor(selected ? new Color(255, 215, 0) : Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, bx + (bw - fm.stringWidth(title)) / 2, by + 38);

        g.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        g.setColor(new Color(200, 200, 200));
        fm = g.getFontMetrics();
        g.drawString(desc, bx + (bw - fm.stringWidth(desc)) / 2, by + 64);
    }

    // ─── PAUSE MENU ─────────────────────────────────
    private void updatePaused() {
        if (keysPressed.contains(KeyEvent.VK_UP) || keysPressed.contains(KeyEvent.VK_W)) {
            pauseMenuIndex = (pauseMenuIndex - 1 + 3) % 3;
        }
        if (keysPressed.contains(KeyEvent.VK_DOWN) || keysPressed.contains(KeyEvent.VK_S)) {
            pauseMenuIndex = (pauseMenuIndex + 1) % 3;
        }
        if (keysPressed.contains(KeyEvent.VK_ENTER)) {
            switch (pauseMenuIndex) {
                case 0 -> gameState = stateBeforePause; // Resume
                case 1 -> { // Restart match
                    SoundManager.playRoundStart();
                    SoundManager.playBattleBGM();
                    startNewMatch();
                }
                case 2 -> { // Quit to Select Screen
                    gameState = State.SELECT;
                    selectPhase = 0;
                    selectIndex = 0;
                    SoundManager.playTitleBGM();
                }
            }
        }
        keysPressed.clear();
    }

    private void renderPaused(Graphics2D g) {
        // Dark overlay
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, W, H);

        // PAUSED title
        Font pf = new Font("Arial Black", Font.BOLD, 48);
        g.setFont(pf);
        FontMetrics fm = g.getFontMetrics();
        String pauseStr = "PAUSED";
        g.setColor(new Color(0, 0, 0, 150));
        g.drawString(pauseStr, (W - fm.stringWidth(pauseStr)) / 2 + 3, 153);
        g.setColor(new Color(255, 215, 0));
        g.drawString(pauseStr, (W - fm.stringWidth(pauseStr)) / 2, 150);

        // Menu items
        String[] items = {"RESUME", "RESTART", "QUIT"};
        for (int i = 0; i < items.length; i++) {
            boolean sel = pauseMenuIndex == i;
            int ix = W / 2, iy = 220 + i * 55;

            g.setFont(new Font("Arial Black", Font.BOLD, sel ? 26 : 22));
            fm = g.getFontMetrics();
            g.setColor(sel ? new Color(255, 215, 0) : new Color(180, 180, 200));
            g.drawString(items[i], ix - fm.stringWidth(items[i]) / 2, iy);

            if (sel) {
                g.setColor(new Color(255, 215, 0, 80));
                g.fillRoundRect(ix - fm.stringWidth(items[i]) / 2 - 20, iy - 25,
                        fm.stringWidth(items[i]) + 40, 35, 10, 10);
                g.setColor(new Color(255, 215, 0));
                g.drawString("▶", ix - fm.stringWidth(items[i]) / 2 - 30, iy);
            }
        }

        // Controls hint
        renderControlsHint(g, 420);
    }

    /** Vẽ hướng dẫn phím điều khiển */
    private void renderControlsHint(Graphics2D g, int startY) {
        g.setFont(new Font("Courier New", Font.BOLD, 11));
        g.setColor(new Color(255, 255, 255, 140));

        String[] lines = {
            "─── PLAYER 1 ───",
            "A/D: Di chuyển   W: Nhảy   S+A/D: Lướt",
            "J: Đánh (combo 3 hit)  K: Skill 1  L: Skill 2",
            "J+K+L: Ultimate",
            "",
            "─── PLAYER 2 (PvP) ───",
            "◀▶: Di chuyển  ▲: Nhảy  Numpad 1/2/3: Attack/Skill"
        };
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < lines.length; i++) {
            if (gameMode == 0 && i >= 5) break; // Ẩn P2 hints nếu 1P mode
            g.drawString(lines[i], (W - fm.stringWidth(lines[i])) / 2, startY + i * 16);
        }
    }

    // ─── MOUSE ───────────────────────────────────────────
    @Override public void mouseClicked(MouseEvent e) {
        mouseClicked = true;
    }
    @Override public void mousePressed(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
    @Override public void mouseDragged(MouseEvent e) {
        mouseX = e.getX(); mouseY = e.getY();
    }
    @Override public void mouseMoved(MouseEvent e) {
        mouseX = e.getX(); mouseY = e.getY();
    }
}
