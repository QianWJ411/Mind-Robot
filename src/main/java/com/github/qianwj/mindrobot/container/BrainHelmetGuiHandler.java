package com.github.qianwj.mindrobot.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import com.github.qianwj.mindrobot.client.gui.GuiBrainHelmet;
import com.github.qianwj.mindrobot.link.RobotLinkManager;
import com.github.qianwj.mindrobot.registry.ModContent;

import cpw.mods.fml.common.network.IGuiHandler;

/**
 * FML bridge for the brain helmet screen (R32, D13).
 * <p>
 * {@code x} carries the helmet location, see {@link ContainerBrainHelmet} for the convention. The client side
 * does not validate anything: the server has already decided to open the screen, and refusing in
 * {@link #getClientGuiElement} would leave the player without a screen while the server keeps the container
 * open.
 */
public class BrainHelmetGuiHandler implements IGuiHandler {

    /** GUI id registered with {@code NetworkRegistry}; unique per mod. */
    public static final int GUI_ID = 0;
    /** {@code x} value meaning "the hotbar slot the player is holding right now". */
    public static final int MAIN_HAND = -1;

    private static final int UNUSED = 0;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != GUI_ID || player == null) {
            return null;
        }
        int helmetSlot = ContainerBrainHelmet.resolveHelmetSlot(player, x);
        if (!isOpenable(player, helmetSlot)) {
            return null;
        }
        return new ContainerBrainHelmet(player, helmetSlot);
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != GUI_ID || player == null) {
            return null;
        }
        return new GuiBrainHelmet(new ContainerBrainHelmet(player, x));
    }

    /**
     * Opens the chip screen on the server; FML tells the client which screen to build.
     *
     * @param helmetSlot InventoryPlayer index of the helmet, or {@link #MAIN_HAND}
     * @return true when the open request was handed to FML
     */
    public static boolean open(EntityPlayer player, int helmetSlot) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return false;
        }
        int slot = ContainerBrainHelmet.resolveHelmetSlot(player, helmetSlot);
        Object mod = ModContent.modInstance();
        if (mod == null) {
            return false;
        }
        player.openGui(mod, GUI_ID, player.worldObj, slot, UNUSED, UNUSED);
        return true;
    }

    /**
     * Server side gate: no screen while driving a robot (the R10 rule extended to the helmet itself, so a
     * chip cannot be swapped under a live link) and no screen when the tracked slot does not hold a helmet.
     */
    private static boolean isOpenable(EntityPlayer player, int helmetSlot) {
        if (player.inventory == null || RobotLinkManager.isDriving(player)) {
            return false;
        }
        return new HelmetChipInventory(player, helmetSlot).getHelmet() != null;
    }
}
