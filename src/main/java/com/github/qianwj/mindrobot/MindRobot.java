package com.github.qianwj.mindrobot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.qianwj.mindrobot.link.RobotRegistry;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

@Mod(
    modid = MindRobot.MODID,
    version = Tags.VERSION,
    name = "Mind-Robot",
    acceptedMinecraftVersions = "[1.7.10]",
    // R38 / D16: GregTech 5 Unofficial is a hard prerequisite (its IC2 API is what the battery slot accepts),
    // so a pack without GT refuses to load this mod instead of running half broken.
    dependencies = "required-after:gregtech")
public class MindRobot {

    public static final String MODID = "mindrobot";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(
        clientSide = "com.github.qianwj.mindrobot.ClientProxy",
        serverSide = "com.github.qianwj.mindrobot.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }

    /**
     * D11: the server stopping hook is a lifecycle event and is only delivered to {@code @Mod.EventHandler}
     * methods (it is not posted on the Forge event bus), so the final registry flush lives here. The world unload
     * bus event is the second safety net.
     */
    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        RobotRegistry.flushAll();
    }
}
