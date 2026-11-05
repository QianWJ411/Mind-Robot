package com.github.qianwj.mindrobot.registry;

import net.minecraft.item.Item;

import com.github.qianwj.mindrobot.MindRobot;
import com.github.qianwj.mindrobot.entity.EntityRobot;
import com.github.qianwj.mindrobot.item.ItemRemoteHelmet;
import com.github.qianwj.mindrobot.network.ModNetwork;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Common registration entry: items, the robot entity and the network channel.
 */
public final class ModContent {

    public static final String REMOTE_HELMET_NAME = "remote_helmet";

    private static final String ROBOT_ENTITY_NAME = "robot";
    private static final int ROBOT_ENTITY_ID = 0;
    private static final int ROBOT_TRACKING_RANGE = 160;
    private static final int ROBOT_UPDATE_FREQUENCY = 3;
    private static final boolean ROBOT_SENDS_VELOCITY = true;

    /** The remote helmet item, registered during preInit. */
    public static Item remoteHelmet;

    private ModContent() {}

    public static void preInit() {
        remoteHelmet = new ItemRemoteHelmet();
        GameRegistry.registerItem(remoteHelmet, REMOTE_HELMET_NAME);

        EntityRegistry.registerModEntity(
            EntityRobot.class,
            ROBOT_ENTITY_NAME,
            ROBOT_ENTITY_ID,
            activeModInstance(),
            ROBOT_TRACKING_RANGE,
            ROBOT_UPDATE_FREQUENCY,
            ROBOT_SENDS_VELOCITY);

        ModNetwork.init();
    }

    /** The mod instance is needed by {@code EntityRegistry.registerModEntity}; FML knows it while preInit runs. */
    private static Object activeModInstance() {
        ModContainer container = Loader.instance()
            .activeModContainer();
        if (container == null) {
            throw new IllegalStateException("Mind-Robot content registered outside of a mod loading phase");
        }
        Object mod = container.getMod();
        MindRobot.LOG.debug("Registering entity against mod container {}", container.getModId());
        return mod;
    }
}
