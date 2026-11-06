package com.github.qianwj.mindrobot.registry;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.github.qianwj.mindrobot.MindRobot;
import com.github.qianwj.mindrobot.item.ItemRobotPart;
import com.github.qianwj.mindrobot.item.RobotPartType;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Every crafting recipe of the mod (R14b / R31 / R34 / D18).
 * <p>
 * The recipes are registered from {@code CommonProxy#init(FMLInitializationEvent)}, i.e. after
 * {@link ModContent#preInit()} has registered the items and before any mod talks about recipes in its own
 * {@code postInit}. {@link #register()} refuses to run with an unregistered item and every recipe is validated
 * before it is handed to the crafting manager: a half registered table that silently produces {@code null} or
 * skips a line is worse than a loud failure during loading.
 * <p>
 * All materials are vanilla on purpose (D18): the parts must be craftable in GTNH before the player has any GT
 * machine, and the assembler recipe is therefore the only one deliberately absent here - it ships together with
 * the assembler block itself.
 */
public final class ModRecipes {

    /** Number of recipes handed to the crafting manager by the current {@link #register()} run. */
    private static int registered;

    private ModRecipes() {}

    /**
     * Adds the shaped recipes. Runs once per side (client and server own their own crafting manager), after the
     * items exist.
     *
     * @throws IllegalStateException when an item is not registered yet or a recipe would be added with a missing
     *                               output or ingredient
     */
    public static void register() {
        requireRegistered("robot part", ModContent.robotPart);
        requireRegistered("remote helmet", ModContent.remoteHelmet);
        requireRegistered("robot camera", ModContent.robotCamera);
        requireRegistered("robot backpack", ModContent.robotBackpack);
        requireRegistered("frequency chip", ModContent.frequencyChip);

        registered = 0;

        // --- assembly-table parts: 4 iron + one conductor core + 2 redstone (D18) ---
        // Mechanical arm: a piston driven by two redstone, mounted on four iron ingots.
        addShaped(
            "robot part arm",
            ItemRobotPart.stackOf(RobotPartType.ARM),
            "RIR",
            "IPI",
            " I ",
            'I',
            Items.iron_ingot,
            'P',
            Blocks.piston,
            'R',
            Items.redstone);
        // Mechanical leg: two iron struts around one iron block, redstone on both sides.
        addShaped(
            "robot part leg",
            ItemRobotPart.stackOf(RobotPartType.LEG),
            "I I",
            "RBR",
            "I I",
            'I',
            Items.iron_ingot,
            'B',
            Blocks.iron_block,
            'R',
            Items.redstone);
        // Built-in core: one diamond in an iron frame, sitting on a redstone block.
        addShaped(
            "robot part core",
            ItemRobotPart.stackOf(RobotPartType.CORE),
            "I I",
            "IDI",
            " B ",
            'I',
            Items.iron_ingot,
            'D',
            Items.diamond,
            'B',
            Blocks.redstone_block);

        // --- equipment that had no recipe at all before ---
        // Helmet MKI (R31): five iron, one glass visor and three redstone. The other generations are upgrades,
        // so MKI is the only one crafted; metadata 0 is HelmetTier.MK1.
        addShaped(
            "remote helmet mki",
            new ItemStack(ModContent.remoteHelmet, 1, 0),
            "III",
            "IGI",
            "RRR",
            'I',
            Items.iron_ingot,
            'G',
            Blocks.glass,
            'R',
            Items.redstone);
        // Plain camera: two iron, two glass panes and two redstone. Metadata 0 is CameraType.NORMAL.
        addShaped(
            "robot camera",
            new ItemStack(ModContent.robotCamera, 1, 0),
            " G ",
            "IGI",
            "R R",
            'G',
            Blocks.glass_pane,
            'I',
            Items.iron_ingot,
            'R',
            Items.redstone);
        // Thermal camera: the plain camera plus two glowstone and two redstone. Metadata 1 is CameraType.THERMAL;
        // ShapedRecipes compares the metadata, so the plain camera cannot be used here by accident.
        addShaped(
            "thermal camera",
            new ItemStack(ModContent.robotCamera, 1, 1),
            " R ",
            "LCL",
            " R ",
            'R',
            Items.redstone,
            'L',
            Blocks.glowstone,
            'C',
            new ItemStack(ModContent.robotCamera, 1, 0));
        // Backpack part: four leather straps around a chest, two iron feet.
        addShaped(
            "robot backpack",
            new ItemStack(ModContent.robotBackpack),
            "H H",
            "HCH",
            "I I",
            'H',
            Items.leather,
            'C',
            Blocks.chest,
            'I',
            Items.iron_ingot);
        // Frequency chip: two gold contacts, three redstone paths, one iron trace.
        addShaped(
            "frequency chip",
            new ItemStack(ModContent.frequencyChip),
            "R R",
            "GIG",
            " R ",
            'R',
            Items.redstone,
            'G',
            Items.gold_ingot,
            'I',
            Items.iron_ingot);

        MindRobot.LOG.info("Registered {} crafting recipes", registered);
    }

    /**
     * Adds one shaped recipe after checking that it can actually be resolved. The check is the point: a missing
     * output or ingredient would otherwise end up as a recipe that never matches (or as a {@code null} result in
     * the crafting table) and would only be noticed in game.
     *
     * @param name   human readable name, used in the log and in the exception message
     * @param output what the recipe produces
     * @param recipe pattern rows followed by the {@code char}/{@code Item} or {@code Block} pairs
     */
    private static void addShaped(String name, ItemStack output, Object... recipe) {
        if (output == null || output.getItem() == null) {
            throw new IllegalStateException("Recipe '" + name + "' has no output item");
        }
        for (Object entry : recipe) {
            if (entry == null) {
                throw new IllegalStateException("Recipe '" + name + "' has a missing ingredient");
            }
        }
        GameRegistry.addShapedRecipe(output, recipe);
        registered++;
        MindRobot.LOG.debug("Registered shaped recipe '{}'", name);
    }

    /** Fails loudly when recipes run before the items exist, instead of adding a recipe with a null result. */
    private static void requireRegistered(String name, Item item) {
        if (item == null) {
            throw new IllegalStateException(
                "Item '" + name + "' is not registered yet: recipes must be registered after ModContent.preInit()");
        }
    }
}
