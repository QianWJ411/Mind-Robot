package com.github.qianwj.mindrobot.link;

/**
 * Channel type decided by the frequency chip (R22).
 * <p>
 * {@link #PUBLIC} channels are identified by a number: any chip with the same number pairs, and the first
 * driver wins (R13/R25). {@link #PRIVATE} channels are identified by a custom name plus a password: both must
 * match, and no player identity is stored at all. The value is persisted by name so a reordered enum never
 * changes the meaning of existing NBT.
 */
public enum ChannelMode {

    /** A name plus a password identify the channel; only a matching pair may connect. */
    PRIVATE("private"),
    /** A number identifies the channel; anyone with the same number may connect (one driver at a time, R13). */
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
     *         choice: an unreadable mode never widens access). Callers that must reject corrupt data compare
     *         the returned {@link #getNbtName()} with the input instead.
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
