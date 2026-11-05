package com.github.qianwj.mindrobot.item;

import net.minecraft.item.ItemStack;

/**
 * Upgrade generations of the brain computer helmet (R29/R30).
 * <p>
 * All generations are the same item and only differ in the item metadata, so adding a generation means adding one
 * constant here plus one set of textures, never a new item registration. The tier owns everything that varies per
 * generation: display name, item icon, armor texture and the default strength of the driving overlay. The
 * brightness the player actually sees is read from {@code Config}, so the values below are only the defaults the
 * configuration file is seeded with.
 */
public enum HelmetTier {

    /** Entry level gear: the noisiest overlay, the picture is still readable. */
    MK1(0, "mk1", "MKI", 0.35F, 0.06F),
    /** Upgraded optics: fine grain that never hides the terrain. */
    MK2(1, "mk2", "MKII", 0.15F, 0.025F),
    /** Best generation: almost clean, only a rare flicker while driving. */
    MK3(2, "mk3", "MKIII", 0.04F, 0.005F);

    private static final HelmetTier[] VALUES = values();

    private final int metadata;
    private final String key;
    private final String displaySuffix;
    private final float defaultNoiseAlpha;
    private final float defaultGlitchChance;

    HelmetTier(int metadata, String key, String displaySuffix, float defaultNoiseAlpha, float defaultGlitchChance) {
        this.metadata = metadata;
        this.key = key;
        this.displaySuffix = displaySuffix;
        this.defaultNoiseAlpha = defaultNoiseAlpha;
        this.defaultGlitchChance = defaultGlitchChance;
    }

    /** Item metadata value that selects this generation. */
    public int getMetadata() {
        return metadata;
    }

    /** Lowercase key used for textures, language keys and configuration entries. */
    public String getKey() {
        return key;
    }

    /** Latin numeral appended to the item name when no localized tier name exists. */
    public String getDisplaySuffix() {
        return displaySuffix;
    }

    /** Default overlay opacity, seeded into {@code Config.noiseMk*} on first launch. */
    public float getDefaultNoiseAlpha() {
        return defaultNoiseAlpha;
    }

    /**
     * Default chance of starting a glitch band, expressed per rendered frame at 60 FPS. The overlay rescales it by
     * the real frame time so the visible frequency does not depend on the player's frame rate.
     */
    public float getDefaultGlitchChance() {
        return defaultGlitchChance;
    }

    /** Translation key of the item name, for example {@code item.remote_helmet.mk1.name}. */
    public String getUnlocalizedName() {
        return "item.remote_helmet." + key + ".name";
    }

    /** Icon name below {@code assets/mindrobot/textures/items}, without the namespace and the extension. */
    public String getItemIconName() {
        return "remote_helmet_" + key;
    }

    /**
     * Armor texture path below {@code assets/mindrobot}. Only layer 1 is used by a helmet, layer 2 exists so the
     * generation is complete if the same material is reused for leggings later.
     */
    public String getArmorTexturePath(int layer) {
        return "textures/models/armor/brain_helmet_" + key + "_layer_" + (layer == 2 ? 2 : 1) + ".png";
    }

    /**
     * Maps an item damage value to a generation.
     *
     * @return the matching generation, or {@link #MK1} when the metadata is out of range (damaged NBT, future
     *         generations after a downgrade), so an unknown value never crashes the render path
     */
    public static HelmetTier byMetadata(int metadata) {
        for (HelmetTier tier : VALUES) {
            if (tier.metadata == metadata) {
                return tier;
            }
        }
        return MK1;
    }

    /**
     * Generation of a helmet stack.
     *
     * @return the generation of the stack, or {@link #MK1} when the stack is null or not a brain computer helmet
     */
    public static HelmetTier byItemStack(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemRemoteHelmet)) {
            return MK1;
        }
        return byMetadata(stack.getItemDamage());
    }
}
