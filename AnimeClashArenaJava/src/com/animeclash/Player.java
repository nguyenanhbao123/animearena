package com.animeclash;

import java.awt.Color;
import java.util.Set;

/**
 * Player — Controller cho người chơi (Input-driven)
 * Kế thừa toàn bộ logic từ CharacterBase, chỉ xử lý phím bấm và di chuyển.
 */
public class Player extends CharacterBase {

    // ─── DASH ─────────────────────────────────────────
    private int dashTimer  = 0;
    private int dashCooldown = 0;
    private static final float JUMP_FORCE = -13f;

    // ★ Configurable keybinding (P1: WASD, P2: Arrow Keys)
    private int keyLeft  = java.awt.event.KeyEvent.VK_A;
    private int keyRight = java.awt.event.KeyEvent.VK_D;

    public Player(GamePanel game, int charID) {
        super(game, charID);
    }

    public Player(GamePanel game, int charID, int keyLeft, int keyRight) {
        super(game, charID);
        this.keyLeft  = keyLeft;
        this.keyRight = keyRight;
    }

    @Override
    protected int getMaxJumps() { 
        return (charID == 2) ? 3 : 2; // Kira có triple jump, còn lại double jump
    }

    // ─── RESET ───────────────────────────────────────
    public void reset(int startX) {
        x = startX; y = FLOOR_Y - height;
        hp = maxHp; energy = 0;
        vx = 0; vy = 0;
        onGround  = true; jumpsLeft = getMaxJumps();
        state     = CharState.IDLE; stateTimer = 0;
        attackCD  = 0; skill1CD = 0; skill2CD = 0;
        dashTimer = 0; dashCooldown = 0;
        cloneTimer = 0; ultimateTimer = 0; skill1VFXTimer = 0;
        hitFlash = false; isInvincible = false;
        combo = 0; comboTimer = 0;
        comboStep = 0; inputBuffered = false;
        attackPhase = AttackPhase.NONE;
        facing = 1;
        rageMode = false;
    }

    // ─── UPDATE ──────────────────────────────────────
    public void update(Set<Integer> keys) {
        time += 0.06f;
        updateCooldowns();
        updateStateTimer();
        updateInvincibility();
        updateAnimation();
        updateCombo();
        updateAttackPhase();

        if (isDead()) {
            applyPhysics();
            return;
        }

        // Dash timers
        if (dashTimer > 0) {
            dashTimer--;
            if (dashTimer == 0) isInvincible = false;
        }
        if (dashCooldown > 0) dashCooldown--;
        if (ultimateTimer > 0) ultimateTimer--;
        if (cloneTimer > 0) cloneTimer--;
        if (skill1VFXTimer > 0) skill1VFXTimer--;
        if (waveTimer > 0) { waveTimer--; waveActive = waveTimer > 0; }

        boolean canMove = state != CharState.ATTACK && state != CharState.SKILL1
                       && state != CharState.SKILL2  && state != CharState.ULTIMATE
                       && state != CharState.HIT;

        if (state == CharState.ATTACK && attackPhase == AttackPhase.RECOVERY) {
            canMove = true;
        }

        if (canMove) {
            if (keys.contains(keyLeft)) {
                vx = -moveSpeed;
                facing = -1;
                if (onGround && state != CharState.RUN && attackPhase == AttackPhase.NONE)
                    state = CharState.RUN;
            } else if (keys.contains(keyRight)) {
                vx = moveSpeed;
                facing = 1;
                if (onGround && state != CharState.RUN && attackPhase == AttackPhase.NONE)
                    state = CharState.RUN;
            } else {
                if (onGround && state == CharState.RUN) state = CharState.IDLE;
            }
        }

        applyPhysics();
    }

    // ─── ACTIONS ─────────────────────────────────────
    public void doJump() {
        if (jumpsLeft > 0 && !isDead()) {
            vy = JUMP_FORCE;
            onGround = false;
            jumpsLeft--;
            state = CharState.JUMP;
            SoundManager.playJump();
            if (jumpsLeft == 0) {
                game.spawnParticles(getCenterX(), y + height, 12, new Color(0, 255, 204), 4f);
            }
        }
    }

    public void doDash(int dir) {
        if (dashCooldown > 0 || isDead()) return;
        dashTimer     = 10;
        dashCooldown  = 50;
        isInvincible  = true;
        invincTimer   = 10;
        vx = dir * 22f;
        facing = dir;
        SoundManager.playDash();
        game.spawnParticles(getCenterX(), getCenterY(), 12, new Color(255, 215, 0, 180), 3f);
    }
}
