package com.github.qianwj.mindrobot.network;

/**
 * One entry of the radial menu. The server decides which entries exist for a player (D6), the client
 * only draws them and reports the chosen one back as a request.
 */
public enum MenuOption {

    /** Shown to a player wearing a remote helmet that is not driving anything. */
    ENTER_ROBOT(0, "gui.mindrobot.radial.enter_robot", LinkRequestMessage.ACTION_ENTER),
    /** Shown to a player that is currently driving a robot. */
    EXIT(1, "gui.mindrobot.radial.exit", LinkRequestMessage.ACTION_EXIT);

    private final byte id;
    private final String translationKey;
    private final byte requestAction;

    MenuOption(int id, String translationKey, byte requestAction) {
        this.id = (byte) id;
        this.translationKey = translationKey;
        this.requestAction = requestAction;
    }

    public byte getId() {
        return id;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public byte getRequestAction() {
        return requestAction;
    }

    public static MenuOption byId(byte id) {
        for (MenuOption option : values()) {
            if (option.id == id) {
                return option;
            }
        }
        return null;
    }

    public static byte[] toIds(MenuOption[] options) {
        if (options == null || options.length == 0) {
            return new byte[0];
        }
        byte[] ids = new byte[options.length];
        for (int i = 0; i < options.length; i++) {
            ids[i] = options[i].id;
        }
        return ids;
    }
}
