package com.theredja.rankeditor.client.gui;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.gui.Theme;
import com.feed_the_beast.ftblib.lib.gui.WidgetType;
import com.feed_the_beast.ftblib.lib.icon.Color4I;

/**
 * Flat dark theme for every FTBLib widget in the editor (buttons, text boxes, lists, scroll bars, menus), so nothing
 * falls back to FTBLib's light-grey textures. Used by {@link GuiRankEditor} and its overlays via {@code getTheme()}.
 */
@SideOnly(Side.CLIENT)
final class DarkTheme extends Theme {

    static final DarkTheme INSTANCE = new DarkTheme();

    private static final Color4I EDGE = Color4I.rgb(0x4A4E57);
    private static final Color4I EDGE_HOVER = Color4I.rgb(0x7A8090);
    private static final Color4I WIDGET = Color4I.rgb(0x343842);
    private static final Color4I WIDGET_HOVER = Color4I.rgb(0x434856);
    private static final Color4I WIDGET_DISABLED = Color4I.rgb(0x26292F);
    private static final Color4I SUNKEN = Color4I.rgb(0x14161A);
    private static final Color4I SUNKEN_HOVER = Color4I.rgb(0x1C1F24);
    private static final Color4I MENU = Color4I.rgb(0x24272E);
    private static final Color4I SCROLL = Color4I.rgb(0x4A4E57);
    private static final Color4I SCROLL_HOVER = Color4I.rgb(0x666C7A);

    private DarkTheme() {
    }

    private static void box(int x, int y, int w, int h, Color4I edge, Color4I fill) {
        edge.draw(x, y, w, h);
        fill.draw(x + 1, y + 1, w - 2, h - 2);
    }

    @Override
    public Color4I getContentColor(WidgetType type) {
        return type == WidgetType.DISABLED ? Ui.DIM : type == WidgetType.MOUSE_OVER ? Ui.PENDING : Ui.TEXT;
    }

    @Override
    public void drawGui(int x, int y, int w, int h, WidgetType type) {
        Ui.drawFrame(x, y, w, h);
    }

    @Override
    public void drawWidget(int x, int y, int w, int h, WidgetType type) {
        drawButton(x, y, w, h, type);
    }

    @Override
    public void drawButton(int x, int y, int w, int h, WidgetType type) {
        switch (type) {
            case MOUSE_OVER -> box(x, y, w, h, EDGE_HOVER, WIDGET_HOVER);
            case DISABLED -> box(x, y, w, h, WIDGET, WIDGET_DISABLED);
            default -> box(x, y, w, h, EDGE, WIDGET);
        }
    }

    @Override
    public void drawSlot(int x, int y, int w, int h, WidgetType type) {
        box(x, y, w, h, EDGE, type == WidgetType.MOUSE_OVER ? SUNKEN_HOVER : SUNKEN);
    }

    @Override
    public void drawContainerSlot(int x, int y, int w, int h) {
        box(x - 1, y - 1, w + 2, h + 2, EDGE, SUNKEN);
    }

    @Override
    public void drawPanelBackground(int x, int y, int w, int h) {
        box(x, y, w, h, EDGE, SUNKEN);
    }

    @Override
    public void drawTextBox(int x, int y, int w, int h) {
        box(x, y, w, h, EDGE, SUNKEN);
    }

    @Override
    public void drawScrollBarBackground(int x, int y, int w, int h, WidgetType type) {
        box(x, y, w, h, EDGE, SUNKEN);
    }

    @Override
    public void drawScrollBar(int x, int y, int w, int h, WidgetType type, boolean vertical) {
        (type == WidgetType.MOUSE_OVER ? SCROLL_HOVER : SCROLL).draw(x + 1, y + 1, w - 2, h - 2);
    }

    @Override
    public void drawCheckboxBackground(int x, int y, int w, int h, boolean radioButton) {
        box(x, y, w, h, EDGE, SUNKEN);
    }

    @Override
    public void drawCheckbox(int x, int y, int w, int h, WidgetType type, boolean selected, boolean radioButton) {
        if (selected) {
            Ui.TRUE.draw(x + 2, y + 2, w - 4, h - 4);
        }
    }

    @Override
    public void drawHorizontalTab(int x, int y, int w, int h, boolean selected) {
        box(x, y, w, h, selected ? EDGE_HOVER : EDGE, selected ? WIDGET_HOVER : WIDGET);
    }

    @Override
    public void drawContextMenuBackground(int x, int y, int w, int h) {
        box(x, y, w, h, EDGE_HOVER, MENU);
    }
}
