package com.github.qianwj.mindrobot.item;

import net.minecraft.item.ItemStack;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicBatteryBuffer;
import gregtech.common.blocks.ItemMachines;
import ic2.api.item.IElectricItem;

/**
 * Battery box of a robot body, read straight off GregTech (R39 / R41 / D17).
 * <p>
 * "Battery box" is GregTech's own block: its Chinese name {@code %1$s电池箱} belongs to
 * {@code gt.blockmachines.batterybuffer}, whose template meta tile entity is a {@code MTEBasicBatteryBuffer}.
 * The mod therefore ships no box item of its own - the robot slot accepts that block item and asks the template
 * for the two numbers the body needs:
 * <ul>
 * <li>how many cells the body gains: {@link MTEBasicBatteryBuffer#mBatteryCount};</li>
 * <li>which cells fit: {@code MTETieredMachineBlock#mTier}, the same voltage grading a placed buffer applies.</li>
 * </ul>
 * The template is looked up in {@link GregTechAPI#METATILEENTITIES} by the damage value of the stack. Those
 * entries are templates, not placed machines, so they have no base tile entity and none is ever asked for.
 * <p>
 * Every method tolerates a {@code null} stack and a stack that is not a battery buffer: such a stack reports zero
 * cells and tier zero, which reads as "no box installed" everywhere.
 */
public final class GtBatteryBox {

    /**
     * Cell slots every body reserves even when GregTech offers no bigger buffer: the four slots the design
     * documents for the 4x buffer. It is a floor, never a cap - a bigger GregTech buffer raises the storage.
     */
    private static final int RESERVED_CELL_SLOTS = 4;

    /** First tier ever handed out by {@code IElectricItem#getTier()}: tier 1 is LV. */
    private static final int LOWEST_TIER = 1;

    /** Biggest cell count seen in GregTech, once {@link #getMaxSlotCount()} has run. */
    private static int maxSlotCount = RESERVED_CELL_SLOTS;
    /** True once the GregTech meta tile entity array was readable, so the scan is not repeated every call. */
    private static boolean maxSlotCountResolved;

    private GtBatteryBox() {}

    /** The floor of {@link com.github.qianwj.mindrobot.inventory.RobotInventory#BATTERY_CELL_CAPACITY}. */
    public static int getReservedCellSlots() {
        return RESERVED_CELL_SLOTS;
    }

    /**
     * Biggest number of cells any GregTech battery buffer holds, never below {@link #RESERVED_CELL_SLOTS}.
     * <p>
     * The number is scanned once from the loaded templates, so the storage of a body follows the GregTech pack
     * instead of a constant here. When the array is not readable yet the scan is retried on the next call rather
     * than frozen at the floor.
     */
    public static int getMaxSlotCount() {
        if (!maxSlotCountResolved) {
            IMetaTileEntity[] templates = GregTechAPI.METATILEENTITIES;
            if (templates != null) {
                int max = RESERVED_CELL_SLOTS;
                for (IMetaTileEntity template : templates) {
                    if (template instanceof MTEBasicBatteryBuffer) {
                        max = Math.max(max, ((MTEBasicBatteryBuffer) template).mBatteryCount);
                    }
                }
                maxSlotCount = max;
                maxSlotCountResolved = true;
            }
        }
        return maxSlotCount;
    }

    /** True when the stack is a GregTech battery buffer block item, whatever its size or tier (R39). */
    public static boolean isBatteryBox(ItemStack stack) {
        return asBatteryBox(stack) != null;
    }

    /** Number of cells the box unlocks: 0 for anything that is not a battery buffer. */
    public static int getSlotCount(ItemStack stack) {
        MTEBasicBatteryBuffer box = asBatteryBox(stack);
        return box == null ? 0 : box.mBatteryCount;
    }

    /** Voltage tier of the box: 0 for anything that is not a battery buffer. */
    public static int getTier(ItemStack stack) {
        MTEBasicBatteryBuffer box = asBatteryBox(stack);
        return box == null ? 0 : box.mTier;
    }

    /** True when the box may take this cell: an IC2 electric item whose tier the box reaches (R39). */
    public static boolean isValidCell(ItemStack box, ItemStack cell) {
        if (cell == null || !(cell.getItem() instanceof IElectricItem)) {
            return false;
        }
        int boxTier = box == null ? 0 : getTier(box);
        return fits(((IElectricItem) cell.getItem()).getTier(cell), boxTier);
    }

    /**
     * True when a battery of this tier fits a box of that tier.
     * <p>
     * The comparison deliberately has no lower bound: whatever base IC2 uses (the API documents 1 = LV, an older
     * build could report 0), a battery reporting below LV must still fit an LV buffer. The clamp only protects
     * against a nonsensical negative report, never rejects a normal cell. A box tier of 0 means "no box", which
     * admits nothing.
     */
    public static boolean fits(int batteryTier, int boxTier) {
        return Math.max(LOWEST_TIER, batteryTier) <= boxTier;
    }

    /** The template behind the stack, or {@code null} when the stack is not a battery buffer block item. */
    private static MTEBasicBatteryBuffer asBatteryBox(ItemStack stack) {
        if (stack == null || stack.getItem() == null || !(stack.getItem() instanceof ItemMachines)) {
            return null;
        }
        IMetaTileEntity[] templates = GregTechAPI.METATILEENTITIES;
        int damage = stack.getItemDamage();
        if (templates == null || damage < 0 || damage >= templates.length) {
            return null;
        }
        IMetaTileEntity template = templates[damage];
        return template instanceof MTEBasicBatteryBuffer ? (MTEBasicBatteryBuffer) template : null;
    }
}
