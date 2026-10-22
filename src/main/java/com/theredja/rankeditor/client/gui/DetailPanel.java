package com.theredja.rankeditor.client.gui;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.theredja.rankeditor.model.Holder;

/** Right area: the header of the selected rank or player above its permission table, or a hint when nothing is selected. */
@SideOnly(Side.CLIENT)
final class DetailPanel extends Panel {

    private final GuiRankEditor editor;
    private final RankHeader rankHeader;
    private final PlayerHeader playerHeader;
    private final PermissionTable table;
    private final Label hint;

    DetailPanel(Panel parent, GuiRankEditor editor) {
        super(parent);
        this.editor = editor;
        this.rankHeader = new RankHeader(this, editor);
        this.playerHeader = new PlayerHeader(this, editor);
        this.table = new PermissionTable(this, editor);
        this.hint = new Label(this, this::hintText).color(Ui.MUTED);
    }

    private String hintText() {
        return editor.tab() == GuiRankEditor.Tab.RANKS ? "Select a rank on the left, or create one with '+ New rank'." : "Select a player on the left.";
    }

    void focusSearch() {
        table.focusSearch();
    }

    private HolderHeader header() {
        return editor.tab() == GuiRankEditor.Tab.RANKS ? rankHeader : playerHeader;
    }

    @Override
    public void addWidgets() {
        Holder current = editor.current();
        if (current == null) {
            add(hint);
        } else {
            add(header());
            add(table);
        }
    }

    @Override
    public void alignWidgets() {
        Ui.place(hint, 4, 4, width - 8, 12);
        if (editor.current() == null) {
            return;
        }
        HolderHeader header = header();
        int headerHeight = header.layout(width);
        header.setPosAndSize(0, 0, width, headerHeight);
        int top = headerHeight + 4;
        Ui.place(table, 0, top, width, height - top);
    }
}
