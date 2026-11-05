package com.github.qianwj.mindrobot;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.github.qianwj.mindrobot.command.CommandMindRobot;
import com.github.qianwj.mindrobot.container.BrainHelmetGuiHandler;
import com.github.qianwj.mindrobot.event.CommonEventHandler;
import com.github.qianwj.mindrobot.event.RobotTargetingHandler;
import com.github.qianwj.mindrobot.link.RobotChunkLoader;
import com.github.qianwj.mindrobot.registry.ModContent;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;

public class CommonProxy {

    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());

        MindRobot.LOG.info(Config.greeting);
        MindRobot.LOG.info("I am Mind-Robot at version " + Tags.VERSION);

        ModContent.preInit();
        // The helmet chip screen (D13) is a plain FML gui handler: registered from the common proxy because a
        // dedicated server needs the server half as well.
        NetworkRegistry.INSTANCE.registerGuiHandler(ModContent.modInstance(), new BrainHelmetGuiHandler());
        // Must run while FML still knows our mod container: ForgeChunkManager refuses to hand out tickets to a
        // mod that never registered a forced chunk callback (D10).
        RobotChunkLoader.init();
        registerEventHandlers();
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {}

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {}

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandMindRobot());
    }

    /**
     * Opens the frequency chip configuration screen. There is no screen on the dedicated server; the chip is
     * configured with {@code /mindrobot chip} there. Overridden on the client.
     */
    public void openChipConfigGui(EntityPlayer player, ItemStack stack) {
        // No-op on the server side.
    }

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
