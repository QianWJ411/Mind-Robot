package com.github.qianwj.mindrobot.item;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.github.qianwj.mindrobot.MindRobot;

import ic2.api.item.IElectricItem;

/**
 * Battery box of a robot body (R39 / R41): the <b>capacity key</b> of the body, not a container.
 * <p>
 * The body itself is the container - the battery cells live in the robot inventory - so a box carries no
 * inventory, no NBT and no screen. It only answers two questions:
 * <ul>
 * <li>how many cells the body may use: 1x, 2x, 3x or 4x (size);</li>
 * <li>which cells fit: a cell is admitted only when its {@code IElectricItem#getTier()} is at most the rated
 * voltage of the box, the same grading a GT battery buffer applies.</li>
 * </ul>
 * Both answers are encoded in the item metadata ({@link BatteryBoxSpec}), so a box is fully described by its
 * stack. Removing the box takes the cells out of service; the robot hands them back to the player instead of
 * swallowing them (R41).
 */
public class ItemRobotBatteryBox extends Item {

    public static final String ITEM_NAME = "robot_battery_box";

    public ItemRobotBatteryBox() {
        setUnlocalizedName(ITEM_NAME);
        setTextureName(MindRobot.MODID + ":" + ITEM_NAME);
        setMaxStackSize(1);
        // Size and voltage are the metadata; the stack is not damageable.
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    /** Size encoded in the stack metadata; a plain stack is the smallest box. */
    public static BatteryBoxSpec.Size getSize(ItemStack box) {
        return BatteryBoxSpec.sizeOf(box == null ? 0 : box.getItemDamage());
    }

    /** Rated voltage encoded in the stack metadata. */
    public static BatteryBoxSpec.Voltage getVoltage(ItemStack box) {
        return BatteryBoxSpec.voltageOf(box == null ? 0 : box.getItemDamage());
    }

    /** Number of cells this box unlocks in the robot body, 1..4 (R41). */
    public static int getCellCapacity(ItemStack box) {
        return getSize(box).getSlotCount();
    }

    /**
     * True when the given stack may be installed as a cell of this box: an electric item (the IC2 contract every
     * GregTech cell implements) whose tier does not exceed the rated voltage of the box.
     */
    public static boolean isValidBattery(ItemStack box, ItemStack cell) {
        if (box == null || !(box.getItem() instanceof ItemRobotBatteryBox)) {
            return false;
        }
        if (cell == null || !(cell.getItem() instanceof IElectricItem)) {
            return false;
        }
        int tier = ((IElectricItem) cell.getItem()).getTier(cell);
        return BatteryBoxSpec.fits(tier, getVoltage(box));
    }

    /** "3x HV Battery Box", so both axes are readable without hovering. */
    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return StatCollector.translateToLocalFormatted(
            "item." + ITEM_NAME + ".display",
            Integer.valueOf(getCellCapacity(stack)),
            getVoltage(stack).getLabel());
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        tooltip.add(
            EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocalFormatted(
                "tooltip.mindrobot.battery_box.cells",
                Integer.valueOf(getCellCapacity(stack))));
        tooltip.add(
            EnumChatFormatting.DARK_GRAY + StatCollector
                .translateToLocalFormatted("tooltip.mindrobot.battery_box.voltage", getVoltage(stack).getLabel()));
        tooltip
            .add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("tooltip.mindrobot.battery_box.hint"));
    }
}
