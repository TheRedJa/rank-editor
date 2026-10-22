package com.theredja.rankeditor.client.gui;

import java.util.List;
import java.util.function.Supplier;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.feed_the_beast.ftblib.lib.gui.Theme;
import com.feed_the_beast.ftblib.lib.gui.Widget;
import com.feed_the_beast.ftblib.lib.icon.Color4I;

/** One line of text whose content and colour are read each frame; trimmed to the widget width with a full-text tooltip. */
@SideOnly(Side.CLIENT)
final class Label extends Widget {

    private final Supplier<String> text;
    private Supplier<Color4I> color = () -> Ui.TEXT;
    private Supplier<List<String>> tooltip;
    private boolean alignRight;

    Label(Panel parent, Supplier<String> text) {
        super(parent);
        this.text = text;
        setHeight(12);
    }

    Label color(Color4I fixed) {
        this.color = () -> fixed;
        return this;
    }

    Label color(Supplier<Color4I> dynamic) {
        this.color = dynamic;
        return this;
    }

    Label tooltip(Supplier<List<String>> lines) {
        this.tooltip = lines;
        return this;
    }

    Label alignRight() {
        this.alignRight = true;
        return this;
    }

    @Override
    public void draw(Theme theme, int x, int y, int w, int h) {
        String shown = Ui.ellipsize(theme, text.get(), w);
        if (shown.isEmpty()) {
            return;
        }
        int tx = alignRight ? x + w - theme.getStringWidth(shown) : x;
        Ui.text(theme, shown, tx, y + (h - 8) / 2, color.get());
    }

    @Override
    public void addMouseOverText(List<String> list) {
        if (tooltip != null) {
            list.addAll(tooltip.get());
            return;
        }
        String full = text.get();
        if (!full.isEmpty() && getGui().getTheme().getStringWidth(full) > width) {
            list.add(full);
        }
    }
}
