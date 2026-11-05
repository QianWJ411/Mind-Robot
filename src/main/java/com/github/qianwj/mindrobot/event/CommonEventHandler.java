package com.github.qianwj.mindrobot.event;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.world.WorldEvent;

import com.github.qianwj.mindrobot.link.DisconnectReason;
import com.github.qianwj.mindrobot.link.RobotLinkManager;
import com.github.qianwj.mindrobot.link.RobotRegistry;
import com.github.qianwj.mindrobot.network.ChipConfigHandler;
import com.github.qianwj.mindrobot.network.ModNetwork;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Server side glue: drives the link table, and guarantees that no connection survives a logout, a dimension
 * change, a respawn or a world shutdown (D4).
 */
public class CommonEventHandler {

    /** D11: a dirty registry is written at most this often, so the robot self report does not touch the disk. */
    private static final int REGISTRY_FLUSH_INTERVAL = 200;

    private int registryFlushCounter;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            RobotLinkManager.processPendingRequests();
            ChipConfigHandler.processPendingRequests();
            flushDirtyRegistries();
        }
    }

    /** Diluted registry write: at most once every {@link #REGISTRY_FLUSH_INTERVAL} server ticks (D11). */
    private void flushDirtyRegistries() {
        if (++registryFlushCounter < REGISTRY_FLUSH_INTERVAL) {
            return;
        }
        registryFlushCounter = 0;
        RobotRegistry.flushAll();
    }

    /**
     * World save (autosave and "save and quit") forces every pending registry row to disk, so a world save
     * always contains the current robot positions and channel snapshots (D11).
     */
    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save event) {
        if (event.world == null || event.world.isRemote) {
            return;
        }
        RobotRegistry registry = RobotRegistry.get(event.world);
        if (registry != null) {
            registry.flush();
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
     * {@code @Mod.EventHandler} methods only, so the server stopping flush lives in
     * {@code MindRobot#serverStopping(FMLServerStoppingEvent)}; this handler is the bus based last chance
     * before a world disappears.
     */
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world == null || event.world.isRemote) {
            return;
        }
        RobotLinkManager.disconnectAll(DisconnectReason.SERVER_STOP);
        // A disconnect re-reports the still loaded robots, so the final registry write must come after it (D11).
        RobotRegistry registry = RobotRegistry.get(event.world);
        if (registry != null) {
            registry.flush();
        }
        ModNetwork.forgetAll();
    }
}
