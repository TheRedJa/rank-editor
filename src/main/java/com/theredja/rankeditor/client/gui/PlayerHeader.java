package com.theredja.rankeditor.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.theredja.rankeditor.model.Holder;
import com.theredja.rankeditor.model.PlayerInfo;

/** Header of a player: name, UUID, online / OP state, the player's own ranks as chips and the ranks that apply. */
@SideOnly(Side.CLIENT)
final class PlayerHeader extends HolderHeader {

    private final GuiRankEditor editor;
    private final Label title;
    private final Label status;
    private final Label uuid;
    private final Label effective;
    private final ChipPanel ranks;

    PlayerHeader(Panel parent, GuiRankEditor editor) {
        super(parent);
        this.editor = editor;
        this.title = new Label(this, () -> {
            Holder player = editor.current();
            return player == null ? "" : "Player: " + editor.state().displayName(player) + (editor.isPlayerPending(player.id) ? "  (modified)" : "");
        }).color(() -> {
            Holder player = editor.current();
            return player != null && editor.isPlayerPending(player.id) ? Ui.PENDING : Ui.TEXT;
        });
        this.status = new Label(this, () -> {
            PlayerInfo info = info();
            return info == null ? "" : (info.online() ? "online" : "offline") + (info.op() ? ", OP" : "");
        }).alignRight().color(() -> {
            PlayerInfo info = info();
            return info != null && info.op() ? Ui.OP : info != null && info.online() ? Ui.TRUE : Ui.MUTED;
        });
        this.uuid = new Label(this, () -> editor.current() == null ? "" : editor.current().id).color(Ui.DIM);
        this.effective = new Label(this, this::effectiveText).color(Ui.MUTED).tooltip(this::effectiveTooltip);
        this.ranks = new ChipPanel(this, editor, "Ranks:", () -> RankActions.parentChips(editor, editor.current()),
                () -> RankActions.openParentMenu(editor, editor.current()));
    }

    private PlayerInfo info() {
        Holder player = editor.current();
        return player == null || editor.state() == null ? null : editor.state().playerInfo.get(UUID.fromString(player.id));
    }

    private String effectiveText() {
        Holder player = editor.current();
        if (player == null) {
            return "";
        }
        List<String> explicit = player.parentIds();
        List<String> names = new ArrayList<>();
        for (Holder rank : editor.state().actualParents(player)) {
            names.add(rank.id + (explicit.contains(rank.id) ? "" : "*"));
        }
        return "Effective: " + (names.isEmpty() ? "(none)" : String.join(", ", names));
    }

    private List<String> effectiveTooltip() {
        List<String> lines = new ArrayList<>();
        lines.add(effectiveText());
        lines.add("Ranks in lookup order (highest power first).");
        lines.add("* = comes from the default player / OP rank, not from this player's own ranks.");
        return lines;
    }

    @Override
    public void addWidgets() {
        add(title);
        add(status);
        add(uuid);
        add(ranks);
        add(effective);
    }

    @Override
    int layout(int w) {
        int statusWidth = Math.min(120, w / 3);
        Ui.place(title, 0, 0, w - statusWidth - 4, 12);
        Ui.place(status, w - statusWidth, 0, statusWidth, 12);
        Ui.place(uuid, 0, 13, w, 10);
        int y = 27;
        int chipsHeight = ranks.layout(w);
        ranks.setPosAndSize(0, y, w, chipsHeight);
        y += chipsHeight + 3;
        Ui.place(effective, 0, y, w, 12);
        return y + 14;
    }
}
