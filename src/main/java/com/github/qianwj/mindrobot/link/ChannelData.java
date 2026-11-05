package com.github.qianwj.mindrobot.link;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

/**
 * The pairing payload of D5: a channel number plus who may use it.
 * <p>
 * The same three values are copied between the frequency chip, the helmet and the robot entity, so all three
 * store an identical snapshot:
 * <ul>
 * <li>{@code channel} - 0..{@link #MAX_CHANNEL}, the actual match key (R21);</li>
 * <li>{@code mode} - {@link ChannelMode#PRIVATE} or {@link ChannelMode#PUBLIC} (R22);</li>
 * <li>{@code owner} - the UUID of the player that configured the channel, only meaningful for a private
 * channel.</li>
 * </ul>
 * An instance only exists when a channel has been configured; "not configured" is represented by {@code null}
 * everywhere (chip, helmet, robot), never by a magic channel value.
 */
public final class ChannelData {

    public static final int MIN_CHANNEL = 0;
    public static final int MAX_CHANNEL = 9999;

    private static final String NBT_CHANNEL = "channel";
    private static final String NBT_MODE = "mode";
    private static final String NBT_OWNER = "owner";

    private final int channel;
    private final ChannelMode mode;
    private final UUID owner;

    /**
     * @param channel 0..{@link #MAX_CHANNEL}
     * @param mode    {@link ChannelMode}, {@code null} falls back to {@link ChannelMode#PRIVATE}
     * @param owner   the player that configured the channel, may be {@code null} for legacy data
     * @throws IllegalArgumentException when the channel is outside the allowed range
     */
    public ChannelData(int channel, ChannelMode mode, UUID owner) {
        if (!isValidChannel(channel)) {
            throw new IllegalArgumentException("Channel out of range: " + channel);
        }
        this.channel = channel;
        this.mode = mode == null ? ChannelMode.PRIVATE : mode;
        this.owner = owner;
    }

    public static boolean isValidChannel(int channel) {
        return channel >= MIN_CHANNEL && channel <= MAX_CHANNEL;
    }

    /**
     * Reads the channel snapshot from an NBT tag.
     *
     * @return the snapshot, or {@code null} when the tag carries no (valid) channel
     */
    public static ChannelData readFromNBT(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey(NBT_CHANNEL)) {
            return null;
        }
        int channel = tag.getInteger(NBT_CHANNEL);
        if (!isValidChannel(channel)) {
            return null;
        }
        UUID owner = null;
        if (tag.hasKey(NBT_OWNER)) {
            try {
                owner = UUID.fromString(tag.getString(NBT_OWNER));
            } catch (IllegalArgumentException ignored) {
                // A malformed owner must not break the item; a private channel then simply has no valid owner.
                owner = null;
            }
        }
        return new ChannelData(channel, ChannelMode.byNbtName(tag.getString(NBT_MODE)), owner);
    }

    /** Writes the snapshot into the supplied tag, replacing any stale channel/owner values. */
    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger(NBT_CHANNEL, channel);
        tag.setString(NBT_MODE, mode.getNbtName());
        if (owner != null) {
            tag.setString(NBT_OWNER, owner.toString());
        } else {
            tag.removeTag(NBT_OWNER);
        }
    }

    /** Removes a channel snapshot from the tag, turning the carrier back into an unconfigured item. */
    public static void removeFromNBT(NBTTagCompound tag) {
        if (tag == null) {
            return;
        }
        tag.removeTag(NBT_CHANNEL);
        tag.removeTag(NBT_MODE);
        tag.removeTag(NBT_OWNER);
    }

    public int getChannel() {
        return channel;
    }

    public ChannelMode getMode() {
        return mode;
    }

    public UUID getOwner() {
        return owner;
    }

    public boolean isPrivate() {
        return mode == ChannelMode.PRIVATE;
    }

    /** True when this channel belongs to the supplied player. A missing owner never matches. */
    public boolean isOwnedBy(UUID playerId) {
        return owner != null && owner.equals(playerId);
    }

    /** Channel numbers must be identical, the mode and owner are checked separately (D5 step 2). */
    public boolean matchesChannel(ChannelData other) {
        return other != null && channel == other.channel;
    }

    @Override
    public String toString() {
        return "ChannelData{channel=" + channel + ", mode=" + mode + ", owner=" + owner + '}';
    }
}
