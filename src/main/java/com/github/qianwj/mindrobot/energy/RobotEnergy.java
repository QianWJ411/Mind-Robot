package com.github.qianwj.mindrobot.energy;

import net.minecraft.item.ItemStack;

import com.github.qianwj.mindrobot.Config;
import com.github.qianwj.mindrobot.entity.EntityRobot;
import com.github.qianwj.mindrobot.inventory.RobotInventory;

import ic2.api.item.ElectricItem;
import ic2.api.item.IElectricItem;

/**
 * Energy budget of a driven robot (R39 / R40 / R42).
 * <p>
 * <b>Where the energy comes from.</b> The battery cells of the body itself. The box only decides how many cells
 * exist and which tiers fit (R41); it holds nothing. That is the whole "robot, then box, then cells" path.
 * <p>
 * <b>How much is drawn.</b> The draw is <b>fixed</b>: the same idle draw per tick, the same price per broken
 * block and per attack, whatever the voltage of the box or the tier of the cells. A better box therefore does
 * not make the robot faster or stronger, it only makes it last longer, because it can hold cells with more EU
 * in them. Nothing here scales an amount by a tier, and the discharge deliberately ignores the transfer limit of
 * the cell: honouring it would let a high tier cell deliver more per tick and the "fixed cost" model would be a
 * lie.
 * <p>
 * Every number comes from {@link Config}, so a pack author can retune the model without a rebuild.
 */
public final class RobotEnergy {

    /**
     * Voltage the discharge is requested at. It is deliberately the top of the IC2 ladder: the request is a
     * capability hint, not a cost, and the robot has no voltage of its own.
     */
    private static final int DISCHARGE_TIER = 5;

    private RobotEnergy() {}

    /**
     * EU currently stored in the live cells of the body.
     *
     * @return the stored amount, or -1 when no electric item API is available at runtime, so callers can tell
     *         "empty" apart from "cannot measure"
     */
    public static double getStoredEu(EntityRobot robot) {
        if (ElectricItem.manager == null) {
            return -1.0D;
        }
        double stored = 0.0D;
        RobotInventory inventory = robot == null ? null : robot.getInventory();
        if (inventory == null) {
            return 0.0D;
        }
        int cells = inventory.getBatteryCellCount();
        for (int cell = 0; cell < cells; ++cell) {
            ItemStack stack = inventory.getBatteryCell(cell);
            if (stack != null && stack.getItem() instanceof IElectricItem) {
                stored += ElectricItem.manager.getCharge(stack);
            }
        }
        return stored;
    }

    /** True while the robot still has energy to spend; unknown availability is treated as "has energy". */
    public static boolean hasEnergy(EntityRobot robot) {
        double stored = getStoredEu(robot);
        return stored < 0.0D || stored > 0.0D;
    }

    /**
     * True when the body can pay the next step of the given cost (R42). The drive is ended the moment this turns
     * false, so a robot never acts on a half paid tick.
     */
    public static boolean canPay(EntityRobot robot, double amount) {
        if (amount <= 0.0D) {
            return true;
        }
        double stored = getStoredEu(robot);
        return stored < 0.0D || stored >= amount;
    }

    /** The fixed price of one controlled tick, in EU (R40). */
    public static double getIdleCost() {
        return Config.euPerTickIdle;
    }

    /**
     * The idle draw of a controlled robot (R40): {@link Config#euPerTickIdle} EU every tick, independent of the
     * installed hardware. Runs on the server tick of the robot itself.
     */
    public static void tickIdle(EntityRobot robot) {
        drain(robot, Config.euPerTickIdle);
    }

    /** Extra draw for a block the robot broke (R40); called by the mining path once it exists. */
    public static void consumeMining(EntityRobot robot) {
        drain(robot, Config.euPerBlockMined);
    }

    /** Extra draw for an attack the robot landed (R40); called by the attack path once it exists. */
    public static void consumeAttack(EntityRobot robot) {
        drain(robot, Config.euPerAttack);
    }

    /**
     * Takes a fixed amount of EU out of the live cells, in slot order, until the amount is covered or the cells
     * are empty. Each drained cell is written back into the inventory, so the new charge reaches the save file.
     *
     * @return the amount that was actually drained
     */
    public static double drain(EntityRobot robot, double amount) {
        if (robot == null || amount <= 0.0D || ElectricItem.manager == null) {
            return 0.0D;
        }
        RobotInventory inventory = robot.getInventory();
        int cells = inventory.getBatteryCellCount();
        double remaining = amount;
        double drained = 0.0D;
        for (int cell = 0; cell < cells && remaining > 0.0D; ++cell) {
            ItemStack stack = inventory.getBatteryCell(cell);
            if (stack == null || !(stack.getItem() instanceof IElectricItem)) {
                continue;
            }
            // ignoreTransferLimit = true (a robot drains instantly), externally = false, simulate = false
            // because the charge must actually leave the cell and reach the save file.
            double taken = ElectricItem.manager.discharge(stack, remaining, DISCHARGE_TIER, true, false, false);
            if (taken <= 0.0D) {
                continue;
            }
            inventory.setInventorySlotContents(RobotInventory.BATTERY_CELL_FIRST_SLOT + cell, stack);
            drained += taken;
            remaining -= taken;
        }
        return drained;
    }
}
