package com.theredja.rankeditor.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.feed_the_beast.ftblib.lib.gui.Theme;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.theredja.rankeditor.model.Holder;
import com.theredja.rankeditor.model.PlayerInfo;
import com.theredja.rankeditor.model.RankState;

/** Left column of the Players tab: a search box above the player list (online first, then by name). */
@SideOnly(Side.CLIENT)
final class PlayerPanel extends Panel {

    /** One list row with everything precomputed, so drawing and filtering never touch the model. */
    private record PlayerRow(Holder holder, String name, String search, boolean online, boolean op, String ranks) {
    }

    private final GuiRankEditor editor;
    private final InputBox search;
    private final PlayerList list;
    private RankState builtFrom;
    private List<PlayerRow> all = List.of();

    PlayerPanel(Panel parent, GuiRankEditor editor) {
        super(parent);
        this.editor = editor;
        this.search = new InputBox(this, editor).ghost("Search players...").onChange(() -> applyFilter(true));
        this.list = new PlayerList();
    }

    void focusSearch() {
        search.focus();
    }

    @Override
    public void addWidgets() {
        RankState state = editor.state();
        if (state != builtFrom) {
            builtFrom = state;
            all = state == null ? List.of() : build(state);
        }
        applyFilter(false);
        add(search);
        add(list);
    }

    @Override
    public void alignWidgets() {
        Ui.place(search, 0, 0, width, 16);
        Ui.place(list, 0, 20, width, height - 20);
    }

    private static List<PlayerRow> build(RankState state) {
        List<PlayerRow> out = new ArrayList<>(state.players.size());
        for (Holder holder : state.players.values()) {
            PlayerInfo info = state.playerInfo.get(UUID.fromString(holder.id));
            String name = state.displayName(holder);
            String ranks = String.join(", ", holder.parentIds());
            out.add(new PlayerRow(holder, name, (name + ' ' + holder.id).toLowerCase(Locale.ROOT), info != null && info.online(), info != null && info.op(),
                    ranks));
        }
        out.sort(Comparator.comparing(PlayerRow::online).reversed().thenComparing(PlayerRow::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private void applyFilter(boolean toTop) {
        String text = search.getText().trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            list.setItems(all);
            if (toTop) {
                list.scrollToTop();
            }
            return;
        }
        String[] terms = text.split("\\s+");
        List<PlayerRow> shown = new ArrayList<>();
        for (PlayerRow entry : all) {
            boolean match = true;
            for (String term : terms) {
                if (!entry.search().contains(term)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                shown.add(entry);
            }
        }
        list.setItems(shown);
        if (toTop) {
            list.scrollToTop();
        }
    }

    private final class PlayerList extends RowList<PlayerRow> {

        PlayerList() {
            super(PlayerPanel.this, 14);
        }

        @Override
        protected String emptyText() {
            return search.getText().isEmpty() ? "No known players" : "No matching players";
        }

        @Override
        protected boolean isSelected(PlayerRow entry) {
            return entry.holder().id.equals(editor.selectedPlayerId());
        }

        @Override
        protected void drawRow(Theme theme, PlayerRow entry, int x, int y, int w, int h, boolean hover) {
            int ty = y + (h - 8) / 2;
            (entry.online() ? Ui.TRUE : Ui.DIM).draw(x + 3, y + h / 2 - 2, 4, 4);
            int left = x + 11;
            int limit = x + w - 4;
            String name = Ui.ellipsize(theme, entry.name(), limit - left);
            Ui.text(theme, name, left, ty, editor.isPlayerPending(entry.holder().id) ? Ui.PENDING : Ui.TEXT);
            left += theme.getStringWidth(name) + 5;
            if (entry.op() && left + theme.getStringWidth("OP") <= limit) {
                Ui.text(theme, "OP", left, ty, Ui.OP);
                left += theme.getStringWidth("OP") + 5;
            }
            if (!entry.ranks().isEmpty() && limit - left > 24) {
                Ui.text(theme, Ui.ellipsize(theme, entry.ranks(), limit - left), left, ty, Ui.DIM);
            }
        }

        @Override
        protected void rowClicked(PlayerRow entry, MouseButton button) {
            editor.selectPlayer(entry.holder().id);
        }

        @Override
        protected void rowTooltip(PlayerRow entry, List<String> out) {
            out.add(entry.name() + (editor.isPlayerPending(entry.holder().id) ? " (modified)" : ""));
            out.add(entry.holder().id);
            out.add((entry.online() ? "Online" : "Offline") + (entry.op() ? ", OP" : ""));
            out.add(entry.ranks().isEmpty() ? "No personal ranks" : "Ranks: " + entry.ranks());
        }
    }
}
