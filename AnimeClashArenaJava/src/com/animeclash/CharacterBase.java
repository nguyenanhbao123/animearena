package com.animeclash;

import java.awt.*;

/**
 * CharacterBase — Lớp nền tảng cho mọi nhân vật
 * Chứa HP, Energy, Physics, Combat state
 */
public abstract class CharacterBase {

    // ─── STATS ───────────────────────────────────────
    protected float hp, maxHp;
    protected float energy, maxEnergy = 100f;
    protected int   attackDmg;
    protected float moveSpeed;

    // ─── POSITION / PHYSICS ──────────────────────────
    protected float x, y;
    protected float vx, vy;
    protected static final float GRAVITY    = 0.55f;
    protected static final float MAX_FALL   = 18f;
    protected static final int   FLOOR_Y    = GamePanel.FLOOR_Y;
    protected int   width = 64, height = 96;

    protected boolean onGround    = true;
    protected int     jumpsLeft   = 1;
    protected int     facing      = 1;   // 1 = right, -1 = left

    // ─── STATE ───────────────────────────────────────
    public enum CharState {
        IDLE, RUN, JUMP, ATTACK, SKILL1, SKILL2, ULTIMATE, HIT, KO, DODGE
    }
    protected CharState state       = CharState.IDLE;
    protected int       stateTimer  = 0;

    // Cooldowns (in frames at 60fps)
    protected int attackCD = 0;
    protected int skill1CD = 0;
    protected int skill2CD = 0;

    // Invincibility
    protected boolean isInvincible = false;
    protected int     invincTimer  = 0;

    // Hit flash
    protected boolean hitFlash    = false;
    protected int     hitFlashTimer = 0;

    // Combo tracking
    protected int combo = 0;
    protected int comboTimer = 0;

    // ─── ATTACK PHASE SYSTEM ─────────────────────────
    // ─── COMBAT PROPERTIES ───────────────────────────
    protected CharacterBase attackTarget;
    public enum AttackPhase { NONE, STARTUP, ACTIVE, RECOVERY }
    protected AttackPhase attackPhase = AttackPhase.NONE;
    protected int phaseTimer   = 0;
    protected int startupLen   = 0;
    protected int activeLen    = 0;
    protected int recoveryLen  = 0;
    protected boolean hitLanded = false;
    protected int comboStep     = 0;
    protected boolean inputBuffered = false;

    // ─── CHARACTER SPECIFIC ──────────────────────────
    public int charID; // 0=Naru, 1=Gokai, 2=Kira, 3=Zin
    protected float cloneX, cloneY;
    protected int cloneTimer;
    protected int skill1VFXTimer;
    protected int ultimateTimer;
    protected boolean rageMode = false;
    protected int rageATK = 0;
    protected boolean waveActive = false;
    protected float waveX, waveW;
    protected int waveTimer;

    public CharacterBase(GamePanel game, int charID) {
        this.game = game;
        this.charID = charID;
        switch (charID) {
            case 0 -> { // Naru (Balanced / Đấu Sĩ Cân Bằng)
                this.maxHp = 1000f; this.hp = 1000f;
                this.attackDmg = 55; this.moveSpeed = 6.2f;
                this.width = 64; this.height = 96;
            }
            case 1 -> { // Gokai (Tanker / Đỡ Đòn Tiên Phong: Máu nhiều dame ít)
                this.maxHp = 1350f; this.hp = 1350f;
                this.attackDmg = 40; this.moveSpeed = 4.8f;
                this.width = 70; this.height = 104;
            }
            case 2 -> { // Kira (Mage / Pháp Sư & Sát Thủ: Máu ít dame cao)
                this.maxHp = 820f; this.hp = 820f;
                this.attackDmg = 75; this.moveSpeed = 7.8f;
                this.width = 60; this.height = 90;
            }
            case 3 -> { // Zin (Swordsman / Kiếm Khách & Chiến Binh)
                this.maxHp = 920f; this.hp = 920f;
                this.attackDmg = 65; this.moveSpeed = 6.6f;
                this.width = 64; this.height = 98;
            }
            default -> {
                this.maxHp = 1000f; this.hp = 1000f;
                this.attackDmg = 55; this.moveSpeed = 6.2f;
            }
        }
    }

    // Reference to game
    protected GamePanel game;

    // ─── ANIMATION ───────────────────────────────────
    protected int   animFrame  = 0;
    protected int   animTick   = 0;
    protected int   animSpeed  = 6;  // frames per animation frame
    protected float time       = 0f;

    // ─── PHYSICS UPDATE ──────────────────────────────
    protected void applyPhysics() {
        // Tự động chuyển onGround = false nếu đang ở trên không hoặc có vận tốc nhảy/hất tung (vy < 0)
        if (y + height < FLOOR_Y || vy < 0) {
            onGround = false;
        }

        // Gravity
        if (!onGround) {
            vy += GRAVITY;
            if (vy > MAX_FALL) vy = MAX_FALL;
        }

        x += vx;
        y += vy;

        // Giới hạn trần nhà (Ceiling bounds) để nhân vật không bao giờ bay mất khỏi màn hình phía trên!
        if (y < 0) {
            y = 0;
            if (vy < 0) vy = 0;
        }

        // Floor collision — dùng y + height (chân nhân vật)
        if (y + height >= FLOOR_Y) {
            y = FLOOR_Y - height;   // đỉnh đầu = sàn - chiều cao
            vy = 0;
            onGround  = true;
            jumpsLeft = getMaxJumps();
            if (state == CharState.JUMP && !isDead()) state = CharState.IDLE;
        }

        // Wall bounds
        x = Math.max(0, Math.min(GamePanel.W - width, x));

        // Friction
        if (onGround) vx *= 0.75f;
        else          vx *= 0.92f;
    }

    protected int getMaxJumps() { return 1; }

    // ─── STATE TIMER ─────────────────────────────────
    protected void updateStateTimer() {
        if (stateTimer > 0) {
            stateTimer--;
            if (stateTimer == 0 && !isDead()) {
                // Hitstun recovery: sau khi HIT kết thúc, cho khoảng bất tử ngắn
                // để tránh stun-lock khi AI tấn công nhanh (Rage Mode)
                if (state == CharState.HIT) {
                    isInvincible = true;
                    invincTimer  = 10;  // ~0.17s recovery
                }
                state = CharState.IDLE;
            }
        }
    }

    protected void updateCooldowns() {
        if (attackCD > 0) attackCD--;
        if (skill1CD > 0) skill1CD--;
        if (skill2CD > 0) skill2CD--;
    }

    protected void updateInvincibility() {
        if (isInvincible && invincTimer > 0) {
            invincTimer--;
            if (invincTimer <= 0) isInvincible = false;
        }
        if (hitFlashTimer > 0) {
            hitFlashTimer--;
            hitFlash = hitFlashTimer % 4 < 2;
            if (hitFlashTimer == 0) hitFlash = false;
        }
    }

    protected void updateAnimation() {
        animTick++;
        if (animTick >= animSpeed) {
            animTick = 0;
            animFrame++;
        }
    }

    protected void updateCombo() {
        if (comboTimer > 0) {
            comboTimer--;
            if (comboTimer == 0) combo = 0;
        }
    }

    // ─── ATTACK PHASE UPDATE ─────────────────────────
    protected void updateAttackPhase() {
        if (attackPhase == AttackPhase.NONE) return;

        if (attackPhase == AttackPhase.ACTIVE) {
            checkActiveHit();
        }

        phaseTimer--;
        if (phaseTimer <= 0) {
            switch (attackPhase) {
                case STARTUP -> {
                    attackPhase = AttackPhase.ACTIVE;
                    phaseTimer  = activeLen;
                    hitLanded   = false;
                }
                case ACTIVE -> {
                    attackPhase = AttackPhase.RECOVERY;
                    phaseTimer  = recoveryLen;
                }
                case RECOVERY -> {
                    attackPhase = AttackPhase.NONE;
                    onAttackPhaseEnd();
                }
                default -> {}
            }
        }
    }

    protected void onAttackPhaseEnd() {
        if (inputBuffered && comboStep > 0) {
            inputBuffered = false;
            doAttack(attackTarget);
        } else {
            comboStep = 0;
            inputBuffered = false;
            if (state == CharState.ATTACK) state = CharState.IDLE;
        }
    }

    public void checkActiveHit() {
        if (attackPhase != AttackPhase.ACTIVE) return;
        if (hitLanded || attackTarget == null) return;

        // Skill hit check (AoE)
        if (state == CharState.SKILL1) {
            if (isInRange(attackTarget, 220) && !attackTarget.isDead()) {
                hitLanded = true;
                game.addScore(50);
                attackTarget.takeDamage(this, (int)(attackDmg * 1.5f), "skill");
            }
            return;
        }

        // Normal attack combo hit check
        if (state != CharState.ATTACK) return;
        if (isInRange(attackTarget, 130) && !attackTarget.isDead()) {
            hitLanded = true;
            float dmgMult = switch (comboStep) {
                case 1 -> 0.8f;
                case 2 -> 1.0f;
                default -> 1.4f;
            };
            String knockType = (comboStep == 0) ? "combo3" : "normal"; // step reset ve 0 o cuoi chuoi
            int dmg = (int)(attackDmg * dmgMult);
            game.addScore(20);
            combo++; comboTimer = 90;
            if (combo >= 10) { dmg = (int)(dmg * 1.2f); game.addScore(150); }
            else if (combo >= 5) { dmg = (int)(dmg * 1.1f); game.addScore(50); }

            if ("combo3".equals(knockType)) {
                attackTarget.takeDamage(this, dmg, "skill");
            } else {
                attackTarget.takeDamage(this, dmg, "normal");
            }
            game.spawnParticles(attackTarget.getCenterX(), attackTarget.getCenterY() - 20, 18, new Color(255, 107, 0), 5f);
        }
    }

    /** Bắt đầu attack với frame data cụ thể */
    protected void startAttackPhase(int startup, int active, int recovery) {
        attackPhase  = AttackPhase.STARTUP;
        startupLen   = startup;
        activeLen    = active;
        recoveryLen  = recovery;
        phaseTimer   = startup;
        hitLanded    = false;
    }

    // ─── COMBAT ──────────────────────────────────────
    public void takeDamage(CharacterBase attacker, int damage, String type) {
        if (isInvincible || isDead()) return;
        game.setPerfectRound(false);

        hp = Math.max(0, hp - damage);
        energy = Math.min(maxEnergy, energy + 8f);

        state       = CharState.HIT;
        stateTimer  = "ultimate".equals(type) ? 28 : "skill".equals(type) ? 22 : 14;
        hitFlash    = true;
        hitFlashTimer = 18;

        // Bị đánh → hủy attack phase hiện tại
        attackPhase = AttackPhase.NONE;

        // Knockback
        float dir = attacker.getCenterX() < getCenterX() ? 1 : -1;
        float kb  = "ultimate".equals(type) ? 18f : "skill".equals(type) ? 10f : 6f;
        vx = dir * kb;
        vy = "skill".equals(type) ? -5f : -2f;
        onGround = false;

        // ★ HITSTOP — freeze cả 2 nhân vật vài frame tạo cảm giác impact
        // Tăng mạnh thời gian hitstop (bị khựng lại) khi trúng skill/ultimate để tăng trải nghiệm đánh nhau hoàn hảo
        int hitstopFrames = "ultimate".equals(type) ? 14 : "skill".equals(type) ? 10 : 4;
        game.triggerHitstop(hitstopFrames);

        // ★ SCREEN SHAKE — rung màn hình theo mức độ đòn
        float shakeIntensity = "ultimate".equals(type) ? 10f : "skill".equals(type) ? 5f : 2f;
        int shakeDuration    = "ultimate".equals(type) ? 12  : "skill".equals(type) ? 6  : 4;
        game.triggerShake(shakeIntensity, shakeDuration);

        // Spawn particles
        Color color = "ultimate".equals(type) ? new Color(255,215,0)
                    : "skill".equals(type)    ? new Color(0,212,255)
                    :                           new Color(255,107,0);
        game.spawnParticles(getCenterX(), getCenterY(),
                "ultimate".equals(type) ? 40 : 15, color, 5f);

        game.addHitEffect(getCenterX(), getCenterY() - 20, damage, type);
        SoundManager.playHit();

        if (hp <= 0) {
            state = CharState.KO;
            stateTimer = 180;
            SoundManager.playKO();
        }
    }

    protected void gainEnergy(float amount) {
        energy = Math.min(maxEnergy, energy + amount);
    }

    // ─── COMBAT METHODS (UNIFIED) ────────────────────
    public void doAttack(CharacterBase target) {
        if (isDead()) return;
        attackTarget = target;

        if (state == CharState.ATTACK && attackPhase == AttackPhase.RECOVERY) {
            inputBuffered = true; return;
        }
        if (attackPhase != AttackPhase.NONE) return;

        state = CharState.ATTACK;
        gainEnergy(5f);

        switch (charID) {
            case 0, 2, 3 -> { // Naru, Kira, Zin (Fast/Medium combo)
                switch (comboStep) {
                    case 0 -> { startAttackPhase(3, 4, 10); comboStep = 1; }
                    case 1 -> { startAttackPhase(3, 5, 10); comboStep = 2; }
                    default -> { startAttackPhase(4, 6, 14); comboStep = 0; }
                }
            }
            case 1 -> { // Gokai (Slow power combo)
                switch (comboStep) {
                    case 0 -> { startAttackPhase(6, 6, 15); comboStep = 1; }
                    default -> { startAttackPhase(8, 8, 20); comboStep = 0; }
                }
            }
        }
        stateTimer = startupLen + activeLen + recoveryLen;
    }

    public void doSkill1(CharacterBase target) {
        if (skill1CD > 0 || isDead()) return;
        state = CharState.SKILL1;
        skill1CD = 200; // Default CD
        attackTarget = target;
        gainEnergy(20f);

        switch (charID) {
            case 0 -> { // Naru (Whirlwind)
                skill1VFXTimer = 35;
                startAttackPhase(6, 10, 8);
                game.spawnParticles(getCenterX(), getCenterY(), 70, new Color(0, 212, 255), 7f);
                SoundManager.playSkill(charID);
                game.showAnnounce("VÒNG XOÁY!", 40);
            }
            case 1 -> { // Gokai (Fire blast)
                skill1VFXTimer = 35;
                startAttackPhase(15, 10, 20);
                game.spawnParticles(getCenterX(), getCenterY(), 50, new Color(255, 60, 0), 8f);
                SoundManager.playSkill(charID);
                game.showAnnounce("HỎA RÌU TRẢM!", 40);
            }
            case 2 -> { // Kira (Energy Wave)
                skill1VFXTimer = 35;
                startAttackPhase(5, 8, 10);
                game.spawnParticles(getCenterX(), getCenterY(), 40, new Color(150, 0, 255), 6f);
                SoundManager.playSkill(charID);
                game.showAnnounce("ĐIỆN TỪ!", 30);
            }
            case 3 -> { // Zin (Dash Slash)
                skill1VFXTimer = 35;
                startAttackPhase(4, 5, 12);
                vx = facing * 25f; // dash forward
                isInvincible = true; invincTimer = 10;
                game.spawnParticles(getCenterX(), getCenterY(), 40, new Color(0, 255, 100), 5f);
                SoundManager.playSkill(charID);
            }
        }
        stateTimer = startupLen + activeLen + recoveryLen;
    }

    public void doSkill2(CharacterBase target) {
        if (skill2CD > 0 || isDead()) return;
        state = CharState.SKILL2;
        skill2CD = 300;
        attackTarget = target;

        switch (charID) {
            case 0 -> { // Naru (Clone dash)
                stateTimer = 40;
                cloneX = x; cloneY = y; cloneTimer = 60;
                float newX = target.getCenterX() + (facing > 0 ? 80 : -80);
                newX = Math.max(0, Math.min(GamePanel.W - width, newX));
                x = newX; y = FLOOR_Y - height;
                game.spawnParticles(getCenterX(), getCenterY(), 25, new Color(255, 215, 0), 5f);
                SoundManager.playSkill(charID);
                game.showAnnounce("PHÂN THÂN!", 40);
            }
            case 1 -> { // Gokai (Rage mode)
                stateTimer = 60;
                cloneX = x; cloneY = y; cloneTimer = 50;
                rageMode = true; rageATK = 100;
                game.spawnParticles(getCenterX(), getCenterY(), 100, new Color(255, 0, 0), 8f);
                SoundManager.playSkill(charID);
                game.showAnnounce("CUỒNG NỘ!", 60);
            }
            case 2 -> { // Kira (Teleport)
                stateTimer = 20;
                cloneX = x; cloneY = y; cloneTimer = 40;
                float newX = target.getCenterX() - facing * 120;
                x = Math.max(0, Math.min(GamePanel.W - width, newX));
                y = FLOOR_Y - height;
                game.spawnParticles(getCenterX(), getCenterY(), 30, new Color(123, 47, 190), 5f);
                SoundManager.playSkill(charID);
            }
            case 3 -> { // Zin (Parry stance)
                stateTimer = 30;
                cloneX = x; cloneY = y; cloneTimer = 40;
                isInvincible = true; invincTimer = 30;
                game.spawnParticles(getCenterX(), getCenterY(), 50, new Color(200, 255, 200), 4f);
                SoundManager.playSkill(charID);
                game.showAnnounce("PHẢN ĐÒN!", 30);
            }
        }
    }

    public void doUltimate(CharacterBase target) {
        if (!isUltimateReady() || isDead() || ultimateTimer > 0) return;
        state = CharState.ULTIMATE;
        stateTimer = 80; ultimateTimer = 80;
        energy = 0f;
        attackTarget = target;

        if (!target.isDead()) {
            game.addScore(200);
            int dmg = switch(charID) {
                case 0 -> 420; // Naru Balanced
                case 1 -> 320; // Gokai Tanker dame ít
                case 2 -> 500; // Kira Mage dồn dame mạnh
                case 3 -> 460; // Zin Swordsman dame cao
                default -> 400;
            };
            target.takeDamage(this, dmg, "ultimate");
        }
        
        Color fxColor = switch(charID) {
            case 0 -> new Color(255, 215, 0); // Naru golden dragon
            case 1 -> new Color(255, 0, 0);   // Gokai red explosion
            case 2 -> new Color(150, 0, 255); // Kira purple laser
            case 3 -> new Color(0, 255, 100); // Zin green slash storm
            default -> Color.WHITE;
        };
        game.spawnParticles(getCenterX(), FLOOR_Y - 30, 300, fxColor, 14f);
        SoundManager.playUltimate(charID);
        
        String ultName = switch(charID) {
            case 0 -> "ĐẠI LONG XOÁY";
            case 1 -> "VÔ CỰC HỎA";
            case 2 -> "HỦY DIỆT ĐIỆN TỪ";
            case 3 -> "BÃO KIẾM";
            default -> "ULTIMATE!";
        };
        game.showAnnounce(ultName, 90);
    }

    // ─── GETTERS & HELPERS ───────────────────────────
    public boolean isDead() { return hp <= 0; }
    public boolean isUltimateReady() { return energy >= maxEnergy; }
    public int getCenterX() { return (int) (x + width / 2); }
    public int getCenterY() { return (int) (y + height / 2); }
    public float getHp() { return hp; }
    public float getMaxHp() { return maxHp; }
    public float getHpPercent() { return hp / maxHp; }
    public float getEnergyPercent() { return energy / maxEnergy; }
    public float getAttackCDPercent() { return 0f; }
    public float getSkill1CDPercent() { return skill1CD > 0 ? (float) skill1CD / 200f : 0f; }
    public float getSkill2CDPercent() { return skill2CD > 0 ? (float) skill2CD / 300f : 0f; }
    public int getCombo() { return combo; }
    public boolean isRageMode() { return rageMode; }
    
    public boolean isInRange(CharacterBase target, int range) {
        if (target == null) return false;
        float dx = getCenterX() - target.getCenterX();
        float dy = getCenterY() - target.getCenterY();
        return Math.sqrt(dx*dx + dy*dy) <= range;
    }
    
    protected void applyHitAlpha(Graphics2D g) {
        if (hitFlash && (time * 10) % 2 < 1) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        }
    }
    
    protected void drawShadow(Graphics2D g) {
        if (onGround) {
            g.setColor(new Color(0, 0, 0, 100));
            g.fillOval((int)x, (int)y + height - 5, width, 10);
        }
    }
    
    protected void resetAlpha(Graphics2D g) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }

    public void render(Graphics2D g) {
        Graphics2D g2 = (Graphics2D) g.create();
        applyHitAlpha(g2);
        drawShadow(g2);

        int ix = (int) x;
        int iy = (int) y;

        // Draw VFX based on state and charID
        if (cloneTimer > 0) {
            Graphics2D gc = (Graphics2D) g2.create();
            gc.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, cloneTimer / 80f * 0.5f));
            if (charID == 0 && AssetLoader.naruSkill2 != null) gc.drawImage(AssetLoader.naruSkill2, (int)cloneX - 75, (int)cloneY - 75, 150, 150, null);
            else if (charID == 1 && AssetLoader.gokaiSkill2 != null) {
                gc.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1.0f, cloneTimer / 30f)));
                gc.drawImage(AssetLoader.gokaiSkill2, ix - 60, iy - 50, 190, 190, null);
            }
            else if (charID == 2 && AssetLoader.kiraSkill2 != null) gc.drawImage(AssetLoader.kiraSkill2, (int)cloneX - 75, (int)cloneY - 75, 150, 150, null);
            else if (charID == 3 && AssetLoader.zinSkill2 != null) {
                gc.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1.0f, cloneTimer / 30f)));
                gc.drawImage(AssetLoader.zinSkill2, ix - 40, iy - 30, 150, 150, null);
            }
            else drawBody(gc, (int)cloneX, (int)cloneY, -facing, false);
            gc.dispose();
        }

        if (rageMode && charID == 1) {
            float pulse = (float)(Math.sin(time * 6) * 0.5 + 0.5);
            for (int r = 60; r > 0; r -= 15) {
                g2.setColor(new Color(255, 0, 0, (int)(pulse * 15)));
                g2.fillOval(ix - r + width/2, iy - r + height/2, r*2, r*2);
            }
        }

        if (ultimateTimer > 0) {
            float prog = ultimateTimer / 80f;
            if (charID == 0 && AssetLoader.naruUltimate != null) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1.0f, prog * 2)));
                g2.drawImage(AssetLoader.naruUltimate, ix + width/2 - 150, (int)(y - 210), 300, 300, null);
            } else if (charID == 1 && AssetLoader.gokaiUltimate != null) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1.0f, prog * 2)));
                g2.drawImage(AssetLoader.gokaiUltimate, ix + width/2 - 200, (int)(y - 250), 400, 380, null);
            } else if (charID == 2 && AssetLoader.kiraUltimate != null) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1.0f, prog * 2)));
                int ultW = 500, ultH = 180;
                int ultX = facing > 0 ? ix + width : ix - ultW;
                g2.drawImage(AssetLoader.kiraUltimate, ultX, iy - 40, ultW, ultH, null);
            } else if (charID == 3 && AssetLoader.zinUltimate != null) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1.0f, prog * 2)));
                g2.drawImage(AssetLoader.zinUltimate, ix + width/2 - 180, (int)(y - 230), 360, 350, null);
            } else {
                Color c = (charID == 1) ? new Color(255, 0, 0, (int)(prog*100)) : new Color(255, 215, 0, (int)(prog*20));
                g2.setColor(c);
                g2.fillOval(ix - 40, iy - 60, width + 80, height + 100);
            }
        }

        if (skill1VFXTimer > 0) {
            float prog = skill1VFXTimer / 35f;
            int sw = 200, sh = 100;
            int sx = (int)(getCenterX() - sw/2);
            int sy = (int)(getCenterY() - sh/2);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, prog));
            
            java.awt.image.BufferedImage skillImg = switch(charID) {
                case 0 -> AssetLoader.naruSkill1;
                case 1 -> AssetLoader.gokaiSkill1;
                case 2 -> AssetLoader.kiraSkill1;
                case 3 -> AssetLoader.zinSkill1;
                default -> null;
            };
            
            if (skillImg != null) {
                if (facing < 0) g2.drawImage(skillImg, sx + sw, sy, sx, sy + sh, 0, 0, skillImg.getWidth(), skillImg.getHeight(), null);
                else g2.drawImage(skillImg, sx, sy, sw, sh, null);
            }
        }

        // Đã loại bỏ hào quang vòng tròn xoay quanh nhân vật trong trận đấu theo yêu cầu

        drawBody(g2, ix, iy, facing, isDead());
        
        resetAlpha(g2);
        g2.dispose();
    }

    private void drawBody(Graphics2D g, int ix, int iy, int dir, boolean ko) {
        java.awt.image.BufferedImage sprite = null;
        int drawW = 130, drawH = 160;

        if (charID == 0) {
            sprite = (state == CharState.HIT && AssetLoader.naruHit != null) ? AssetLoader.naruHit : ((state == CharState.ATTACK && AssetLoader.naruAttack != null) ? AssetLoader.naruAttack : AssetLoader.naruIdle);
        } else if (charID == 1) {
            sprite = (state == CharState.HIT && AssetLoader.gokaiHit != null) ? AssetLoader.gokaiHit : ((state == CharState.ATTACK && AssetLoader.gokaiAttack != null) ? AssetLoader.gokaiAttack : AssetLoader.gokaiIdle);
            drawW = 160; drawH = 200;
        } else if (charID == 2) {
            sprite = (state == CharState.HIT && AssetLoader.kiraHit != null) ? AssetLoader.kiraHit : ((state == CharState.ATTACK && AssetLoader.kiraAttack != null) ? AssetLoader.kiraAttack : AssetLoader.kiraIdle);
            drawW = 140; drawH = 150;
        } else if (charID == 3) {
            sprite = (state == CharState.HIT && AssetLoader.zinHit != null) ? AssetLoader.zinHit : ((state == CharState.ATTACK && AssetLoader.zinAttack != null) ? AssetLoader.zinAttack : AssetLoader.zinIdle);
            drawW = 140; drawH = 170;
        }

        if (sprite == null) { // Fallback shape if no sprite
            g.setColor(charID == 0 ? Color.BLUE : charID == 1 ? Color.RED : charID == 2 ? Color.MAGENTA : Color.GREEN);
            if (ko) g.fillRect(ix, iy + height/2, width, height/2);
            else g.fillRect(ix, iy, width, height);
            return;
        }

        int groundLine = (int)(y + height);
        int drawX = (int)(x + width / 2f - drawW / 2f);
        int drawY = groundLine - drawH;

        int effectiveDir = dir;
        if (charID == 1) {
            effectiveDir = -dir; // Gokai's idle and attack sprites both face left by default
        } else if (charID == 2) {
            // Kira's idle faces left by default (-dir), but kira_attack faces right (dir)
            if (state == CharState.ATTACK && AssetLoader.kiraAttack != null) {
                effectiveDir = dir;
            } else {
                effectiveDir = -dir;
            }
        }

        if (ko) {
            Graphics2D gKO = (Graphics2D) g.create();
            gKO.rotate(Math.PI / 2, ix + width / 2.0, iy + height / 2.0);
            gKO.drawImage(sprite, drawX, drawY, drawX + drawW, drawY + drawH, 0, 0, sprite.getWidth(), sprite.getHeight(), null);
            gKO.dispose();
        } else if (state == CharState.HIT) {
            // ★ HIỆU ỨNG TRÚNG SKILL BỊ KHỰNG (HIT FLINCH & REELING STATE)
            // Nghiêng người ra phía sau chịu đòn, tạo cảm giác chấn động mạnh mẽ
            Graphics2D gHit = (Graphics2D) g.create();
            double tiltAngle = Math.toRadians(dir > 0 ? -18 : 18); 
            gHit.rotate(tiltAngle, drawX + drawW / 2.0, drawY + drawH / 2.0);
            
            // Dịch chuyển rung nhẹ theo phương ngang để tạo cảm giác bị khựng/chuyển động chấn thương
            int shakeOffset = (stateTimer % 4 > 1) ? -dir * 6 : dir * 4;
            
            if (effectiveDir < 0) {
                gHit.drawImage(sprite, drawX + drawW + shakeOffset, drawY, drawX + shakeOffset, drawY + drawH, 0, 0, sprite.getWidth(), sprite.getHeight(), null);
            } else {
                gHit.drawImage(sprite, drawX + shakeOffset, drawY, drawX + drawW + shakeOffset, drawY + drawH, 0, 0, sprite.getWidth(), sprite.getHeight(), null);
            }
            gHit.dispose();
        } else if (effectiveDir < 0) {
            g.drawImage(sprite, drawX + drawW, drawY, drawX, drawY + drawH, 0, 0, sprite.getWidth(), sprite.getHeight(), null);
        } else {
            g.drawImage(sprite, drawX, drawY, drawX + drawW, drawY + drawH, 0, 0, sprite.getWidth(), sprite.getHeight(), null);
        }
    }
}
