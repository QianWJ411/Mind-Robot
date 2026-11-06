package com.github.qianwj.mindrobot.item;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

import com.github.qianwj.mindrobot.MindRobot;
import com.github.qianwj.mindrobot.registry.ModContent;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Assembly-table part of a robot body (R14b / R34 / D18): mechanical arm, mechanical leg and built-in core.
 * <p>
 * One item with three metadata values ({@link RobotPartType}) instead of three items, the same scheme the helmet
 * generations and the cameras use. A part is a discrete physical piece with its own durability, so it never
 * stacks and every piece can wear down on its own. Five parts fit into a body and their durability adds up to
 * the structure cap of the finished robot (R16); the assembly table sums it with
 * {@link #getMaxDurability(ItemStack)} (or the remaining value with {@link #getRemainingDurability(ItemStack)}).
 * <p>
 * Durability lives in the NBT tag, not in the item damage value: in 1.7.10 the damage value <em>is</em> the
 * metadata, and the metadata is already the part kind. The item level {@code maxDamage} is only the upper bound
 * of the three kinds - {@link #getMaxDamage(ItemStack)} answers the real per-kind value for a concrete stack.
 * <p>
 * The part carries no behaviour of its own: installing one is simply putting it into the matching slot of the
 * assembly table, and that table owns the type check (R34).
 */
public class ItemRobotPart extends Item {

    public static final String ITEM_NAME = "robot_part";

    /** NBT key of the remaining durability of a single part; absent means "still brand new". */
    private static final String NBT_REMAINING_DURABILITY = "MindRobotPartDurability";

    /** Item icons per kind, indexed by {@link RobotPartType#getMetadata()}; filled by {@link #registerIcons}. */
    private IIcon[] partIcons;

    public ItemRobotPart() {
        setUnlocalizedName(ITEM_NAME);
        setTextureName(MindRobot.MODID + ":" + ITEM_NAME);
        // A part is one physical piece with its own durability, so it never stacks.
        setMaxStackSize(1);
        // The metadata is the part kind (R34), so the stack must not be damageable through the damage value.
        setHasSubtypes(true);
        // Upper bound of the three kinds; the per-stack value comes from getMaxDamage(ItemStack) below.
        setMaxDamage(RobotPartType.CORE.getMaxDurability());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        // The base texture stays registered as a fallback for any lookup that ignores the metadata.
        super.registerIcons(register);
        partIcons = new IIcon[RobotPartType.values().length];
        for (RobotPartType type : RobotPartType.values()) {
            partIcons[type.getMetadata()] = register.registerIcon(MindRobot.MODID + ":" + type.getItemIconName());
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        if (partIcons == null) {
            return super.getIconFromDamage(damage);
        }
        RobotPartType type = RobotPartType.fromMeta(damage);
        IIcon icon = partIcons[type.getMetadata()];
        return icon == null ? super.getIconFromDamage(damage) : icon;
    }

    /**
     * The durability of a part depends on its kind, not on the single item field. This is the Forge per-stack
     * hook ({@code ItemStack#getMaxDamage} routes through it), so the assembly table can simply call
     * {@code stack.getMaxDamage()} and get 400 / 450 / 600 for an arm / leg / core.
     */
    @Override
    public int getMaxDamage(ItemStack stack) {
        RobotPartType type = getType(stack);
        return type == null ? 0 : type.getMaxDurability();
    }

    /**
     * Never reports a part as damaged: the item damage value is the part kind, so the vanilla durability bar
     * would read metadata 1 and 2 as wear. The real remaining durability is shown by the tooltip instead.
     */
    @Override
    public boolean isDamaged(ItemStack stack) {
        return false;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        RobotPartType type = getType(stack);
        if (type != null) {
            String name = type.getUnlocalizedName();
            if (StatCollector.canTranslate(name)) {
                return StatCollector.translateToLocal(name);
            }
        }
        return StatCollector.translateToLocal(getUnlocalizedName() + ".name");
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        RobotPartType type = getType(stack);
        if (type == null) {
            return;
        }
        tooltip.add(
            EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocalFormatted(
                "tooltip.mindrobot.part.durability",
                getRemainingDurability(stack),
                type.getMaxDurability()));
        tooltip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("tooltip.mindrobot.part.hint"));
    }

    /**
     * Part kind of a stack.
     *
     * @return the kind, or {@code null} when the stack is empty or not a robot part - slot validation relies on
     *         that null: an assembly-table slot must never treat an unrelated item as "some arm"
     */
    public static RobotPartType getType(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemRobotPart)) {
            return null;
        }
        return RobotPartType.fromMeta(stack.getItemDamage());
    }

    /**
     * Durability a brand new part of this kind carries (400 arm / 450 leg / 600 core), or {@code 0} when the
     * stack is not a robot part.
     */
    public static int getMaxDurability(ItemStack stack) {
        RobotPartType type = getType(stack);
        return type == null ? 0 : type.getMaxDurability();
    }

    /**
     * Remaining durability of a single part; a stack that was never worn (a freshly crafted one, or one without
     * any NBT at all) reports its full durability. The value is clamped to {@code 0..max} so a hand edited tag
     * cannot produce a part stronger than a new one, and it is {@code 0} when the stack is not a robot part.
     */
    public static int getRemainingDurability(ItemStack stack) {
        RobotPartType type = getType(stack);
        if (type == null) {
            return 0;
        }
        if (!stack.hasTagCompound()) {
            return type.getMaxDurability();
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(NBT_REMAINING_DURABILITY)) {
            return type.getMaxDurability();
        }
        return Math.max(0, Math.min(tag.getInteger(NBT_REMAINING_DURABILITY), type.getMaxDurability()));
    }

    /** Writes the remaining durability of a part, clamped to {@code 0..max}; ignored for any other stack. */
    public static void setRemainingDurability(ItemStack stack, int remaining) {
        RobotPartType type = getType(stack);
        if (type == null) {
            return;
        }
        int clamped = Math.max(0, Math.min(remaining, type.getMaxDurability()));
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setInteger(NBT_REMAINING_DURABILITY, clamped);
    }

    /**
     * A brand new part of the given kind, carrying its full durability. This is the only supported way to build
     * a part stack: it keeps the metadata and the durability tag in sync.
     */
    public static ItemStack stackOf(RobotPartType type) {
        RobotPartType part = type == null ? RobotPartType.ARM : type;
        if (ModContent.robotPart == null) {
            throw new IllegalStateException("Robot part item requested before ModContent.preInit() registered it");
        }
        ItemStack stack = new ItemStack(ModContent.robotPart, 1, part.getMetadata());
        setRemainingDurability(stack, part.getMaxDurability());
        return stack;
    }
}
