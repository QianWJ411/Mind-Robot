package com.github.qianwj.mindrobot.link;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;

/**
 * Why a connection request was refused (D5 validation order, revised by R22 / R28).
 * <p>
 * A refusal must never be silent: the server turns the reason into a localized chat message so the player
 * learns whether the helmet has no usable channel, no robot answers that public number, no private channel
 * carries that name, the private password does not match, or the robot is already taken.
 */
public enum LinkDenial {

    /** The player is not wearing a remote helmet at all. */
    HELMET_MISSING("chat.mindrobot.denial.helmet_missing"),
    /** D5 step 1: the helmet carries no channel chip, so there is nothing to pair with. */
    HELMET_UNPAIRED("chat.mindrobot.denial.helmet_unpaired"),
    /** D5 step 1: the chip is installed but its name / password fields are incomplete (R26). */
    HELMET_UNCONFIGURED("chat.mindrobot.denial.helmet_unconfigured"),
    /** R28: no loaded robot carries the public channel number in this dimension. A chunk is never woken. */
    NO_ROBOT("chat.mindrobot.denial.no_robot"),
    /** R28: the registry has no private channel under this name in this dimension. */
    NO_ROBOT_PRIVATE("chat.mindrobot.denial.no_robot_private"),
    /** The private channel name exists, but the password does not match (stored and compared in plain text). */
    PRIVATE_PASSWORD("chat.mindrobot.denial.private_password"),
    /** Defensive re-check: the robot found for the helmet no longer answers on that channel. */
    CHANNEL_MISMATCH("chat.mindrobot.denial.channel_mismatch"),
    /** The public robot is already driven by somebody else (R13/R25). */
    ROBOT_OCCUPIED_PUBLIC("chat.mindrobot.denial.occupied_public"),
    /** The private robot is already driven by somebody else (R13/R25). */
    ROBOT_OCCUPIED_PRIVATE("chat.mindrobot.denial.occupied_private"),
    /** The player is already driving something. */
    ALREADY_DRIVING("chat.mindrobot.denial.already_driving"),
    /** R14b / D17: the body is missing a part, so it is not a finished robot yet. */
    ROBOT_INCOMPLETE("chat.mindrobot.denial.robot_incomplete"),
    /** R40: the battery box is empty, so the body cannot spend the energy a drive costs. */
    ROBOT_UNPOWERED("chat.mindrobot.denial.robot_unpowered");

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

    /** Sends the occupancy refusal with the label the mode actually has: a number or a private name. */
    public static void sendOccupied(EntityPlayer player, ChannelData channel) {
        if (channel == null) {
            return;
        }
        if (channel.isPrivate()) {
            ROBOT_OCCUPIED_PRIVATE.send(player, channel.getName());
        } else {
            ROBOT_OCCUPIED_PUBLIC.send(player, channel.getChannel());
        }
    }
}
