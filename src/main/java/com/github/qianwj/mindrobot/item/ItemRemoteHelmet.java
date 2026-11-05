package com.github.qianwj.mindrobot.item;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.github.qianwj.mindrobot.MindRobot;

/**
 * Remote control helmet. A head slot armor piece that stores the UUID of the robot it is paired with
 * inside its own stack NBT (design decision D5).
 */
public class ItemRemoteHelmet extends ItemArmor {

    /** NBT key holding the paired robot UUID as a string. */
    public static final String NBT_PAIRED_ROBOT = "PairedRobot";

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
        UUID robotId = getPairedRobot(stack);
        String key = robotId == null ? "tooltip.mindrobot.helmet.unpaired" : "tooltip.mindrobot.helmet.paired";
        tooltip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal(key));
    }

    /**
     * Reads the UUID of the robot paired with this helmet stack.
     *
     * @return the paired robot UUID, or null when the stack is unpaired or the tag is malformed
     */
    public static UUID getPairedRobot(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return null;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(NBT_PAIRED_ROBOT)) {
            return null;
        }
        try {
            return UUID.fromString(tag.getString(NBT_PAIRED_ROBOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Writes (or clears, when robotId is null) the paired robot UUID on this helmet stack.
     */
    public static void setPairedRobot(ItemStack stack, UUID robotId) {
        if (stack == null) {
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            if (robotId == null) {
                return;
            }
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        if (robotId == null) {
            tag.removeTag(NBT_PAIRED_ROBOT);
        } else {
            tag.setString(NBT_PAIRED_ROBOT, robotId.toString());
        }
    }
}
