package com.github.qianwj.mindrobot.client.key;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;

/**
 * Client key bindings. R opens the radial menu (R2, R11).
 */
public final class KeyBindings {

    public static final String CATEGORY = "key.categories.mindrobot";

    public static final KeyBinding OPEN_RADIAL_MENU = new KeyBinding(
        "key.mindrobot.radial_menu",
        Keyboard.KEY_R,
        CATEGORY);

    private KeyBindings() {}

    public static void init() {
        ClientRegistry.registerKeyBinding(OPEN_RADIAL_MENU);
    }
}
