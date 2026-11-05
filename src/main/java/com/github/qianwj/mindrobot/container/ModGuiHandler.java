package com.github.qianwj.mindrobot.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.IGuiHandler;

/**
 * The single FML gui handler of the mod.
 * <p>
 * {@code NetworkRegistry.registerGuiHandler} keeps exactly one handler per mod container (the registry is a
 * map keyed by the {@code ModContainer}), so a second registration silently replaces the first. Every screen of
 * the mod therefore goes through this dispatcher, which routes the GUI id to the handler that owns it: the
 * brain helmet chip slot (id 0, R32) and the robot item container (id 1, R35).
 */
public class ModGuiHandler implements IGuiHandler {

    private final IGuiHandler brainHelmet = new BrainHelmetGuiHandler();
    private final IGuiHandler robot = new RobotGuiHandler();

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        IGuiHandler handler = handlerFor(id);
        return handler == null ? null : handler.getServerGuiElement(id, player, world, x, y, z);
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        IGuiHandler handler = handlerFor(id);
        return handler == null ? null : handler.getClientGuiElement(id, player, world, x, y, z);
    }

    private IGuiHandler handlerFor(int id) {
        if (id == BrainHelmetGuiHandler.GUI_ID) {
            return brainHelmet;
        }
        if (id == RobotGuiHandler.GUI_ID) {
            return robot;
        }
        return null;
    }
}
