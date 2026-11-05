package com.github.qianwj.mindrobot.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderHandEvent;

import com.github.qianwj.mindrobot.client.gui.GuiBrainHelmet;
import com.github.qianwj.mindrobot.client.gui.RadialMenuGui;
import com.github.qianwj.mindrobot.client.key.KeyBindings;
import com.github.qianwj.mindrobot.entity.EntityRobot;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Client side of the camera take over (D1) plus the radial menu key.
 * <ul>
 * <li>camera: {@code Minecraft.renderViewEntity} points at the driven robot while a link is open;</li>
 * <li>first person hand: {@link RenderHandEvent} is cancelled while driving;</li>
 * <li>own inventory: {@link GuiInventory} is refused while driving (R10), and so is the helmet chip screen
 * (D13), because both would let the driver move the items the link depends on.</li>
 * </ul>
 */
public class ClientEventHandler {

    private boolean cameraOverridden;

    @SubscribeEvent
    public void onRenderHand(RenderHandEvent event) {
        if (ClientLinkState.isLinked()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (!ClientLinkState.isLinked()) {
            return;
        }
        // The own inventory (R10) and the helmet chip slot: both would let the driver rearrange the items the
        // live link depends on.
        if (!(event.gui instanceof GuiInventory) && !(event.gui instanceof GuiBrainHelmet)) {
            return;
        }
        event.setCanceled(true);
        Minecraft.getMinecraft()
            .displayGuiScreen(null);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        boolean pressed = KeyBindings.OPEN_RADIAL_MENU.isPressed();
        if (!pressed || minecraft.thePlayer == null || minecraft.currentScreen != null) {
            return;
        }
        minecraft.displayGuiScreen(new RadialMenuGui(ClientLinkState.getOptions()));
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null) {
            cameraOverridden = false;
            return;
        }

        EntityLivingBase target = ClientLinkState.isLinked() ? resolveRobot(minecraft) : null;
        if (target != null) {
            minecraft.renderViewEntity = target;
            cameraOverridden = true;
        } else if (cameraOverridden) {
            minecraft.renderViewEntity = minecraft.thePlayer;
            cameraOverridden = false;
        }
    }

    private EntityRobot resolveRobot(Minecraft minecraft) {
        int entityId = ClientLinkState.getRobotEntityId();
        if (minecraft.theWorld == null || entityId < 0) {
            return null;
        }
        Entity entity = minecraft.theWorld.getEntityByID(entityId);
        return entity instanceof EntityRobot ? (EntityRobot) entity : null;
    }
}
