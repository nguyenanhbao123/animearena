package com.animeclash;

import java.awt.*;

/**
 * HUD — Heads-Up Display
 * HP Bars, Energy Bars, Timer, Round Indicator, Combo Counter, Portraits
 */
public class HUD {

    private static final int W = GamePanel.W;
    private float time = 0f;

    // ★ HP Lerp: thanh trắng giảm chậm phía sau thanh chính
    private float p1DisplayHp = 1f;  // % hiển thị (lerp dần về actual)
    private float aiDisplayHp = 1f;

    // ★ Combo text animation
    private int   lastCombo = 0;
    private float comboScale = 1f;  // scale bounce khi combo tăng
    private int   comboFlashTimer = 0;

    public HUD(GamePanel game) {
    }

    public void render(Graphics2D g, Player player, CharacterBase opponent,
                       int timerSec, int round, int p1Wins, int aiWins, int score, int gameMode) {
        time += 0.04f;

        // ★ HP Lerp update: giảm dần về HP thực (tốc độ 0.02/frame)
        float actualP1 = player.getHpPercent();
        float actualAI = opponent.getHpPercent();
        if (p1DisplayHp > actualP1) p1DisplayHp -= 0.015f;
        if (p1DisplayHp < actualP1) p1DisplayHp = actualP1; // khi hồi HP
        if (aiDisplayHp > actualAI) aiDisplayHp -= 0.015f;
        if (aiDisplayHp < actualAI) aiDisplayHp = actualAI;

        // ★ Combo bounce decay
        if (comboScale > 1f) comboScale -= 0.08f;
        if (comboScale < 1f) comboScale = 1f;
        if (comboFlashTimer > 0) comboFlashTimer--;

        // Detect combo increase
        int currentCombo = player.getCombo();
        if (currentCombo > lastCombo && currentCombo >= 3) {
            comboScale = 1.8f;  // bounce up
            comboFlashTimer = 12;
        }
        lastCombo = currentCombo;

        // Semi-transparent top bar
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, W, 90);

        drawP1HUD(g, player);
        drawAIHUD(g, opponent, gameMode);
        drawCenterHUD(g, timerSec, round, p1Wins, aiWins);
        drawCombo(g, player.getCombo());
        drawScoreSmall(g, score);

        // Rage mode border warning
        boolean isRage = (opponent instanceof EnemyAI) && ((EnemyAI) opponent).isRageMode();
        if (isRage) drawRageBorder(g);

        // Energy ultimate ready flash for player
        if (player.isUltimateReady()) drawUltimateReady(g);
    }

    private String getCharName(int charID) {
        return switch(charID) {
            case 0 -> "NARU";
            case 1 -> "GOKAI";
            case 2 -> "KIRA";
            case 3 -> "ZIN";
            default -> "HERO";
        };
    }

    private java.awt.image.BufferedImage getCharPortrait(int charID) {
        return switch(charID) {
            case 0 -> AssetLoader.naruIdle;
            case 1 -> AssetLoader.gokaiIdle;
            case 2 -> AssetLoader.kiraIdle;
            case 3 -> AssetLoader.zinIdle;
            default -> null;
        };
    }

    // ─── P1 HUD (left) ───────────────────────────────
    private void drawP1HUD(Graphics2D g, Player player) {
        int px = 12, py = 8;

        // Portrait frame
        drawPortrait(g, px, py, true, player.isDead(), player.charID);

        // Bars area
        int bx = px + 72;

        // Name tag
        g.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g.setColor(new Color(255, 215, 0));
        g.drawString("P1: " + getCharName(player.charID), bx, py + 13);

        // HP Bar
        drawHPBar(g, bx, py + 18, 280, player.getHpPercent(), true, player.isDead());

        // HP text
        g.setFont(new Font("Arial", Font.BOLD, 9));
        g.setColor(Color.WHITE);
        g.drawString((int)player.getHp() + " / " + (int)player.getMaxHp(),
                bx + 4, py + 32);

        // Energy bar
        drawEnergyBar(g, bx, py + 40, 280, player.getEnergyPercent());

        // Skill cooldown indicators
        drawSkillIndicators(g, bx, py + 56, player, true);
    }

    // ─── AI HUD (right) ──────────────────────────────
    private void drawAIHUD(Graphics2D g, CharacterBase opponent, int gameMode) {
        int px = W - 12 - 70, py = 8;
        boolean isRage = (opponent instanceof EnemyAI) && ((EnemyAI) opponent).isRageMode();
        boolean isPlayer2 = gameMode == 1;

        // Portrait frame
        drawPortrait(g, px, py, false, opponent.isDead(), opponent.charID);

        // Bars (right-aligned, fill from right)
        int bx = W - 12 - 70 - 290;  // start of bars

        // Name tag
        g.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g.setColor(isRage ? new Color(255, 60, 60) : (isPlayer2 ? new Color(0, 212, 255) : new Color(255, 100, 200)));
        FontMetrics fm = g.getFontMetrics();
        String nameStr = (isPlayer2 ? "P2: " : "AI: ") + getCharName(opponent.charID) + (isRage ? " ★ RAGE" : "");
        g.drawString(nameStr, px - fm.stringWidth(nameStr) - 4, py + 13);

        // HP Bar (fills from right)
        drawHPBarRight(g, bx, py + 18, 280, opponent.getHpPercent(), isRage);

        // HP text
        g.setFont(new Font("Arial", Font.BOLD, 9));
        g.setColor(Color.WHITE);
        String hpStr = (int)opponent.getHp() + " / " + (int)opponent.getMaxHp();
        fm = g.getFontMetrics();
        g.drawString(hpStr, bx + 280 - 4 - fm.stringWidth(hpStr), py + 32);

        // Energy bar (right aligned)
        drawEnergyBarRight(g, bx, py + 40, 280, opponent.getEnergyPercent());

        if (isPlayer2 && opponent instanceof Player) {
            drawSkillIndicators(g, bx + 280 - 250, py + 56, (Player)opponent, false);
        }
    }

    // ─── CENTER HUD ──────────────────────────────────
    private void drawCenterHUD(Graphics2D g, int timerSec, int round, int p1Wins, int aiWins) {
        int cx = W / 2;

        // ROUND indicator
        g.setFont(new Font("Courier New", Font.BOLD, 11));
        g.setColor(new Color(255, 215, 0));
        String roundStr = "ROUND " + round;
        FontMetrics fm = g.getFontMetrics();
        g.drawString(roundStr, cx - fm.stringWidth(roundStr) / 2, 16);

        // Timer circle background
        g.setColor(new Color(0, 0, 0, 180));
        g.fillOval(cx - 32, 18, 64, 64);

        // Timer circle border
        float timerPulse = timerSec <= 10
            ? (float)(Math.abs(Math.sin(time * 10)) * 0.5 + 0.5)
            : 1f;
        g.setColor(timerSec <= 10
            ? new Color(255, (int)(150 * timerPulse), 0, 220)
            : new Color(255, 215, 0, 200));
        g.setStroke(new BasicStroke(3));
        g.drawOval(cx - 32, 18, 64, 64);
        g.setStroke(new BasicStroke(1));

        // Timer number
        Font timerFont = new Font("Arial Black", Font.BOLD, timerSec >= 10 ? 32 : 36);
        g.setFont(timerFont);
        fm = g.getFontMetrics();
        String tsStr = String.valueOf(timerSec);
        g.setColor(timerSec <= 10 ? new Color(255, 80, 0) : Color.WHITE);
        if (timerSec <= 10) {
            // Warning scale pulse
            g.setFont(new Font("Arial Black", Font.BOLD, (int)(32 + timerPulse * 6)));
            fm = g.getFontMetrics();
        }
        g.drawString(tsStr, cx - fm.stringWidth(tsStr) / 2, 62);

        // Round win dots
        drawRoundDots(g, cx, 88, p1Wins, aiWins);
    }

    private void drawRoundDots(Graphics2D g, int cx, int y, int p1Wins, int aiWins) {
        // P1 dots (left of center)
        for (int i = 0; i < 2; i++) {
            int dx = cx - 30 + i * 18;
            boolean won = i < p1Wins;
            g.setColor(won ? new Color(255, 215, 0) : new Color(60, 60, 80));
            g.fillOval(dx - 6, y - 6, 12, 12);
            if (won) {
                g.setColor(new Color(255, 215, 0, 80));
                g.fillOval(dx - 9, y - 9, 18, 18);
            }
            g.setColor(new Color(255,255,255,60));
            g.setStroke(new BasicStroke(1));
            g.drawOval(dx - 6, y - 6, 12, 12);
        }
        // AI dots (right of center)
        for (int i = 0; i < 2; i++) {
            int dx = cx + 14 + i * 18;
            boolean won = i < aiWins;
            g.setColor(won ? new Color(255, 45, 120) : new Color(60, 60, 80));
            g.fillOval(dx - 6, y - 6, 12, 12);
            if (won) {
                g.setColor(new Color(255, 45, 120, 80));
                g.fillOval(dx - 9, y - 9, 18, 18);
            }
            g.setColor(new Color(255,255,255,60));
            g.drawOval(dx - 6, y - 6, 12, 12);
        }
        g.setStroke(new BasicStroke(1));
    }

    // ─── COMBO COUNTER ───────────────────────────────
    private void drawCombo(Graphics2D g, int combo) {
        if (combo < 3) return;
        int cx = 22, cy = GamePanel.H / 2 - 20;

        // ★ Combo label với scale bounce
        Graphics2D g2 = (Graphics2D) g.create();
        float sc = comboScale;
        g2.translate(cx, cy);
        g2.scale(sc, sc);
        g2.translate(-cx, -cy);

        g2.setFont(new Font("Courier New", Font.BOLD, 11));
        g2.setColor(new Color(255, 215, 0));
        g2.drawString("COMBO", cx, cy);

        Font bigFont = new Font("Arial Black", Font.BOLD,
                combo >= 10 ? 52 : combo >= 5 ? 46 : 40);
        g2.setFont(bigFont);
        Color comboColor = combo >= 10 ? new Color(255, 45, 120)
                         : combo >= 5  ? new Color(255, 107, 0)
                         :               new Color(255, 215, 0);
        // Shadow
        g2.setColor(new Color(0,0,0,150));
        g2.drawString(combo + "x", cx + 2, cy + 46);
        g2.setColor(comboColor);
        g2.drawString(combo + "x", cx, cy + 44);

        // ★ Descriptive text
        if (combo >= 3) {
            g2.setFont(new Font("Courier New", Font.BOLD, 10));
            String desc;
            Color descColor;
            if (combo >= 10) {
                desc = "★ DEVASTATING! +20% DMG";
                descColor = new Color(255, 45, 120);
            } else if (combo >= 7) {
                desc = "⚡ EXCELLENT! +10% DMG";
                descColor = new Color(0, 255, 204);
            } else if (combo >= 5) {
                desc = "★ GREAT! +10% DMG";
                descColor = new Color(0, 255, 204);
            } else {
                desc = "KEEP GOING!";
                descColor = new Color(255, 215, 0, 180);
            }
            g2.setColor(descColor);
            g2.drawString(desc, cx, cy + 60);
        }

        // ★ Flash ring khi combo tăng
        if (comboFlashTimer > 0) {
            float flashAlpha = comboFlashTimer / 12f;
            g2.setColor(new Color(255, 215, 0, (int)(flashAlpha * 120)));
            g2.setStroke(new BasicStroke(3));
            int ringR = (int)(30 + (1 - flashAlpha) * 20);
            g2.drawOval(cx + 10 - ringR, cy + 30 - ringR, ringR * 2, ringR * 2);
            g2.setStroke(new BasicStroke(1));
        }

        g2.dispose();
    }

    // ─── SCORE ───────────────────────────────────────
    private void drawScoreSmall(Graphics2D g, int score) {
        g.setFont(new Font("Courier New", Font.PLAIN, 10));
        g.setColor(new Color(255, 255, 255, 120));
        g.drawString("SCORE: " + score, W/2 - 35, GamePanel.H - 10);
    }

    // ─── RAGE BORDER ─────────────────────────────────
    private void drawRageBorder(Graphics2D g) {
        float pulse = (float)(Math.abs(Math.sin(time * 8)) * 0.6 + 0.2);
        g.setColor(new Color(255, 0, 0, (int)(pulse * 90)));
        g.setStroke(new BasicStroke(12));
        g.drawRect(6, 6, W - 12, GamePanel.H - 12);
        g.setStroke(new BasicStroke(1));
    }

    // ─── ULTIMATE READY ──────────────────────────────
    private void drawUltimateReady(Graphics2D g) {
        float pulse = (float)(Math.abs(Math.sin(time * 10)));
        g.setFont(new Font("Courier New", Font.BOLD, 11));
        g.setColor(new Color(255, 215, 0, (int)(pulse * 220 + 35)));
        g.drawString("⚡ ULTIMATE READY! (J+K+L)", 14, GamePanel.H - 25);
    }

    // ─── PORTRAITS ───────────────────────────────────
    private void drawPortrait(Graphics2D g, int x, int y, boolean isPlayer, boolean dead, int charID) {
        Color borderColor = isPlayer ? new Color(255, 107, 0) : new Color(0, 212, 255);

        // Nền tối
        g.setColor(new Color(10, 5, 25));
        g.fillRect(x, y, 66, 66);

        // Vẽ ảnh sprite vào portrait (thu nhỏ xuống)
        java.awt.image.BufferedImage portrait = getCharPortrait(charID);
        if (portrait != null) {
            // Crop phần thân trên (50% trên cùng) của sprite để lấy mặt + người
            int cropH = portrait.getHeight() * 2 / 3;
            if (dead) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
            // Vẽ ngược hướng để nhân vật quay vào giữa
            if (isPlayer) {
                g.drawImage(portrait,
                    x + 5, y + 4, x + 61, y + 62,     // dst
                    0, 0, portrait.getWidth(), cropH,   // src (chỉ lấy nửa trên)
                    null);
            } else {
                // flip để quay mặt sang trái (vào trong)
                g.drawImage(portrait,
                    x + 61, y + 4, x + 5, y + 62,
                    0, 0, portrait.getWidth(), cropH,
                    null);
            }
            if (dead) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        } else {
            // Fallback: vẽ mặt người bằng code
            if (isPlayer) drawNaruFace(g, x + 33, y + 33, dead);
            else drawGokaiFace(g, x + 33, y + 33, dead);
        }

        // Viền border
        g.setColor(borderColor);
        g.setStroke(new BasicStroke(2.5f));
        g.drawRect(x, y, 66, 66);
        g.setStroke(new BasicStroke(1));

        // Góc trang trí
        g.setColor(borderColor);
        g.fillRect(x, y, 8, 3);           g.fillRect(x, y, 3, 8);
        g.fillRect(x+58, y, 8, 3);        g.fillRect(x+63, y, 3, 8);
        g.fillRect(x, y+63, 8, 3);        g.fillRect(x, y+58, 3, 8);
        g.fillRect(x+58, y+63, 8, 3);     g.fillRect(x+63, y+58, 3, 8);

        // Dead X overlay
        if (dead) {
            g.setColor(new Color(255, 0, 0, 160));
            g.setStroke(new BasicStroke(3));
            g.drawLine(x+8, y+8, x+58, y+58);
            g.drawLine(x+58, y+8, x+8, y+58);
            g.setStroke(new BasicStroke(1));
        }
    }

    private void drawNaruFace(Graphics2D g, int cx, int cy, boolean dead) {
        // Hair
        g.setColor(new Color(255, 80, 0));
        g.fillOval(cx-14, cy-22, 28, 22);
        // Spikes
        int[] sx = {cx-12, cx-5, cx+3, cx+10};
        int[] sy = {cy-24, cy-32, cy-33, cy-25};
        for (int i = 0; i < sx.length; i++) g.fillOval(sx[i]-4, sy[i]-4, 10,12);
        // Face
        g.setColor(new Color(255, 200, 160));
        g.fillOval(cx-13, cy-14, 26, 22);
        // Eyes
        if (!dead) {
            g.setColor(new Color(0, 220, 180));
            g.fillOval(cx-9, cy-8, 7, 5);
            g.fillOval(cx+2, cy-8, 7, 5);
        } else {
            g.setColor(new Color(150,80,0));
            g.setStroke(new BasicStroke(2));
            g.drawLine(cx-8,cy-9, cx-3,cy-5);
            g.drawLine(cx-3,cy-9, cx-8,cy-5);
            g.drawLine(cx+3,cy-9, cx+8,cy-5);
            g.drawLine(cx+8,cy-9, cx+3,cy-5);
            g.setStroke(new BasicStroke(1));
        }
        // Scarf
        g.setColor(new Color(255, 215, 0));
        g.fillRoundRect(cx-10, cy+6, 20, 5, 3, 3);
    }

    private void drawGokaiFace(Graphics2D g, int cx, int cy, boolean dead) {
        // Shoulders
        g.setColor(new Color(25, 25, 55));
        g.fillOval(cx-22, cy+5, 18, 14);
        g.fillOval(cx+4, cy+5, 18, 14);
        // Body
        g.setColor(new Color(35, 35, 75));
        g.fillRoundRect(cx-14, cy+2, 28, 18, 5, 5);
        // Helmet
        g.setColor(new Color(22, 22, 50));
        g.fillOval(cx-15, cy-20, 30, 28);
        // Crest
        g.setColor(new Color(255, 215, 0));
        g.fillRect(cx-15, cy-20, 30, 5);
        // Eyes
        g.setColor(dead ? new Color(100,0,0) : new Color(255, 215, 0));
        g.fillRect(cx-10, cy-8, 8, 4);
        g.fillRect(cx+2, cy-8, 8, 4);
    }

    // ─── HP BARS ─────────────────────────────────────
    private void drawHPBar(Graphics2D g, int x, int y, int w, float pct, boolean isP1, boolean dead) {
        // Background
        g.setColor(new Color(20, 10, 40));
        g.fillRoundRect(x, y, w, 13, 4, 4);

        // ★ TRAILING BAR (thanh trắng giảm chậm phía sau)
        int trailW = (int)(w * Math.max(0, p1DisplayHp));
        if (trailW > 0) {
            g.setColor(new Color(255, 255, 255, 80));
            g.fillRoundRect(x, y, trailW, 13, 4, 4);
        }

        // Bar fill (actual HP)
        int fillW = (int)(w * Math.max(0, pct));
        Color barColor;
        if (dead || pct < 0.2f) {
            float pulse = (float)(Math.abs(Math.sin(time * 8)));
            barColor = new Color(255, (int)(30 * pulse), 0);
        } else if (pct < 0.5f) {
            barColor = new Color(255, 120, 0);
        } else {
            barColor = new Color(255, 200, 50);
        }
        if (fillW > 0) {
            GradientPaint hp = new GradientPaint(x, y, barColor.brighter(), x, y+13, barColor.darker());
            g.setPaint(hp);
            g.fillRoundRect(x, y, fillW, 13, 4, 4);

            // Highlight stripe
            g.setColor(new Color(255, 255, 255, 40));
            g.fillRoundRect(x, y, fillW, 5, 4, 4);
        }

        // Border
        g.setColor(new Color(255, 255, 255, 60));
        g.setStroke(new BasicStroke(1));
        g.drawRoundRect(x, y, w, 13, 4, 4);
        g.setStroke(new BasicStroke(1));

        // Tick marks
        g.setColor(new Color(0, 0, 0, 80));
        for (int t = 1; t < 10; t++) {
            g.drawLine(x + t * w / 10, y, x + t * w / 10, y + 13);
        }
    }

    private void drawHPBarRight(Graphics2D g, int x, int y, int w, float pct, boolean rage) {
        g.setColor(new Color(20, 10, 40));
        g.fillRoundRect(x, y, w, 13, 4, 4);

        // ★ TRAILING BAR (thanh trắng giảm chậm phía sau)
        int trailW = (int)(w * Math.max(0, aiDisplayHp));
        if (trailW > 0) {
            g.setColor(new Color(255, 255, 255, 80));
            g.fillRoundRect(x + w - trailW, y, trailW, 13, 4, 4);
        }

        int fillW = (int)(w * Math.max(0, pct));
        Color barColor = rage
            ? new Color(255, 40, 0)
            : pct < 0.2f ? new Color(255, 0, 50) : new Color(100, 60, 220);
        if (fillW > 0) {
            // Fills from RIGHT to left for AI
            GradientPaint hp = new GradientPaint(x+w, y, barColor.brighter(), x+w, y+13, barColor.darker());
            g.setPaint(hp);
            g.fillRoundRect(x + w - fillW, y, fillW, 13, 4, 4);
            g.setColor(new Color(255, 255, 255, 40));
            g.fillRoundRect(x + w - fillW, y, fillW, 5, 4, 4);
        }
        g.setColor(new Color(255, 255, 255, 60));
        g.setStroke(new BasicStroke(1));
        g.drawRoundRect(x, y, w, 13, 4, 4);
    }

    // ─── ENERGY BARS ─────────────────────────────────
    private void drawEnergyBar(Graphics2D g, int x, int y, int w, float pct) {
        g.setColor(new Color(20, 10, 40));
        g.fillRoundRect(x, y, w, 8, 3, 3);

        int fillW = (int)(w * Math.max(0, pct));
        if (fillW > 0) {
            float pulse = pct >= 1f ? (float)(Math.abs(Math.sin(time * 10))) * 0.4f + 0.6f : 1f;
            Color ec = pct >= 1f
                ? new Color(255, 230, 50)
                : new Color(255, 180, 0);
            g.setColor(new Color(ec.getRed(), ec.getGreen(), ec.getBlue(),
                    (int)(220 * pulse)));
            g.fillRoundRect(x, y, fillW, 8, 3, 3);
        }
        g.setColor(new Color(255, 215, 0, 80));
        g.setStroke(new BasicStroke(1));
        g.drawRoundRect(x, y, w, 8, 3, 3);

        // Label
        g.setFont(new Font("Arial", Font.PLAIN, 8));
        g.setColor(new Color(255, 215, 0, 160));
        g.drawString("⚡ ENERGY", x + 2, y + 7);
    }

    private void drawEnergyBarRight(Graphics2D g, int x, int y, int w, float pct) {
        g.setColor(new Color(20, 10, 40));
        g.fillRoundRect(x, y, w, 8, 3, 3);
        int fillW = (int)(w * Math.max(0, pct));
        if (fillW > 0) {
            g.setColor(new Color(255, 200, 0, 200));
            g.fillRoundRect(x + w - fillW, y, fillW, 8, 3, 3);
        }
        g.setColor(new Color(255, 215, 0, 80));
        g.setStroke(new BasicStroke(1));
        g.drawRoundRect(x, y, w, 8, 3, 3);
    }

    // ─── SKILL INDICATORS ────────────────────────────
    private void drawSkillIndicators(Graphics2D g, int x, int y, Player player, boolean isP1) {
        // Small boxes showing skill ready/cooldown with CD overlay
        String[] labels = isP1 ? new String[]{"J:ATK", "K:SK1", "L:SK2", "J+K+L:ULT"}
                               : new String[]{"1:ATK", "2:SK1", "3:SK2", "1+2+3:ULT"};
        float[] cdPcts = {
            player.getAttackCDPercent(),
            player.getSkill1CDPercent(),
            player.getSkill2CDPercent(),
            0f  // ultimate doesn't have traditional CD
        };
        Color[] readyColors = {
            new Color(255, 107, 0),
            new Color(0, 212, 255),
            new Color(0, 255, 100),
            player.isUltimateReady() ? new Color(255, 215, 0) : new Color(80, 80, 80)
        };
        g.setFont(new Font("Arial", Font.BOLD, 8));
        for (int i = 0; i < labels.length; i++) {
            int bx = x + i * 64;
            if (bx + 58 > x + 280) break;
            boolean onCD = cdPcts[i] > 0.01f;
            Color baseColor = onCD ? new Color(80, 80, 80) : readyColors[i];

            // Nền
            g.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 100));
            g.fillRoundRect(bx, y, 60, 14, 3, 3);

            // Cooldown overlay bar (fill từ phải sang trái khi CD giảm)
            if (onCD) {
                int cdFillW = (int)(60 * cdPcts[i]);
                g.setColor(new Color(40, 40, 60, 180));
                g.fillRoundRect(bx, y, cdFillW, 14, 3, 3);
            }

            // Border
            g.setColor(onCD ? new Color(120, 120, 140) : readyColors[i]);
            g.drawRoundRect(bx, y, 60, 14, 3, 3);

            // Label text
            g.setColor(onCD ? new Color(180, 180, 180) : readyColors[i]);
            g.drawString(labels[i], bx + 3, y + 10);

            // "READY" flash khi vừa hết CD
            if (!onCD && i > 0 && i < 3) {
                float pulse = (float)(Math.sin(time * 8) * 0.4 + 0.6);
                g.setColor(new Color(readyColors[i].getRed(), readyColors[i].getGreen(),
                                     readyColors[i].getBlue(), (int)(pulse * 60)));
                g.fillRoundRect(bx, y, 60, 14, 3, 3);
            }
        }
    }
}
