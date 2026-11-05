package com.github.qianwj.mindrobot.link;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;

/**
 * Where a robot was last seen when a link ended (R27).
 * <p>
 * The value is written into both sides of the pairing on every disconnect: the helmet stack the driver wore
 * and the robot entity itself. The helmet copy is what lets a player wake the machine again after a logout or
 * a restart, and the robot copy survives the helmet being lost or replaced.
 * <p>
 * This is deliberately separate from {@link ChannelData}: the channel is the pairing key and rarely changes,
 * while this position is rewritten on every disconnect.
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
