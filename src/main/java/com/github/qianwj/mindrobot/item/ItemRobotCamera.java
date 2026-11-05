package com.github.qianwj.mindrobot.item;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

import com.github.qianwj.mindrobot.MindRobot;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Replacement camera of a robot body (R33).
 * <p>
 * One item with two metadata values ({@link CameraType}) instead of two items: the two cameras share every
 * behaviour except the night vision they grant while driving, the robot slot tests them with a single
 * {@code instanceof}, and the helmet generations already use the metadata-subtype scheme, so the icon lookup,
 * the language keys and the tooltip stay one code path.
 * <p>
 * The camera is a container part, not an assembly-table part (R33): it is placed into the camera slot of the
 * robot container and swapped at any time. Nothing is written into the item itself - the installed camera is
 * simply the stack that currently sits in that slot, which is why a swap needs no bookkeeping at all.
 */
public class ItemRobotCamera extends Item {

    public static final String ITEM_NAME = "robot_camera";

    /** Item icons per kind, indexed by {@link CameraType#getMetadata()}; filled by {@link #registerIcons}. */
    private IIcon[] cameraIcons;

    public ItemRobotCamera() {
        setUnlocalizedName(ITEM_NAME);
        setTextureName(MindRobot.MODID + ":" + ITEM_NAME);
        setMaxStackSize(1);
        // The metadata is the camera kind, so the stack must not be damageable and must expose subtypes.
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        // The base texture stays registered as a fallback for any lookup that ignores the metadata.
        super.registerIcons(register);
        cameraIcons = new IIcon[CameraType.values().length];
        for (CameraType type : CameraType.values()) {
            cameraIcons[type.getMetadata()] = register.registerIcon(MindRobot.MODID + ":" + type.getItemIconName());
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        if (cameraIcons == null) {
            return super.getIconFromDamage(damage);
        }
        CameraType type = CameraType.byMetadata(damage);
        IIcon icon = cameraIcons[type.getMetadata()];
        return icon == null ? super.getIconFromDamage(damage) : icon;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        CameraType type = CameraType.byItemStack(stack);
        if (type != null) {
            String name = type.getUnlocalizedName();
            if (StatCollector.canTranslate(name)) {
                return StatCollector.translateToLocal(name);
            }
        }
        return StatCollector.translateToLocal(getUnlocalizedName() + ".name");
    }

    /** The thermal camera advertises the night vision it grants, the normal one advertises its plain optics. */
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        CameraType type = CameraType.byItemStack(stack);
        String key = type == CameraType.THERMAL ? "tooltip.mindrobot.camera.thermal"
            : "tooltip.mindrobot.camera.normal";
        tooltip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal(key));
        tooltip.add(
            EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("tooltip.mindrobot.camera.hint"));
    }
}
