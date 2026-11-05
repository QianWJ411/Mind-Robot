package com.github.qianwj.mindrobot;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.github.qianwj.mindrobot.client.ClientEventHandler;
import com.github.qianwj.mindrobot.client.gui.ChipConfigGui;
import com.github.qianwj.mindrobot.client.key.KeyBindings;
import com.github.qianwj.mindrobot.client.render.NoiseOverlay;
import com.github.qianwj.mindrobot.client.render.RenderRobot;
import com.github.qianwj.mindrobot.entity.EntityRobot;
import com.github.qianwj.mindrobot.item.ItemFrequencyChip;

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

        // Driving picture noise: a Forge bus HUD listener, it shares the link lifetime with the camera takeover.
        MinecraftForge.EVENT_BUS.register(new NoiseOverlay());
    }

    @Override
    public void openChipConfigGui(EntityPlayer player, ItemStack stack) {
        Minecraft.getMinecraft()
            .displayGuiScreen(new ChipConfigGui(ItemFrequencyChip.getChannelData(stack)));
    }
}
