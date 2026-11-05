package com.github.qianwj.mindrobot.event;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import com.github.qianwj.mindrobot.entity.EntityRobot;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Makes mobs attack robots (R18, D8).
 * <p>
 * Vanilla target selection only hunts {@code EntityPlayer}, so a robot is ignored by default. Two tasks are
 * needed, not one: {@code EntityAIAttackOnCollide} refuses a target whose class does not match its own
 * {@code classTarget} (the vanilla one is built against {@code EntityPlayer}), so a robot would be selected and
 * then never attacked. Every {@link EntityMob} that joins the world therefore gets both a robot target task and
 * a robot attack task, and a robot that joins the world back-fills the mobs already loaded around it.
 * <p>
 * On top of that a throttled scan hands robots to creatures that never registered a vanilla target task but do
 * read {@code getAttackTarget()}.
 * <p>
 * Passive hierarchies ({@link EntityAnimal}, {@link EntityAmbientCreature}) are skipped: they also extend
 * {@code EntityCreature}, and cows or bats must not hunt robots.
 */
public class RobotTargetingHandler {

    private static final int TARGET_TASK_PRIORITY = 0;
    private static final int ATTACK_TASK_PRIORITY = 0;
    private static final int TARGET_CHANCE = 0;
    private static final boolean TARGET_CHECK_SIGHT = true;
    private static final double ATTACK_SPEED = 1.0D;
    private static final boolean ATTACK_LONG_MEMORY = false;
    private static final double BACKFILL_RADIUS = 32.0D;
    private static final double AGGRO_SCAN_RADIUS = 32.0D;
    private static final double DEFAULT_FOLLOW_RANGE = 16.0D;
    private static final int AGGRO_SCAN_INTERVAL_TICKS = 20;

    private int tickCounter;

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.world == null || event.world.isRemote || event.entity == null) {
            return;
        }
        Entity entity = event.entity;
        if (entity instanceof EntityRobot) {
            injectNearbyMobs((EntityRobot) entity);
        } else if (entity instanceof EntityMob) {
            inject((EntityMob) entity);
        }
    }

    /** Throttled aggro scan for creatures that only read {@code getAttackTarget()}. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++tickCounter < AGGRO_SCAN_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.worldServers == null) {
            return;
        }
        for (WorldServer world : server.worldServers) {
            if (world != null) {
                scanWorld(world);
            }
        }
    }

    private void scanWorld(WorldServer world) {
        Object[] loaded = world.loadedEntityList.toArray();
        for (Object candidate : loaded) {
            if (!(candidate instanceof EntityRobot)) {
                continue;
            }
            EntityRobot robot = (EntityRobot) candidate;
            if (robot.isDead || !robot.isEntityAlive()) {
                continue;
            }
            List<EntityCreature> nearby = world.selectEntitiesWithinAABB(
                EntityCreature.class,
                robot.boundingBox.expand(AGGRO_SCAN_RADIUS, AGGRO_SCAN_RADIUS, AGGRO_SCAN_RADIUS),
                null);
            for (EntityCreature creature : nearby) {
                tryAggro(creature, robot);
            }
        }
    }

    private void tryAggro(EntityCreature creature, EntityRobot robot) {
        if (!isEligible(creature) || creature.isDead || creature.getAttackTarget() != null) {
            return;
        }
        if (!isWithinFollowRange(creature, robot) || !creature.canEntityBeSeen(robot)) {
            return;
        }
        creature.setAttackTarget(robot);
    }

    private boolean isWithinFollowRange(EntityCreature creature, EntityRobot robot) {
        IAttributeInstance followRange = creature.getEntityAttribute(SharedMonsterAttributes.followRange);
        double range = followRange == null ? DEFAULT_FOLLOW_RANGE : followRange.getAttributeValue();
        return creature.getDistanceSqToEntity(robot) <= range * range;
    }

    private void injectNearbyMobs(EntityRobot robot) {
        List<EntityMob> nearby = robot.worldObj.selectEntitiesWithinAABB(
            EntityMob.class,
            robot.boundingBox.expand(BACKFILL_RADIUS, BACKFILL_RADIUS, BACKFILL_RADIUS),
            null);
        for (EntityMob mob : nearby) {
            inject(mob);
        }
    }

    private void inject(EntityMob mob) {
        if (mob.targetTasks == null || mob.tasks == null) {
            return;
        }
        if (!hasTask(mob.targetTasks, RobotTargetTask.class)) {
            mob.targetTasks.addTask(TARGET_TASK_PRIORITY, new RobotTargetTask(mob));
        }
        if (!hasTask(mob.tasks, RobotAttackTask.class)) {
            mob.tasks.addTask(ATTACK_TASK_PRIORITY, new RobotAttackTask(mob));
        }
    }

    /**
     * Marker based dedup: the injected tasks are subclasses, so they can be told apart from the vanilla player
     * targeting tasks of the same mob. Dedup is per instance on purpose, vanilla rebuilds the task lists of
     * every mob that is loaded from disk.
     */
    private boolean hasTask(EntityAITasks tasks, Class<?> marker) {
        for (EntityAITasks.EntityAITaskEntry entry : tasks.taskEntries) {
            if (marker.isInstance(entry.action)) {
                return true;
            }
        }
        return false;
    }

    private boolean isEligible(Entity entity) {
        return entity instanceof EntityCreature && !(entity instanceof EntityAnimal)
            && !(entity instanceof EntityAmbientCreature);
    }

    /** Selects robots as targets. */
    private static final class RobotTargetTask extends EntityAINearestAttackableTarget {

        RobotTargetTask(EntityCreature owner) {
            super(owner, EntityRobot.class, TARGET_CHANCE, TARGET_CHECK_SIGHT);
        }
    }

    /** Chases and hits robots; without it the mob would hold a robot target and never attack. */
    private static final class RobotAttackTask extends EntityAIAttackOnCollide {

        RobotAttackTask(EntityCreature owner) {
            super(owner, EntityRobot.class, ATTACK_SPEED, ATTACK_LONG_MEMORY);
        }
    }
}
