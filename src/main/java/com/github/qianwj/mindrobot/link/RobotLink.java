package com.github.qianwj.mindrobot.link;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;

/**
 * One active player to robot connection.
 * <p>
 * The anchor is the spot the player body entered from (R5); the body is pinned there for the whole
 * connection so piston pushes, water flow and explosions cannot move it (design decision D3).
 */
public final class RobotLink {

    private final UUID playerId;
    private final UUID robotId;
    private final int dimensionId;
    private final double anchorX;
    private final double anchorY;
    private final double anchorZ;
    private final float anchorYaw;
    private final float anchorPitch;

    RobotLink(EntityPlayer player, UUID robotId) {
        this.playerId = player.getUniqueID();
        this.robotId = robotId;
        this.dimensionId = player.dimension;
        this.anchorX = player.posX;
        this.anchorY = player.posY;
        this.anchorZ = player.posZ;
        this.anchorYaw = player.rotationYaw;
        this.anchorPitch = player.rotationPitch;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getRobotId() {
        return robotId;
    }

    public int getDimensionId() {
        return dimensionId;
    }

    public double getAnchorX() {
        return anchorX;
    }

    public double getAnchorY() {
        return anchorY;
    }

    public double getAnchorZ() {
        return anchorZ;
    }

    public float getAnchorYaw() {
        return anchorYaw;
    }

    public float getAnchorPitch() {
        return anchorPitch;
    }
}
