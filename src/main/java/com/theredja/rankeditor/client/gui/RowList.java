package com.theredja.rankeditor.client.gui;

import java.util.List;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.feed_the_beast.ftblib.lib.gui.ScrollBar;
import com.feed_the_beast.ftblib.lib.gui.Theme;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;

/**
 * Scrollable list of fixed-height rows that are drawn directly instead of being widgets, so the cost is proportional
 * to the visible rows rather than the item count. Scrolling is in whole rows, which keeps rows from being cut at the
 * edges and avoids scissoring. The list is a persistent panel: refreshing the editor only re-feeds {@link #setItems}.
 */
@SideOnly(Side.CLIENT)
abstract class RowList<T> extends Panel {

    static final int BAR_WIDTH = 8;

    private final int rowHeight;
    private final ListScrollBar bar;
    private List<T> items = List.of();
    private int mouseX;
    private int mouseY;

    RowList(Panel parent, int rowHeight) {
        super(parent);
        this.rowHeight = rowHeight;
        this.bar = new ListScrollBar();
        bar.setScrollStep(3);
    }

    final void setItems(List<T> list) {
        this.items = list;
        updateBar();
    }

    final List<T> items() {
        return items;
    }

    final void scrollToTop() {
        bar.setValue(0);
    }

    final int contentWidth() {
        return width - BAR_WIDTH;
    }

    final int visibleRows() {
        return Math.max(1, height / rowHeight);
    }

    /** Mouse x relative to the list's left edge, as of the last hover update. */
    final int localMouseX() {
        return mouseX - getX();
    }

    private void updateBar() {
        bar.setMaxValue(Math.max(0, items.size() - visibleRows()));
    }

    /** Index of the row under the mouse, or -1 (also while an overlay is open or over the scroll bar). */
    final int hoverIndex() {
        if (!isMouseOver() || Ui.overlayOpen(this)) {
            return -1;
        }
        int lx = mouseX - getX();
        int ly = mouseY - getY();
        if (lx < 0 || lx >= contentWidth() || ly < 0) {
            return -1;
        }
        int row = ly / rowHeight;
        int index = bar.getValue() + row;
        return row < visibleRows() && index < items.size() ? index : -1;
    }

    @Override
    public void addWidgets() {
        add(bar);
    }

    @Override
    public void alignWidgets() {
        bar.setPosAndSize(width - BAR_WIDTH, 0, BAR_WIDTH, height);
        updateBar();
    }

    @Override
    public void updateMouseOver(int x, int y) {
        super.updateMouseOver(x, y);
        mouseX = x;
        mouseY = y;
    }

    @Override
    public void drawBackground(Theme theme, int x, int y, int w, int h) {
        theme.drawPanelBackground(x, y, w, h);
        if (items.isEmpty()) {
            Ui.text(theme, Ui.ellipsize(theme, emptyText(), contentWidth() - 8), x + 4, y + 4, Ui.MUTED);
            return;
        }
        int first = bar.getValue();
        int end = Math.min(items.size(), first + visibleRows());
        int hover = hoverIndex();
        int rowWidth = contentWidth();
        for (int i = first; i < end; i++) {
            T item = items.get(i);
            int ry = y + (i - first) * rowHeight;
            if (i % 2 == 1) {
                Ui.STRIPE.draw(x, ry, rowWidth, rowHeight);
            }
            if (isSelected(item)) {
                Ui.SELECTED.draw(x, ry, rowWidth, rowHeight);
            } else if (i == hover) {
                Ui.HOVER.draw(x, ry, rowWidth, rowHeight);
            }
            drawRow(theme, item, x, ry, rowWidth, rowHeight, i == hover);
        }
    }

    @Override
    public boolean mousePressed(MouseButton button) {
        if (super.mousePressed(button)) {
            return true;
        }
        int index = hoverIndex();
        if (index < 0) {
            return false;
        }
        rowClicked(items.get(index), button);
        return true;
    }

    @Override
    public boolean mouseScrolled(int scroll) {
        return !Ui.overlayOpen(this) && super.mouseScrolled(scroll);
    }

    @Override
    public void addMouseOverText(List<String> list) {
        int index = hoverIndex();
        if (index >= 0) {
            rowTooltip(items.get(index), list);
        }
        super.addMouseOverText(list);
    }

    protected abstract void drawRow(Theme theme, T item, int x, int y, int w, int h, boolean hover);

    protected boolean isSelected(T item) {
        return false;
    }

    protected void rowClicked(T item, MouseButton button) {
    }

    protected void rowTooltip(T item, List<String> list) {
    }

    protected String emptyText() {
        return "Nothing to show";
    }

    private final class ListScrollBar extends ScrollBar {

        ListScrollBar() {
            super(RowList.this, Plane.VERTICAL, 0);
        }

        @Override
        public int getScrollBarSize() {
            int max = getMaxValue();
            if (max <= 0) {
                return 0;
            }
            int visible = visibleRows();
            return Math.max(10, height * visible / (max + visible));
        }

        @Override
        public boolean canMouseScroll() {
            return RowList.this.isMouseOver();
        }
    }
}
