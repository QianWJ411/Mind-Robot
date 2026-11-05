package com.github.qianwj.mindrobot.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import com.github.qianwj.mindrobot.MindRobot;
import com.github.qianwj.mindrobot.link.RobotLink;
import com.github.qianwj.mindrobot.link.RobotLinkManager;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * Network setup and the server side state push.
 * <p>
 * Three messages are enough for P0.5: a client to server connection intent
 * ({@link LinkRequestMessage}), a server to client snapshot ({@link LinkStateMessage}) and the frequency chip
 * configuration request ({@link ChipConfigMessage}).
 */
public final class ModNetwork {

    private static final int DISCRIMINATOR_REQUEST = 0;
    private static final int DISCRIMINATOR_STATE = 1;
    private static final int DISCRIMINATOR_CHIP_CONFIG = 2;

    private static SimpleNetworkWrapper channel;

    /** Last snapshot sent per player, so the server only pushes when the visible state changes. */
    private static final Map<UUID, LinkStateMessage> LAST_SENT = new HashMap<UUID, LinkStateMessage>();

    private ModNetwork() {}

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(MindRobot.MODID);
        channel.registerMessage(LinkRequestHandler.class, LinkRequestMessage.class, DISCRIMINATOR_REQUEST, Side.SERVER);
        channel.registerMessage(LinkStateHandler.class, LinkStateMessage.class, DISCRIMINATOR_STATE, Side.CLIENT);
        channel
            .registerMessage(ChipConfigHandler.class, ChipConfigMessage.class, DISCRIMINATOR_CHIP_CONFIG, Side.SERVER);
    }

    public static void sendToServer(IMessage message) {
        if (channel != null) {
            channel.sendToServer(message);
        }
    }

    /** Pushes the current connection snapshot to one player, only when it actually changed. */
    public static void syncLinkState(EntityPlayer player) {
        if (channel == null || !(player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP playerMp = (EntityPlayerMP) player;
        RobotLink link = RobotLinkManager.getLink(playerMp.getUniqueID());
        int robotEntityId = link == null ? -1 : RobotLinkManager.getRobotEntityId(playerMp);
        LinkStateMessage snapshot = new LinkStateMessage(
            link != null,
            robotEntityId,
            MenuOption.toIds(RobotLinkManager.menuOptionsFor(playerMp)));

        if (snapshot.matches(LAST_SENT.get(playerMp.getUniqueID()))) {
            return;
        }
        LAST_SENT.put(playerMp.getUniqueID(), snapshot);
        try {
            channel.sendTo(snapshot, playerMp);
        } catch (RuntimeException e) {
            // The connection may already be closing (logout / server stop); a failed push is harmless.
            MindRobot.LOG.warn("Failed to send robot link state to {}", playerMp.getCommandSenderName(), e);
        }
    }

    /** Drops the cached snapshot of a player that left the game. */
    public static void forgetPlayer(UUID playerId) {
        LAST_SENT.remove(playerId);
    }

    /** Drops every cached snapshot, used when the worlds unload so a rejoin always gets a fresh state. */
    public static void forgetAll() {
        LAST_SENT.clear();
    }
}
