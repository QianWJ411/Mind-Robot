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
import com.github.qianwj.mindrobot.link.LastKnownPosition;

/**
 * Remote control helmet. A head slot armor piece that stores a snapshot of the channel it is paired with
 * (D5: {@code mode} plus either a public number or a private name and password), copied from a frequency chip
 * via shift + right click. The UUID pairing of P0 is gone; a helmet no longer names a specific robot.
 * The private password is stored in plain text inside the snapshot but is never shown in the tooltip.
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
        // Only the public number or the private name is displayed; the password stays inside the NBT.
        tooltip.add(EnumChatFormatting.DARK_GRAY + helmetLabel(channel));
        tooltip.add(
            EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal(
                channel.isPrivate() ? "tooltip.mindrobot.mode.private" : "tooltip.mindrobot.mode.public"));
        LastKnownPosition lastKnown = getLastKnownPosition(stack);
        if (lastKnown != null) {
            tooltip.add(
                EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocalFormatted(
                    "tooltip.mindrobot.helmet.lastpos",
                    lastKnown.getBlockX(),
                    lastKnown.getBlockY(),
                    lastKnown.getBlockZ(),
                    lastKnown.getDimension()));
        }
    }

    /**
     * Where the paired robot last reported itself (R27), or {@code null} when never recorded. This is a display
     * mirror copied from {@code RobotRegistry}: the tooltip shows it, the connection flow never reads it.
     */
    public static LastKnownPosition getLastKnownPosition(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return null;
        }
        return LastKnownPosition.readFromNBT(stack.getTagCompound());
    }

    /** Refreshes the display mirror; the server copies it out of the registry/live robot on link open and close. */
    public static void setLastKnownPosition(ItemStack stack, LastKnownPosition position) {
        if (stack == null) {
            return;
        }
        if (position == null) {
            if (stack.hasTagCompound()) {
                LastKnownPosition.removeFromNBT(stack.getTagCompound());
            }
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        position.writeToNBT(tag);
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

    /** True when this helmet carries a complete channel snapshot (public number, or private name + password). */
    public static boolean hasChannel(ItemStack stack) {
        ChannelData channel = getChannelData(stack);
        return channel != null && channel.isConfigured();
    }

    /** Localized label of a snapshot: the number for a public channel, the name for a private one. */
    private static String helmetLabel(ChannelData channel) {
        if (channel.isPrivate()) {
            return StatCollector.translateToLocalFormatted("tooltip.mindrobot.helmet.name", channel.getName());
        }
        return StatCollector.translateToLocalFormatted("tooltip.mindrobot.helmet.channel", channel.getChannel());
    }
}
