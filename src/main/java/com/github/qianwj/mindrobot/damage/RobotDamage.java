package com.github.qianwj.mindrobot.damage;

import net.minecraft.util.DamageSource;

/**
 * Damage classification for robot chassis.
 * <p>
 * 1.7.10 only ships the damage sources used below; there is no {@code cramming} constant, suffocation in
 * blocks is {@link DamageSource#inWall}.
 */
public final class RobotDamage {

    /** Water suffocation: a sealed chassis does not drown. */
    private static final float DROWN_MULTIPLIER = 0.0F;
    /** Falls: no flesh, no fall damage. */
    private static final float FALL_MULTIPLIER = 0.0F;
    /** Being crushed inside blocks is the worst case for a chassis. */
    private static final float IN_WALL_MULTIPLIER = 4.0F;
    /** Leaving the world destroys the chassis outright. */
    private static final float DESTRUCTIVE = Float.POSITIVE_INFINITY;
    private static final float DEFAULT_MULTIPLIER = 1.0F;

    private RobotDamage() {}

    /**
     * @return the damage actually applied to structural integrity, {@code 0} for ignored sources and
     *         {@link Float#POSITIVE_INFINITY} for sources that destroy the chassis outright
     */
    public static float apply(DamageSource source, float amount) {
        if (source == null || amount <= 0.0F) {
            return 0.0F;
        }
        if (source == DamageSource.outOfWorld) {
            return DESTRUCTIVE;
        }
        if (source == DamageSource.drown) {
            return amount * DROWN_MULTIPLIER;
        }
        if (source == DamageSource.fall) {
            return amount * FALL_MULTIPLIER;
        }
        if (source == DamageSource.inWall) {
            return amount * IN_WALL_MULTIPLIER;
        }
        return amount * DEFAULT_MULTIPLIER;
    }

    /** True when the source bypasses durability and destroys the chassis immediately. */
    public static boolean isDestructive(DamageSource source) {
        return source == DamageSource.outOfWorld;
    }
}
