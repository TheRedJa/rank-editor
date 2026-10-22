package com.theredja.rankeditor.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;

import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.feed_the_beast.ftblib.lib.gui.Theme;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;

/**
 * Modal dialog drawn on top of the editor (as its context-menu overlay, so the screen is never replaced): a title,
 * optional message lines, labelled text fields with per-field validation, and OK / Cancel. A message-only dialog is a
 * yes/no confirmation. The handler returns an error to keep the dialog open and show the message.
 */
@SideOnly(Side.CLIENT)
final class PromptDialog extends Panel {

    /** Receives the field values in order; returns an error message, or null to accept and close. */
    interface Handler {
        String accept(List<String> values);
    }

    private record Field(String label, InputBox box, Function<String, String> validator) {
    }

    private static final int WIDTH = 250;
    private static final int PAD = 8;
    private static final int LINE = 10;

    private final GuiRankEditor editor;
    private final String title;
    private final List<String> messages = new ArrayList<>();
    private final List<Field> fields = new ArrayList<>();
    private final TextActionButton ok;
    private final TextActionButton cancel;
    private Handler handler = values -> null;
    private String handlerError = "";
    private List<String> wrapped = List.of();
    private int fieldsTop;
    private int labelWidth;
    private int errorY;

    PromptDialog(GuiRankEditor editor, String title) {
        super(editor);
        this.editor = editor;
        this.title = title;
        this.ok = new TextActionButton(this, "OK", this::submit).enabled(this::isValid);
        this.cancel = new TextActionButton(this, "Cancel", editor::closeOverlay);
    }

    PromptDialog message(String... lines) {
        messages.addAll(List.of(lines));
        return this;
    }

    PromptDialog okLabel(String text) {
        ok.label(() -> text);
        return this;
    }

    PromptDialog onAccept(Handler action) {
        this.handler = action;
        return this;
    }

    /** Adds a field; {@code validator} returns an error message for a bad value or null for a good one. */
    PromptDialog field(String label, String initial, Function<String, String> validator) {
        int index = fields.size();
        InputBox box = new InputBox(this, null)
                .validator(text -> validator.apply(text) == null)
                .onChange(() -> handlerError = "")
                .onEnter(this::submit)
                .onTab(() -> focusField(index + 1));
        box.setSilently(initial);
        fields.add(new Field(label, box, validator));
        return this;
    }

    void open() {
        editor.openOverlay(this);
        focusField(0);
    }

    private void focusField(int index) {
        if (!fields.isEmpty()) {
            fields.get(Math.floorMod(index, fields.size())).box().focus();
        }
    }

    private String firstError() {
        for (Field field : fields) {
            String error = field.validator().apply(field.box().getText());
            if (error != null) {
                return field.box().getText().isEmpty() ? "" : error;
            }
        }
        return "";
    }

    private boolean isValid() {
        for (Field field : fields) {
            if (field.validator().apply(field.box().getText()) != null) {
                return false;
            }
        }
        return true;
    }

    private void submit() {
        if (!isValid()) {
            for (int i = 0; i < fields.size(); i++) {
                if (fields.get(i).validator().apply(fields.get(i).box().getText()) != null) {
                    focusField(i);
                    break;
                }
            }
            return;
        }
        List<String> values = new ArrayList<>();
        for (Field field : fields) {
            values.add(field.box().getText());
        }
        String error = handler.accept(values);
        if (error != null) {
            handlerError = error;
        } else if (editor.contextMenu == this) {
            editor.closeOverlay();
        }
    }

    @Override
    public void setX(int value) {
    }

    @Override
    public void setY(int value) {
    }

    @Override
    public void addWidgets() {
        for (Field field : fields) {
            add(field.box());
        }
        add(ok);
        add(cancel);
    }

    @Override
    public void alignWidgets() {
        Theme theme = getGui().getTheme();
        int inner = WIDTH - 2 * PAD;
        wrapped = new ArrayList<>();
        for (String message : messages) {
            wrapped.addAll(Ui.wrap(theme, message, inner));
        }
        labelWidth = 0;
        for (Field field : fields) {
            labelWidth = Math.max(labelWidth, theme.getStringWidth(field.label()) + 8);
        }
        int y = PAD + 14 + wrapped.size() * LINE + (wrapped.isEmpty() ? 0 : 4);
        fieldsTop = y;
        for (Field field : fields) {
            field.box().setPosAndSize(PAD + labelWidth, y, inner - labelWidth, 16);
            y += 20;
        }
        errorY = y + 1;
        y += fields.isEmpty() ? 0 : LINE + 6;
        ok.setPosAndSize(WIDTH - PAD - 150, y, 72, 16);
        cancel.setPosAndSize(WIDTH - PAD - 72, y, 72, 16);
        setSize(WIDTH, y + 16 + PAD);
        posX = (parent.width - width) / 2;
        posY = (parent.height - height) / 2;
    }

    @Override
    public void drawBackground(Theme theme, int x, int y, int w, int h) {
        Ui.drawFrame(x, y, w, h);
        Ui.text(theme, title, x + PAD, y + PAD, Ui.PENDING);
        int ty = y + PAD + 14;
        for (String line : wrapped) {
            Ui.text(theme, line, x + PAD, ty, Ui.TEXT);
            ty += LINE;
        }
        int fy = y + fieldsTop;
        for (Field field : fields) {
            Ui.text(theme, field.label(), x + PAD, fy + 4, Ui.MUTED);
            fy += 20;
        }
        String error = handlerError.isEmpty() ? firstError() : handlerError;
        if (!error.isEmpty()) {
            Ui.text(theme, Ui.ellipsize(theme, error, w - 2 * PAD), x + PAD, y + errorY, Ui.FALSE);
        }
    }

    @Override
    public boolean mousePressed(MouseButton button) {
        for (Field field : fields) {
            if (field.box().isFocused() && !field.box().isMouseOver()) {
                field.box().setFocused(false);
            }
        }
        super.mousePressed(button);
        return true;
    }

    @Override
    public boolean keyPressed(int key, char keyChar) {
        if (!super.keyPressed(key, keyChar) && (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER)) {
            submit();
        }
        return true;
    }
}
