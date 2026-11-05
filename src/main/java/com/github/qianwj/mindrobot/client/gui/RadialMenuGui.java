package com.github.qianwj.mindrobot.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.github.qianwj.mindrobot.client.key.KeyBindings;
import com.github.qianwj.mindrobot.network.LinkRequestMessage;
import com.github.qianwj.mindrobot.network.MenuOption;
import com.github.qianwj.mindrobot.network.ModNetwork;

/**
 * Self drawn radial menu (R3, D6). The ring and the sectors are painted with the tessellator, the mouse
 * angle picks a sector, and the chosen entry is sent to the server as a request. The client never changes
 * the connection state itself.
 */
public class RadialMenuGui extends GuiScreen {

    private static final double FULL_CIRCLE = 360.0D;
    /** Sectors start at twelve o'clock and grow clockwise. */
    private static final double START_ANGLE = -90.0D;
    private static final float INNER_RADIUS_RATIO = 0.14F;
    private static final float OUTER_RADIUS_RATIO = 0.28F;
    private static final int MIN_INNER_RADIUS = 16;
    private static final int MIN_RING_THICKNESS = 28;
    private static final int SECTOR_COLOR = 0x333333;
    private static final int SECTOR_COLOR_HOVERED = 0x5AA0E0;
    private static final int SECTOR_ALPHA = 170;
    private static final int SECTOR_ALPHA_HOVERED = 220;
    private static final int LABEL_COLOR = 0xFFFFFF;
    private static final int HINT_COLOR = 0x909090;

    private final List<MenuOption> options;
    private int centerX;
    private int centerY;
    private int innerRadius;
    private int outerRadius;
    private int hoveredIndex = -1;

    public RadialMenuGui(List<MenuOption> options) {
        this.options = options == null ? new ArrayList<MenuOption>() : new ArrayList<MenuOption>(options);
    }

    @Override
    public void initGui() {
        centerX = width / 2;
        centerY = height / 2;
        int base = Math.min(width, height);
        innerRadius = Math.max(MIN_INNER_RADIUS, (int) (base * INNER_RADIUS_RATIO));
        outerRadius = Math.max(innerRadius + MIN_RING_THICKNESS, (int) (base * OUTER_RADIUS_RATIO));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        hoveredIndex = findHoveredSector(mouseX, mouseY);

        for (int i = 0; i < options.size(); i++) {
            boolean hovered = i == hoveredIndex;
            drawSector(i, hovered ? SECTOR_COLOR_HOVERED : SECTOR_COLOR, hovered ? SECTOR_ALPHA_HOVERED : SECTOR_ALPHA);
            drawSectorLabel(
                i,
                StatCollector.translateToLocal(
                    options.get(i)
                        .getTranslationKey()));
        }

        if (options.isEmpty()) {
            drawCenteredString(
                fontRendererObj,
                StatCollector.translateToLocal("gui.mindrobot.radial.empty"),
                centerX,
                centerY - 4,
                HINT_COLOR);
        }
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("gui.mindrobot.radial.hint"),
            centerX,
            centerY + outerRadius + 12,
            HINT_COLOR);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return;
        }
        int index = findHoveredSector(mouseX, mouseY);
        if (index < 0 || index >= options.size()) {
            return;
        }
        ModNetwork.sendToServer(
            new LinkRequestMessage(
                options.get(index)
                    .getRequestAction()));
        mc.displayGuiScreen(null);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1 || keyCode == KeyBindings.OPEN_RADIAL_MENU.getKeyCode()) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * Maps a mouse position to the sector under it.
     *
     * @return sector index, or -1 when the mouse is inside the hole or outside the ring
     */
    private int findHoveredSector(int mouseX, int mouseY) {
        if (options.isEmpty()) {
            return -1;
        }
        double deltaX = mouseX - centerX;
        double deltaY = mouseY - centerY;
        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        if (distance < innerRadius || distance > outerRadius) {
            return -1;
        }
        double span = FULL_CIRCLE / options.size();
        double angle = Math.toDegrees(Math.atan2(deltaY, deltaX));
        double relative = (angle - (START_ANGLE - span / 2.0D)) % FULL_CIRCLE;
        if (relative < 0.0D) {
            relative += FULL_CIRCLE;
        }
        int index = (int) (relative / span);
        return index >= options.size() ? options.size() - 1 : index;
    }

    private void drawSector(int index, int color, int alpha) {
        if (options.isEmpty()) {
            return;
        }
        double span = FULL_CIRCLE / options.size();
        double start = START_ANGLE - span / 2.0D + index * span;
        int steps = Math.max(4, (int) Math.ceil(span / 6.0D));

        Tessellator tessellator = Tessellator.instance;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        tessellator.startDrawing(GL11.GL_TRIANGLE_STRIP);
        tessellator.setColorRGBA_I(color, alpha);
        for (int step = 0; step <= steps; step++) {
            double angle = Math.toRadians(start + span * step / steps);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            tessellator.addVertex(centerX + cos * innerRadius, centerY + sin * innerRadius, zLevel);
            tessellator.addVertex(centerX + cos * outerRadius, centerY + sin * outerRadius, zLevel);
        }
        tessellator.draw();
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private void drawSectorLabel(int index, String label) {
        double span = FULL_CIRCLE / options.size();
        double middle = Math.toRadians(START_ANGLE + index * span);
        int radius = (innerRadius + outerRadius) / 2;
        int labelX = centerX + (int) (Math.cos(middle) * radius);
        int labelY = centerY + (int) (Math.sin(middle) * radius) - 4;
        drawCenteredString(fontRendererObj, label, labelX, labelY, LABEL_COLOR);
    }
}
