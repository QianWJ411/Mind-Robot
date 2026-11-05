package com.github.qianwj.mindrobot.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import com.github.qianwj.mindrobot.link.ChannelData;
import com.github.qianwj.mindrobot.link.ChannelMode;
import com.github.qianwj.mindrobot.network.ChipConfigMessage;
import com.github.qianwj.mindrobot.network.ModNetwork;

/**
 * Frequency chip setup screen (R21, R22).
 * <p>
 * A numeric text field plus a private/public toggle. Confirming only sends {@link ChipConfigMessage}: the
 * NBT of the held chip is written by the server, never by this screen (D2). The current channel is read
 * from the client copy of the stack just to prefill the field.
 */
public class ChipConfigGui extends GuiScreen {

    private static final int BUTTON_MODE = 0;
    private static final int BUTTON_CONFIRM = 1;
    private static final int BUTTON_CANCEL = 2;

    private static final int FIELD_WIDTH = 120;
    private static final int FIELD_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 20;
    private static final int HALF_BUTTON_WIDTH = 58;
    private static final int ROW_GAP = 6;

    private static final int TITLE_COLOR = 0xFFFFFF;
    private static final int LABEL_COLOR = 0xA0A0A0;
    private static final int ERROR_COLOR = 0xFF5555;

    private final ChannelData initialChannel;
    private GuiTextField channelField;
    private boolean privateMode;
    private String errorText;

    /**
     * @param initialChannel the channel currently on the chip, or {@code null} when unconfigured
     */
    public ChipConfigGui(ChannelData initialChannel) {
        this.initialChannel = initialChannel;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int left = width / 2 - FIELD_WIDTH / 2;
        int fieldTop = height / 2 - FIELD_HEIGHT;

        channelField = new GuiTextField(fontRendererObj, left, fieldTop, FIELD_WIDTH, FIELD_HEIGHT);
        channelField.setMaxStringLength(4);
        channelField.setText(initialChannel == null ? "" : String.valueOf(initialChannel.getChannel()));
        channelField.setFocused(true);

        privateMode = initialChannel == null || initialChannel.isPrivate();
        buttonList.add(
            new GuiButton(
                BUTTON_MODE,
                left,
                fieldTop + FIELD_HEIGHT + ROW_GAP,
                BUTTON_WIDTH,
                BUTTON_HEIGHT,
                modeLabel()));
        buttonList.add(
            new GuiButton(
                BUTTON_CONFIRM,
                left,
                fieldTop + FIELD_HEIGHT + ROW_GAP + BUTTON_HEIGHT + ROW_GAP,
                HALF_BUTTON_WIDTH,
                BUTTON_HEIGHT,
                StatCollector.translateToLocal("gui.mindrobot.chip.confirm")));
        buttonList.add(
            new GuiButton(
                BUTTON_CANCEL,
                left + FIELD_WIDTH - HALF_BUTTON_WIDTH,
                fieldTop + FIELD_HEIGHT + ROW_GAP + BUTTON_HEIGHT + ROW_GAP,
                HALF_BUTTON_WIDTH,
                BUTTON_HEIGHT,
                StatCollector.translateToLocal("gui.mindrobot.chip.cancel")));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int centerY = height / 2;
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("gui.mindrobot.chip.title"),
            width / 2,
            centerY - 64,
            TITLE_COLOR);
        drawString(
            fontRendererObj,
            StatCollector.translateToLocal("gui.mindrobot.chip.channel"),
            width / 2 - FIELD_WIDTH / 2,
            centerY - FIELD_HEIGHT - 12,
            LABEL_COLOR);
        channelField.drawTextBox();
        if (errorText != null) {
            drawCenteredString(fontRendererObj, errorText, width / 2, centerY + 58, ERROR_COLOR);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        channelField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        // A channel number is digits only, so letters never reach the field and paste shortcuts are ignored.
        if (typedChar != 0 && !Character.isDigit(typedChar)) {
            return;
        }
        if (channelField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            confirm();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_MODE) {
            privateMode = !privateMode;
            button.displayString = modeLabel();
        } else if (button.id == BUTTON_CONFIRM) {
            confirm();
        } else if (button.id == BUTTON_CANCEL) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void updateScreen() {
        channelField.updateCursorCounter();
        super.updateScreen();
    }

    private String modeLabel() {
        return StatCollector
            .translateToLocal(privateMode ? "gui.mindrobot.chip.mode_private" : "gui.mindrobot.chip.mode_public");
    }

    /** Parses the field locally so obviously invalid input never reaches the server. */
    private void confirm() {
        String text = channelField.getText()
            .trim();
        int channel;
        try {
            channel = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            errorText = StatCollector.translateToLocal("gui.mindrobot.chip.invalid");
            return;
        }
        if (!ChannelData.isValidChannel(channel)) {
            errorText = StatCollector.translateToLocal("gui.mindrobot.chip.invalid");
            return;
        }
        ModNetwork.sendToServer(new ChipConfigMessage(channel, privateMode ? ChannelMode.PRIVATE : ChannelMode.PUBLIC));
        mc.displayGuiScreen(null);
    }
}
