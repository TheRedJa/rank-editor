package com.theredja.rankeditor.client.gui;

import java.util.List;
import java.util.function.Supplier;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.feed_the_beast.ftblib.lib.gui.Theme;
import com.feed_the_beast.ftblib.lib.gui.Widget;
import com.feed_the_beast.ftblib.lib.icon.Color4I;

/** A caption followed by a wrapping row of {@link Chip}s and a "+" button; the content is rebuilt on every refresh. */
@SideOnly(Side.CLIENT)
final class ChipPanel extends Panel {

    /** @param remove null makes the chip read-only */
    record Item(String text, Color4I color, String tooltip, Runnable remove) {
    }

    private static final int LINE = Chip.HEIGHT + 2;
    private static final int GAP = 3;

    private final String caption;
    private final Supplier<List<Item>> items;
    private final TextActionButton addButton;
    private final int captionWidth;

    ChipPanel(Panel parent, GuiRankEditor editor, String caption, Supplier<List<Item>> items, Runnable onAdd) {
        super(parent);
        this.caption = caption;
        this.items = items;
        this.captionWidth = getGui().getTheme().getStringWidth(caption) + 6;
        this.addButton = new TextActionButton(this, "+", onAdd)
                .enabled(editor::canMutate)
                .tooltip(() -> List.of(editor.canEdit() ? "Add" : "Read-only"));
        addButton.setSize(18, Chip.HEIGHT);
    }

    @Override
    public void addWidgets() {
        for (Item item : items.get()) {
            add(new Chip(this, item.text(), item.color(), item.tooltip(), item.remove()));
        }
        add(addButton);
    }

    @Override
    public void alignWidgets() {
        layout(width);
    }

    /** Flows the chips into {@code availableWidth} and returns the resulting height. */
    int layout(int availableWidth) {
        setWidth(availableWidth);
        int x = captionWidth;
        int y = 0;
        for (Widget w : widgets) {
            if (x + w.width > availableWidth && x > captionWidth) {
                x = captionWidth;
                y += LINE;
            }
            w.setPos(x, y);
            x += w.width + GAP;
        }
        setHeight(y + Chip.HEIGHT);
        return height;
    }

    @Override
    public void drawBackground(Theme theme, int x, int y, int w, int h) {
        Ui.text(theme, caption, x, y + (Chip.HEIGHT - 8) / 2, Ui.MUTED);
    }
}
