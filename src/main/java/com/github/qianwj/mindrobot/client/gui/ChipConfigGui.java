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
 * Frequency chip setup screen (R21, R22, D5).
 * <p>
 * The mode decides which fields are shown: a public channel has one numeric field, a private channel has a
 * name field and a password field. Confirming only sends {@link ChipConfigMessage}: the NBT of the held chip
 * is written by the server, never by this screen (D2). The current channel is read from the client copy of
 * the stack just to prefill the fields; the stored password is never read back into the screen, so changing
 * a private channel always means typing the password again.
 * <p>
 * The password field is a normal {@link GuiTextField} that is drawn and then covered with the field
 * background plus one asterisk per character. That is a purely visual mask: the value itself is stored and
 * sent in plain text, which is what a server operator needs to repair a forgotten password by hand.
 */
public class ChipConfigGui extends GuiScreen {

    private static final int BUTTON_MODE = 0;
    private static final int BUTTON_CONFIRM = 1;
    private static final int BUTTON_CANCEL = 2;

    private static final int FIELD_WIDTH = 180;
    private static final int FIELD_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int HALF_BUTTON_WIDTH = 86;
    private static final int LABEL_OFFSET = 11;
    private static final int ROW_GAP = 9;

    private static final int TITLE_COLOR = 0xFFFFFF;
    private static final int LABEL_COLOR = 0xA0A0A0;
    private static final int ERROR_COLOR = 0xFF5555;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int FIELD_BACKGROUND = 0xFF000000;

    private final ChannelData initialChannel;
    private GuiTextField channelField;
    private GuiTextField nameField;
    private GuiTextField passwordField;
    private GuiButton modeButton;
    private GuiButton confirmButton;
    private GuiButton cancelButton;
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
        privateMode = initialChannel == null || initialChannel.isPrivate();
        int left = width / 2 - FIELD_WIDTH / 2;
        int top = height / 2 - 86;

        channelField = new GuiTextField(fontRendererObj, left, top, FIELD_WIDTH, FIELD_HEIGHT);
        channelField.setMaxStringLength(4);
        channelField.setText(
            initialChannel != null && initialChannel.isPublic() ? String.valueOf(initialChannel.getChannel()) : "");

        nameField = new GuiTextField(fontRendererObj, left, top, FIELD_WIDTH, FIELD_HEIGHT);
        nameField.setMaxStringLength(ChannelData.MAX_NAME_LENGTH);
        nameField.setText(initialChannel != null && initialChannel.isPrivate() ? initialChannel.getName() : "");

        passwordField = new GuiTextField(fontRendererObj, left, top, FIELD_WIDTH, FIELD_HEIGHT);
        passwordField.setMaxStringLength(ChannelData.MAX_PASSWORD_LENGTH);
        // Deliberately not prefilled: the stored password must not be shown back on screen.

        modeButton = new GuiButton(BUTTON_MODE, left, top + 18, FIELD_WIDTH, BUTTON_HEIGHT, modeLabel());
        confirmButton = new GuiButton(
            BUTTON_CONFIRM,
            left,
            top,
            HALF_BUTTON_WIDTH,
            BUTTON_HEIGHT,
            StatCollector.translateToLocal("gui.mindrobot.chip.confirm"));
        cancelButton = new GuiButton(
            BUTTON_CANCEL,
            left + FIELD_WIDTH - HALF_BUTTON_WIDTH,
            top,
            HALF_BUTTON_WIDTH,
            BUTTON_HEIGHT,
            StatCollector.translateToLocal("gui.mindrobot.chip.cancel"));
        buttonList.add(modeButton);
        buttonList.add(confirmButton);
        buttonList.add(cancelButton);

        layout();
        focusInitialField();
    }

    /** Places the fields of the active mode and the button row below them. */
    private void layout() {
        int left = width / 2 - FIELD_WIDTH / 2;
        int formTop = modeButton.yPosition + BUTTON_HEIGHT + 14;

        channelField.xPosition = left;
        channelField.yPosition = formTop + LABEL_OFFSET;
        nameField.xPosition = left;
        nameField.yPosition = formTop + LABEL_OFFSET;
        passwordField.xPosition = left;
        passwordField.yPosition = formTop + 2 * (LABEL_OFFSET + FIELD_HEIGHT) + ROW_GAP;

        int buttonsTop = privateMode ? passwordField.yPosition + FIELD_HEIGHT + 14
            : channelField.yPosition + FIELD_HEIGHT + 14;
        confirmButton.xPosition = left;
        confirmButton.yPosition = buttonsTop;
        cancelButton.xPosition = left + FIELD_WIDTH - HALF_BUTTON_WIDTH;
        cancelButton.yPosition = buttonsTop;

        channelField.setVisible(!privateMode);
        nameField.setVisible(privateMode);
        passwordField.setVisible(privateMode);
    }

    private void focusInitialField() {
        channelField.setFocused(!privateMode);
        nameField.setFocused(privateMode);
        passwordField.setFocused(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("gui.mindrobot.chip.title"),
            width / 2,
            height / 2 - 106,
            TITLE_COLOR);
        if (privateMode) {
            drawString(
                fontRendererObj,
                StatCollector.translateToLocal("gui.mindrobot.chip.name"),
                nameField.xPosition,
                nameField.yPosition - 10,
                LABEL_COLOR);
            nameField.drawTextBox();
            drawString(
                fontRendererObj,
                StatCollector.translateToLocal("gui.mindrobot.chip.password"),
                passwordField.xPosition,
                passwordField.yPosition - 10,
                LABEL_COLOR);
            drawMaskedPasswordField();
        } else {
            drawString(
                fontRendererObj,
                StatCollector.translateToLocal("gui.mindrobot.chip.channel"),
                channelField.xPosition,
                channelField.yPosition - 10,
                LABEL_COLOR);
            channelField.drawTextBox();
        }
        if (errorText != null) {
            drawCenteredString(fontRendererObj, errorText, width / 2, confirmButton.yPosition + 26, ERROR_COLOR);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /**
     * Draws the password field, then repaints its interior so the real characters are never visible and one
     * asterisk per character is shown instead.
     */
    private void drawMaskedPasswordField() {
        passwordField.drawTextBox();
        int x = passwordField.xPosition;
        int y = passwordField.yPosition;
        int width = passwordField.width;
        int height = passwordField.height;
        drawRect(x + 1, y + 1, x + width - 1, y + height - 1, FIELD_BACKGROUND);

        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < passwordField.getText()
            .length(); ++i) {
            stars.append('*');
        }
        String masked = fontRendererObj.trimStringToWidth(stars.toString(), width - 12);
        int textY = y + (height - 8) / 2;
        fontRendererObj.drawStringWithShadow(masked, x + 4, textY, TEXT_COLOR);
        if (passwordField.isFocused()) {
            // The real caret is hidden by the mask, so show a caret at the end of the masked text.
            int caretX = Math.min(x + 4 + fontRendererObj.getStringWidth(masked), x + width - 8);
            fontRendererObj.drawStringWithShadow("_", caretX, textY, TEXT_COLOR);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (privateMode) {
            nameField.mouseClicked(mouseX, mouseY, mouseButton);
            passwordField.mouseClicked(mouseX, mouseY, mouseButton);
        } else {
            channelField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            confirm();
            return;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            cycleFocus();
            return;
        }
        GuiTextField field = focusedField();
        if (field == null) {
            super.keyTyped(typedChar, keyCode);
            return;
        }
        // Printable characters are filtered per field before the widget sees them: the channel number takes
        // digits only, the private name and password take any printable character (CJK included). Editing
        // keys and shortcuts (backspace, arrows, paste) pass through untouched.
        if (isPrintable(typedChar) && !accepts(field, typedChar)) {
            return;
        }
        if (field.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private static boolean isPrintable(char typedChar) {
        return typedChar >= 32 && typedChar != 127 && typedChar != 167;
    }

    private boolean accepts(GuiTextField field, char typedChar) {
        if (field == channelField) {
            return Character.isDigit(typedChar);
        }
        return true;
    }

    private GuiTextField focusedField() {
        if (!privateMode) {
            return channelField;
        }
        if (nameField.isFocused()) {
            return nameField;
        }
        if (passwordField.isFocused()) {
            return passwordField;
        }
        return null;
    }

    private void cycleFocus() {
        if (!privateMode) {
            channelField.setFocused(true);
            return;
        }
        boolean nameHadFocus = nameField.isFocused();
        nameField.setFocused(!nameHadFocus);
        passwordField.setFocused(nameHadFocus);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_MODE) {
            privateMode = !privateMode;
            errorText = null;
            modeButton.displayString = modeLabel();
            layout();
            focusInitialField();
        } else if (button.id == BUTTON_CONFIRM) {
            confirm();
        } else if (button.id == BUTTON_CANCEL) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void updateScreen() {
        channelField.updateCursorCounter();
        nameField.updateCursorCounter();
        passwordField.updateCursorCounter();
        super.updateScreen();
    }

    private String modeLabel() {
        return StatCollector
            .translateToLocal(privateMode ? "gui.mindrobot.chip.mode_private" : "gui.mindrobot.chip.mode_public");
    }

    /** Validates the active mode locally so obviously invalid input never reaches the server. */
    private void confirm() {
        if (privateMode) {
            String name = nameField.getText()
                .trim();
            String password = passwordField.getText();
            if (!ChannelData.isValidName(name) || !ChannelData.isValidPassword(password)) {
                errorText = StatCollector.translateToLocal("gui.mindrobot.chip.invalid_private");
                return;
            }
            ModNetwork.sendToServer(new ChipConfigMessage(ChannelMode.PRIVATE, -1, name, password));
        } else {
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
            ModNetwork.sendToServer(new ChipConfigMessage(ChannelMode.PUBLIC, channel, null, null));
        }
        // Do not keep the typed plain text password in the screen state after it has been handed to the packet.
        passwordField.setText("");
        mc.displayGuiScreen(null);
    }
}
