package com.theredja.rankeditor.client.gui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.gui.GuiHelper;
import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.feed_the_beast.ftblib.lib.gui.SimpleTextButton;
import com.feed_the_beast.ftblib.lib.gui.WidgetType;
import com.feed_the_beast.ftblib.lib.icon.Icon;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;

/** Centered text button with a lambda action, an optional enabled state and an optional tooltip. */
@SideOnly(Side.CLIENT)
final class TextActionButton extends SimpleTextButton {

    private final Runnable action;
    private Supplier<String> label;
    private BooleanSupplier enabled = () -> true;
    private Supplier<List<String>> tooltip;

    TextActionButton(Panel parent, String text, Runnable action) {
        super(parent, text, Icon.EMPTY);
        this.action = action;
        setHeight(16);
    }

    TextActionButton label(Supplier<String> dynamic) {
        this.label = dynamic;
        return this;
    }

    TextActionButton enabled(BooleanSupplier supplier) {
        this.enabled = supplier;
        return this;
    }

    TextActionButton tooltip(Supplier<List<String>> lines) {
        this.tooltip = lines;
        return this;
    }

    TextActionButton tooltip(String line) {
        return tooltip(() -> List.of(line));
    }

    @Override
    public String getTitle() {
        return label != null ? label.get() : super.getTitle();
    }

    @Override
    public boolean renderTitleInCenter() {
        return true;
    }

    @Override
    public WidgetType getWidgetType() {
        return enabled.getAsBoolean() ? super.getWidgetType() : WidgetType.DISABLED;
    }

    @Override
    public void onClicked(MouseButton button) {
        if (button.isLeft()) {
            GuiHelper.playClickSound();
            action.run();
        }
    }

    @Override
    public void addMouseOverText(List<String> list) {
        if (tooltip != null) {
            list.addAll(tooltip.get());
        } else {
            super.addMouseOverText(list);
        }
    }
}
