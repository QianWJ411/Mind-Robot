package com.github.qianwj.mindrobot.link;

import net.minecraft.nbt.NBTTagCompound;

/**
 * The pairing payload of R22 / D5: the channel type is decided by the chip, and the payload only carries the
 * fields that type needs.
 * <ul>
 * <li>{@link ChannelMode#PUBLIC} - {@code channel} (0..{@link #MAX_CHANNEL}); any two chips with the same
 * number pair (R21).</li>
 * <li>{@link ChannelMode#PRIVATE} - {@code name} (1..{@link #MAX_NAME_LENGTH} characters, CJK allowed) plus
 * {@code password}; both must match exactly, case included. There is no per-player identity in the model: the
 * name and the password are the whole authorisation (R22).</li>
 * </ul>
 * The password is stored <b>in plain text</b> on purpose. This is a local co-op remote control, not an
 * authentication service: a server operator who has to recover a forgotten password must be able to repair
 * the save by editing {@code <world>/mindrobot/robots.json} (and the chip / helmet NBT for the same
 * values) by hand.
 * Hashes would make that impossible. The trade off is that anyone able to read the world files can read the
 * password, which is accepted for this mod. For the same reason nothing here ever prints the password: see
 * {@link #toString()}.
 * <p>
 * An instance only exists when a channel has been configured; "not configured" is represented by {@code null}
 * everywhere (chip, helmet, robot, registry), never by a magic value. A blank name or a blank password is
 * therefore not a private channel and cannot connect (R26).
 */
public final class ChannelData {

    public static final int MIN_CHANNEL = 0;
    public static final int MAX_CHANNEL = 9999;

    public static final int MIN_NAME_LENGTH = 1;
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_PASSWORD_LENGTH = 64;

    private static final String NBT_CHANNEL = "channel";
    private static final String NBT_MODE = "mode";
    private static final String NBT_NAME = "name";
    private static final String NBT_PASSWORD = "password";

    /** {@link #MIN_CHANNEL}..{@link #MAX_CHANNEL} for a public channel, -1 for a private one. */
    private final int channel;
    private final ChannelMode mode;
    /** Private channel name, {@code null} for a public one. */
    private final String name;
    /** Private channel password in plain text, {@code null} for a public one. Never shown in a GUI or tooltip. */
    private final String password;

    private ChannelData(ChannelMode mode, int channel, String name, String password) {
        this.mode = mode;
        this.channel = channel;
        this.name = name;
        this.password = password;
    }

    public static boolean isValidChannel(int channel) {
        return channel >= MIN_CHANNEL && channel <= MAX_CHANNEL;
    }

    public static boolean isValidName(String name) {
        if (name == null) {
            return false;
        }
        int length = name.length();
        return length >= MIN_NAME_LENGTH && length <= MAX_NAME_LENGTH;
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= 1 && password.length() <= MAX_PASSWORD_LENGTH;
    }

    /**
     * Builds a public channel.
     *
     * @throws IllegalArgumentException when the number is outside {@link #MIN_CHANNEL}..{@link #MAX_CHANNEL}
     */
    public static ChannelData publicChannel(int channel) {
        if (!isValidChannel(channel)) {
            throw new IllegalArgumentException("Channel out of range: " + channel);
        }
        return new ChannelData(ChannelMode.PUBLIC, channel, null, null);
    }

    /**
     * Builds a private channel. The name is trimmed, the password is kept exactly as typed (it is compared
     * verbatim).
     *
     * @return the channel, or {@code null} when the name or the password is empty / too long, which D5 defines
     *         as "not configured" (R26)
     */
    public static ChannelData privateChannel(String name, String password) {
        String trimmedName = name == null ? "" : name.trim();
        if (!isValidName(trimmedName) || !isValidPassword(password)) {
            return null;
        }
        // The submitted password is used as the stored value: no trimming, no case folding, plain text.
        return new ChannelData(ChannelMode.PRIVATE, -1, trimmedName, password);
    }

    /**
     * Reads the channel snapshot from an NBT tag.
     * <p>
     * Only the current format is accepted: an explicit {@code mode} decides which fields are required, and a
     * tag that does not carry a complete snapshot is rejected. There is deliberately no migration path for
     * the replaced {@code channel}-only format: this mod has never been used in a real world, so there is
     * nothing to preserve, and a half readable snapshot would be a connection bug waiting to happen.
     *
     * @return the snapshot, or {@code null} when the tag carries no complete channel
     */
    public static ChannelData readFromNBT(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey(NBT_MODE)) {
            return null;
        }
        String modeName = tag.getString(NBT_MODE);
        ChannelData data;
        if (ChannelMode.PUBLIC.getNbtName()
            .equalsIgnoreCase(modeName)) {
            if (!tag.hasKey(NBT_CHANNEL)) {
                return null;
            }
            data = new ChannelData(ChannelMode.PUBLIC, tag.getInteger(NBT_CHANNEL), null, null);
        } else if (ChannelMode.PRIVATE.getNbtName()
            .equalsIgnoreCase(modeName)) {
                data = new ChannelData(ChannelMode.PRIVATE, -1, tag.getString(NBT_NAME), tag.getString(NBT_PASSWORD));
            } else {
                return null;
            }
        return data.isConfigured() ? data : null;
    }

    /** Writes the snapshot into the supplied tag, replacing any values of the other mode. */
    public void writeToNBT(NBTTagCompound tag) {
        tag.setString(NBT_MODE, mode.getNbtName());
        if (mode == ChannelMode.PUBLIC) {
            tag.setInteger(NBT_CHANNEL, channel);
            tag.removeTag(NBT_NAME);
            tag.removeTag(NBT_PASSWORD);
        } else {
            tag.setString(NBT_NAME, name == null ? "" : name);
            tag.setString(NBT_PASSWORD, password == null ? "" : password);
            tag.removeTag(NBT_CHANNEL);
        }
    }

    /** Removes a channel snapshot from the tag, turning the carrier back into an unconfigured item. */
    public static void removeFromNBT(NBTTagCompound tag) {
        if (tag == null) {
            return;
        }
        tag.removeTag(NBT_CHANNEL);
        tag.removeTag(NBT_MODE);
        tag.removeTag(NBT_NAME);
        tag.removeTag(NBT_PASSWORD);
    }

    /** The channel number, or -1 for a private channel. */
    public int getChannel() {
        return channel;
    }

    public ChannelMode getMode() {
        return mode;
    }

    /** The private channel name, or {@code null} for a public channel. */
    public String getName() {
        return name;
    }

    /**
     * The private channel password in plain text, or {@code null} for a public channel. Only the matching
     * code and the save file editor should ever call this; the GUI and the tooltips show the name only.
     */
    public String getPassword() {
        return password;
    }

    public boolean isPublic() {
        return mode == ChannelMode.PUBLIC;
    }

    public boolean isPrivate() {
        return mode == ChannelMode.PRIVATE;
    }

    /** True when every field the current mode needs is present and in range (R26). */
    public boolean isConfigured() {
        if (mode == ChannelMode.PUBLIC) {
            return isValidChannel(channel);
        }
        return isValidName(name) && isValidPassword(password);
    }

    /**
     * D5 matching: public channels pair by number, private channels pair by name and password. A private
     * snapshot never matches a public one, and an incomplete snapshot never matches anything.
     */
    public boolean matches(ChannelData other) {
        if (other == null || mode != other.mode || !isConfigured() || !other.isConfigured()) {
            return false;
        }
        if (mode == ChannelMode.PUBLIC) {
            return channel == other.channel;
        }
        return matchesPrivate(other.name, other.password);
    }

    /** Plain text credential check, used by the registry lookup; the same rule as {@link #matches}. */
    boolean matchesPrivate(String name, String password) {
        return mode == ChannelMode.PRIVATE && isConfigured()
            && this.name.equals(name)
            && this.password.equals(password);
    }

    /** Renders the snapshot for logs; the password is never included. */
    @Override
    public String toString() {
        if (mode == ChannelMode.PUBLIC) {
            return "ChannelData{public, channel=" + channel + '}';
        }
        return "ChannelData{private, name='" + name + "', password=<hidden>}";
    }
}
