package com.animeclash;

import java.awt.*;
import java.util.Random;

/**
 * Particle — hạt hiệu ứng cho combat, skills, và aura
 */
public class Particle {
    private float x, y;
    private float vx, vy;
    private float life;       // 0.0 → 1.0 (1.0 = fresh)
    private float decay;
    private float size;
    private Color color;
    private static final float GRAVITY = 0.08f;
    private static final Random rng = new Random();

    public Particle(float x, float y, Color color, float speed) {
        this.x = x;
        this.y = y;
        this.color = color;
        this.life  = 1f;

        double angle = rng.nextDouble() * Math.PI * 2;
        float spd    = speed * (0.3f + rng.nextFloat() * 0.7f);
        this.vx = (float)(Math.cos(angle) * spd);
        this.vy = (float)(Math.sin(angle) * spd) - rng.nextFloat() * 2f;

        this.decay = 0.018f + rng.nextFloat() * 0.035f;
        this.size  = 2f + rng.nextFloat() * 6f;
    }

    public void update() {
        x  += vx;
        y  += vy;
        vy += GRAVITY;
        vx *= 0.97f;
        life -= decay;
    }

    public void render(Graphics2D g) {
        int alpha = (int)(life * 220);
        if (alpha < 5) return;
        Color c = new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        g.setColor(c);
        int sz = (int)(size * life);
        if (sz < 1) sz = 1;
        g.fillOval((int)(x - sz/2f), (int)(y - sz/2f), sz, sz);
    }

    public boolean isDead() { return life <= 0; }
}
