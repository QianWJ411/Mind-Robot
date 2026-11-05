package com.github.qianwj.mindrobot;

import net.minecraftforge.common.MinecraftForge;

import com.github.qianwj.mindrobot.event.CommonEventHandler;
import com.github.qianwj.mindrobot.event.RobotTargetingHandler;
import com.github.qianwj.mindrobot.registry.ModContent;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());

        MindRobot.LOG.info(Config.greeting);
        MindRobot.LOG.info("I am Mind-Robot at version " + Tags.VERSION);

        ModContent.preInit();
        registerEventHandlers();
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {}

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {}

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {}

    /**
     * Registers the common event handlers. Forge events and FML game events live on two different buses in
     * 1.7.10, so the same handler instances are attached to both.
     */
    protected void registerEventHandlers() {
        CommonEventHandler commonHandler = new CommonEventHandler();
        MinecraftForge.EVENT_BUS.register(commonHandler);
        FMLCommonHandler.instance()
            .bus()
            .register(commonHandler);

        // Handles both EntityJoinWorldEvent (Forge bus) and the throttled ServerTickEvent scan (FML bus).
        RobotTargetingHandler targetingHandler = new RobotTargetingHandler();
        MinecraftForge.EVENT_BUS.register(targetingHandler);
        FMLCommonHandler.instance()
            .bus()
            .register(targetingHandler);
    }
}
