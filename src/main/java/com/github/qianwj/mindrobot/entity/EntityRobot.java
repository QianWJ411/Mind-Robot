package com.github.qianwj.mindrobot.entity;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.github.qianwj.mindrobot.api.IRobotCore;
import com.github.qianwj.mindrobot.damage.RobotDamage;
import com.github.qianwj.mindrobot.link.ChannelData;
import com.github.qianwj.mindrobot.link.RobotLinkManager;
import com.github.qianwj.mindrobot.link.RobotRegistry;

/**
 * Remote controlled robot body.
 * <p>
 * It extends {@link EntityLivingBase} because the client camera field
 * {@code Minecraft.renderViewEntity} is declared as {@code EntityLivingBase}, not {@code Entity}
 * (design decision D1).
 * <p>
 * Health is a disguise only: the vanilla health pool never changes and vanilla death handling is never
 * entered, so other mods cannot drain or resurrect the robot through living entity hooks. The real
 * durability is {@link #getStructuralIntegrity()} and is routed through {@link RobotDamage}.
 */
public class EntityRobot extends EntityLivingBase implements IRobotCore {

    private static final String NBT_STRUCTURAL_INTEGRITY = "StructuralIntegrity";

    /** How often the robot reports its own position into {@link RobotRegistry} (R27). 20 ticks = 1 second. */
    private static final int POSITION_REPORT_INTERVAL = 20;
    private static final int UNKNOWN_CHUNK = Integer.MIN_VALUE;

    /** Display only health value. Never used as durability. */
    private static final float DISGUISE_MAX_HEALTH = 20.0F;
    private static final double DEFAULT_MOVEMENT_SPEED = 0.1D;

    /** P0 placeholder cap, the assembly parts of P3 will sum their durability into this. */
    public static final int DEFAULT_MAX_STRUCTURAL_INTEGRITY = 100;

    private static final int EQUIPMENT_SLOTS = 5;
    private static final float DESTROY_EFFECT_HEIGHT_OFFSET = 0.5F;

    /**
     * Channel snapshot of this robot (D5): the channel number, the private/public mode and the owner that
     * configured it. {@code null} until an assembly table (P3) or {@code /mindrobot spawn} assigns one.
     */
    private ChannelData channelData;
    /** Forced true on every load from NBT, so the first tick after a restart re-registers the robot (R27). */
    private boolean positionReportPending = true;
    /** Chunk of the last report: a border crossing reports at once instead of waiting for the interval. */
    private int lastReportedChunkX = UNKNOWN_CHUNK;
    private int lastReportedChunkZ = UNKNOWN_CHUNK;
    /** Real durability of the chassis. */
    private int structuralIntegrity = DEFAULT_MAX_STRUCTURAL_INTEGRITY;

    public EntityRobot(World world) {
        super(world);
        setSize(0.6F, 1.8F);
        // Bypass the overridden no-op on purpose: this is the one place the disguise value is written.
        super.setHealth(DISGUISE_MAX_HEALTH);
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        // Registered by the superclass; set explicitly so other mods reading the attributes never NPE.
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(DISGUISE_MAX_HEALTH);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(DEFAULT_MOVEMENT_SPEED);
    }

    /** Disguise only, the real durability lives in {@link #structuralIntegrity}. */
    @Override
    public void setHealth(float health) {
        // Intentionally empty: the robot must never enter the vanilla death flow.
    }

    /**
     * Robot self reporting (R27 / D11): the machine, not the helmet, is the source of its own position.
     * <p>
     * A row is written every {@link #POSITION_REPORT_INTERVAL} ticks, immediately after a chunk border
     * crossing, and once on the first tick after the entity was loaded from disk (so a restart never leaves a
     * stale coordinate behind). Client side copies never report.
     */
    @Override
    public void onEntityUpdate() {
        super.onEntityUpdate();
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        reportPosition();
    }

    /** Writes the registry row when one of the three triggers fires; otherwise does nothing. */
    private void reportPosition() {
        int chunkX = MathHelper.floor_double(posX) >> 4;
        int chunkZ = MathHelper.floor_double(posZ) >> 4;
        boolean crossedChunk = chunkX != lastReportedChunkX || chunkZ != lastReportedChunkZ;
        boolean due = ticksExisted % POSITION_REPORT_INTERVAL == 0;
        if (!positionReportPending && !crossedChunk && !due) {
            return;
        }
        RobotRegistry registry = RobotRegistry.get(worldObj);
        if (registry == null) {
            return;
        }
        positionReportPending = false;
        lastReportedChunkX = chunkX;
        lastReportedChunkZ = chunkZ;
        registry.report(this);
    }

    @Override
    public ItemStack getHeldItem() {
        return null;
    }

    @Override
    public ItemStack getEquipmentInSlot(int slot) {
        return null;
    }

    @Override
    public void setCurrentItemOrArmor(int slot, ItemStack stack) {
        // The robot has no equipment slots in P0.
    }

    @Override
    public ItemStack[] getLastActiveItems() {
        return new ItemStack[EQUIPMENT_SLOTS];
    }

    /**
     * Routes every hit into {@link RobotDamage} instead of the vanilla damage pipeline.
     * {@code super.attackEntityFrom} is deliberately not called: no hurt event, no death event.
     */
    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (worldObj == null || worldObj.isRemote || isDead || source == null) {
            return false;
        }
        int integrityBefore = structuralIntegrity;
        applyStructuralDamage(source, amount);
        return isDead || structuralIntegrity < integrityBefore;
    }

    /**
     * Applies classified damage to the chassis.
     *
     * @return the remaining structural integrity
     */
    @Override
    public int applyStructuralDamage(DamageSource source, float amount) {
        if (worldObj == null || worldObj.isRemote || isDead) {
            return structuralIntegrity;
        }
        float adjusted = RobotDamage.apply(source, amount);
        if (adjusted <= 0.0F) {
            return structuralIntegrity;
        }
        if (Float.isInfinite(adjusted)) {
            destroyRobot();
            return structuralIntegrity;
        }
        structuralIntegrity -= Math.max(1, MathHelper.ceiling_float_int(adjusted));
        if (structuralIntegrity <= 0) {
            structuralIntegrity = 0;
            destroyRobot();
        }
        return structuralIntegrity;
    }

    @Override
    public int getStructuralIntegrity() {
        return structuralIntegrity;
    }

    @Override
    public int getMaxStructuralIntegrity() {
        return DEFAULT_MAX_STRUCTURAL_INTEGRITY;
    }

    @Override
    public boolean isRobot() {
        return true;
    }

    /**
     * Ends the chassis: effect, death flag and the connection teardown. Never touches the vanilla death
     * path, so no living death event is fired for the robot.
     */
    public void destroyRobot() {
        if (isDead) {
            return;
        }
        structuralIntegrity = 0;
        if (!worldObj.isRemote) {
            double effectY = posY + height * DESTROY_EFFECT_HEIGHT_OFFSET;
            worldObj.playSoundEffect(posX, effectY, posZ, "random.explode", 1.0F, 1.0F);
            worldObj.spawnParticle("largeexplode", posX, effectY, posZ, 0.0D, 0.0D, 0.0D);
            RobotLinkManager.notifyRobotDestroyed(this);
            // Unregister last: the disconnect above may re-report the still loaded robot, and a destroyed
            // machine must not stay a wake-up target (R27).
            RobotRegistry registry = RobotRegistry.get(worldObj);
            if (registry != null) {
                registry.remove(getUniqueID());
            }
        }
        setDead();
    }

    /** Safety net: even a direct {@code onDeath} call is redirected away from the vanilla pipeline. */
    @Override
    public void onDeath(DamageSource source) {
        destroyRobot();
    }

    /** Channel snapshot this robot answers on, or {@code null} when it has none (D5). */
    public ChannelData getChannelData() {
        return channelData;
    }

    public void setChannelData(ChannelData channelData) {
        this.channelData = channelData;
        // A chip installed after spawning must be visible in the registry on the next tick, not one second
        // later (R26 vs R27: the row only exists for a robot that answers on a channel).
        positionReportPending = true;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setInteger(NBT_STRUCTURAL_INTEGRITY, structuralIntegrity);
        if (channelData != null) {
            channelData.writeToNBT(tag);
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        structuralIntegrity = tag.hasKey(NBT_STRUCTURAL_INTEGRITY)
            ? MathHelper.clamp_int(tag.getInteger(NBT_STRUCTURAL_INTEGRITY), 1, getMaxStructuralIntegrity())
            : getMaxStructuralIntegrity();
        channelData = ChannelData.readFromNBT(tag);
        // The registry row is rewritten on the first tick after a load, so a restart never leaves the old
        // coordinate (R27) and the helmet mirror can be refreshed from a fresh row.
        positionReportPending = true;
        lastReportedChunkX = UNKNOWN_CHUNK;
        lastReportedChunkZ = UNKNOWN_CHUNK;
    }
}
