package com.github.qianwj.mindrobot.event;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.world.WorldEvent;

import com.github.qianwj.mindrobot.link.DisconnectReason;
import com.github.qianwj.mindrobot.link.RobotLinkManager;
import com.github.qianwj.mindrobot.network.ModNetwork;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Server side glue: drives the link table, and guarantees that no connection survives a logout, a dimension
 * change, a respawn or a world shutdown (D4).
 */
public class CommonEventHandler {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            RobotLinkManager.processPendingRequests();
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        RobotLinkManager.tickPlayer(player);
        ModNetwork.syncLinkState(player);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        RobotLinkManager.disconnect(event.player, DisconnectReason.PLAYER_LOGOUT);
        ModNetwork.forgetPlayer(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        RobotLinkManager.disconnect(event.player, DisconnectReason.DIMENSION_CHANGE);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // The EntityPlayerMP instance is replaced on respawn, a link must not outlive the old body.
        RobotLinkManager.disconnect(event.player, DisconnectReason.PLAYER_DEATH);
    }

    /**
     * Server shutdown and "save and quit" both unload the worlds through {@code MinecraftServer#stopServer},
     * which posts {@link WorldEvent.Unload} for every world. FML lifecycle events are delivered to
     * {@code @Mod.EventHandler} methods only, so this is the bus based equivalent of a server stopping hook.
     */
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world == null || event.world.isRemote) {
            return;
        }
        RobotLinkManager.disconnectAll(DisconnectReason.SERVER_STOP);
        ModNetwork.forgetAll();
    }
}
