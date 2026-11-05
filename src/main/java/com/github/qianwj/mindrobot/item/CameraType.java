package com.github.qianwj.mindrobot.item;

import net.minecraft.item.ItemStack;

/**
 * The two camera kinds a robot can carry (R33).
 * <p>
 * Both are the same item and only differ in the item metadata, the very same scheme the helmet generations
 * use: adding a camera means adding one constant here plus one icon, never another item registration. The
 * camera slot of the robot container accepts either kind, so swapping a camera is an ordinary item swap in the
 * container and takes effect on the next tick - there is no separate install step and no state machine.
 */
public enum CameraType {

    /** Plain optics: the picture is exactly what the driver would see standing there. */
    NORMAL(0, "normal"),
    /** Thermal optics: driving the robot grants night vision (R33). */
    THERMAL(1, "thermal");

    private static final CameraType[] VALUES = values();

    private final int metadata;
    private final String key;

    CameraType(int metadata, String key) {
        this.metadata = metadata;
        this.key = key;
    }

    /** Item metadata value that selects this kind. */
    public int getMetadata() {
        return metadata;
    }

    /** Lowercase key used for icons and language keys. */
    public String getKey() {
        return key;
    }

    /** Translation key of the item name, for example {@code item.robot_camera.thermal.name}. */
    public String getUnlocalizedName() {
        return "item." + ItemRobotCamera.ITEM_NAME + "." + key + ".name";
    }

    /** Icon name below {@code assets/mindrobot/textures/items}, without the namespace and the extension. */
    public String getItemIconName() {
        return ItemRobotCamera.ITEM_NAME + "_" + key;
    }

    /**
     * Maps an item damage value to a camera kind.
     *
     * @return the matching kind, or {@link #NORMAL} for an out of range value, so a damaged or hand written
     *         stack never breaks the render path
     */
    public static CameraType byMetadata(int metadata) {
        for (CameraType type : VALUES) {
            if (type.metadata == metadata) {
                return type;
            }
        }
        return NORMAL;
    }

    /**
     * Camera kind of a stack.
     *
     * @return the kind, or {@code null} when the stack is empty or not a robot camera
     */
    public static CameraType byItemStack(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemRobotCamera)) {
            return null;
        }
        return byMetadata(stack.getItemDamage());
    }
}
