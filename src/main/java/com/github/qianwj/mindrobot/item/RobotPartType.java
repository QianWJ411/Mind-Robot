package com.github.qianwj.mindrobot.item;

/**
 * The three kinds of assembly-table part a robot body is built from (R14b / R34 / D18).
 * <p>
 * All three are the same item and only differ in the item metadata, the very same scheme the helmet generations
 * and the cameras use: adding a kind means adding one constant here plus one icon, never another item
 * registration. The assembly table validates a slot by <em>kind</em> only (R34): a robot has two arm slots, two
 * leg slots and one core slot, and both arm slots accept any arm - there is no left and right part, so four
 * constants (and four mirrored recipes) are simply gone.
 * <p>
 * Each kind owns its own durability. Five parts fit into a body, so the sum of the durability of the five parts
 * is the structure durability cap of the finished robot (R16); the assembly table reads it through
 * {@link ItemRobotPart#getMaxDurability(net.minecraft.item.ItemStack)}.
 */
public enum RobotPartType {

    /** Mechanical arm, accepted by either of the two arm slots. */
    ARM(0, "arm", 400),
    /** Mechanical leg, accepted by either of the two leg slots. */
    LEG(1, "leg", 450),
    /** Built-in core, the only part the core slot accepts. */
    CORE(2, "core", 600);

    private static final RobotPartType[] VALUES = values();

    private final int metadata;
    private final String key;
    private final int maxDurability;

    RobotPartType(int metadata, String key, int maxDurability) {
        this.metadata = metadata;
        this.key = key;
        this.maxDurability = maxDurability;
    }

    /** Item metadata value that selects this kind. */
    public int getMetadata() {
        return metadata;
    }

    /** Lowercase key used for icons and language keys. */
    public String getKey() {
        return key;
    }

    /** Durability a brand new part of this kind carries; five of them add up to the body structure cap (R16). */
    public int getMaxDurability() {
        return maxDurability;
    }

    /** Translation key of the item name, for example {@code item.robot_part.arm.name}. */
    public String getUnlocalizedName() {
        return "item." + ItemRobotPart.ITEM_NAME + "." + key + ".name";
    }

    /** Icon name below {@code assets/mindrobot/textures/items}, without the namespace and the extension. */
    public String getItemIconName() {
        return ItemRobotPart.ITEM_NAME + "_" + key;
    }

    /**
     * Maps an item damage value to a part kind.
     *
     * @return the matching kind, or {@link #ARM} when the metadata is out of range (hand written stack, a part
     *         kind removed in a later version), so an unknown value never crashes the render path or a slot check
     */
    public static RobotPartType fromMeta(int metadata) {
        for (RobotPartType type : VALUES) {
            if (type.metadata == metadata) {
                return type;
            }
        }
        return ARM;
    }
}
