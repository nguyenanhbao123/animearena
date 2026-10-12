package com.animeclash;

import java.awt.Color;

/**
 * EnemyAI — Controller cho AI (FSM-driven)
 * Có thể điều khiển bất kỳ charID nào.
 */
public class EnemyAI extends CharacterBase {

    public enum AIState {
        IDLE, CHASE, ATTACK, USE_SKILL1, USE_SKILL2, DODGE, ULTIMATE
    }
    private AIState aiState = AIState.IDLE;

    private int consecutiveHits = 0;
    private int hitResetTimer   = 0;

    private int aiSkill1CD = 0;
    private int aiSkill2CD = 0;
    private int aiAttackCD = 0;

    private Player targetPlayer;
    private int difficulty = 1; // 0 = Dễ, 1 = Vừa, 2 = Khó

    public EnemyAI(GamePanel game, int charID) {
        super(game, charID);
        facing = -1;
    }

    public void setDifficulty(int diff) {
        this.difficulty = diff;
        applyDifficultyStats();
    }

    private void applyDifficultyStats() {
        if (difficulty == 0) { // Dễ (Easy)
            this.maxHp *= 0.85f;
            this.hp = this.maxHp;
            this.attackDmg = (int)(this.attackDmg * 0.8f);
            this.moveSpeed *= 0.75f;
        } else if (difficulty == 2) { // Khó (Hard)
            this.maxHp *= 1.15f;
            this.hp = this.maxHp;
            this.attackDmg = (int)(this.attackDmg * 1.25f);
            this.moveSpeed *= 1.25f;
        }
    }

    public void setTarget(Player p) {
        this.targetPlayer = p;
    }

    public void reset(int startX) {
        x = startX; y = FLOOR_Y - height;
        hp = maxHp; energy = 0;
        vx = 0; vy = 0;
        onGround  = true; jumpsLeft = 1;
        state     = CharState.IDLE; stateTimer = 0;
        attackCD  = 0; skill1CD = 0; skill2CD = 0;
        aiSkill1CD = 0; aiSkill2CD = 0; aiAttackCD = 0;
        cloneTimer = 0; ultimateTimer = 0; skill1VFXTimer = 0;
        hitFlash = false; isInvincible = false;
        combo = 0; comboTimer = 0;
        comboStep = 0; inputBuffered = false;
        attackPhase = AttackPhase.NONE;
        facing = -1;
        rageMode = false;
        consecutiveHits = 0;
    }

    @Override
    public void takeDamage(CharacterBase attacker, int damage, String type) {
        super.takeDamage(attacker, damage, type);
        consecutiveHits++;
        hitResetTimer = 120;
    }

    public void update(Player player) {
        this.targetPlayer = player;
        time += 0.06f;

        updateCooldowns();
        updateStateTimer();
        updateInvincibility();
        updateAnimation();
        updateCombo();
        updateAttackPhase();

        checkActiveHit();

        if (isDead()) {
            applyPhysics();
            return;
        }

        if (ultimateTimer > 0) ultimateTimer--;
        if (cloneTimer > 0) cloneTimer--;
        if (skill1VFXTimer > 0) skill1VFXTimer--;
        if (waveTimer > 0) { waveTimer--; waveActive = waveTimer > 0; }

        if (aiAttackCD > 0) aiAttackCD--;
        if (aiSkill1CD > 0) aiSkill1CD--;
        if (aiSkill2CD > 0) aiSkill2CD--;

        if (hitResetTimer > 0) {
            hitResetTimer--;
            if (hitResetTimer == 0) consecutiveHits = 0;
        }

        // Không AI logic nếu đang bận
        if (state == CharState.ATTACK || state == CharState.SKILL1
         || state == CharState.SKILL2 || state == CharState.ULTIMATE
         || state == CharState.HIT) {
            applyPhysics();
            return;
        }

        float dx = player.getCenterX() - getCenterX();
        facing = dx > 0 ? 1 : -1;
        float dist = Math.abs(dx);

        determineAIState(dist);
        executeAIState(dist, dx);

        applyPhysics();
    }

    private void determineAIState(float dist) {
        if (difficulty == 0) {
            // EASY: Phản xạ chậm, ít dùng chiêu, ít né
            if (isUltimateReady() && ultimateTimer == 0 && Math.random() < 0.5) {
                aiState = AIState.ULTIMATE;
            } else if (consecutiveHits >= 4 && Math.random() < 0.3) {
                aiState = AIState.DODGE;
            } else if (dist <= 160 && aiAttackCD <= 0 && Math.random() < 0.4) {
                aiState = AIState.ATTACK;
            } else if (dist > 160 && dist <= 400 && aiSkill1CD <= 0 && Math.random() < 0.2) {
                aiState = AIState.USE_SKILL1;
            } else if (aiSkill2CD <= 0 && dist > 180 && Math.random() < 0.2) {
                aiState = AIState.USE_SKILL2;
            } else if (dist > 160) {
                aiState = AIState.CHASE;
            } else {
                aiState = AIState.IDLE;
            }
        } else if (difficulty == 1) {
            // MEDIUM: Bình thường
            if (isUltimateReady() && ultimateTimer == 0) {
                aiState = AIState.ULTIMATE;
            } else if (consecutiveHits >= 3) {
                aiState = AIState.DODGE;
            } else if (dist <= 160 && aiAttackCD <= 0) {
                aiState = AIState.ATTACK;
            } else if (dist > 160 && dist <= 420 && aiSkill1CD <= 0) {
                aiState = AIState.USE_SKILL1;
            } else if (aiSkill2CD <= 0 && dist > 160) {
                aiState = AIState.USE_SKILL2;
            } else if (dist > 140) {
                aiState = AIState.CHASE;
            } else {
                aiState = AIState.IDLE;
            }
        } else {
            // HARD: Cực thông minh, né nhanh, bay nhảy tiếp cận, combo liên tục
            if (isUltimateReady() && ultimateTimer == 0) {
                aiState = AIState.ULTIMATE;
            } else if (consecutiveHits >= 2) { // Bị đánh 2 phát là lập tức né & lướt phản công
                aiState = AIState.DODGE;
            } else if (dist <= 170 && aiAttackCD <= 0) {
                aiState = AIState.ATTACK;
            } else if (dist > 170 && dist <= 450 && aiSkill1CD <= 0) {
                aiState = AIState.USE_SKILL1;
            } else if (aiSkill2CD <= 0 && dist > 150) {
                aiState = AIState.USE_SKILL2;
            } else if (dist > 120) {
                aiState = AIState.CHASE;
                // Thỉnh thoảng nhảy khi chase để tiếp cận nhanh / né chiêu địch
                if (Math.random() < 0.05 && onGround) {
                    vy = -12f;
                    onGround = false;
                }
            } else {
                aiState = AIState.IDLE;
            }
        }
    }

    private void executeAIState(float dist, float dx) {
        switch (aiState) {
            case IDLE -> vx *= 0.7f;
            case CHASE -> {
                vx = Math.signum(dx) * moveSpeed;
                if (difficulty == 2 && dist > 300 && Math.random() < 0.1 && onGround) {
                    vx = Math.signum(dx) * moveSpeed * 2.5f;
                }
            }
            case ATTACK -> {
                vx *= 0.5f;
                if (aiAttackCD <= 0) {
                    doAttack(targetPlayer);
                    int cd = rageMode ? 20 : 40;
                    if (difficulty == 0) cd = 60;
                    if (difficulty == 2) cd = 25;
                    aiAttackCD = cd;
                }
            }
            case USE_SKILL1 -> {
                vx *= 0.5f;
                if (aiSkill1CD <= 0) {
                    doSkill1(targetPlayer);
                    aiSkill1CD = difficulty == 0 ? 400 : (difficulty == 2 ? 200 : 300);
                }
            }
            case USE_SKILL2 -> {
                if (aiSkill2CD <= 0) {
                    doSkill2(targetPlayer);
                    aiSkill2CD = difficulty == 0 ? 500 : (difficulty == 2 ? 250 : 400);
                }
            }
            case DODGE -> {
                vx = -Math.signum(dx) * (difficulty == 2 ? 18f : 15f);
                vy = -8f;
                onGround = false;
                isInvincible = true; invincTimer = difficulty == 2 ? 40 : 30;
                consecutiveHits = 0;
                aiState = AIState.IDLE;
                game.spawnParticles(getCenterX(), getCenterY(), 15, new Color(123, 47, 190), 4f);
            }
            case ULTIMATE -> {
                vx *= 0.2f;
                doUltimate(targetPlayer);
            }
        }
    }
}
