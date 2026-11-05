package com.github.qianwj.mindrobot.link;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

/**
 * One active player to robot connection.
 * <p>
 * The anchor is the spot the player body entered from (R5); the body is pinned there for the whole
 * connection so piston pushes, water flow and explosions cannot move it (design decision D3).
 * <p>
 * The last known robot position is tracked as well: when the robot entity is missing from the loaded entity
 * list because its chunk was unloaded, that position is what pulls the chunk back (R24) instead of treating
 * the disappearance as a destroyed robot.
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

    private double robotX;
    private double robotY;
    private double robotZ;

    RobotLink(EntityPlayer player, UUID robotId, double robotX, double robotY, double robotZ) {
        this.playerId = player.getUniqueID();
        this.robotId = robotId;
        this.dimensionId = player.dimension;
        this.anchorX = player.posX;
        this.anchorY = player.posY;
        this.anchorZ = player.posZ;
        this.anchorYaw = player.rotationYaw;
        this.anchorPitch = player.rotationPitch;
        updateRobotPosition(robotX, robotY, robotZ);
    }

    /** Records where the robot was last seen, so an unloaded chunk can be pulled back at that spot. */
    void updateRobotPosition(double x, double y, double z) {
        this.robotX = x;
        this.robotY = y;
        this.robotZ = z;
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

    public int getRobotChunkX() {
        return MathHelper.floor_double(robotX) >> 4;
    }

    public int getRobotChunkZ() {
        return MathHelper.floor_double(robotZ) >> 4;
    }

    public double getRobotX() {
        return robotX;
    }

    public double getRobotY() {
        return robotY;
    }

    public double getRobotZ() {
        return robotZ;
    }
}
