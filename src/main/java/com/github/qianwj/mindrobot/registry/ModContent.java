package com.github.qianwj.mindrobot.registry;

import net.minecraft.item.Item;

import com.github.qianwj.mindrobot.MindRobot;
import com.github.qianwj.mindrobot.entity.EntityRobot;
import com.github.qianwj.mindrobot.item.ItemFrequencyChip;
import com.github.qianwj.mindrobot.item.ItemRemoteHelmet;
import com.github.qianwj.mindrobot.item.ItemRobotBackpack;
import com.github.qianwj.mindrobot.item.ItemRobotCamera;
import com.github.qianwj.mindrobot.item.ItemRobotPart;
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
    public static final String FREQUENCY_CHIP_NAME = "frequency_chip";

    private static final String ROBOT_ENTITY_NAME = "robot";
    private static final int ROBOT_ENTITY_ID = 0;
    private static final int ROBOT_TRACKING_RANGE = 160;
    private static final int ROBOT_UPDATE_FREQUENCY = 3;
    private static final boolean ROBOT_SENDS_VELOCITY = true;

    /** The remote helmet item, registered during preInit. */
    public static Item remoteHelmet;
    /** The frequency chip item, registered during preInit (R13, R21). */
    public static Item frequencyChip;
    /** The robot camera item (R33): normal and thermal share one item, distinguished by metadata. */
    public static Item robotCamera;
    /** The backpack part (R14b): unlocks the 54 backpack cells of a body. */
    public static Item robotBackpack;
    /** The assembly-table part (R14b / R34): arm, leg and core share one item, distinguished by metadata. */
    public static Item robotPart;

    private ModContent() {}

    public static void preInit() {
        remoteHelmet = new ItemRemoteHelmet();
        GameRegistry.registerItem(remoteHelmet, REMOTE_HELMET_NAME);

        frequencyChip = new ItemFrequencyChip();
        GameRegistry.registerItem(frequencyChip, FREQUENCY_CHIP_NAME);

        robotCamera = new ItemRobotCamera();
        GameRegistry.registerItem(robotCamera, ItemRobotCamera.ITEM_NAME);

        robotBackpack = new ItemRobotBackpack();
        GameRegistry.registerItem(robotBackpack, ItemRobotBackpack.ITEM_NAME);

        robotPart = new ItemRobotPart();
        GameRegistry.registerItem(robotPart, ItemRobotPart.ITEM_NAME);

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

    /**
     * The mod instance FML associates with this mod. The GUI handler has to be registered against, and the
     * screens opened with, the very same object - FML maps an {@code Object} back to its {@code ModContainer}
     * by identity and silently ignores a handler registered under a different instance.
     */
    public static Object modInstance() {
        ModContainer container = Loader.instance()
            .getIndexedModList()
            .get(MindRobot.MODID);
        return container == null ? null : container.getMod();
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
