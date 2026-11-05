package com.github.qianwj.mindrobot.link;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;

/**
 * Why a connection request was refused (D5 validation order).
 * <p>
 * A refusal must never be silent: the server turns the reason into a localized chat message so the player
 * learns whether the helmet is unpaired, the robot is on another channel, the channel is private to someone
 * else or the robot is already taken. The same enum keeps the server side single-entry validation readable.
 */
public enum LinkDenial {

    /** The player is not wearing a remote helmet at all. */
    HELMET_MISSING("chat.mindrobot.denial.helmet_missing"),
    /** D5 step 1: the helmet has no chip data, so there is nothing to pair with. */
    HELMET_UNPAIRED("chat.mindrobot.denial.helmet_unpaired"),
    /** No loaded robot in this world carries the helmet channel. */
    NO_ROBOT("chat.mindrobot.denial.no_robot"),
    /** D5 step 2: helmet channel and robot channel differ. */
    CHANNEL_MISMATCH("chat.mindrobot.denial.channel_mismatch"),
    /** D5 step 3: private robot, but the helmet owner is somebody else. */
    PRIVATE_OWNER("chat.mindrobot.denial.private_owner"),
    /** D5 steps 4/5: the robot already serves another driver. */
    ROBOT_OCCUPIED("chat.mindrobot.denial.occupied"),
    /** The player is already driving something. */
    ALREADY_DRIVING("chat.mindrobot.denial.already_driving");

    private final String translationKey;

    LinkDenial(String translationKey) {
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    /** Sends this reason to the player as a translatable chat line (translated on the client). */
    public void send(EntityPlayer player, Object... arguments) {
        if (player == null) {
            return;
        }
        player.addChatMessage(new ChatComponentTranslation(translationKey, arguments));
    }
}
