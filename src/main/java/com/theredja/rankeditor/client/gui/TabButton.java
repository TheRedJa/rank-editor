package com.theredja.rankeditor.client.gui;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.gui.Button;
import com.feed_the_beast.ftblib.lib.gui.GuiHelper;
import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.feed_the_beast.ftblib.lib.gui.Theme;
import com.feed_the_beast.ftblib.lib.gui.WidgetType;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;

/** Tab header: FTBLib has no tab control, so this is a button with a selected state and an accent underline. */
@SideOnly(Side.CLIENT)
final class TabButton extends Button {

    private final Supplier<String> label;
    private final BooleanSupplier selected;
    private final Runnable action;

    TabButton(Panel parent, Supplier<String> label, BooleanSupplier selected, Runnable action) {
        super(parent);
        this.label = label;
        this.selected = selected;
        this.action = action;
    }

    @Override
    public String getTitle() {
        return label.get();
    }

    @Override
    public void draw(Theme theme, int x, int y, int w, int h) {
        boolean active = selected.getAsBoolean();
        theme.drawButton(x, y, w, h, active ? WidgetType.MOUSE_OVER : getWidgetType());
        if (active) {
            Ui.ACCENT.draw(x + 3, y + h - 4, w - 6, 2);
        }
        String text = Ui.ellipsize(theme, getTitle(), w - 6);
        Ui.text(theme, text, x + (w - theme.getStringWidth(text)) / 2, y + (h - 8) / 2 - 1, active ? Ui.PENDING : Ui.TEXT);
    }

    @Override
    public void onClicked(MouseButton button) {
        if (button.isLeft()) {
            GuiHelper.playClickSound();
            action.run();
        }
    }
}
