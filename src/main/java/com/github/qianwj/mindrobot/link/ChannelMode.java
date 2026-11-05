package com.github.qianwj.mindrobot.link;

/**
 * Visibility of a frequency channel (R22).
 * <p>
 * {@link #PRIVATE} channels only accept the player that configured the channel, {@link #PUBLIC} channels
 * accept anyone holding a helmet with the same number. The value is persisted by name so a reordered enum
 * never changes the meaning of existing NBT.
 */
public enum ChannelMode {

    /** Only the owner recorded in the channel data may connect. */
    PRIVATE("private"),
    /** Anyone with the same channel number may connect (one driver at a time, R13). */
    PUBLIC("public");

    private final String nbtName;

    ChannelMode(String nbtName) {
        this.nbtName = nbtName;
    }

    public String getNbtName() {
        return nbtName;
    }

    /**
     * Reads a mode written by {@link #getNbtName()}.
     *
     * @return the matching mode, or {@link #PRIVATE} when the tag is missing or unknown (the conservative
     *         choice: an unreadable mode never widens access)
     */
    public static ChannelMode byNbtName(String nbtName) {
        if (nbtName != null) {
            for (ChannelMode mode : values()) {
                if (mode.nbtName.equalsIgnoreCase(nbtName)) {
                    return mode;
                }
            }
        }
        return PRIVATE;
    }

    /**
     * Reads a mode from its network ordinal.
     *
     * @return the matching mode, or {@link #PRIVATE} for out of range values
     */
    public static ChannelMode byId(int id) {
        ChannelMode[] modes = values();
        return id >= 0 && id < modes.length ? modes[id] : PRIVATE;
    }
}
