package com.github.qianwj.mindrobot.container;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import com.github.qianwj.mindrobot.client.gui.GuiRobotContainer;
import com.github.qianwj.mindrobot.entity.EntityRobot;
import com.github.qianwj.mindrobot.link.RobotLinkManager;
import com.github.qianwj.mindrobot.registry.ModContent;

import cpw.mods.fml.common.network.IGuiHandler;

/**
 * FML bridge for the robot item container (R35 / D15).
 * <p>
 * A robot is not a tile entity, so {@code x} carries the entity id instead of a block coordinate; the server
 * resolves it back to the loaded {@link EntityRobot} with {@link World#getEntityByID(int)} and refuses to open a
 * screen for an id that no longer exists. {@code y} and {@code z} are unused.
 * <p>
 * <b>Opening rule.</b> A player who is driving a robot gets no machine screen at all - the same rule that hides
 * the body inventory while driving (R10). A robot that is being driven by anyone is refused too, so a
 * bystander cannot empty a machine that is in the middle of a drive; a parked robot is open to everyone.
 */
public class RobotGuiHandler implements IGuiHandler {

    /** GUI id registered with {@code NetworkRegistry}; the helmet screen owns 0. */
    public static final int GUI_ID = 1;

    private static final int UNUSED = 0;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != GUI_ID || player == null || world == null) {
            return null;
        }
        EntityRobot robot = findRobot(world, x);
        if (!canOpen(player, robot)) {
            return null;
        }
        return new ContainerRobot(player, robot);
    }

    /**
     * The client half trusts the server: it was the server that decided to open the screen, and refusing here
     * would leave the player without a screen while the container stays open. Only a missing entity is fatal -
     * without the entity there is nothing to build the slots from.
     */
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != GUI_ID || player == null || world == null) {
            return null;
        }
        EntityRobot robot = findRobot(world, x);
        if (robot == null) {
            return null;
        }
        return new GuiRobotContainer(new ContainerRobot(player, robot));
    }

    /**
     * Opens the robot container on the server; FML tells the client which screen to build.
     *
     * @return true when the open request was handed to FML
     */
    public static boolean open(EntityPlayer player, EntityRobot robot) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return false;
        }
        if (!canOpen(player, robot)) {
            return false;
        }
        Object mod = ModContent.modInstance();
        if (mod == null) {
            return false;
        }
        player.openGui(mod, GUI_ID, player.worldObj, robot.getEntityId(), UNUSED, UNUSED);
        return true;
    }

    /**
     * Server side gate: the robot must be alive in this world, the player must not be driving (R10) and the
     * robot must not be driven by anyone else (a machine under control stays sealed during the drive).
     */
    public static boolean canOpen(EntityPlayer player, EntityRobot robot) {
        if (player == null || robot == null || robot.worldObj == null || robot.worldObj != player.worldObj) {
            return false;
        }
        if (robot.isDead || !robot.isEntityAlive()) {
            return false;
        }
        if (RobotLinkManager.isDriving(player)) {
            return false;
        }
        return !RobotLinkManager.isRobotDriven(robot);
    }

    /** Resolves the entity id carried by the open request; anything that is not a robot is a miss. */
    private static EntityRobot findRobot(World world, int entityId) {
        Entity entity = world.getEntityByID(entityId);
        return entity instanceof EntityRobot ? (EntityRobot) entity : null;
    }
}
