package com.theredja.rankeditor.client.gui;

import java.util.List;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.feed_the_beast.ftblib.lib.gui.Theme;
import com.feed_the_beast.ftblib.lib.icon.Color4I;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.theredja.rankeditor.model.Holder;
import com.theredja.rankeditor.model.RankState;

/** Left column of the Ranks tab: the "+ New rank" button above the rank list ordered by power. */
@SideOnly(Side.CLIENT)
final class RankPanel extends Panel {

    private final GuiRankEditor editor;
    private final TextActionButton newRank;
    private final RankList list;

    RankPanel(Panel parent, GuiRankEditor editor) {
        super(parent);
        this.editor = editor;
        this.newRank = new TextActionButton(this, "+ New rank", () -> RankActions.newRank(editor))
                .enabled(editor::canMutate)
                .tooltip(() -> List.of(editor.canEdit() ? "Create a rank" : "Read-only: you don't have rankeditor.edit"));
        this.list = new RankList();
    }

    @Override
    public void addWidgets() {
        RankState state = editor.state();
        list.setItems(state == null ? List.of() : state.ranksByPower());
        add(newRank);
        add(list);
    }

    @Override
    public void alignWidgets() {
        Ui.place(newRank, 0, 0, width, 16);
        Ui.place(list, 0, 20, width, height - 20);
    }

    /** Rows: name, default-rank markers, power. */
    private final class RankList extends RowList<Holder> {

        RankList() {
            super(RankPanel.this, 14);
        }

        @Override
        protected String emptyText() {
            return "No ranks defined";
        }

        @Override
        protected boolean isSelected(Holder rank) {
            return rank.id.equals(editor.selectedRankId());
        }

        @Override
        protected void drawRow(Theme theme, Holder rank, int x, int y, int w, int h, boolean hover) {
            RankState state = editor.state();
            int ty = y + (h - 8) / 2;
            int right = x + w - 4;
            String power = Integer.toString(state.power(rank));
            Ui.textRight(theme, power, right, ty, Ui.MUTED);
            right -= theme.getStringWidth(power) + 6;
            right = marker(theme, "OP", rank == state.defaultOpRank(), state.explicitDefault(RankState.NODE_DEFAULT_OP) != null, Ui.OP, right, ty);
            right = marker(theme, "P", rank == state.defaultPlayerRank(), state.explicitDefault(RankState.NODE_DEFAULT_PLAYER) != null, Ui.TRUE,
                    right, ty);
            Color4I color = editor.isRankPending(rank.id) ? Ui.PENDING : Ui.TEXT;
            Ui.text(theme, Ui.ellipsize(theme, rank.id, right - x - 8), x + 4, ty, color);
        }

        /** Draws a right-aligned tag, bright when explicitly configured, dim when only the fallback picked this rank. */
        private int marker(Theme theme, String tag, boolean applies, boolean explicit, Color4I color, int right, int ty) {
            if (!applies) {
                return right;
            }
            Ui.textRight(theme, tag, right, ty, explicit ? color : color.withAlpha(110));
            return right - theme.getStringWidth(tag) - 5;
        }

        @Override
        protected void rowClicked(Holder rank, MouseButton button) {
            editor.selectRank(rank.id);
            if (button.isRight()) {
                RankActions.openMenu(editor, rank);
            }
        }

        @Override
        protected void rowTooltip(Holder rank, List<String> out) {
            RankState state = editor.state();
            out.add(rank.id + (editor.isRankPending(rank.id) ? " (modified)" : ""));
            out.add("Power " + state.power(rank));
            List<String> parents = rank.parentIds();
            if (!parents.isEmpty()) {
                out.add("Parents: " + String.join(", ", parents));
            }
            defaultLine(out, "player", rank == state.defaultPlayerRank(), state.explicitDefault(RankState.NODE_DEFAULT_PLAYER) != null, "lowest");
            defaultLine(out, "OP", rank == state.defaultOpRank(), state.explicitDefault(RankState.NODE_DEFAULT_OP) != null, "highest");
            out.add("Right-click for actions");
        }

        private void defaultLine(List<String> out, String who, boolean applies, boolean explicit, String extreme) {
            if (applies) {
                out.add(explicit ? "Default " + who + " rank"
                        : "Default " + who + " rank (implicit: " + extreme + " power rank because no rank sets default_" + who.toLowerCase() + "_rank)");
            }
        }
    }
}
