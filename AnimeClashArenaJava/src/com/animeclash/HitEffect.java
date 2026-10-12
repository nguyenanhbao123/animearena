package com.animeclash;

import java.awt.*;

/**
 * HitEffect — hiển thị số sát thương nổi lên khi đánh trúng
 */
public class HitEffect {
    private float x, y;
    private int   damage;
    private String type;
    private float life = 1f;
    private float vy = -1.5f;

    public HitEffect(float x, float y, int damage, String type) {
        this.x = x; this.y = y;
        this.damage = damage;
        this.type   = type;
    }

    public void update() {
        y  += vy;
        vy += 0.04f;
        life -= 0.028f;
    }

    public void render(Graphics2D g) {
        if (life <= 0) return;
        int alpha = (int)(Math.min(1f, life * 2f) * 220);
        Color c;
        int fontSize;
        switch (type) {
            case "ultimate" -> { c = new Color(255, 215,  0, alpha); fontSize = 28; }
            case "skill"    -> { c = new Color(0,   212,255, alpha); fontSize = 22; }
            default         -> { c = new Color(255, 107,  0, alpha); fontSize = 18; }
        }
        g.setFont(new Font("Segoe UI", Font.BOLD, (int)(fontSize * (0.8f + life * 0.2f))));
        g.setColor(new Color(0, 0, 0, alpha / 2));
        g.drawString("-" + damage, (int)x + 2, (int)y + 2);
        g.setColor(c);
        g.drawString("-" + damage, (int)x, (int)y);
    }

    public boolean isDead() { return life <= 0; }
}
