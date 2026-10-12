package com.animeclash;

import java.awt.*;
import java.awt.LinearGradientPaint;

/**
 * Background — vẽ 5 lớp parallax của đấu trường Học Viện Siêu Năng Lực
 * Layer 4: Bầu trời đêm tím thẫm
 * Layer 3: Tháp học viện + cổng năng lượng
 * Layer 2: Trung cảnh
 * Layer 1: Sàn đá + vết nứt glitch
 * Layer 0: Hàng rào plasma
 */
public class Background {

    private static final int W = GamePanel.W;
    private static final int H = GamePanel.H;
    private static final int FLOOR_Y = GamePanel.FLOOR_Y;

    private float time = 0f;

    // Star positions (static)
    private final int[][] stars = {
        {80,30},{200,22},{350,55},{500,18},{650,42},
        {750,28},{850,52},{120,78},{430,68},{900,33},
        {280,14},{600,76},{720,14},{50,98},{940,68},
        {165,45},{470,10},{820,80},{310,60},{580,35}
    };

    public void update() {
        time += 0.02f;
    }

    public void render(Graphics2D g) {
        if (AssetLoader.bgImage != null) {
            g.drawImage(AssetLoader.bgImage, 0, 0, W, H, null);
        } else {
            // Fallback nếu ảnh lỗi
            drawSky(g);
            drawStars(g);
            drawTowers(g);
            drawPortals(g);
            drawMidground(g);
            drawFloor(g);
            drawGlitchCracks(g);
            drawPlasmaFence(g);
        }
    }

    // ─── SKY ─────────────────────────────────────────
    private void drawSky(Graphics2D g) {
        GradientPaint sky = new GradientPaint(
            0, 0, new Color(26, 5, 51),
            0, (int)(H * 0.65f), new Color(61, 26, 107)
        );
        g.setPaint(sky);
        g.fillRect(0, 0, W, (int)(H * 0.65f));
    }

    // ─── STARS ───────────────────────────────────────
    private void drawStars(Graphics2D g) {
        Color[] starColors = {
            Color.WHITE, new Color(0,255,204), new Color(255,45,120),
            new Color(0,212,255), new Color(255,215,0)
        };
        for (int i = 0; i < stars.length; i++) {
            float twinkle = (float)(Math.sin(time * 3 + i * 0.7) * 0.5 + 0.5);
            Color sc = starColors[i % starColors.length];
            int alpha = (int)(twinkle * 200 + 55);
            g.setColor(new Color(sc.getRed(), sc.getGreen(), sc.getBlue(), alpha));
            int size = (i % 3 == 0) ? 3 : 2;
            g.fillOval(stars[i][0] - size/2, stars[i][1] - size/2, size, size);
        }
    }

    // ─── TOWERS ──────────────────────────────────────
    private void drawTowers(Graphics2D g) {
        Color towerColor = new Color(28, 28, 60);

        // Left tower
        g.setColor(towerColor);
        g.fillRect(20,  90, 65, 270);
        g.fillRect(10,  78, 85, 18);
        g.fillRect(38,  52, 30, 32);
        // Spire
        int[] ltx = {52, 38, 68};
        int[] lty = {32, 52, 52};
        g.fillPolygon(ltx, lty, 3);

        // Right tower
        g.fillRect(870, 100, 70, 260);
        g.fillRect(858, 88, 90, 18);
        g.fillRect(890, 62, 32, 32);
        int[] rtx = {906, 890, 922};
        int[] rty = {42, 62, 62};
        g.fillPolygon(rtx, rty, 3);

        // Tower windows (glowing)
        drawTowerWindows(g);
    }

    private void drawTowerWindows(Graphics2D g) {
        int[][] windows = {
            {40,110},{62,110},{40,148},{62,148},{40,186},{62,186},{40,224},{62,224},
            {888,118},{912,118},{888,156},{912,156},{888,194},{912,194}
        };
        for (int i = 0; i < windows.length; i++) {
            float glow = (float)(Math.sin(time * 2 + i) * 0.4 + 0.6);
            int alpha = (int)(glow * 180);
            Color wc = i % 3 == 0
                ? new Color(123, 47, 190, alpha)
                : new Color(0, 212, 255, alpha / 2);
            g.setColor(wc);
            g.fillRect(windows[i][0], windows[i][1], 13, 16);
            // Glow halo
            g.setColor(new Color(123, 47, 190, alpha / 4));
            g.fillRect(windows[i][0] - 3, windows[i][1] - 3, 19, 22);
        }
    }

    // ─── ENERGY PORTALS ──────────────────────────────
    private void drawPortals(Graphics2D g) {
        drawPortal(g, 165, 200, 55, time);
        drawPortal(g, 798, 190, 50, -time);
    }

    private void drawPortal(Graphics2D g, int cx, int cy, int r, float angle) {
        // Outer glow
        for (int i = r; i > 0; i -= 8) {
            int alpha = (int)((1f - (float)i/r) * 60);
            g.setColor(new Color(123, 47, 190, alpha));
            g.fillOval(cx - i, (int)(cy - i * 1.3f), i*2, (int)(i*2.6f));
        }
        // Inner glow
        RadialGradientPaint rg = new RadialGradientPaint(
            cx, cy, r,
            new float[]{0f, 0.5f, 1f},
            new Color[]{
                new Color(0, 212, 255, 150),
                new Color(123, 47, 190, 100),
                new Color(0, 0, 0, 0)
            }
        );
        g.setPaint(rg);
        g.fillOval(cx - r, (int)(cy - r * 1.3f), r*2, (int)(r*2.6f));

        // Rotating dots
        g.setPaint(null);
        for (int i = 0; i < 6; i++) {
            double a = angle * 2 + i * Math.PI / 3;
            int px = (int)(cx + Math.cos(a) * r * 0.7f);
            int py = (int)(cy + Math.sin(a) * r * 0.5f);
            float glow = (float)(Math.sin(time * 5 + i) * 0.5 + 0.5);
            g.setColor(new Color(0, 255, 204, (int)(glow * 220)));
            g.fillOval(px - 5, py - 5, 10, 10);
        }
    }

    // ─── MIDGROUND ───────────────────────────────────
    private void drawMidground(Graphics2D g) {
        GradientPaint mid = new GradientPaint(
            0, (int)(H * 0.55f), new Color(44, 44, 84),
            0, FLOOR_Y, new Color(58, 58, 92)
        );
        g.setPaint(mid);
        g.fillRect(0, (int)(H * 0.55f), W, FLOOR_Y - (int)(H * 0.55f) + 5);
    }

    // ─── FLOOR ───────────────────────────────────────
    private void drawFloor(Graphics2D g) {
        // Floor base
        GradientPaint floor = new GradientPaint(
            0, FLOOR_Y, new Color(74, 74, 106),
            0, H, new Color(26, 26, 46)
        );
        g.setPaint(floor);
        g.fillRect(0, FLOOR_Y, W, H - FLOOR_Y);

        // Stone tile grid
        g.setColor(new Color(255, 255, 255, 18));
        g.setStroke(new BasicStroke(1));
        for (int gx = 0; gx < W; gx += 64) {
            g.drawLine(gx, FLOOR_Y, gx, H);
        }
        for (int gy = FLOOR_Y; gy < H; gy += 32) {
            g.drawLine(0, gy, W, gy);
        }
        g.setStroke(new BasicStroke(1));

        // Floor highlight line (3-color: transparent → white → transparent)
        LinearGradientPaint hl = new LinearGradientPaint(
            0, FLOOR_Y, W, FLOOR_Y,
            new float[]{0f, 0.5f, 1f},
            new Color[]{
                new Color(255,255,255,0),
                new Color(255,255,255,30),
                new Color(255,255,255,0)
            });
        g.setPaint(hl);
        g.setStroke(new BasicStroke(2));
        g.drawLine(0, FLOOR_Y, W, FLOOR_Y);
        g.setStroke(new BasicStroke(1));
    }

    // ─── GLITCH CRACKS ───────────────────────────────
    private void drawGlitchCracks(Graphics2D g) {
        int[][] cracks = {{180, FLOOR_Y+4, 90}, {440, FLOOR_Y+7, 130}, {690, FLOOR_Y+3, 70}};
        for (int i = 0; i < cracks.length; i++) {
            float glow = (float)(Math.sin(time * 4 + i * 1.3) * 0.5 + 0.5);
            boolean useCyan = Math.sin(time * 2 + i) > 0;
            Color c1 = useCyan ? new Color(0, 255, 204) : new Color(255, 45, 120);

            g.setColor(new Color(c1.getRed(), c1.getGreen(), c1.getBlue(),
                    (int)(glow * 200 + 55)));
            g.setStroke(new BasicStroke(2.5f));
            // Jagged crack line
            int cx = cracks[i][0], cw = cracks[i][2];
            int prevX = cx, prevY = cracks[i][1];
            for (int seg = 0; seg < 6; seg++) {
                int nx = cx + (seg+1) * cw / 6;
                int ny = cracks[i][1] + (int)(Math.sin(seg * 1.8) * 4);
                g.drawLine(prevX, prevY, nx, ny);
                prevX = nx; prevY = ny;
            }

            // Glow under crack
            g.setColor(new Color(c1.getRed(), c1.getGreen(), c1.getBlue(),
                    (int)(glow * 60)));
            g.setStroke(new BasicStroke(8));
            g.drawLine(cx, cracks[i][1] + 2, cx + cw, cracks[i][1] + 2);
            g.setStroke(new BasicStroke(1));
        }
    }

    // ─── PLASMA FENCE ────────────────────────────────
    private void drawPlasmaFence(Graphics2D g) {
        int fenceY = FLOOR_Y - 12;
        float glow = (float)(Math.sin(time * 6) * 0.35 + 0.65);

        // Posts
        g.setColor(new Color(80, 80, 110));
        g.fillRect(0,   fenceY - 38, 9, 50);
        g.fillRect(W-9, fenceY - 38, 9, 50);

        // Post caps (gold)
        g.setColor(new Color(255, 215, 0));
        g.fillRect(0,   fenceY - 40, 9, 5);
        g.fillRect(W-9, fenceY - 40, 9, 5);

        // Plasma beam — multiple passes for glow effect
        for (int pass = 3; pass > 0; pass--) {
            int alpha = (int)(glow * (pass == 1 ? 180 : pass == 2 ? 60 : 20));
            float width = pass == 1 ? 1.5f : pass == 2 ? 4f : 8f;
            g.setColor(new Color(255, 215, 0, alpha));
            g.setStroke(new BasicStroke(width));
            g.drawLine(9, fenceY, W-9, fenceY);
        }
        g.setStroke(new BasicStroke(1));

        // Plasma sparkles
        for (int i = 0; i < 5; i++) {
            float sparkX = 9 + (i + 1) * (float)(W - 18) / 6;
            float sparkGlow = (float)(Math.sin(time * 8 + i * 1.2) * 0.5 + 0.5);
            g.setColor(new Color(255, 255, 200, (int)(sparkGlow * 160)));
            g.fillOval((int)sparkX - 3, fenceY - 3, 6, 6);
        }
    }
}
