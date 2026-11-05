package com.github.qianwj.mindrobot.link;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;

/**
 * Display mirror of a robot position, stored in the helmet NBT (R27 / D11).
 * <p>
 * The authoritative position lives in {@link RobotRegistry}, written by the robot itself. This value is
 * copied out of the registry (or out of the live entity) when a link opens or ends, purely so the player can
 * read "where the machine last reported itself" in the item tooltip. It is never used to find or wake a robot:
 * a reconnect asks the registry, so a helmet left in a chest or copied to another player cannot steer a wake-up
 * to a stale coordinate.
 * <p>
 * This is deliberately separate from {@link ChannelData}: the channel is the pairing key and rarely changes,
 * while this position is refreshed on every link.
 */
public final class LastKnownPosition {

    private static final String NBT_X = "lastX";
    private static final String NBT_Y = "lastY";
    private static final String NBT_Z = "lastZ";
    private static final String NBT_DIMENSION = "lastDim";

    private final double x;
    private final double y;
    private final double z;
    private final int dimension;

    public LastKnownPosition(double x, double y, double z, int dimension) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
    }

    /**
     * Reads a stored position.
     *
     * @return the position, or {@code null} when the tag carries none (never recorded)
     */
    public static LastKnownPosition readFromNBT(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey(NBT_X)) {
            return null;
        }
        return new LastKnownPosition(
            tag.getDouble(NBT_X),
            tag.getDouble(NBT_Y),
            tag.getDouble(NBT_Z),
            tag.getInteger(NBT_DIMENSION));
    }

    public void writeToNBT(NBTTagCompound tag) {
        if (tag == null) {
            return;
        }
        tag.setDouble(NBT_X, x);
        tag.setDouble(NBT_Y, y);
        tag.setDouble(NBT_Z, z);
        tag.setInteger(NBT_DIMENSION, dimension);
    }

    public static void removeFromNBT(NBTTagCompound tag) {
        if (tag == null) {
            return;
        }
        tag.removeTag(NBT_X);
        tag.removeTag(NBT_Y);
        tag.removeTag(NBT_Z);
        tag.removeTag(NBT_DIMENSION);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public int getDimension() {
        return dimension;
    }

    public int getBlockX() {
        return MathHelper.floor_double(x);
    }

    public int getBlockY() {
        return MathHelper.floor_double(y);
    }

    public int getBlockZ() {
        return MathHelper.floor_double(z);
    }

    public int getChunkX() {
        return MathHelper.floor_double(x) >> 4;
    }

    public int getChunkZ() {
        return MathHelper.floor_double(z) >> 4;
    }

    @Override
    public String toString() {
        return "LastKnownPosition{" + getBlockX() + ", " + getBlockY() + ", " + getBlockZ() + " dim=" + dimension + '}';
    }
}
