package com.theredja.rankeditor.client.gui;

import java.util.List;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.feed_the_beast.ftblib.lib.gui.Theme;
import com.feed_the_beast.ftblib.lib.gui.Widget;
import com.feed_the_beast.ftblib.lib.icon.Color4I;

/** Shared colours and small drawing/layout helpers for the editor widgets. */
@SideOnly(Side.CLIENT)
final class Ui {

    static final Color4I TEXT = Color4I.WHITE;
    static final Color4I MUTED = Color4I.rgb(0xA0A0A0);
    static final Color4I DIM = Color4I.rgb(0x858A94);
    static final Color4I TRUE = Color4I.rgb(0x55FF55);
    static final Color4I FALSE = Color4I.rgb(0xFF5555);
    static final Color4I VALUE = Color4I.rgb(0x8FD8FF);
    static final Color4I PENDING = Color4I.rgb(0xFFDD55);
    static final Color4I WARN = Color4I.rgb(0xFFAA00);
    static final Color4I OP = Color4I.rgb(0xFF9F40);
    static final Color4I ACCENT = Color4I.rgb(0x6FA8FF);

    static final Color4I HOVER = Color4I.WHITE.withAlpha(28);
    static final Color4I SELECTED = Color4I.rgb(0x6FA8FF).withAlpha(70);
    static final Color4I STRIPE = Color4I.WHITE.withAlpha(10);
    static final Color4I DIM_SCREEN = Color4I.BLACK.withAlpha(120);
    static final Color4I FRAME_BORDER = Color4I.rgb(0x000000);
    static final Color4I FRAME_EDGE = Color4I.rgb(0x4A4E57);
    static final Color4I FRAME_FILL = Color4I.rgb(0x1E2026).withAlpha(240);
    static final Color4I BOX_FILL = Color4I.rgb(0x2B2E35);

    private Ui() {
    }

    /**
     * Dark window frame. FTBLib's default theme is light grey, on which the editor's light text colours (and shadow)
     * are hard to read, so the editor and its dialogs use this instead of {@code theme.drawGui}.
     */
    static void drawFrame(int x, int y, int w, int h) {
        FRAME_BORDER.draw(x, y, w, h);
        FRAME_EDGE.draw(x + 1, y + 1, w - 2, h - 2);
        FRAME_FILL.draw(x + 2, y + 2, w - 4, h - 4);
    }

    /** A {@code size}x{@code size} box with a high-contrast tick (on) or cross (off), drawn from pixels so no font glyph is needed. */
    static void drawCheckBox(int x, int y, int size, boolean on, Color4I tick, boolean hovered) {
        (hovered ? TEXT : MUTED).draw(x, y, size, size);
        BOX_FILL.draw(x + 1, y + 1, size - 2, size - 2);
        int s = size - 4;
        int ox = x + 2;
        int oy = y + 2;
        if (on) {
            // Short stroke down-right, then a long stroke up-right.
            int mid = s / 3;
            for (int i = 0; i <= mid; i++) {
                tick.draw(ox + i, oy + s / 2 + i - 1, 2, 2);
            }
            for (int i = 0; i <= s - mid - 1; i++) {
                tick.draw(ox + mid + i, oy + s / 2 + mid - 1 - i, 2, 2);
            }
        } else {
            for (int i = 0; i < s - 1; i++) {
                FALSE.draw(ox + i, oy + i, 2, 2);
                FALSE.draw(ox + s - 2 - i, oy + i, 2, 2);
            }
        }
    }

    /** Trims {@code text} to {@code width} pixels, ending in "..." when it was cut. */
    static String ellipsize(Theme theme, String text, int width) {
        if (width <= 0) {
            return "";
        }
        if (theme.getStringWidth(text) <= width) {
            return text;
        }
        int dots = theme.getStringWidth("...");
        return width <= dots ? theme.trimStringToWidth(text, width) : theme.trimStringToWidth(text, width - dots) + "...";
    }

    static void text(Theme theme, String text, int x, int y, Color4I color) {
        theme.drawString(text, x, y, color, Theme.SHADOW);
    }

    static void textRight(Theme theme, String text, int rightX, int y, Color4I color) {
        theme.drawString(text, rightX - theme.getStringWidth(text), y, color, Theme.SHADOW);
    }

    /** Colour for a stored permission value: allow green, anything else boolean-looking red, other values neutral. */
    static Color4I valueColor(String value) {
        return switch (value) {
            case "true" -> TRUE;
            case "false" -> FALSE;
            default -> VALUE;
        };
    }

    static List<String> wrap(Theme theme, String text, int width) {
        return theme.listFormattedStringToWidth(text, width);
    }

    /** True while a context menu, dialog or value editor is shown on top of the editor. */
    static boolean overlayOpen(Widget widget) {
        return widget.getGui().contextMenu != null;
    }

    /** Positions and sizes a child; panels are re-aligned because their own refresh ran with the old size. */
    static <W extends Widget> W place(W widget, int x, int y, int width, int height) {
        widget.setPosAndSize(x, y, width, height);
        if (widget instanceof Panel panel) {
            panel.alignWidgets();
        }
        return widget;
    }
}
