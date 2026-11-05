package com.github.qianwj.mindrobot.item;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.github.qianwj.mindrobot.MindRobot;

/**
 * Backpack part of a robot body (R14b / D17).
 * <p>
 * Without this part the body has no bulk storage at all: the 54 backpack cells are not shown in the container
 * and {@code RobotInventory#insertIntoBackpack} refuses everything, so an unfinished robot cannot collect loot.
 * The part itself carries no state - installing it is simply the act of putting it into the backpack slot of
 * the robot container, and taking it out locks the cells again.
 */
public class ItemRobotBackpack extends Item {

    public static final String ITEM_NAME = "robot_backpack";

    public ItemRobotBackpack() {
        setUnlocalizedName(ITEM_NAME);
        setTextureName(MindRobot.MODID + ":" + ITEM_NAME);
        setMaxStackSize(1);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        tooltip.add(
            EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("tooltip.mindrobot.backpack.hint"));
    }
}
