package com.theredja.rankeditor.client.gui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.gui.GuiHelper;
import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.feed_the_beast.ftblib.lib.gui.Theme;
import com.feed_the_beast.ftblib.lib.gui.Widget;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;

/** Checkbox with a label: green tick when on, red cross when off, dimmed tick when it holds only through a fallback. */
@SideOnly(Side.CLIENT)
final class ToggleBox extends Widget {

    private static final int BOX = 11;

    private final String label;
    private final BooleanSupplier checked;
    private final BooleanSupplier implicit;
    private final Runnable toggle;
    private BooleanSupplier enabled = () -> true;
    private Supplier<List<String>> tooltip;

    ToggleBox(Panel parent, String label, BooleanSupplier checked, BooleanSupplier implicit, Runnable toggle) {
        super(parent);
        this.label = label;
        this.checked = checked;
        this.implicit = implicit;
        this.toggle = toggle;
        setHeight(BOX + 2);
    }

    ToggleBox enabled(BooleanSupplier supplier) {
        this.enabled = supplier;
        return this;
    }

    ToggleBox tooltip(Supplier<List<String>> lines) {
        this.tooltip = lines;
        return this;
    }

    @Override
    public void draw(Theme theme, int x, int y, int w, int h) {
        boolean on = checked.getAsBoolean();
        boolean fallback = !on && implicit.getAsBoolean();
        boolean usable = enabled.getAsBoolean();
        Ui.drawCheckBox(x, y + 1, BOX, on || fallback, fallback ? Ui.TRUE.withAlpha(110) : Ui.TRUE, usable && isMouseOver());
        String text = fallback ? label + " (implicit)" : label;
        Ui.text(theme, Ui.ellipsize(theme, text, w - BOX - 5), x + BOX + 4, y + (h - 8) / 2, usable ? Ui.TEXT : Ui.MUTED);
    }

    @Override
    public boolean mousePressed(MouseButton button) {
        if (!isMouseOver()) {
            return false;
        }
        if (button.isLeft() && enabled.getAsBoolean()) {
            GuiHelper.playClickSound();
            toggle.run();
        }
        return true;
    }

    @Override
    public void addMouseOverText(List<String> list) {
        if (tooltip != null) {
            list.addAll(tooltip.get());
        }
    }
}
