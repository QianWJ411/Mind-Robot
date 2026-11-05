package com.github.qianwj.mindrobot.client;

import java.util.ArrayList;
import java.util.List;

import com.github.qianwj.mindrobot.network.MenuOption;

/**
 * Client side mirror of the server connection state.
 * <p>
 * It is filled by {@code LinkStateHandler} on the network thread and read by the camera and the radial
 * menu on the client thread, so the fields are volatile. Deliberately free of Minecraft client types:
 * the message handler that writes it is loaded on the dedicated server as well.
 */
public final class ClientLinkState {

    private static volatile boolean linked;
    private static volatile int robotEntityId = -1;
    private static volatile byte[] optionIds = new byte[0];

    private ClientLinkState() {}

    public static void update(boolean linkedIn, int robotEntityIdIn, byte[] optionIdsIn) {
        linked = linkedIn;
        robotEntityId = robotEntityIdIn;
        optionIds = optionIdsIn == null ? new byte[0] : optionIdsIn;
    }

    public static void reset() {
        update(false, -1, new byte[0]);
    }

    public static boolean isLinked() {
        return linked;
    }

    public static int getRobotEntityId() {
        return robotEntityId;
    }

    /** Menu entries the server allows right now, in server order. */
    public static List<MenuOption> getOptions() {
        List<MenuOption> options = new ArrayList<MenuOption>();
        for (byte id : optionIds) {
            MenuOption option = MenuOption.byId(id);
            if (option != null) {
                options.add(option);
            }
        }
        return options;
    }
}
