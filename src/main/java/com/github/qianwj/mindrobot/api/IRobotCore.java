package com.github.qianwj.mindrobot.api;

import net.minecraft.util.DamageSource;

/**
 * Public surface of a Mind-Robot chassis for other mods.
 * <p>
 * The entity is an {@code EntityLivingBase} only because the client camera needs that type; its vanilla
 * health is a disguise and never represents durability. Use this interface to read and damage the robot.
 */
public interface IRobotCore {

    /** Remaining durability of the chassis. */
    int getStructuralIntegrity();

    /** Durability cap of the chassis. */
    int getMaxStructuralIntegrity();

    /**
     * Applies damage through the robot damage pipeline (classification included).
     *
     * @return the remaining structural integrity after the hit
     */
    int applyStructuralDamage(DamageSource source, float amount);

    /** Marker so callers can recognise a chassis without depending on the entity class. */
    boolean isRobot();
}
