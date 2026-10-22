package com.theredja.rankeditor.client.gui;

import java.util.List;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.gui.GuiHelper;
import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.feed_the_beast.ftblib.lib.gui.Theme;
import com.feed_the_beast.ftblib.lib.gui.Widget;
import com.feed_the_beast.ftblib.lib.icon.Color4I;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;

/** Small rounded label with an optional "x" that removes the item it stands for. */
@SideOnly(Side.CLIENT)
final class Chip extends Widget {

    static final int HEIGHT = 14;
    private static final int REMOVE_WIDTH = 10;

    private final String text;
    private final Color4I color;
    private final String tooltip;
    private final Runnable remove;
    private int lastMouseX;

    Chip(Panel parent, String text, Color4I color, String tooltip, Runnable remove) {
        super(parent);
        this.text = text;
        this.color = color;
        this.tooltip = tooltip;
        this.remove = remove;
        setSize(parent.getGui().getTheme().getStringWidth(text) + 8 + (remove == null ? 0 : REMOVE_WIDTH), HEIGHT);
    }

    private boolean overRemove() {
        return remove != null && isMouseOver() && lastMouseX >= getX() + width - REMOVE_WIDTH;
    }

    @Override
    public void updateMouseOver(int mouseX, int mouseY) {
        super.updateMouseOver(mouseX, mouseY);
        lastMouseX = mouseX;
    }

    @Override
    public void draw(Theme theme, int x, int y, int w, int h) {
        color.withAlpha(isMouseOver() ? 90 : 55).draw(x, y, w, h);
        GuiHelper.drawHollowRect(x, y, w, h, color.withAlpha(170), true);
        Ui.text(theme, text, x + 4, y + (h - 8) / 2, Ui.TEXT);
        if (remove != null) {
            Ui.text(theme, "x", x + w - REMOVE_WIDTH + 2, y + (h - 8) / 2, overRemove() ? Ui.FALSE : Ui.MUTED);
        }
    }

    @Override
    public boolean mousePressed(MouseButton button) {
        if (!isMouseOver()) {
            return false;
        }
        if (button.isLeft() && overRemove()) {
            GuiHelper.playClickSound();
            remove.run();
        }
        return true;
    }

    @Override
    public void addMouseOverText(List<String> list) {
        if (tooltip != null && !tooltip.isEmpty()) {
            list.add(tooltip);
        }
        if (remove != null) {
            list.add("Click x to remove");
        }
    }
}
