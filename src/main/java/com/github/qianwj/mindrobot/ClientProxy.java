package com.github.qianwj.mindrobot;

import net.minecraftforge.common.MinecraftForge;

import com.github.qianwj.mindrobot.client.ClientEventHandler;
import com.github.qianwj.mindrobot.client.key.KeyBindings;
import com.github.qianwj.mindrobot.client.render.RenderRobot;
import com.github.qianwj.mindrobot.entity.EntityRobot;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    // Override CommonProxy methods here, if you want a different behaviour on the client (e.g. registering renders).
    // Don't forget to call the super methods as well.

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        KeyBindings.init();
        RenderingRegistry.registerEntityRenderingHandler(EntityRobot.class, new RenderRobot());

        ClientEventHandler clientHandler = new ClientEventHandler();
        MinecraftForge.EVENT_BUS.register(clientHandler);
        FMLCommonHandler.instance()
            .bus()
            .register(clientHandler);
    }
}
