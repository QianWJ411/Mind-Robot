package com.github.qianwj.mindrobot.item;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.github.qianwj.mindrobot.MindRobot;
import com.github.qianwj.mindrobot.link.ChannelData;

/**
 * Remote control helmet. A head slot armor piece that stores a snapshot of the frequency channel it is
 * paired with (D5: {@code channel} / {@code mode} / {@code owner}), copied from a frequency chip via
 * shift + right click. The UUID pairing of P0 is gone; a helmet no longer names a specific robot.
 */
public class ItemRemoteHelmet extends ItemArmor {

    /** Head slot index inside EntityPlayer#getCurrentArmor. */
    public static final int HEAD_ARMOR_SLOT = 3;

    public ItemRemoteHelmet() {
        // renderIndex 2 selects the vanilla "iron" armor layer as a fallback, armorType 0 is the head slot.
        super(ArmorMaterial.IRON, 2, 0);
        setUnlocalizedName("remote_helmet");
        setTextureName(MindRobot.MODID + ":remote_helmet");
        setMaxStackSize(1);
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, int slot, String type) {
        // Slot 2 is the leggings slot, every other slot uses the first armor layer.
        return MindRobot.MODID + ":textures/models/armor/remote_helmet_layer_" + (slot == 2 ? 2 : 1) + ".png";
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        ChannelData channel = getChannelData(stack);
        if (channel == null) {
            tooltip.add(
                EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("tooltip.mindrobot.helmet.unpaired"));
            tooltip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("tooltip.mindrobot.helmet.hint"));
            return;
        }
        tooltip.add(
            EnumChatFormatting.DARK_GRAY
                + StatCollector.translateToLocalFormatted("tooltip.mindrobot.helmet.channel", channel.getChannel()));
        tooltip.add(
            EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal(
                channel.isPrivate() ? "tooltip.mindrobot.mode.private" : "tooltip.mindrobot.mode.public"));
    }

    /**
     * Reads the channel snapshot installed in this helmet.
     *
     * @return the snapshot, or {@code null} when no chip has been installed (or the tag is malformed)
     */
    public static ChannelData getChannelData(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return null;
        }
        return ChannelData.readFromNBT(stack.getTagCompound());
    }

    /**
     * Writes (or clears, when {@code channel} is null) the channel snapshot of this helmet stack.
     */
    public static void setChannelData(ItemStack stack, ChannelData channel) {
        if (stack == null) {
            return;
        }
        if (channel == null) {
            if (stack.hasTagCompound()) {
                ChannelData.removeFromNBT(stack.getTagCompound());
            }
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        channel.writeToNBT(tag);
    }

    /** True when this helmet carries a usable channel snapshot. */
    public static boolean hasChannel(ItemStack stack) {
        return getChannelData(stack) != null;
    }
}
