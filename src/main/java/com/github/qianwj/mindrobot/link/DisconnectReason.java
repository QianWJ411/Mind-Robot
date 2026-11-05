package com.github.qianwj.mindrobot.link;

/**
 * Every way a player/robot link can end. The reason is only used for logging today, but it keeps the
 * single disconnect entry point self-documenting and gives later stages a place to hook feedback.
 */
public enum DisconnectReason {

    /** The player picked the exit option in the radial menu. */
    PLAYER_REQUEST,
    /** The player body died (R7), including starvation. */
    PLAYER_DEATH,
    /** The robot entity was destroyed (R8); the player body survives. */
    ROBOT_DESTROYED,
    /** The helmet vanished from the head slot while driving (R10 abnormal path). */
    HELMET_REMOVED,
    /** The player left the server. */
    PLAYER_LOGOUT,
    /** The player changed dimension. */
    DIMENSION_CHANGE,
    /** The integrated or dedicated server is shutting down. */
    SERVER_STOP
}
