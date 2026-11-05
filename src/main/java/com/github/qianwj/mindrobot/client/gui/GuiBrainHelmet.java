package com.github.qianwj.mindrobot.client.gui;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.StatCollector;

import com.github.qianwj.mindrobot.container.ContainerBrainHelmet;

/**
 * Screen of the brain helmet chip slot (R32, D13).
 * <p>
 * The frame is painted with rectangles instead of a texture: the mod ships no container texture, and a
 * missing one would render as the purple checkerboard. The size and the layout are the standard chest ones -
 * 176x166, the single slot on top, then the player inventory - so the screen sits next to vanilla containers
 * without looking foreign. The slot positions come from the container, the screen only paints their recesses,
 * so the two never drift apart.
 */
public class GuiBrainHelmet extends GuiContainer {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 166;
    private static final int SLOT_BACKGROUND = 18;

    private static final int BORDER_COLOR = 0xFF000000;
    private static final int PANEL_COLOR = 0xFFC6C6C6;
    private static final int HIGHLIGHT_COLOR = 0xFFFFFFFF;
    private static final int SHADOW_COLOR = 0xFF555555;
    private static final int SLOT_FRAME_COLOR = 0xFF373737;
    private static final int SLOT_FILL_COLOR = 0xFF8B8B8B;
    private static final int LABEL_COLOR = 0x404040;

    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 6;
    private static final int INVENTORY_LABEL_Y = 72;

    public GuiBrainHelmet(ContainerBrainHelmet container) {
        super(container);
        this.xSize = PANEL_WIDTH;
        this.ySize = PANEL_HEIGHT;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int left = this.guiLeft;
        int top = this.guiTop;
        drawRect(left - 1, top - 1, left + this.xSize + 1, top + this.ySize + 1, BORDER_COLOR);
        drawRect(left, top, left + this.xSize, top + this.ySize, PANEL_COLOR);
        // One pixel bevel: light on the top and left edge, dark on the bottom and right edge.
        drawRect(left, top, left + this.xSize, top + 1, HIGHLIGHT_COLOR);
        drawRect(left, top, left + 1, top + this.ySize, HIGHLIGHT_COLOR);
        drawRect(left, top + this.ySize - 1, left + this.xSize, top + this.ySize, SHADOW_COLOR);
        drawRect(left + this.xSize - 1, top, left + this.xSize, top + this.ySize, SHADOW_COLOR);

        for (int i = 0; i < this.inventorySlots.inventorySlots.size(); ++i) {
            Slot slot = (Slot) this.inventorySlots.inventorySlots.get(i);
            drawSlotBackground(left + slot.xDisplayPosition - 1, top + slot.yDisplayPosition - 1);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj
            .drawString(StatCollector.translateToLocal("gui.mindrobot.helmet.title"), TITLE_X, TITLE_Y, LABEL_COLOR);
        fontRendererObj
            .drawString(StatCollector.translateToLocal("container.inventory"), TITLE_X, INVENTORY_LABEL_Y, LABEL_COLOR);
    }

    /** Draws the recessed square a vanilla slot has, so the empty chip slot still reads as a slot. */
    private void drawSlotBackground(int x, int y) {
        drawRect(x, y, x + SLOT_BACKGROUND, y + SLOT_BACKGROUND, SLOT_FRAME_COLOR);
        drawRect(x + 1, y + 1, x + SLOT_BACKGROUND - 1, y + SLOT_BACKGROUND - 1, SLOT_FILL_COLOR);
    }
}
